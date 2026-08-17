package io.github.yonggoose.organizationdefaults

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPException
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRingCollection
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentVerifierBuilderProvider
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.security.Security

/**
 * The outcome of inspecting one detached signature.
 *
 * [detail] is always populated so the caller can log or report it; it explains the failure for
 * [Verdict.FAILED] and names the signing key for [Verdict.OK].
 */
data class SignatureVerification(val verdict: Verdict, val detail: String) {
    val isOk: Boolean get() = verdict == Verdict.OK

    enum class Verdict { OK, FAILED }

    companion object {
        fun ok(detail: String): SignatureVerification = SignatureVerification(Verdict.OK, detail)

        fun failed(detail: String): SignatureVerification = SignatureVerification(Verdict.FAILED, detail)
    }
}

/**
 * The public keys a build checks its signatures against.
 *
 * Loaded once per run and reused for every file, since parsing a key ring per artifact would be
 * the same work repeated. [origin] only ever appears in messages — a report that says a key is
 * missing is not much use without saying which key ring it was missing from.
 */
class PgpPublicKeys private constructor(
    private val keys: PGPPublicKeyRingCollection,
    val origin: String
) {

    /** The key that made a signature, including subkeys, or `null` when this ring does not have it. */
    internal fun findKey(keyId: Long): PGPPublicKey? = keys.getPublicKey(keyId)

    companion object {
        /**
         * The same, from a file.
         *
         * Reading it belongs here rather than at the call site: a `publicKeyRing` pointed at a
         * directory, or at a file the build cannot open, would otherwise throw a bare
         * `FileNotFoundException` past the message written for exactly that case.
         */
        fun load(file: File): PgpPublicKeys {
            val origin = "'${file.path}'"
            val bytes = try {
                file.readBytes()
            } catch (e: Exception) {
                throw IllegalArgumentException(
                    "Could not read a PGP public key ring from $origin: " +
                        "${e.javaClass.simpleName}: ${e.message}",
                    e
                )
            }
            return load(bytes, origin)
        }

        /**
         * Reads an armored or binary public key ring out of [source].
         *
         * Throws rather than returning null: a build that configured a key and got an unreadable
         * one must not quietly fall back to checking structure only. That would turn configuring
         * verification into a no-op, silently, which is the failure mode this whole task exists
         * to remove.
         */
        fun load(source: ByteArray, origin: String): PgpPublicKeys {
            PgpSignatureVerifier.ensureProvider()
            val keys = try {
                ByteArrayInputStream(source).use { bytes ->
                    PGPPublicKeyRingCollection(PGPUtil.getDecoderStream(bytes), JcaKeyFingerprintCalculator())
                }
            } catch (e: Exception) {
                throw IllegalArgumentException(
                    "Could not read a PGP public key ring from $origin: " +
                        "${e.javaClass.simpleName}: ${e.message}. Export one with " +
                        "'gpg --armor --export <key-id>'.",
                    e
                )
            }
            if (!keys.keyRings.hasNext()) {
                throw IllegalArgumentException("No PGP public keys found in $origin.")
            }
            return PgpPublicKeys(keys, origin)
        }
    }
}

/**
 * Inspects detached PGP signatures with Bouncy Castle.
 *
 * Deliberately free of Gradle types so the verdicts can be unit tested against real signature
 * files. Anything that cannot be read is a failure, never a pass — an unreadable signature is
 * not a verified signature.
 *
 * Handles both ASCII-armored (`.asc`) and raw binary (`.sig`) detached signatures, since
 * `signing.signatureType` decides which a build produces.
 *
 * How much is checked depends on whether the caller supplies public keys. Without them this reads
 * the signature's *structure* only, and cannot tell a well-formed signature over different content
 * from a correct one. With them the signature is verified against the bytes it covers, which is
 * the check Maven Central's consumers will eventually perform.
 */
object PgpSignatureVerifier {

    init {
        ensureProvider()
    }

    internal fun ensureProvider() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    /**
     * @param publicKeys the ring to verify against, or `null` to check the signature's structure
     *   only. A caller that has keys and passes them gets a verdict about the content; a caller
     *   that passes `null` gets one about the file being a parseable detached signature.
     */
    @JvmOverloads
    fun verify(
        artifactFile: File,
        signatureFile: File,
        publicKeys: PgpPublicKeys? = null
    ): SignatureVerification {
        if (!artifactFile.exists()) {
            return SignatureVerification.failed("Artifact file does not exist: ${artifactFile.absolutePath}")
        }
        if (!signatureFile.exists()) {
            return SignatureVerification.failed("Signature file does not exist: ${signatureFile.absolutePath}")
        }
        if (signatureFile.length() == 0L) {
            return SignatureVerification.failed("Signature file is empty: ${signatureFile.absolutePath}")
        }

        // No ASCII-armor marker pre-check: `signing.signatureType` can emit raw binary `.sig`
        // files, and rejecting those on missing BEGIN/END text would fail a correctly signed
        // build. PGPUtil.getDecoderStream handles both forms, so let the parse be the verdict.
        val signature = when (val parsed = readSignature(artifactFile, signatureFile)) {
            is ParseResult.Failed -> return SignatureVerification.failed(parsed.detail)
            is ParseResult.Parsed -> parsed.signature
        }

        return if (publicKeys == null) {
            SignatureVerification.ok(
                "Signature for ${artifactFile.name} carries key ID ${keyId(signature)}."
            )
        } else {
            verifyAgainst(artifactFile, signature, publicKeys)
        }
    }

    private fun verifyAgainst(
        artifactFile: File,
        signature: PGPSignature,
        publicKeys: PgpPublicKeys
    ): SignatureVerification =
        // The key lookup is inside the try, not before it: getPublicKey declares PGPException, so
        // a ring holding one entry BouncyCastle chokes on would otherwise escape the task action
        // entirely instead of joining the other per-file verdicts.
        try {
            val key = publicKeys.findKey(signature.keyID)
                ?: return SignatureVerification.failed(
                    "${artifactFile.name} is signed with key ${keyId(signature)}, which is not in " +
                        "${publicKeys.origin}. Either the wrong key signed it, or the configured " +
                        "key ring is missing that key."
                )

            signature.init(
                JcaPGPContentVerifierBuilderProvider().setProvider(BouncyCastleProvider.PROVIDER_NAME),
                key
            )
            // Streamed rather than `readBytes()`: an artifact is whatever size the build produces,
            // and a fat jar read whole is a heap spike for no reason.
            FileInputStream(artifactFile).use { input ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) {
                        break
                    }
                    signature.update(buffer, 0, read)
                }
            }

            if (signature.verify()) {
                SignatureVerification.ok(
                    "Signature for ${artifactFile.name} verified against key ${keyId(signature)}."
                )
            } else {
                SignatureVerification.failed(
                    "The signature for ${artifactFile.name} does not match its contents. It was " +
                        "made with key ${keyId(signature)}, so the file has changed since it was " +
                        "signed, or the signature belongs to a different file."
                )
            }
        } catch (e: PGPException) {
            SignatureVerification.failed(
                "Could not verify the signature for ${artifactFile.name} against " +
                    "${publicKeys.origin}: ${e.javaClass.simpleName}: ${e.message}"
            )
        } catch (e: Exception) {
            SignatureVerification.failed(
                "Could not read ${artifactFile.name} while verifying its signature: " +
                    "${e.javaClass.simpleName}: ${e.message}"
            )
        }

    /**
     * The signature in [signatureFile], or why there is none.
     *
     * A return type rather than a nullable plus a field: this is an `object`, Gradle runs tasks
     * from different projects in parallel, and a failure message parked in a property would be
     * read by whichever call got there second.
     */
    private sealed interface ParseResult {
        data class Parsed(val signature: PGPSignature) : ParseResult

        data class Failed(val detail: String) : ParseResult
    }

    private fun readSignature(artifactFile: File, signatureFile: File): ParseResult =
        try {
            FileInputStream(signatureFile).use { input ->
                val pgpFactory = PGPObjectFactory(PGPUtil.getDecoderStream(input), JcaKeyFingerprintCalculator())
                when (val parsed = pgpFactory.nextObject()) {
                    !is PGPSignatureList -> ParseResult.Failed(
                        "Expected a detached signature list for ${artifactFile.name} but got " +
                            (parsed?.javaClass?.name ?: "nothing") + "."
                    )
                    else -> if (parsed.size() == 0) {
                        ParseResult.Failed("No signatures found in the signature file for ${artifactFile.name}.")
                    } else {
                        // Only the first, as before. Gradle's signing plugin emits exactly one per
                        // file, and inventing a policy for the rest without a build that produces
                        // them would be guesswork.
                        ParseResult.Parsed(parsed[0])
                    }
                }
            }
        } catch (e: Exception) {
            // Fail closed: an unreadable signature is not a verified signature.
            ParseResult.Failed(
                "Could not read the PGP signature for ${artifactFile.name}: ${e.javaClass.simpleName}: ${e.message}"
            )
        }

    private fun keyId(signature: PGPSignature): String = String.format("0x%X", signature.keyID)
}
