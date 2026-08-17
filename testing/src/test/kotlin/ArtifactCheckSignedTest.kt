import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPPublicKey
import org.bouncycastle.openpgp.PGPPublicKeyRing
import org.bouncycastle.openpgp.PGPSecretKey
import org.bouncycastle.openpgp.PGPSignature
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder
import org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.bouncycastle.openpgp.operator.jcajce.JcePBESecretKeyEncryptorBuilder
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.Security
import java.util.Date

/**
 * The only tests that drive a real signed build end to end.
 *
 * Every other TestKit case sets `signing { setRequired(false) }` and produces no signatures, so
 * `validatePgpSignatures` returns before a single `.asc` is looked at — leaving the signature
 * collection, the POM and module-metadata lookups and the verification itself unexercised in a
 * real Gradle build. This signs with a throwaway in-memory key so those paths actually run.
 */
class ArtifactCheckSignedTest {

    @TempDir
    lateinit var projectDir: Path

    private val keyPassword = "test-password"

    /** One throwaway key, in the two armored forms a build needs: one to sign with, one to check against. */
    private data class TestKey(val secret: String, val public: String)

    /** An armored, password-protected secret key ring suitable for `useInMemoryPgpKeys`, plus its public half. */
    private fun generateKey(): TestKey {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(BouncyCastleProvider())
        }

        val rsa = KeyPairGenerator.getInstance("RSA", BouncyCastleProvider.PROVIDER_NAME)
        rsa.initialize(2048)
        val keyPair = JcaPGPKeyPair(PGPPublicKey.RSA_GENERAL, rsa.generateKeyPair(), Date())
        val checksum = JcaPGPDigestCalculatorProviderBuilder().build().get(HashAlgorithmTags.SHA1)

        val secretKey = PGPSecretKey(
            PGPSignature.DEFAULT_CERTIFICATION,
            keyPair,
            "Artifact Check Test <test@example.org>",
            checksum,
            null,
            null,
            JcaPGPContentSignerBuilder(keyPair.publicKey.algorithm, HashAlgorithmTags.SHA256),
            JcePBESecretKeyEncryptorBuilder(SymmetricKeyAlgorithmTags.AES_256, checksum)
                .setProvider(BouncyCastleProvider.PROVIDER_NAME)
                .build(keyPassword.toCharArray())
        )

        val armoredSecret = ByteArrayOutputStream()
        ArmoredOutputStream(armoredSecret).use { secretKey.encode(it) }

        val armoredPublic = ByteArrayOutputStream()
        ArmoredOutputStream(armoredPublic).use { PGPPublicKeyRing(listOf(secretKey.publicKey)).encode(it) }

        return TestKey(
            secret = armoredSecret.toString(Charsets.UTF_8.name()),
            public = armoredPublic.toString(Charsets.UTF_8.name())
        )
    }

    /**
     * @param artifactCheckBlock configuration to splice in, so a test can point the check at a
     *   public key -- or at the wrong one.
     */
    private fun writeSignedProject(artifactCheckBlock: String = "") {
        projectDir.resolve("settings.gradle.kts").toFile()
            .writeText(PomFixture.singleProjectSettings("signed"))

        val key = generateKey()
        projectDir.resolve("secring.asc").toFile().writeText(key.secret)
        projectDir.resolve("pubring.asc").toFile().writeText(key.public)

        projectDir.resolve("src/main/java").toFile().mkdirs()
        projectDir.resolve("src/main/java/Library.java").toFile().writeText(
            "public class Library { public static int answer() { return 42; } }\n"
        )

        projectDir.resolve("build.gradle.kts").toFile().writeText(
            """
            plugins {
                java
                `maven-publish`
                signing
                id("io.github.yonggoose.maven.central.utility.plugin.check")
                id("io.github.yonggoose.maven.central.utility.plugin.project")
            }

            ${PomFixture.pomBlock()}

            publishing {
                publications {
                    create<MavenPublication>("maven") {
                        from(components["java"])
                    }
                }
            }

            signing {
                useInMemoryPgpKeys(file("secring.asc").readText(), "$keyPassword")
                sign(publishing.publications)
            }

            $artifactCheckBlock
            """.trimIndent()
        )
    }

    @Test
    fun `a genuinely signed publication passes signature verification`() {
        writeSignedProject()

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("checkProjectArtifact", "--info", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
            .build()

        Assertions.assertEquals(TaskOutcome.SUCCESS, result.task(":checkProjectArtifact")?.outcome)

        // Not the "SKIPPED" wording: this run must have actually inspected signatures.
        Assertions.assertTrue(
            result.output.contains("metadata and PGP signatures verified successfully"),
            result.output
        )

        // The overall verdict alone cannot tell "everything was checked" from "only the POM was";
        // `--info` logs one line per file, so assert both kinds were reached.
        Assertions.assertTrue(
            result.output.contains("PGP signature verified for artifact"),
            "the jar's own signature was never inspected"
        )
        Assertions.assertTrue(
            result.output.contains("PGP signature verified for POM pom-default.xml"),
            "the POM signature was never inspected"
        )
        // Gradle Module Metadata is signed and uploaded alongside the POM. findModuleMetadataFile
        // returns null when it was not produced, which would skip this silently -- assert it was
        // genuinely checked rather than skipped.
        Assertions.assertTrue(
            result.output.contains("PGP signature verified for module metadata module.json"),
            "the Gradle Module Metadata signature was never inspected"
        )

        // The Sign dependency has to have pulled signing into the graph, otherwise the
        // signatures would not exist yet and verification would have failed.
        Assertions.assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":signMavenPublication")?.outcome,
            "expected the Sign task to have run; tasks were ${result.tasks.map { it.path }}"
        )
    }

    /**
     * The stale-`build/` case: sign once, then run again with signing excluded.
     *
     * Every `.asc` from the first run is still on disk and every `Signature` object still declares
     * it, so a check that asks only `does this file exist` reports the artifacts as verified while
     * this build signed nothing. The signatures have to be tied to their `Sign` task's outcome for
     * the second run to come back honest.
     */
    @Test
    fun `signatures left over from an earlier run are not counted as this build's`() {
        writeSignedProject()

        GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("checkProjectArtifact")
            .withPluginClasspath()
            .forwardOutput()
            .build()

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("checkProjectArtifact", "-x", "signMavenPublication", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
            .buildAndFail()

        Assertions.assertEquals(TaskOutcome.FAILED, result.task(":checkProjectArtifact")?.outcome)
        Assertions.assertTrue(
            result.output.contains("No PGP signatures were produced"),
            result.output
        )
        Assertions.assertFalse(
            result.output.contains("verified successfully"),
            "leftover .asc files were reported as a successful verification"
        )
    }

    @Test
    fun `signatures are verified against the configured public key`() {
        writeSignedProject(
            """
            artifactCheck {
                publicKeyRing = file("pubring.asc")
            }
            """.trimIndent()
        )

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("checkProjectArtifact", "--info", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
            .build()

        Assertions.assertEquals(TaskOutcome.SUCCESS, result.task(":checkProjectArtifact")?.outcome)

        // "verified against key" is only ever printed by the branch that ran a real verification;
        // the structure-only branch says "carries key ID".
        Assertions.assertTrue(
            result.output.contains("verified against key"),
            "the signatures were not checked against the configured key:\n${result.output}"
        )
        Assertions.assertFalse(
            result.output.contains("checked for structure only"),
            "a public key was configured, so the structural-only warning must not appear"
        )
    }

    /**
     * The end-to-end shape of the case `PgpSignatureVerifierTest` covers directly: a build whose
     * signatures were made by a key the configured ring does not hold. Before public keys were
     * read at all this run reported success.
     */
    @Test
    fun `a signature made by a key outside the configured ring fails the build`() {
        writeSignedProject(
            """
            artifactCheck {
                publicKeyRing = file("someone-elses-pubring.asc")
            }
            """.trimIndent()
        )
        // A valid key ring, just not the one that signed anything here.
        projectDir.resolve("someone-elses-pubring.asc").toFile().writeText(generateKey().public)

        val result = GradleRunner.create()
            .withProjectDir(projectDir.toFile())
            .withArguments("checkProjectArtifact", "--stacktrace")
            .withPluginClasspath()
            .forwardOutput()
            .buildAndFail()

        Assertions.assertEquals(TaskOutcome.FAILED, result.task(":checkProjectArtifact")?.outcome)
        Assertions.assertTrue(
            result.output.contains("which is not in"),
            "expected the report to name the unknown signing key:\n${result.output}"
        )
    }

    // A *corrupt* signature cannot be staged here: rewriting an .asc makes its Sign task out of
    // date, so the next run simply regenerates it. PgpSignatureVerifierTest drives verify()
    // directly for those.
}
