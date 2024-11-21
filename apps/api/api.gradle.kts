plugins {
    application
    `java-test-fixtures`
}

application {
    applicationName = "app"
    mainClass.set("vanillakotlin.api.AppKt")
}

dependencies {
    implementation(project(":libs:client"))
    implementation(project(":libs:db"))
    implementation(libs.kotlin.coroutines)

    testImplementation(testFixtures(project(":libs:common")))
    testImplementation(libs.okhttp.mock.server)
}

tasks {
    withType<Test> {
        dependsOn(":db-migration:flywayMigrate")
    }
}
