plugins {
    `java-test-fixtures`
}

// we're using api here so that the dependencies are available to all projects that depend on this project
// these dependencies are the common dependencies that we use in all the modules.  Be aware that this means
// additions will be added to all applications.
dependencies {
    api(libs.bundles.jackson)
    api(libs.hoplite.hocon)
    api(libs.bundles.resilience4j)
    api(libs.bundles.logging)

    // this is another set of opinionated defaults you should use if you don't already have a strong preference
    // it runs tests using the JUnit runner, and adds the kotest assertions library for better assertions
    testApi(libs.bundles.testing)
    testFixturesApi(libs.bundles.testing)
}
