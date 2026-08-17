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
            PATTERN.replace(text) { match ->
                // `isNotBlank`, not just non-null: a coordinate set to "" would otherwise
                // substitute to nothing and turn the template into
                // `https://github.com/YongGoose/` -- a URL that looks fine and is wrong. Left in
                // place instead, where `unresolved` picks it up.
                coordinates[match.groupValues[1]]?.takeIf { it.isNotBlank() } ?: match.value
            }
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
        fun scan(text: String?) {
            if (text != null && PATTERN.containsMatchIn(text)) {
                found.add(text)
            }
        }

        // The coordinates are scanned here even though [mapText] skips them, and *because* it
        // skips them: nothing substitutes into a coordinate, so `version = "\${version}-RC"`
        // survives resolution untouched. `validateCoordinates` only rejects a version that is
        // blank or a snapshot, so without this it would publish as written.
        scan(pom.groupId)
        scan(pom.artifactId)
        scan(pom.version)

        mapText(pom) { text ->
            scan(text)
            text
        }
        return found.toList()
    }

    /**
     * Rebuilds [pom] with [transform] applied to every text field except the coordinates.
     *
     * One traversal for both callers on purpose. Two would drift, and the way they would drift is
     * a field that gets substituted but never checked — or checked but never substituted.
     *
     * Every rebuild goes through `copy`, never a constructor. A constructor call compiles just as
     * happily after a field is added to one of these data classes, and silently resets it to its
     * default in every merged POM.
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
                it.copy(
                    name = map(it.name),
                    url = map(it.url),
                    distribution = map(it.distribution),
                    comments = map(it.comments)
                )
            },
            organization = pom.organization?.let { org ->
                org.copy(name = map(org.name), url = map(org.url))
            },
            developers = pom.developers.map {
                it.copy(
                    id = map(it.id),
                    name = map(it.name),
                    email = map(it.email),
                    url = map(it.url),
                    organization = map(it.organization),
                    organizationUrl = map(it.organizationUrl),
                    timezone = map(it.timezone)
                )
            },
            issueManagement = pom.issueManagement?.let { issues ->
                issues.copy(system = map(issues.system), url = map(issues.url))
            },
            mailingLists = pom.mailingLists.map {
                it.copy(
                    name = map(it.name),
                    subscribe = map(it.subscribe),
                    unsubscribe = map(it.unsubscribe),
                    post = map(it.post),
                    archive = map(it.archive)
                )
            },
            scm = pom.scm?.let { scm ->
                scm.copy(
                    connection = map(scm.connection),
                    developerConnection = map(scm.developerConnection),
                    url = map(scm.url)
                )
            }
        )
    }
}

/**
 * [PomPlaceholders.resolve], as an extension so the merge chain reads in one line.
 */
internal fun OrganizationDefaults.resolvePlaceholders(): OrganizationDefaults = PomPlaceholders.resolve(this)
