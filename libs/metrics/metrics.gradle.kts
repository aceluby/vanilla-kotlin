plugins {
    `java-test-fixtures`
}

dependencies {
    api(project(":libs:common"))
    api(libs.otel.api)

    testImplementation(testFixtures(project(":libs:common")))
    testImplementation(libs.bundles.otel.testing)
}
