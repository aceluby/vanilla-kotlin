tasks {
    withType<Test> {
        dependsOn(":db-migration:flywayMigrate")
    }
}
