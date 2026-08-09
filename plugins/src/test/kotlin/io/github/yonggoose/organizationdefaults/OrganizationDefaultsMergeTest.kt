package io.github.yonggoose.organizationdefaults

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

class OrganizationDefaultsMergeTest {

    private val parent = OrganizationDefaults(
        groupId = "io.github.yonggoose",
        artifactId = "parent",
        version = "1.0.0",
        name = "Parent",
        description = "Parent description",
        url = "https://example.org",
        inceptionYear = "2023",
        licenses = listOf(License(name = "Apache-2.0")),
        organization = Organization(name = "YongGoose", url = "https://github.com/YongGoose"),
        developers = listOf(Developer(id = "dev1")),
        issueManagement = IssueManagement(system = "GitHub", url = "https://example.org/issues"),
        mailingLists = listOf(MailingList(name = "dev")),
        scm = Scm(url = "https://github.com/YongGoose/parent")
    )

    @Test
    fun `a null override keeps the receiver untouched`() {
        assertSame(parent, parent.merge(null))
    }

    @Test
    fun `scalar fields fall back to the parent only when unset`() {
        val merged = parent.merge(OrganizationDefaults(artifactId = "child", version = "2.0.0"))

        assertEquals("child", merged.artifactId)
        assertEquals("2.0.0", merged.version)
        assertEquals("io.github.yonggoose", merged.groupId)
        assertEquals("Parent", merged.name)
        assertEquals("2023", merged.inceptionYear)
    }

    @Test
    fun `list fields are replaced wholesale, never appended`() {
        val merged = parent.merge(
            OrganizationDefaults(licenses = listOf(License(name = "MIT"), License(name = "BSD-3-Clause")))
        )

        assertEquals(listOf("MIT", "BSD-3-Clause"), merged.licenses.map { it.name })
        // Untouched lists still come from the parent.
        assertEquals(listOf("dev1"), merged.developers.map { it.id })
        assertEquals(listOf("dev"), merged.mailingLists.map { it.name })
    }

    @Test
    fun `an empty list inherits the parent's`() {
        val merged = parent.merge(OrganizationDefaults(licenses = emptyList()))
        assertEquals(listOf("Apache-2.0"), merged.licenses.map { it.name })
    }

    @Test
    fun `null nested blocks inherit while set ones override`() {
        val merged = parent.merge(
            OrganizationDefaults(
                scm = Scm(url = "https://gitlab.com/child"),
                organization = null,
                issueManagement = null
            )
        )

        assertEquals("https://gitlab.com/child", merged.scm?.url)
        assertEquals("YongGoose", merged.organization?.name)
        assertEquals("GitHub", merged.issueManagement?.system)
    }

    @Test
    fun `an override replaces a nested block entirely rather than field by field`() {
        // The parent's scm has only a url; the child's has only a connection. The child wins
        // whole, so the parent's url is not carried over.
        val merged = parent.merge(OrganizationDefaults(scm = Scm(connection = "scm:git:git@gitlab.com:child.git")))

        assertEquals("scm:git:git@gitlab.com:child.git", merged.scm?.connection)
        assertNull(merged.scm?.url)
    }

    @Test
    fun `merging onto an empty parent yields the override`() {
        val merged = OrganizationDefaults().merge(parent)
        assertEquals(parent, merged)
    }
}
