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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

        val target = File(tempDir, PgpSignatureVerifier.signatureNameFor(source))
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
        val empty = File(tempDir, PgpSignatureVerifier.signatureNameFor(jar)).apply { writeText("") }

        val result = PgpSignatureVerifier.verify(jar, empty)

        assertFalse(result.isOk)
        assertTrue(result.detail.contains("empty"), result.detail)
    }

    @Test
    fun `a file without PGP armor markers fails`() {
        val jar = artifact()
        val notASignature = File(tempDir, PgpSignatureVerifier.signatureNameFor(jar))
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
        val lines = signature.readLines()
        val truncated = lines.take(2) + lines.drop(2).dropLast(1).take(1) + lines.last()
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

    @Test
    fun `the sibling signature wins when publications produce identically named files`() {
        // Every MavenPublication writes build/publications/<name>/pom-default.xml, so a build with
        // several publications has several pom-default.xml.asc files. Keying on the name alone
        // paired a POM with another publication's signature.
        val pluginMavenPom = File(tempDir, "publications/pluginMaven/pom-default.xml")
        val markerPom = File(tempDir, "publications/pluginMarkerMaven/pom-default.xml")
        val signatures = listOf(
            File(tempDir, "publications/pluginMaven/pom-default.xml.asc"),
            File(tempDir, "publications/pluginMarkerMaven/pom-default.xml.asc")
        )

        assertEquals(
            signatures[0].absoluteFile,
            PgpSignatureVerifier.resolveSignatureFor(pluginMavenPom, signatures)?.absoluteFile
        )
        assertEquals(
            signatures[1].absoluteFile,
            PgpSignatureVerifier.resolveSignatureFor(markerPom, signatures)?.absoluteFile
        )
    }

    @Test
    fun `an unambiguous name match is accepted when the signature is not a sibling`() {
        val jar = File(tempDir, "libs/my-library-1.0.0.jar")
        val elsewhere = listOf(File(tempDir, "signatures/my-library-1.0.0.jar.asc"))

        assertEquals(
            elsewhere.single().absoluteFile,
            PgpSignatureVerifier.resolveSignatureFor(jar, elsewhere)?.absoluteFile
        )
    }

    @Test
    fun `an ambiguous name match with no sibling resolves to nothing`() {
        val pom = File(tempDir, "publications/pluginMaven/pom-default.xml")
        val decoys = listOf(
            File(tempDir, "publications/otherA/pom-default.xml.asc"),
            File(tempDir, "publications/otherB/pom-default.xml.asc")
        )

        assertNull(
            PgpSignatureVerifier.resolveSignatureFor(pom, decoys),
            "an ambiguous match must fail closed rather than pick one arbitrarily"
        )
    }

    @Test
    fun `the sources jar signature never satisfies the main jar`() {
        val mainJar = File(tempDir, "libs/my-library-1.0.0.jar")
        val sourcesSignature = listOf(File(tempDir, "libs/my-library-1.0.0-sources.jar.asc"))

        assertNull(PgpSignatureVerifier.resolveSignatureFor(mainJar, sourcesSignature))
    }

    @Test
    fun `an empty signature set resolves to nothing`() {
        assertNull(PgpSignatureVerifier.resolveSignatureFor(File(tempDir, "a.jar"), emptyList()))
    }

    @Test
    fun `signature names are derived exactly, never by stripping the extension`() {
        // The old prefix match paired foo-1.0.jar with foo-1.0-sources.jar.asc.
        assertEquals("my-library-1.0.0.jar.asc", PgpSignatureVerifier.signatureNameFor(File("my-library-1.0.0.jar")))
        assertEquals("pom-default.xml.asc", PgpSignatureVerifier.signatureNameFor(File("pom-default.xml")))

        val mainJar = File("my-library-1.0.0.jar")
        val sourcesJar = File("my-library-1.0.0-sources.jar")
        assertFalse(
            PgpSignatureVerifier.signatureNameFor(mainJar) == PgpSignatureVerifier.signatureNameFor(sourcesJar),
            "the main jar and the sources jar must not resolve to the same signature"
        )
    }
}
