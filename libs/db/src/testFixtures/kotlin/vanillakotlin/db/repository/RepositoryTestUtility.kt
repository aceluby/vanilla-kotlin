package vanillakotlin.db.repository

import org.jdbi.v3.core.Jdbi
import vanillakotlin.db.DbConfig
import vanillakotlin.db.createJdbi
import vanillakotlin.serde.mapper

fun buildTestDb(): Jdbi = createJdbi(
    config = DbConfig(
        username = "vanilla_kotlin_app",
        password = "vanilla_kotlin_app",
        databaseName = "vanilla_kotlin",
    ),
    objectMapper = mapper,
)
