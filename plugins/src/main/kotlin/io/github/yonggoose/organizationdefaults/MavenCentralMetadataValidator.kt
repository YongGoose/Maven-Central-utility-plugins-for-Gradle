package io.github.yonggoose.organizationdefaults

/**
 * Checks [OrganizationDefaults] against what Maven Central requires of a POM.
 *
 * Kept free of Gradle types so it can be unit tested directly, and returns every problem it
 * finds rather than the first, so one run tells you everything that is wrong.
 *
 * See https://central.sonatype.org/publish/requirements/
 */
object MavenCentralMetadataValidator {

    const val SNAPSHOT_SUFFIX: String = "-SNAPSHOT"

    /**
     * A single Maven coordinate segment: alphanumerics plus `_`, with `-` allowed only inside.
     * Deliberately permits digits and hyphens, which real coordinates such as `log4j.log4j`
     * and `io.github.my-org` rely on.
     */
    private const val COORDINATE_SEGMENT = "[A-Za-z0-9_](?:[A-Za-z0-9_-]*[A-Za-z0-9_])?"

    /** Central expects a reverse-DNS namespace, so at least one dot-separated segment. */
    private val groupIdPattern = Regex("^" + COORDINATE_SEGMENT + "(?:\\." + COORDINATE_SEGMENT + ")+\$")

    /**
     * Maven's coordinate rule is `[A-Za-z0-9_\-.]+`, and dotted artifactIds are published on
     * Central today (`org.osgi:org.osgi.core`, `org.eclipse.jdt:org.eclipse.jdt.core`). Only the
     * groupId carries the reverse-DNS expectation; requiring the same shape here would reject
     * artifacts that publish fine.
     */
    private val artifactIdPattern = Regex("^[A-Za-z0-9_](?:[A-Za-z0-9_.-]*[A-Za-z0-9_])?\$")

    fun validate(pom: OrganizationDefaults): List<String> {
        val errors = mutableListOf<String>()

        validateCoordinates(pom, errors)

        if (pom.name.isNullOrBlank()) {
            errors.add("Missing name: Maven Central requires a project name.")
        }
        if (pom.description.isNullOrBlank()) {
            errors.add("Missing description: Maven Central requires a project description.")
        }
        if (pom.url.isNullOrBlank()) {
            errors.add("Missing url: Maven Central requires a project URL.")
        }

        validateLicenses(pom, errors)
        validateDevelopers(pom, errors)
        validateScm(pom, errors)

        return errors
    }

    private fun validateCoordinates(pom: OrganizationDefaults, errors: MutableList<String>) {
        val groupId = pom.groupId
        if (groupId.isNullOrBlank() || !groupIdPattern.matches(groupId)) {
            errors.add(
                "Invalid groupId: must be a dotted Maven coordinate such as 'io.github.yonggoose' " +
                    "(was: ${describe(groupId)})."
            )
        }

        val artifactId = pom.artifactId
        if (artifactId.isNullOrBlank() || !artifactIdPattern.matches(artifactId)) {
            errors.add(
                "Invalid artifactId: must be a non-blank Maven coordinate such as 'my-library' " +
                    "(was: ${describe(artifactId)})."
            )
        }

        val version = pom.version
        if (version.isNullOrBlank() || version.endsWith(SNAPSHOT_SUFFIX)) {
            errors.add("Invalid version: The version must not be null, blank, or end with '$SNAPSHOT_SUFFIX'.")
        }
    }

    private fun validateLicenses(pom: OrganizationDefaults, errors: MutableList<String>) {
        if (pom.licenses.isEmpty()) {
            errors.add("Missing licenses: Maven Central requires at least one license.")
            return
        }
        pom.licenses.forEachIndexed { index, license ->
            if (license.name.isNullOrBlank()) {
                errors.add("Invalid licenses[$index]: 'name' is required.")
            }
        }
    }

    private fun validateDevelopers(pom: OrganizationDefaults, errors: MutableList<String>) {
        if (pom.developers.isEmpty()) {
            errors.add("Missing developers: Maven Central requires at least one developer.")
            return
        }
        pom.developers.forEachIndexed { index, developer ->
            if (developer.id.isNullOrBlank() && developer.name.isNullOrBlank()) {
                errors.add("Invalid developers[$index]: at least one of 'id' or 'name' is required.")
            }
        }
    }

    private fun validateScm(pom: OrganizationDefaults, errors: MutableList<String>) {
        val scm = pom.scm
        if (scm == null) {
            errors.add("Missing scm: Maven Central requires source control information.")
            return
        }
        if (scm.url.isNullOrBlank()) {
            errors.add("Missing scm.url: Maven Central requires the repository URL.")
        }
    }

    private fun describe(value: String?): String = if (value == null) "<not set>" else "'$value'"
}
