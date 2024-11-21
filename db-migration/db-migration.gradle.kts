buildscript {
    dependencies {
        classpath(libs.postgresql)
        classpath(libs.flyway.database.postgresql)
    }
}

plugins {
    alias(libs.plugins.flyway)
}

flyway {
    val dbhost =
        when {
            System.getenv().containsKey("DATABASE_HOST") -> System.getenv("DATABASE_HOST")
            System.getenv().containsKey("CI") -> "postgres"
            else -> "localhost"
        }
    baselineOnMigrate = true
    baselineVersion = "0"
    url = "jdbc:postgresql://$dbhost:5432/vanilla_kotlin"
    user = System.getenv("DATABASE_USERNAME") ?: "vanilla_kotlin"
    password = System.getenv("DATABASE_PASSWORD") ?: "vanilla_kotlin"
}
