import org.bouncycastle.bcpg.ArmoredOutputStream
import org.bouncycastle.bcpg.HashAlgorithmTags
import org.bouncycastle.bcpg.SymmetricKeyAlgorithmTags
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openpgp.PGPPublicKey
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
 * The only test that drives a real signed build end to end.
 *
 * Every other TestKit case sets `signing { setRequired(false) }`, which returns from
 * `validatePgpSignatures` before a single `.asc` is looked at — so the signature matching, the
 * POM lookup and the conditional `Sign` dependency were entirely unexercised in a real Gradle
 * build. This signs with a throwaway in-memory key so those paths actually run.
 */
class ArtifactCheckSignedTest {

    @TempDir
    lateinit var projectDir: Path

    private val keyPassword = "test-password"

    /** An armored, password-protected secret key ring suitable for `useInMemoryPgpKeys`. */
    private fun generateArmoredSecretKey(): String {
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

        val armored = ByteArrayOutputStream()
        ArmoredOutputStream(armored).use { secretKey.encode(it) }
        return armored.toString(Charsets.UTF_8.name())
    }

    private fun writeSignedProject() {
        projectDir.resolve("secring.asc").toFile().writeText(generateArmoredSecretKey())

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

        // The conditional dependsOn(provider { ... }) has to have pulled Sign into the graph,
        // otherwise the signatures would not exist yet and verification would have failed.
        Assertions.assertEquals(
            TaskOutcome.SUCCESS,
            result.task(":signMavenPublication")?.outcome,
            "expected the Sign task to have run; tasks were ${result.tasks.map { it.path }}"
        )
    }

    // A "signature is missing" case cannot be staged here: deleting an .asc makes its Sign task
    // out of date, so the next run simply regenerates it. That path is covered instead by
    // PgpSignatureVerifierTest, which drives resolveSignatureFor and verify() directly.
}
