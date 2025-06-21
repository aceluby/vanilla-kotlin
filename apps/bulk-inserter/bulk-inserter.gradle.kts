plugins {
    application
    `java-test-fixtures`
}

application {
    applicationName = "app"
    mainClass.set("vanillakotlin.bulkinserter.AppKt")
}

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:db"))
    implementation(project(":libs:kafka"))
    implementation(project(":libs:metrics"))
    implementation(project(":libs:http4k"))
    testImplementation(testFixtures(project(":libs:common")))
    testImplementation(testFixtures(project(":libs:kafka")))
    testImplementation(testFixtures(project(":libs:db")))
    testImplementation(libs.kafka.client)
}

tasks {
    withType<Test> {
        dependsOn(":db-migration:flywayMigrate")
    }
}
