data class OrganizationDefaults(
    val name: String? = null,
    val url: String? = null,
    val license: String? = null,
    val developers: List<String> = emptyList()
) {
    fun merge(override: OrganizationDefaults?): OrganizationDefaults {
        if (override == null) return this
        return OrganizationDefaults(
            name = override.name ?: this.name,
            url = override.url ?: this.url,
            license = override.license ?: this.license,
            developers = override.developers.ifEmpty { this.developers }
        )
    }
}