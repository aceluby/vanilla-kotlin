plugins {
    application
    `java-test-fixtures`
}

application {
    applicationName = "app"
    mainClass.set("vanillakotlin.outboxprocessor.AppKt")
}

dependencies {
    implementation(project(":libs:db"))
    implementation(project(":libs:kafka"))
    implementation(project(":libs:common"))
    implementation(project(":libs:http4k"))
    implementation(project(":libs:metrics"))

    testImplementation(testFixtures(project(":libs:db")))
    testImplementation(testFixtures(project(":libs:common")))
    testImplementation(testFixtures(project(":libs:kafka")))
    testImplementation(libs.kafka.client)
}

tasks {
    withType<Test> {
        dependsOn(":db-migration:flywayMigrate")
    }
}
