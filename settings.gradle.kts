rootProject.name = "vanilla-kotlin-public"
include(
    "libs:common",
    "libs:db",
    "libs:client",
    "libs:metrics",
    "libs:kafka",
    "apps",
    "apps:api",
    "apps:bulk-inserter",
    "apps:kafka-transformer",
    "apps:outbox-processor",
    "db-migration",
)

pluginManagement {
    repositories {
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()
        mavenLocal()
    }
}

// This processing allows us to use the submodule name for submodule build files.
// Else they all need to be named build.gradle.kts, which is inconvenient when searching for the file
rootProject.children.forEach {
    renameBuildFiles(it)
}

fun renameBuildFiles(descriptor: ProjectDescriptor) {
    descriptor.buildFileName = "${descriptor.name}.gradle.kts"
    descriptor.children.forEach {
        renameBuildFiles(it)
    }
}
