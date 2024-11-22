plugins {
    application
    `java-test-fixtures`
}

application {
    applicationName = "app"
    mainClass.set("vanillakotlin.api.AppKt")
}

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:client"))
    implementation(project(":libs:db"))
    implementation(project(":libs:metrics"))
    implementation(project(":libs:http4k"))

    testImplementation(testFixtures(project(":libs:common")))
    testImplementation(libs.okhttp.mock.server)
}

tasks {
    withType<Test> {
        dependsOn(":db-migration:flywayMigrate")
    }
}
