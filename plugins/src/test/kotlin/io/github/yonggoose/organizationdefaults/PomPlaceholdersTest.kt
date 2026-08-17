package io.github.yonggoose.organizationdefaults

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PomPlaceholdersTest {

    private fun pom(
        groupId: String? = "io.github.yonggoose",
        artifactId: String? = "library",
        version: String? = "1.0.0",
        scm: Scm? = null,
        url: String? = null,
        name: String? = null
    ) = OrganizationDefaults(
        groupId = groupId,
        artifactId = artifactId,
        version = version,
        name = name,
        url = url,
        scm = scm
    )

    @Test
    fun `the three coordinates are substituted wherever they appear`() {
        val resolved = PomPlaceholders.resolve(
            pom(
                name = "\${artifactId}",
                url = "https://github.com/YongGoose/\${artifactId}",
                scm = Scm(
                    connection = "scm:git:git@github.com:YongGoose/\${artifactId}.git",
                    developerConnection = "scm:git:git@github.com:YongGoose/\${artifactId}.git",
                    url = "https://github.com/YongGoose/\${artifactId}/tree/v\${version}"
                )
            )
        )

        assertEquals("library", resolved.name)
        assertEquals("https://github.com/YongGoose/library", resolved.url)
        assertEquals("scm:git:git@github.com:YongGoose/library.git", resolved.scm?.connection)
        assertEquals("scm:git:git@github.com:YongGoose/library.git", resolved.scm?.developerConnection)
        assertEquals("https://github.com/YongGoose/library/tree/v1.0.0", resolved.scm?.url)
    }

    /**
     * The scenario #32 is about, and the one the first attempt at it could not do: a template
     * declared once at the root, resolved per module.
     *
     * Resolution therefore has to run on the *merged* POM. Substituting while snapshotting an
     * extension resolves the root's template against the root's own artifactId — here `parent`,
     * which is precisely the value the module overrode.
     */
    @Test
    fun `a root template resolves against the module's own artifactId`() {
        val root = OrganizationDefaults(
            groupId = "io.github.yonggoose",
            artifactId = "parent",
            version = "1.0.0",
            scm = Scm(url = "https://github.com/YongGoose/\${artifactId}")
        )
        val module = OrganizationDefaults(artifactId = "core")

        val resolved = PomPlaceholders.resolve(root.merge(module))

        assertEquals("https://github.com/YongGoose/core", resolved.scm?.url)
        assertEquals("core", resolved.artifactId)
    }

    @Test
    fun `coordinates are left alone, so one cannot be written in terms of another`() {
        val resolved = PomPlaceholders.resolve(pom(artifactId = "\${groupId}"))

        assertEquals("\${groupId}", resolved.artifactId)
    }

    @Test
    fun `a misspelled placeholder is kept rather than guessed at, and reported`() {
        val resolved = PomPlaceholders.resolve(
            pom(scm = Scm(url = "https://github.com/YongGoose/\${artifctId}"))
        )

        assertEquals("https://github.com/YongGoose/\${artifctId}", resolved.scm?.url)
        assertEquals(listOf("https://github.com/YongGoose/\${artifctId}"), PomPlaceholders.unresolved(resolved))
    }

    /**
     * An unset coordinate must not substitute to the empty string or to `null`: the URL would look
     * plausible and be wrong, which is the failure mode the whole check is written against.
     */
    @Test
    fun `a placeholder for a coordinate this pom does not set is left in place`() {
        val resolved = PomPlaceholders.resolve(
            pom(artifactId = null, scm = Scm(url = "https://github.com/YongGoose/\${artifactId}"))
        )

        assertEquals("https://github.com/YongGoose/\${artifactId}", resolved.scm?.url)
        assertTrue(PomPlaceholders.unresolved(resolved).isNotEmpty())
    }

    @Test
    fun `text with no placeholder is returned unchanged, dollar signs included`() {
        val original = pom(
            name = "Costs \$5",
            url = "https://example.org/\$artifactId",
            scm = Scm(url = "https://example.org/\${a.b}")
        )

        val resolved = PomPlaceholders.resolve(original)

        // `$artifactId` without braces and `${a.b}` with a dot are not the syntax, so neither is
        // touched -- and neither is reported, or every POM mentioning a price would fail.
        assertEquals(original, resolved)
        assertEquals(emptyList(), PomPlaceholders.unresolved(resolved))
    }

    @Test
    fun `every text field is covered, not just scm`() {
        val resolved = PomPlaceholders.resolve(
            OrganizationDefaults(
                groupId = "io.github.yonggoose",
                artifactId = "library",
                version = "1.0.0",
                description = "The \${artifactId} module",
                inceptionYear = "\${version}",
                licenses = listOf(License(name = "\${artifactId}-license", url = "https://x/\${artifactId}")),
                organization = Organization(name = "\${groupId}", url = "https://x/\${groupId}"),
                developers = listOf(Developer(id = "\${artifactId}-dev", organizationUrl = "https://x/\${groupId}")),
                issueManagement = IssueManagement(system = "GitHub", url = "https://x/\${artifactId}/issues"),
                mailingLists = listOf(MailingList(name = "\${artifactId}-dev", post = "\${artifactId}@x.org"))
            )
        )

        assertEquals("The library module", resolved.description)
        assertEquals("1.0.0", resolved.inceptionYear)
        assertEquals("library-license", resolved.licenses[0].name)
        assertEquals("https://x/library", resolved.licenses[0].url)
        assertEquals("io.github.yonggoose", resolved.organization?.name)
        assertEquals("https://x/io.github.yonggoose", resolved.organization?.url)
        assertEquals("library-dev", resolved.developers[0].id)
        assertEquals("https://x/io.github.yonggoose", resolved.developers[0].organizationUrl)
        assertEquals("https://x/library/issues", resolved.issueManagement?.url)
        assertEquals("library-dev", resolved.mailingLists[0].name)
        assertEquals("library@x.org", resolved.mailingLists[0].post)
        assertEquals(emptyList(), PomPlaceholders.unresolved(resolved))
    }
}
