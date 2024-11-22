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
}

tasks {
    withType<Test> {
        dependsOn(":db-migration:flywayMigrate")
    }
}
