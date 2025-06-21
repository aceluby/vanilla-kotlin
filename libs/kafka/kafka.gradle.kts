plugins {
    `java-test-fixtures`
}

dependencies {
    api(project(":libs:common"))
    api(project(":libs:metrics"))
    api(libs.kafka.client)
    implementation(libs.kotlin.coroutines)

    testFixturesImplementation(testFixtures(project(":libs:common")))
    testFixturesImplementation(libs.kafka.client)

    testImplementation(testFixtures(project(":libs:common")))
    testImplementation(libs.bundles.jupiter)
    testImplementation(libs.kafka.client)
}
