package vanillakotlin.db

import com.fasterxml.jackson.databind.ObjectMapper
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.postgres.PostgresPlugin
import vanillakotlin.db.repository.FavoriteThingMapper
import vanillakotlin.db.repository.OutboxMapper
import vanillakotlin.models.FavoriteThing
import vanillakotlin.models.Outbox

data class DbConfig(
    val username: String,
    val password: String,
    val databaseName: String,
    val host: String = "localhost",
    val port: Int = 5432,
) {
    val jdbcUrl: String
        get() = "jdbc:postgresql://$host:$port/$databaseName"
}

fun createJdbi(
    config: DbConfig,
    objectMapper: ObjectMapper,
): Jdbi {
    val jdbi = Jdbi.create(config.jdbcUrl, config.username, config.password)
        .installPlugin(PostgresPlugin())

    // Register row mappers
    jdbi.registerRowMapper(FavoriteThing::class.java, FavoriteThingMapper())
    jdbi.registerRowMapper(Outbox::class.java, OutboxMapper())

    return jdbi
}
