package common

import org.gradle.internal.impldep.org.bouncycastle.bcpg.ArmoredOutputStream
import org.gradle.internal.impldep.org.bouncycastle.bcpg.HashAlgorithmTags
import org.gradle.internal.impldep.org.bouncycastle.jce.provider.BouncyCastleProvider
import org.gradle.internal.impldep.org.bouncycastle.openpgp.PGPPublicKey
import org.gradle.internal.impldep.org.bouncycastle.openpgp.PGPSignature
import org.gradle.internal.impldep.org.bouncycastle.openpgp.operator.PGPDigestCalculator
import org.gradle.internal.impldep.org.bouncycastle.openpgp.operator.jcajce.JcaPGPContentSignerBuilder
import org.gradle.internal.impldep.org.bouncycastle.openpgp.operator.jcajce.JcaPGPDigestCalculatorProviderBuilder
import org.gradle.internal.impldep.org.bouncycastle.openpgp.operator.jcajce.JcaPGPKeyPair
import org.gradle.internal.impldep.org.bouncycastle.openpgp.PGPKeyRingGenerator
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Security
import java.util.Date

class RealEnvironmentSetup private constructor() {

    companion object {
        @JvmStatic
        fun setupGpgHomeWithPgpKey(projectDir: File, keyName: String = "24875D73", passphrase: String = "gradle") {
            Security.addProvider(BouncyCastleProvider())

            val gpgHome = File(projectDir, "gnupg-home")
            gpgHome.mkdirs()
            File(gpgHome, "gpg.conf").writeText("")

            val keyPairGenerator = KeyPairGenerator.getInstance("RSA", "BC")
            keyPairGenerator.initialize(2048, SecureRandom())
            val javaKeyPair = keyPairGenerator.generateKeyPair()

            val publicKeyFile = File(projectDir, "public.asc")
            val privateKeyFile = File(projectDir, "private.asc")
            generatePgpKeyFiles(javaKeyPair, publicKeyFile, privateKeyFile)
        }

        private fun generatePgpKeyFiles(
            javaKeyPair: KeyPair,
            publicKeyFile: File,
            privateKeyFile: File
        ) {
            val identity = "yongjunh@apache.com"

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