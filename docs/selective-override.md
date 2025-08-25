# Selective Override

While benefiting from centralized POM management, submodules can override specific metadata when needed.

## Use Cases

- Different `artifactId` per submodule  
- Unique description for certain modules  
- Module-specific license information  

## Usage

After setting defaults in the root project, submodules can override only the required elements using `projectPom`:

### Root project (build.gradle.kts)

```kotlin
rootProjectPom {
    groupId = "io.github.yonggoose"
    artifactId = "parent-project"
    version = "1.0.0"
    
    name = "Parent Project"
    description = "Parent project description"
    
    developers {
        developer {
            id = "dev1"
            name = "Developer1"
            email = "dev1@example.com"
        }
    }
}
```

## Submodule (sub/build.gradle.kts)

```kotlin
plugins {
    id("io.github.yonggoose.maven.central.utility.plugin.project") version "0.1.6"
}

projectPom {
    artifactId = "child-module"
    name = "Child Module"
    description = "This is a specialized module with different functionality"
    
    developers {
        developer {
            id = "dev2"
            name = "Developer2"
            email = "dev2@example.com"
        }
    }
}
```

## Merge Mechanism
The `merge()` method in the `OrganizationDefaults` class follows these rules:

1. Values explicitly set in the submodule override the defaults 
2. Unset values inherit from the root project 
3. List-type fields (licenses, developers, etc.) fully replace the parent list when overridden

```kotlin
fun merge(override: OrganizationDefaults?): OrganizationDefaults {
    if (override == null) return this
    return OrganizationDefaults(
        groupId = override.groupId ?: this.groupId,
        artifactId = override.artifactId ?: this.artifactId,
        version = override.version ?: this.version,
        developers = override.developers.ifEmpty { this.developers },
        // other fields...
    )
}
```

## Example
In a multi-module project, you can keep `group` and `version` common while giving each module its own `artifactId` and description:
```
root-project (groupId = com.example, version = 1.0.0)
  ├── core (artifactId = core, description = "Core functionality")
  ├── api (artifactId = api, description = "API module")
  └── util (artifactId = util, description = "Utility functions")
```