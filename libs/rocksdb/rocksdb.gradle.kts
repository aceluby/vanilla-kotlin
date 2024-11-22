plugins {
    `java-test-fixtures`
}

dependencies {
    api(project(":libs:common"))
    api(project(":libs:metrics"))
    implementation(libs.rocksdb)

    testImplementation(testFixtures(project(":libs:common")))
}
