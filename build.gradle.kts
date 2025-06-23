import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask

plugins {
    kotlin("jvm") version libs.versions.kotlin.core
    id("com.diffplug.spotless") version libs.versions.spotless
    alias(libs.plugins.versions)
}

// configuring information that will be applied to all projects, including this root
allprojects {
    apply(plugin = "kotlin")
    apply(plugin = "com.diffplug.spotless")

    // This line configures the target JVM version. For applications, it's typically the latest LTS version.
    // For libraries, it's typically the earliest LTS version.
    kotlin {
        jvmToolchain(21)
        // set all compiler warnings as errors so builds will fail if there are warnings
        compilerOptions { allWarningsAsErrors = true }
    }

    tasks {
        withType<Test> {
            // use junit as your test runner unless you have a love for something else.
            useJUnitPlatform()

            // log all the test events that were run, and include output from stdout and stderr
            testLogging {
                events("passed", "skipped", "failed", "standardOut", "standardError")
            }

            // this add-opens is to work around a JVM 17 strong encapsulation change. In a test we're modifying the environment using
            // the `withEnvironment` and `withSystemProperties` kotest system extensions.
            // If you're not using those extensions, you can remove this override.
            jvmArgs("--add-opens", "java.base/java.util=ALL-UNNAMED")
        }
    }

    spotless {
        kotlin { 
            ktlint().setEditorConfigPath("${project.rootDir}/.editorconfig")
        }
        kotlinGradle { 
            ktlint().setEditorConfigPath("${project.rootDir}/.editorconfig")
        }
    }
}

// Configure the versions plugin to only show actual releases, not snapshots or pre-releases
tasks {
    withType<DependencyUpdatesTask> {
        rejectVersionIf {
            (
                "[0-9,.v-]+(-r)?".toRegex().matches(candidate.version) ||
                    listOf("RELEASE", "FINAL", "GA").contains(candidate.version.uppercase())
                ).not()
        }
    }
}
