rootProject.name = "kotlin-pom-gradle"

include(
    "core:api",
    "core:dsl",
    "core:metadata",
    "core:hierarchy",
    "plugins:gradle-plugin",
    "features:pom-generator",
    "features:property-resolver",
    "features:validation"
)

include("core")
include("plugins")
include("testing")