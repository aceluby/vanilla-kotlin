plugins {
    `java-test-fixtures`
}

dependencies {
    api(project(":libs:common"))
    api(project(":libs:metrics"))
    implementation(libs.kafka.client)
    implementation(libs.kotlin.coroutines)

    testImplementation(testFixtures(project(":libs:common")))
}
