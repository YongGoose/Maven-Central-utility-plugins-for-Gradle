package io.github.yonggoose.organizationdefaults

/**
 * Substitutes `${groupId}`, `${artifactId}` and `${version}` into the rest of the POM metadata.
 *
 * The point is templates that are worth declaring once. An organization's SCM URLs differ from
 * repository to repository only by the artifact name, so
 *
 * ```kotlin
 * rootProjectPom {
 *     scm {
 *         url = "https://github.com/YongGoose/\${artifactId}"
 *     }
 * }
 * ```
 *
 * is a default that actually holds across modules, where a literal URL is not.
 *
 * **This runs on the merged result, never on a single extension.** That is the whole feature: the
 * template comes from `rootProjectSetting` or `rootProjectPom`, and the `artifactId` it needs comes
 * from the module's own `projectPom`. Substituting while snapshotting an extension would resolve
 * the root's template against the root's own coordinates — which in this setup are the ones the
 * module was going to override.
 *
 * The coordinates themselves are left alone. They are what everything else is resolved against,
 * and a coordinate referring to another coordinate is a knot with no upside.
 *
 * Kept free of Gradle types, like [MavenCentralMetadataValidator], so it can be unit tested
 * directly.
 */
internal object PomPlaceholders {

    /**
     * `${name}` for a plain identifier.
     *
     * Anything else — `${a.b}`, `$artifactId` without braces — is not a placeholder and is left
     * exactly as written, so metadata that happens to contain a dollar sign is not rewritten.
     */
    private val PATTERN = Regex("""\$\{([A-Za-z_][A-Za-z0-9_]*)}""")

    /** Every placeholder this understands, with the value it stands for. */
    private fun coordinatesOf(pom: OrganizationDefaults): Map<String, String?> = mapOf(
        "groupId" to pom.groupId,
        "artifactId" to pom.artifactId,
        "version" to pom.version
    )

    /**
     * [pom] with every placeholder replaced by the coordinate it names.
     *
     * A placeholder naming something unknown (`${artifctId}`), or a coordinate this POM has not
     * set, is left in place rather than replaced with a guess or an empty string. Whatever is left
     * is then reported by [unresolved]: a URL that silently became
     * `https://github.com/YongGoose/null` would be published without anyone noticing.
     */
    fun resolve(pom: OrganizationDefaults): OrganizationDefaults {
        val coordinates = coordinatesOf(pom)
        return mapText(pom) { text ->
            PATTERN.replace(text) { match -> coordinates[match.groupValues[1]] ?: match.value }
        }
    }

    /**
     * The placeholders still present in [pom], each as the whole text that contains it.
     *
     * Reported with the surrounding value rather than a field path because that is what makes the
     * mistake obvious — `${artifctId}` next to the URL it broke says more than `scm.url` does.
     */
    fun unresolved(pom: OrganizationDefaults): List<String> {
        val found = LinkedHashSet<String>()
        mapText(pom) { text ->
            if (PATTERN.containsMatchIn(text)) {
                found.add(text)
            }
            text
        }
        return found.toList()
    }

    /**
     * Rebuilds [pom] with [transform] applied to every text field except the coordinates.
     *
     * One traversal for both callers on purpose. Two would drift, and the way they would drift is
     * a field that gets substituted but never checked — or checked but never substituted.
     */
    private fun mapText(
        pom: OrganizationDefaults,
        transform: (String) -> String
    ): OrganizationDefaults {
        fun map(value: String?): String? = value?.let(transform)

        return pom.copy(
            name = map(pom.name),
            description = map(pom.description),
            url = map(pom.url),
            inceptionYear = map(pom.inceptionYear),
            licenses = pom.licenses.map {
                License(map(it.name), map(it.url), map(it.distribution), map(it.comments))
            },
            organization = pom.organization?.let { Organization(map(it.name), map(it.url)) },
            developers = pom.developers.map {
                Developer(
                    id = map(it.id),
                    name = map(it.name),
                    email = map(it.email),
                    url = map(it.url),
                    organization = map(it.organization),
                    organizationUrl = map(it.organizationUrl),
                    timezone = map(it.timezone)
                )
            },
            issueManagement = pom.issueManagement?.let { IssueManagement(map(it.system), map(it.url)) },
            mailingLists = pom.mailingLists.map {
                MailingList(
                    name = map(it.name),
                    subscribe = map(it.subscribe),
                    unsubscribe = map(it.unsubscribe),
                    post = map(it.post),
                    archive = map(it.archive)
                )
            },
            scm = pom.scm?.let { Scm(map(it.connection), map(it.developerConnection), map(it.url)) }
        )
    }
}

/**
 * [PomPlaceholders.resolve], as an extension so the merge chain reads in one line.
 */
internal fun OrganizationDefaults.resolvePlaceholders(): OrganizationDefaults = PomPlaceholders.resolve(this)
