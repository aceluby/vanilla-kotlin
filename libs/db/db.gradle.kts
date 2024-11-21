plugins {
    `java-test-fixtures`
}

dependencies {
//    api(libs.jdbc.lite)
//    api(project(":libs:common"))

//    implementation(libs.postgresql)
//    testImplementation(testFixtures(project(":libs:common")))
}

tasks {
    withType<Test> {
        dependsOn(":db-migration:flywayMigrate")
    }
}
