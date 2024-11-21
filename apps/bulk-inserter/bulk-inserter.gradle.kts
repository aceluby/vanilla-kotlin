plugins {
    application
    `java-test-fixtures`
}

application {
    applicationName = "app"
    mainClass.set("vanillakotlin.bulkinserter.AppKt")
}

dependencies {
    implementation(project(":libs:db"))
    testImplementation(testFixtures(project(":libs:common")))
}

tasks {
    withType<Test> {
        dependsOn(":db-migration:flywayMigrate")
    }
}
