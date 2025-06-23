# Gradle Setup Guide

This document explains how Gradle is configured in the vanilla Kotlin project, highlighting key features that make
dependency management, toolchain setup, and code quality enforcement simple and maintainable.

## Overview

The project uses modern Gradle features to create a clean, maintainable build setup:

- **Version Catalog**: Centralized dependency version management
- **Kotlin Toolchain**: Simplified JVM version configuration
- **JUnit Platform**: Modern testing framework setup
- **Spotless + EditorConfig**: Automated code formatting
- **Ben Manes Versions Plugin**: Easy dependency upgrade identification

## Version Catalog (`gradle/libs.versions.toml`)

The project uses Gradle's [Version Catalog](https://docs.gradle.org/current/userguide/platforms.html) feature to
centralize all dependency versions in a single file. This eliminates version conflicts and makes upgrades much easier.

### Structure

```toml
[versions]
kotlin-core = "2.1.21"
http4k = "6.15.0.1"
jackson = "2.19.1"
jdbi = "3.49.5"
jupiter = "5.13.1"
kafka = "3.9.0"
# ... more versions

[libraries]
# Individual library declarations
jackson-databind = { module = "com.fasterxml.jackson.core:jackson-databind", version.ref = "jackson" }
jackson-kotlin = { module = "com.fasterxml.jackson.module:jackson-module-kotlin", version.ref = "jackson" }
http4k-core = { module = "org.http4k:http4k-core", version.ref = "http4k" }
# ... more libraries

[bundles]
# Grouped dependencies that are commonly used together
jackson = [
    "jackson-databind",
    "jackson-jdk8",
    "jackson-jsr310",
    "jackson-kotlin",
]
testing = [
    "jupiter-api",
    "jupiter-engine",
    "jupiter-params",
    "kotest-assertions-core",
    "kotest-runner-junit5",
]

[plugins]
# Plugin version management
flyway = { id = "org.flywaydb.flyway", version.ref = "flyway" }
versions = { id = "com.github.ben-manes.versions", version.ref = "versions" }
```

### Benefits

**1. Single Source of Truth**

- All versions managed in one file
- No duplicate version declarations across modules
- Easy to see what versions are being used

**2. Consistent Dependencies**

- Related libraries automatically use the same version
- Reduces version conflicts between modules

**3. Bundle Management**

- Commonly used dependency groups (like `jackson`, `testing`) can be added as bundles
- Reduces boilerplate in module build files

### Usage in Build Files

```kotlin
// libs/common/common.gradle.kts
dependencies {
    // Use bundles for related dependencies
    api(libs.bundles.jackson)
    api(libs.bundles.hoplite)

    // Use individual libraries
    api(libs.kotlin.coroutines)

    // Testing dependencies
    testApi(libs.bundles.testing)
}
```

## Kotlin Toolchain Configuration

The project uses
Gradle's [Kotlin JVM Toolchain](https://kotlinlang.org/docs/gradle-configure-project.html#gradle-java-toolchains-support)
feature for simplified JVM version management.

### Configuration

```kotlin
// build.gradle.kts (root)
allprojects {
    kotlin {
        jvmToolchain(21)  // Use Java 21 for all modules
        compilerOptions {
            allWarningsAsErrors = true  // Fail builds on warnings
        }
    }
}
```

### Benefits

**1. Automatic JDK Management**

- Gradle automatically downloads the correct JDK if not available
- Consistent Java version across all developers and CI

**2. Simplified Configuration**

- One line configures compilation, testing, and runtime
- No need to manage `JAVA_HOME` or multiple JDK installations

**3. Version Flexibility**

- Easy to upgrade Java versions across the entire project
- Different modules can use different toolchain versions if needed

## Testing Setup

The project is configured to use JUnit 5 (Jupiter) with Kotest assertions for modern, readable testing.

### Configuration

```kotlin
// build.gradle.kts (root)
allprojects {
    tasks {
        withType<Test> {
            useJUnitPlatform()  // Enable JUnit 5

            testLogging {
                events("passed", "skipped", "failed", "standardOut", "standardError")
            }

            // Required for Kotest system extensions
            jvmArgs("--add-opens", "java.base/java.util=ALL-UNNAMED")
        }
    }
}
```

### Testing Dependencies

```toml
# gradle/libs.versions.toml
[bundles]
testing = [
    "jupiter-api", # JUnit 5 API
    "jupiter-engine", # JUnit 5 runtime
    "jupiter-params", # Parameterized tests
    "kotest-assertions-core", # Better assertions
    "kotest-runner-junit5", # Kotest integration
    "kotest-property", # Property-based testing
    "kotest-extensions", # System property overrides
]
```

### Benefits

**1. Modern Testing Framework**

- JUnit 5 provides better test organization and features
- Parameterized tests, nested tests, dynamic tests

**2. Readable Assertions**

- Kotest provides fluent, readable assertion syntax
- Better error messages than standard JUnit assertions

**3. System Testing Support**

- Kotest extensions allow easy system property and environment overrides
- Essential for integration testing

### Example Test

```kotlin
@Test fun `should process valid message`() {
    val result = service.processMessage(validMessage)

    assertSoftly(result) {
        this.status shouldBe ProcessingStatus.SUCCESS
        this.processedAt shouldNotBe null
        this.errors shouldBe emptyList()
    }
}
```

## Spotless + EditorConfig Integration

The project uses [Spotless](https://github.com/diffplug/spotless) for automated code formatting, integrated with
EditorConfig for IDE compatibility.

### Spotless Configuration

```kotlin
// build.gradle.kts (root)
allprojects {
    apply(plugin = "com.diffplug.spotless")

    spotless {
        kotlin {
            ktlint().setEditorConfigPath("${project.rootDir}/.editorconfig")
        }
        kotlinGradle {
            ktlint().setEditorConfigPath("${project.rootDir}/.editorconfig")
        }
    }
}
```

### EditorConfig Rules

```ini
# .editorconfig
[*.{kt,kts}]
max_line_length = 180

# ktlint rules for parameter formatting
ktlint_function_signature_rule_force_multiline_when_parameter_count_greater_or_equal_than = 2
ktlint_class_signature_rule_force_multiline_when_parameter_count_greater_or_equal_than = 2
ktlint_standard_parameter-list-wrapping = enabled
ktlint_standard_multiline-expression-wrapping = disabled
ktlint_standard_annotation = enabled
ktlint_standard_wrapping = enabled
```

### Benefits

**1. Consistent Code Style**

- All code automatically formatted to project standards
- No arguments about formatting in code reviews

**2. IDE Integration**

- EditorConfig ensures IDEs use the same formatting rules
- Developers see consistent formatting in their editors

**3. Automated Enforcement**

- CI builds fail if code isn't properly formatted
- `./gradlew spotlessApply` fixes formatting issues automatically

### Usage

```bash
# Check if code is properly formatted
./gradlew spotlessCheck

# Automatically fix formatting issues
./gradlew spotlessApply
```

## Ben Manes Versions Plugin

The [Versions Plugin](https://github.com/ben-manes/gradle-versions-plugin) helps identify dependency upgrade
opportunities.

### Configuration

```kotlin
// build.gradle.kts (root)
plugins {
    alias(libs.plugins.versions)
}

tasks {
    withType<DependencyUpdatesTask> {
        rejectVersionIf {
            // Only show stable releases, not snapshots or pre-releases
            (
                "[0-9,.v-]+(-r)?".toRegex().matches(candidate.version) ||
                    listOf("RELEASE", "FINAL", "GA").contains(candidate.version.uppercase())
                ).not()
        }
    }
}
```

### Benefits

**1. Easy Upgrade Identification**

- Shows which dependencies have newer versions available
- Filters out unstable/pre-release versions

**2. Security Updates**

- Helps identify dependencies with security vulnerabilities
- Makes it easy to stay current with patches

**3. Version Catalog Integration**

- Works seamlessly with version catalogs
- Shows exactly which versions in `libs.versions.toml` can be updated

### Usage

```bash
# Show available dependency updates
./gradlew dependencyUpdates

# Example output:
# The following dependencies have later milestone versions:
#  - com.fasterxml.jackson.core:jackson-databind [2.19.1 -> 2.19.2]
#  - org.jetbrains.kotlin:kotlin-stdlib [2.1.21 -> 2.1.30]
```

## Project Structure

The Gradle setup supports a clean multi-module structure:

```
vanilla-kotlin-public/
├── build.gradle.kts              # Root build configuration
├── settings.gradle.kts           # Project structure
├── gradle/
│   └── libs.versions.toml        # Version catalog
├── .editorconfig                 # Code style rules
├── libs/                         # Shared libraries
│   ├── common/common.gradle.kts
│   ├── db/db.gradle.kts
│   └── ...
└── apps/                         # Applications
    ├── api/api.gradle.kts
    ├── bulk-inserter/bulk-inserter.gradle.kts
    └── ...
```

### Module Naming Convention

```kotlin
// settings.gradle.kts
rootProject.children.forEach {
    renameBuildFiles(it)
}

fun renameBuildFiles(descriptor: ProjectDescriptor) {
    descriptor.buildFileName = "${descriptor.name}.gradle.kts"
    descriptor.children.forEach {
        renameBuildFiles(it)
    }
}
```

This allows each module to have a descriptive build file name (e.g., `api.gradle.kts`) instead of generic
`build.gradle.kts` files.

## Best Practices

### 1. Version Management

- Keep all versions in the catalog, even if used by only one module
- Use semantic versioning and update regularly
- Group related dependencies with the same version

### 2. Dependency Organization

- Use `api` for dependencies that are part of the module's public API
- Use `implementation` for internal dependencies
- Use `testApi` for test dependencies shared across modules

### 3. Build Configuration

- Keep root `build.gradle.kts` focused on cross-cutting concerns
- Module-specific configuration goes in individual module files
- Use `allprojects` for settings that apply everywhere

### 4. Code Quality

- Run `spotlessApply` before committing code
- Configure CI to fail on formatting violations
- Regularly check for dependency updates

## Common Tasks

### Development Workflow

```bash
# Build everything
./gradlew build

# Run tests
./gradlew test

# Fix code formatting
./gradlew spotlessApply

# Check for dependency updates
./gradlew dependencyUpdates

# Run specific module tests
./gradlew :libs:kafka:test
```

### Dependency Management

```bash
# See all project dependencies
./gradlew dependencies

# See dependency insight for specific library
./gradlew dependencyInsight --dependency jackson-databind

# Check for vulnerable dependencies
./gradlew dependencyCheckAnalyze
```

## Migration Guide

### Adding New Dependencies

1. **Add version to catalog**:
   ```toml
   [versions]
   new-library = "1.0.0"
   
   [libraries]
   new-library = { module = "com.example:new-library", version.ref = "new-library" }
   ```

2. **Use in module**:
   ```kotlin
   dependencies {
       implementation(libs.new.library)
   }
   ```

### Upgrading Dependencies

1. **Check for updates**:
   ```bash
   ./gradlew dependencyUpdates
   ```

2. **Update version catalog**:
   ```toml
   [versions]
   jackson = "2.19.2"  # Updated from 2.19.1
   ```

3. **Test and verify**:
   ```bash
   ./gradlew test
   ```

This Gradle setup provides a solid foundation for maintainable, scalable Kotlin projects with minimal configuration
overhead. 
