plugins {
    `java-test-fixtures`
}

dependencies {
    api(project(":libs:common"))
    api(project(":libs:metrics"))
    api(libs.okhttp)
    implementation(libs.resilience4j.retry)

    testImplementation(testFixtures(project(":libs:common")))
    testImplementation(libs.okhttp.mock.server)
}
