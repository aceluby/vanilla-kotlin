package vanillakotlin.db.repository

fun buildTestDb(): Db {
    return Db(
        config =
            DbConfig(
                username = "vanilla_kotlin_app",
                password = "vanilla_kotlin_app",
                databaseName = "vanilla_kotlin",
            ),
    )
}
