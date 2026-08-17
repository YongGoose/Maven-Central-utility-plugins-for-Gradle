package io.github.yonggoose.organizationdefaults

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property

/**
 * Configuration for `checkProjectArtifact`, registered as `artifactCheck` by
 * [ArtifactCheckPluginForProject].
 *
 * Only the public key lives here, and only because the plugin cannot work it out on its own. The
 * `signing` plugin holds a *secret* key, and the three ways a build can configure one do not lead
 * back to a public key the same way — `useGpgCmd()` hands signing to an external process that
 * never exposes one. Asking for it explicitly is the one form that works for every setup and keeps
 * the check honest about what it verified.
 *
 * ```kotlin
 * artifactCheck {
 *     publicKeyRing = file("pubring.asc")
 *     // or, for CI:
 *     inMemoryPublicKey = providers.environmentVariable("SIGNING_PUBLIC_KEY")
 * }
 * ```
 *
 * With neither set, signatures are checked for structure only, exactly as before this existed.
 */
abstract class ArtifactCheckExtension {

    /**
     * An exported public key ring, armored (`gpg --armor --export`) or binary (`gpg --export`).
     *
     * It only ever needs the public half. Pointing this at a secret key ring fails with a parse
     * error rather than working by accident.
     */
    abstract val publicKeyRing: RegularFileProperty

    /**
     * The same thing as text, for builds that get the key from an environment variable or a
     * credentials store rather than a file on disk.
     */
    abstract val inMemoryPublicKey: Property<String>
}
