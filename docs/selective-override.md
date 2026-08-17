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
    id("io.github.yonggoose.maven.central.utility.plugin.project") version "0.1.7"
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

Three levels are merged in order, each overriding the one before it:

```
rootProjectSetting  (settings.gradle.kts, optional)
      ↓
rootProjectPom      (root build.gradle.kts, optional)
      ↓
projectPom          (the module itself)
```

The `merge()` method in the `OrganizationDefaults` class follows these rules:

1. Values explicitly set at a lower level override the ones above it
2. Unset values inherit from the level above
3. List-type fields (licenses, developers, etc.) fully replace the parent list when overridden
4. **Block-type fields (`scm`, `organization`, `issueManagement`) also replace wholesale, not
   field by field.** A submodule that declares any part of a block declares all of it.

> [!WARNING]
> Rule 4 is easy to trip over now that `checkProjectArtifact` requires `scm.url`. This submodule
> does **not** inherit the root's `scm.url` — it overrides the whole `scm` block and ends up
> without one:
>
> ```kotlin
> projectPom {
>     scm {
>         connection = "scm:git:git@github.com:YongGoose/child.git"
>     }
> }
> ```
>
> Repeat the fields you still need, or leave the block out entirely to inherit it whole.

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