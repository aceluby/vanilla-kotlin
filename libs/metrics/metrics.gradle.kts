plugins {
    `java-test-fixtures`
}

dependencies {
    api(project(":libs:common"))
    api(libs.bundles.otel)

    testImplementation(testFixtures(project(":libs:common")))
    testImplementation(libs.bundles.otel.testing)
}
