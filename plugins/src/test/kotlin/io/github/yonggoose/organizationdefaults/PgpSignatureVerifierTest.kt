package io.github.yonggoose.organizationdefaults

import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.BCPGOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.PGPSignatureGenerator
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.KeyPairGenerator
import java.security.Security
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Exercises the signature check against real Bouncy Castle output.
 *
 * The verifier used to return `true` from its catch block, so anything it could not parse was
 * reported as verified. Every negative case here would have passed before that was fixed.
 */
class PgpSignatureVerifierTest {

    @TempDir
    lateinit var tempDir: File

    private fun artifact(name: String = "my-library-1.0.0.jar", content: String = "artifact bytes"): File =
        File(tempDir, name).apply { writeText(content) }

    /** Produces a genuine armored detached signature over [source]. */
    private fun signatureFor(source: File): File {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }

        val generator = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME)
        generator.initialize(2048)
        val keyPair = JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, generator.generateKeyPair(), Date(0))

        val signatureGenerator = PGPSignatureGenerator(
            JcaPGPContentSignerBuilder(keyPair.publicKey.algorithm, HashAlgorithmTags.SHA256)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
        )
        signatureGenerator.init(PGPSignature.BINARY_DOCUMENT, keyPair.privateKey)
        signatureGenerator.update(source.readBytes())

        val target = File(tempDir, source.name + ".asc")
        // Close the file stream explicitly rather than relying on the BC wrappers to cascade:
        // a leaked handle keeps Windows from deleting the @TempDir, failing the test afterwards.
        target.outputStream().use { fileOut ->
            ArmoredOutputStream(fileOut).use { armored ->
                BCPGOutputStream(armored).use { packets -> signatureGenerator.generate().encode(packets) }
            }
        }
        return target
    }

    @Test
    fun `a real detached signature verifies and reports its key id`() {
        val jar = artifact()
        val result = PgpSignatureVerifier.verify(jar, signatureFor(jar))

        assertTrue(result.isOk, "expected the signature to verify but got: ${result.detail}")
        assertTrue(result.detail.contains("0x"), "expected the key id in the detail: ${result.detail}")
    }

    @Test
    fun `an empty signature file fails`() {
        val jar = artifact()
        val empty = File(tempDir, jar.name + ".asc").apply { writeText("") }

        val result = PgpSignatureVerifier.verify(jar, empty)

        assertFalse(result.isOk)
        assertTrue(result.detail.contains("empty"), result.detail)
    }

    @Test
    fun `a file without PGP armor markers fails`() {
        val jar = artifact()
        val notASignature = File(tempDir, jar.name + ".asc")
            .apply { writeText("this is definitely not a signature\n") }

        val result = PgpSignatureVerifier.verify(jar, notASignature)

        assertFalse(result.isOk)
        assertTrue(result.detail.contains("armored"), result.detail)
    }

    @Test
    fun `a truncated signature fails instead of being treated as verified`() {
        val jar = artifact()
        val signature = signatureFor(jar)

        // Keep the BEGIN/END markers so the cheap text check still passes, and drop half of the
        // payload. This is the case the old catch block swallowed into a `true`.
        //
        // Slice by armor structure, not by line index: bcpg may or may not emit a `Version:`
        // header, and an index-based slice silently turns "truncated payload" into "no payload"
        // when it does.
        val lines = signature.readLines()
        val separator = lines.indexOfFirst { it.isBlank() }
        val checksum = lines.indexOfLast { it.startsWith("=") }
        assertTrue(separator in 0 until checksum, "unexpected armor layout: $lines")

        val payload = lines.subList(separator + 1, checksum)
        assertTrue(payload.size >= 2, "payload too short to truncate meaningfully: $payload")

        val truncated = lines.take(separator + 1) + payload.take(payload.size / 2) + lines.drop(checksum)
        signature.writeText(truncated.joinToString("\n"))

        val result = PgpSignatureVerifier.verify(jar, signature)

        assertFalse(result.isOk, "a truncated signature must not verify (detail was: ${result.detail})")
    }

    @Test
    fun `a signature whose payload is garbage fails`() {
        val jar = artifact()
        val signature = signatureFor(jar)
        signature.writeText(
            """
            -----BEGIN PGP SIGNATURE-----

            bm90IGEgcmVhbCBzaWduYXR1cmUgcGF5bG9hZA==
            =AAAA
            -----END PGP SIGNATURE-----
            """.trimIndent()
        )

        val result = PgpSignatureVerifier.verify(jar, signature)

        assertFalse(result.isOk, "garbage inside the armor must not verify (detail was: ${result.detail})")
    }

    @Test
    fun `a missing artifact or signature file fails`() {
        val jar = artifact()
        val signature = signatureFor(jar)

        val missingArtifact = PgpSignatureVerifier.verify(File(tempDir, "absent.jar"), signature)
        assertFalse(missingArtifact.isOk)
        assertTrue(missingArtifact.detail.contains("Artifact file does not exist"), missingArtifact.detail)

        val missingSignature = PgpSignatureVerifier.verify(jar, File(tempDir, "absent.jar.asc"))
        assertFalse(missingSignature.isOk)
        assertTrue(missingSignature.detail.contains("Signature file does not exist"), missingSignature.detail)
    }

    // The signature-to-file pairing used to be derived here from file names and paths, and got
    // it wrong three times. It now comes from Gradle's own `Signature.toSign`, so there is no
    // matching logic left in this class to test -- see ArtifactCheckPluginForProject.collectSignatures.
}
