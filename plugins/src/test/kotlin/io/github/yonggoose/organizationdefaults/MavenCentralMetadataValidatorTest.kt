package io.github.yonggoose.organizationdefaults

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MavenCentralMetadataValidatorTest {

    private fun validPom(
        groupId: String? = "io.github.yonggoose",
        artifactId: String? = "my-library",
        version: String? = "1.0.0"
    ): OrganizationDefaults =
        OrganizationDefaults(
            groupId = groupId,
            artifactId = artifactId,
            version = version,
            name = "My Library",
            description = "A description",
            url = "https://example.org",
            licenses = listOf(License(name = "Apache-2.0", url = "https://www.apache.org/licenses/LICENSE-2.0")),
            developers = listOf(Developer(id = "dev1", name = "Developer One")),
            scm = Scm(
                connection = "scm:git:git@github.com:YongGoose/my-library.git",
                developerConnection = "scm:git:git@github.com:YongGoose/my-library.git",
                url = "https://github.com/YongGoose/my-library"
            )
        )

    private fun assertOnlyProblem(errors: List<String>, expectedPrefix: String) {
        assertEquals(1, errors.size, "expected exactly one problem but got $errors")
        assertTrue(errors.single().startsWith(expectedPrefix), "unexpected problem: ${errors.single()}")
    }

    @Test
    fun `a complete pom has no problems`() {
        assertEquals(emptyList(), MavenCentralMetadataValidator.validate(validPom()))
    }

    @Test
    fun `groupIds with digits and hyphens are accepted`() {
        // The previous pattern was ^[a-z]+(\.[a-z][a-z0-9]*)+$, which rejected both of these.
        for (groupId in listOf("log4j.log4j", "io.github.my-org", "io.github.my-org2", "org.apache.maven_core")) {
            assertEquals(
                emptyList(),
                MavenCentralMetadataValidator.validate(validPom(groupId = groupId)),
                "expected '$groupId' to be accepted"
            )
        }
    }

    @Test
    fun `groupIds without a dot or with malformed segments are rejected`() {
        for (groupId in listOf("invalidGroup", "io..github", ".io.github", "io.github.", "io.github.-org", "")) {
            assertOnlyProblem(
                MavenCentralMetadataValidator.validate(validPom(groupId = groupId)),
                "Invalid groupId"
            )
        }
    }

    @Test
    fun `a null groupId is reported rather than crashing`() {
        assertOnlyProblem(MavenCentralMetadataValidator.validate(validPom(groupId = null)), "Invalid groupId")
    }

    @Test
    fun `artifactIds must be non-blank and free of leading or trailing separators`() {
        for (artifactId in listOf("", "   ", "-leading", "trailing-", ".leading", "trailing.")) {
            assertOnlyProblem(
                MavenCentralMetadataValidator.validate(validPom(artifactId = artifactId)),
                "Invalid artifactId"
            )
        }
        assertEquals(emptyList(), MavenCentralMetadataValidator.validate(validPom(artifactId = "my_lib-2")))
    }

    @Test
    fun `dotted artifactIds are accepted`() {
        // Unlike groupId, an artifactId carries no reverse-DNS expectation, and dotted ones are
        // published on Central: org.osgi:org.osgi.core, org.eclipse.jdt:org.eclipse.jdt.core.
        for (artifactId in listOf("org.osgi.core", "org.eclipse.jdt.core", "a")) {
            assertEquals(
                emptyList(),
                MavenCentralMetadataValidator.validate(validPom(artifactId = artifactId)),
                "expected '$artifactId' to be accepted"
            )
        }
    }

    @Test
    fun `snapshot and blank versions are rejected`() {
        for (version in listOf("1.0.0-SNAPSHOT", "", "   ")) {
            assertOnlyProblem(MavenCentralMetadataValidator.validate(validPom(version = version)), "Invalid version")
        }
    }

    @Test
    fun `every required field is reported when the pom holds only coordinates`() {
        val errors = MavenCentralMetadataValidator.validate(
            OrganizationDefaults(groupId = "io.github.yonggoose", artifactId = "my-library", version = "1.0.0")
        )

        assertEquals(
            listOf(
                "Missing name",
                "Missing description",
                "Missing url",
                "Missing licenses",
                "Missing developers",
                "Missing scm"
            ),
            errors.map { it.substringBefore(':') }
        )
    }

    @Test
    fun `a license without a name is reported with its index`() {
        val pom = validPom().copy(
            licenses = listOf(License(name = "MIT"), License(url = "https://example.org/license"))
        )
        assertOnlyProblem(MavenCentralMetadataValidator.validate(pom), "Invalid licenses[1]")
    }

    @Test
    fun `a developer needs either an id or a name`() {
        val idOnly = validPom().copy(developers = listOf(Developer(id = "dev1")))
        assertEquals(emptyList(), MavenCentralMetadataValidator.validate(idOnly))

        val nameOnly = validPom().copy(developers = listOf(Developer(name = "Developer One")))
        assertEquals(emptyList(), MavenCentralMetadataValidator.validate(nameOnly))

        val emailOnly = validPom().copy(developers = listOf(Developer(email = "dev@example.org")))
        assertOnlyProblem(MavenCentralMetadataValidator.validate(emailOnly), "Invalid developers[0]")
    }

    @Test
    fun `scm needs a url but connection details are optional`() {
        assertOnlyProblem(MavenCentralMetadataValidator.validate(validPom().copy(scm = null)), "Missing scm:")
        assertOnlyProblem(
            MavenCentralMetadataValidator.validate(validPom().copy(scm = Scm(connection = "scm:git:..."))),
            "Missing scm.url"
        )
        assertEquals(
            emptyList(),
            MavenCentralMetadataValidator.validate(
                validPom().copy(scm = Scm(url = "https://github.com/YongGoose/my-library"))
            )
        )
    }
}
