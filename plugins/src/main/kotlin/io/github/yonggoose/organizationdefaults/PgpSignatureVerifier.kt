package io.github.yonggoose.organizationdefaults

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPObjectFactory
import org.bouncycastle.openpgp.PGPSignatureList
import org.bouncycastle.openpgp.PGPUtil
import org.bouncycastle.openpgp.operator.jcajce.JcaKeyFingerprintCalculator
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
 * Inspects detached `.asc` signatures with Bouncy Castle.
 *
 * Deliberately free of Gradle types so the verdicts can be unit tested against real signature
 * files. Anything that cannot be read is a failure, never a pass — an unreadable signature is
 * not a verified signature.
 *
 * Scope: this validates the *structure* of a signature, not its cryptographic validity against a
 * public key, so it cannot detect a well-formed signature produced over different content. Full
 * verification is tracked in
 * https://github.com/YongGoose/Maven-Central-utility-plugins-for-Gradle/issues/22.
 */
object PgpSignatureVerifier {

    private const val PGP_SIGNATURE_HEADER = "BEGIN PGP SIGNATURE"
    private const val PGP_SIGNATURE_FOOTER = "END PGP SIGNATURE"

    init {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }
    }

    fun verify(artifactFile: File, signatureFile: File): SignatureVerification {
        if (!artifactFile.exists()) {
            return SignatureVerification.failed("Artifact file does not exist: ${artifactFile.absolutePath}")
        }
        if (!signatureFile.exists()) {
            return SignatureVerification.failed("Signature file does not exist: ${signatureFile.absolutePath}")
        }
        if (signatureFile.length() == 0L) {
            return SignatureVerification.failed("Signature file is empty: ${signatureFile.absolutePath}")
        }

        val signatureContent = signatureFile.readText()
        if (!signatureContent.contains(PGP_SIGNATURE_HEADER) || !signatureContent.contains(PGP_SIGNATURE_FOOTER)) {
            return SignatureVerification.failed(
                "Not an armored PGP signature (missing BEGIN/END markers): ${signatureFile.absolutePath}"
            )
        }

        return readSignatureList(artifactFile, signatureFile)
    }

    private fun readSignatureList(artifactFile: File, signatureFile: File): SignatureVerification {
        return try {
            FileInputStream(signatureFile).use { input ->
                val pgpFactory = PGPObjectFactory(PGPUtil.getDecoderStream(input), JcaKeyFingerprintCalculator())
                val parsed = pgpFactory.nextObject()

                if (parsed !is PGPSignatureList) {
                    val actual = parsed?.javaClass?.name ?: "nothing"
                    return SignatureVerification.failed(
                        "Expected a detached signature list for ${artifactFile.name} but got $actual."
                    )
                }
                if (parsed.size() == 0) {
                    return SignatureVerification.failed(
                        "No signatures found in the signature file for ${artifactFile.name}."
                    )
                }

                SignatureVerification.ok(
                    "Signature for ${artifactFile.name} carries key ID ${String.format("0x%X", parsed[0].keyID)}."
                )
            }
        } catch (e: Exception) {
            // Fail closed: an unreadable signature is not a verified signature.
            SignatureVerification.failed(
                "Could not read the PGP signature for ${artifactFile.name}: ${e.javaClass.simpleName}: ${e.message}"
            )
        }
    }
}
