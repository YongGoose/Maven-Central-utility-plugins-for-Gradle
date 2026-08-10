package io.github.yonggoose.organizationdefaults

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Covers the DSL contract that [OrganizationDefaults.merge] depends on.
 *
 * Note the receiver: the user-facing `scm { }` block binds `ScmContainer`, not `ScmSpec`, so a
 * build script assigns straight onto the container.
 */
class PomMetadataExtensionTest {

    private class TestExtension : AbstractPomMetadataExtension()

    @Test
    fun `nested blocks are null until something is configured`() {
        val ext = TestExtension()

        assertNull(ext.scm)
        assertNull(ext.organization)
        assertNull(ext.issueManagement)
        assertEquals(emptyList(), ext.licenses)
        assertEquals(emptyList(), ext.developers)
        assertEquals(emptyList(), ext.mailingLists)
    }

    @Test
    fun `an all-null block still reads as unconfigured`() {
        // Returning Scm(null, null, null) here would make merge() treat an empty block as an
        // override and silently discard the parent's scm.
        val ext = TestExtension()
        ext.scm { }
        ext.organization { }
        ext.issueManagement { }

        assertNull(ext.scm)
        assertNull(ext.organization)
        assertNull(ext.issueManagement)
    }

    @Test
    fun `repeated blocks accumulate onto the same container`() {
        val ext = TestExtension()

        ext.scm { url = "https://github.com/YongGoose/library" }
        ext.scm { connection = "scm:git:git@github.com:YongGoose/library.git" }

        assertEquals("https://github.com/YongGoose/library", ext.scm?.url)
        assertEquals("scm:git:git@github.com:YongGoose/library.git", ext.scm?.connection)
        assertNull(ext.scm?.developerConnection)
    }

    @Test
    fun `repeated organization and issueManagement blocks accumulate too`() {
        val ext = TestExtension()

        ext.organization { name = "YongGoose" }
        ext.organization { url = "https://github.com/YongGoose" }
        ext.issueManagement { system = "GitHub" }
        ext.issueManagement { url = "https://example.org/issues" }

        assertEquals("YongGoose", ext.organization?.name)
        assertEquals("https://github.com/YongGoose", ext.organization?.url)
        assertEquals("GitHub", ext.issueManagement?.system)
        assertEquals("https://example.org/issues", ext.issueManagement?.url)
    }

    @Test
    fun `repeated list blocks append`() {
        val ext = TestExtension()

        ext.licenses { license { name = "Apache-2.0" } }
        ext.licenses { license { name = "MIT" } }
        ext.developers { developer { id = "dev1" } }
        ext.developers { developer { id = "dev2" } }

        assertEquals(listOf("Apache-2.0", "MIT"), ext.licenses.map { it.name })
        assertEquals(listOf("dev1", "dev2"), ext.developers.map { it.id })
    }

    @Test
    fun `toOrganizationDefaults snapshots everything that was configured`() {
        val ext = TestExtension().apply {
            groupId = "io.github.yonggoose"
            artifactId = "library"
            version = "1.0.0"
            name = "Library"
            description = "A description"
            url = "https://example.org"
            inceptionYear = "2024"
            licenses {
                license {
                    name = "Apache-2.0"
                    url = "https://example.org/license"
                }
            }
            developers {
                developer {
                    id = "dev1"
                    name = "Developer One"
                }
            }
            mailingLists {
                mailingList {
                    name = "dev"
                    post = "dev@example.org"
                }
            }
            organization {
                name = "YongGoose"
                url = "https://github.com/YongGoose"
            }
            issueManagement {
                system = "GitHub"
                url = "https://example.org/issues"
            }
            scm {
                url = "https://github.com/YongGoose/library"
            }
        }

        assertEquals(
            OrganizationDefaults(
                groupId = "io.github.yonggoose",
                artifactId = "library",
                version = "1.0.0",
                name = "Library",
                description = "A description",
                url = "https://example.org",
                inceptionYear = "2024",
                licenses = listOf(License(name = "Apache-2.0", url = "https://example.org/license")),
                organization = Organization(name = "YongGoose", url = "https://github.com/YongGoose"),
                developers = listOf(Developer(id = "dev1", name = "Developer One")),
                issueManagement = IssueManagement(system = "GitHub", url = "https://example.org/issues"),
                mailingLists = listOf(MailingList(name = "dev", post = "dev@example.org")),
                scm = Scm(url = "https://github.com/YongGoose/library")
            ),
            ext.toOrganizationDefaults()
        )
    }
}
