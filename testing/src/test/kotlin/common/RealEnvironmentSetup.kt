package common

import org.gradle.internal.impldep.org.bouncycastle.bcpg.ArmoredOutputStream
import org.gradle.internal.impldep.org.bouncycastle.bcpg.HashAlgorithmTags
import org.gradle.internal.impldep.org.bouncycastle.jce.provider.BouncyCastleProvider
import org.gradle.internal.impldep.org.bouncycastle.openpgp.PGPEncryptedData
import org.gradle.internal.impldep.org.bouncycastle.openpgp.PGPKeyPair
import org.gradle.internal.impldep.org.bouncycastle.openpgp.PGPKeyRingGenerator
import org.gradle.internal.impldep.org.bouncycastle.openpgp.PGPPrivateKey
import org.gradle.internal.impldep.org.bouncycastle.openpgp.PGPPublicKey
import org.gradle.internal.impldep.org.bouncycastle.openpgp.PGPSignature
import org.gradle.internal.impldep.org.bouncycastle.openpgp.PGPSignatureSubpacketVector
import org.gradle.internal.impldep.org.bouncycastle.openpgp.operator.PGPDigestCalculator
import org.gradle.internal.impldep.org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder
import org.gradle.internal.impldep.org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder
import org.gradle.internal.impldep.org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Security
import java.util.Date

class RealEnvironmentSetup private constructor() {

    companion object {
        @JvmStatic
        fun generateRealPgpKeys(projectDir: File) {
            Security.addProvider(BouncyCastleProvider())

            val keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC")
            keyPairGenerator.initialize(2048, SecureRandom())
            val javaKeyPair = keyPairGenerator.generateKeyPair()

            val publicKeyFile = File(projectDir, "public.asc")
            val privateKeyFile = File(projectDir, "private.asc")

            generatePgpKeyFiles(javaKeyPair, publicKeyFile, privateKeyFile)
        }

        @JvmStatic
        fun generateRealSignedArtifacts(projectDir: File) {
            val buildDir = File(projectDir, "build")
            buildDir.mkdirs()

            val publicationsDir = File(buildDir, "publications/mavenJava")
            publicationsDir.mkdirs()

            val projectName = projectDir.name

            val jarFile = File(publicationsDir, "$projectName.jar")
            val javadocJarFile = File(publicationsDir, "$projectName-javadoc.jar")
            val sourcesJarFile = File(publicationsDir, "$projectName-sources.jar")
            val pomFile = File(publicationsDir, "pom-default.xml")

            createRealArtifact(jarFile)
            createRealArtifact(javadocJarFile)
            createRealArtifact(sourcesJarFile)
            pomFile.writeText("""
                <?xml version="1.0" encoding="UTF-8"?>
                <project>
                    <groupId>io.github.yonggoose</groupId>
                    <artifactId>$projectName</artifactId>
                    <version>1.0.0</version>
                </project>
            """.trimIndent())

            createRealSignatureFile(jarFile)
            createRealSignatureFile(javadocJarFile)
            createRealSignatureFile(sourcesJarFile)
            createRealSignatureFile(pomFile)
        }

        private fun createRealArtifact(file: File) {
            file.outputStream().use { output ->
                val content = "PK\u0003\u0004\u0014\u0000\u0000\u0000\u0008\u0000"
                output.write(content.toByteArray())
                output.write(ByteArray(100) { it.toByte() })
            }
        }

        private fun createRealSignatureFile(artifactFile: File) {
            try {
                val signatureFile = File(artifactFile.parentFile, "${artifactFile.name}.asc")

                val signatureContent = """
                -----BEGIN PGP SIGNATURE-----
                
                iQEcBAABCAAGBQJkA1c1AAoJEJ6+CcKcIbSx0kEH/1KNk7vliiY7vlxOOeWBYQye
                nHsL8XEkjvPBNJBWpCtiRgUf9B5VKgFjh6MheTEQVJ6q+IOc5Ic8SubHtTy1f0M5
                7QZw/CNO0WM0iW4Dgw9X86QNbW5H1zF0KIXnlz+mcCgCVOHlYqpBDI2aWQM+WUJQ
                vvIVDkFHKlLb+YrKN6eQLfQUVuEw9JcTkPPgLqEz5/hQHUb7TlE+SBVOUgDZcEWV
                kgKuXE8pL0GbAPBh2SdMoIEsKH2YqwFNL4yYRyDaOvBKEXZbGjhJAaeJaFCQJA2s
                zRy5ecpK5PLm4aCJ4+IIuCvhSQsV6S3JZt1AKPn/uaJ0T8jWTQWWYIb6XKE=
                =t/i8
                -----END PGP SIGNATURE-----
            """.trimIndent()

                signatureFile.writeText(signatureContent)
            } catch (e: Exception) {
                println("Warning: Could not create PGP signature: ${e.message}")
            }
        }

        private fun generatePgpKeyFiles(
            javaKeyPair: KeyPair,
            publicKeyFile: File,
            privateKeyFile: File
        ) {
            val identity = "yongjunh@apache.com"
            val passphrase = "test123".toCharArray()

            val pgpKeyPair = JcaPGPKeyPair(
                PGPPublicKey.RSA_GENERAL,
                javaKeyPair,
                Date()
            )

            val pgpCalculator: PGPDigestCalculator = JcaPGPDigestCalculatorProviderBuilder()
                .setProvider("BC")
                .build()
                .get(HashAlgorithmTags.SHA256)

            val contentSignerBuilder = JcaPGPContentSignerBuilder(
                PGPPublicKey.RSA_GENERAL,
                HashAlgorithmTags.SHA256
            )

            val keyRingGen = PGPKeyRingGenerator(
                PGPSignature.POSITIVE_CERTIFICATION,
                pgpKeyPair,
                identity,
                pgpCalculator,
                null,
                null,
                contentSignerBuilder,
                null
            )

            val publicKeyRing = keyRingGen.generatePublicKeyRing()
            publicKeyFile.outputStream().use { out ->
                ArmoredOutputStream(out).use { armoredOut ->
                    publicKeyRing.encode(armoredOut)
                }
            }

            val secretKeyRing = keyRingGen.generateSecretKeyRing()
            privateKeyFile.outputStream().use { out ->
                ArmoredOutputStream(out).use { armoredOut ->
                    secretKeyRing.encode(armoredOut)
                }
            }
        }
    }
}
