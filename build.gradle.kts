import com.diffplug.gradle.spotless.BaseKotlinExtension.KtlintConfig

plugins {
    kotlin("jvm") version libs.versions.kotlin.core
    id("com.diffplug.spotless") version libs.versions.spotless
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
        val configOverride: Map<String, Any> = buildMap {
            put("ktlint_function_signature_rule_force_multiline_when_parameter_count_greater_or_equal_than", 2)
            put("ktlint_standard_multiline-expression-wrapping", "disabled")
            // Enable annotation formatting rule to enforce proper annotation placement
            put("ktlint_standard_annotation", "enabled")
            // Disable filename rule as it's not related to annotation formatting
            put("ktlint_standard_filename", "disabled")
            // Disable value-argument-comment rule to allow inline comments in argument lists
            put("ktlint_standard_value-argument-comment", "disabled")
        }
        val configure: KtlintConfig.() -> KtlintConfig = {
            setEditorConfigPath("${project.rootDir}/.editorconfig")
            editorConfigOverride(configOverride)
        }
        kotlin { ktlint().configure() }
        kotlinGradle { ktlint().configure() }
    }
}
