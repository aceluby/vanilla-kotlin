plugins {
    `java-test-fixtures`
}

dependencies {
    api(project(":libs:common"))
    api(libs.bundles.http4k)
    api(libs.kasechange)

    testImplementation(testFixtures(project(":libs:common")))
}
