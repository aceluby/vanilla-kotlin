package vanillakotlin.db.repository

import com.fasterxml.jackson.module.kotlin.readValue
import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import vanillakotlin.models.Outbox
import vanillakotlin.serde.mapper
import java.sql.ResultSet

/**
 * These operations must be executed within the context of a transaction and can't be executed on their own,
 * so we're requiring a Handle argument (which represents an active transaction in JDBI).
 */

fun insertOutbox(
    handle: Handle,
    outbox: Outbox,
): Outbox = handle.createQuery(
    """
        INSERT INTO outbox (message_key, headers, body) 
        VALUES (:messageKey, :headers, :body) 
        RETURNING *
    """.trimIndent(),
)
    .bind("messageKey", outbox.messageKey)
    .bind("headers", mapper.writeValueAsBytes(outbox.headers))
    .bind("body", outbox.body)
    .mapTo(Outbox::class.java)
    .single()

fun popOutbox(
    handle: Handle,
    limit: Int = Int.MAX_VALUE,
): List<Outbox> = handle.createQuery(
    """
        DELETE FROM outbox WHERE id = ANY (
          SELECT id FROM outbox
          ORDER BY created_ts
          FOR UPDATE SKIP LOCKED
          LIMIT :limit
        ) RETURNING *
    """.trimIndent(),
)
    .bind("limit", limit)
    .mapTo(Outbox::class.java)
    .list()

// Custom row mapper for Outbox
class OutboxMapper : RowMapper<Outbox> {
    override fun map(
        rs: ResultSet,
        ctx: StatementContext,
    ): Outbox = Outbox(
        id = rs.getLong("id"),
        messageKey = rs.getString("message_key"),
        headers = mapper.readValue(rs.getBytes("headers")),
        body = rs.getBytes("body"),
        createdTs = rs.getTimestamp("created_ts").toInstant(),
    )
}

fun mapToOutbox(resultSet: ResultSet): Outbox = with(resultSet) {
    Outbox(
        id = getLong("id"),
        messageKey = getString("message_key"),
        headers = mapper.readValue(getBytes("headers")),
        body = getBytes("body"),
        createdTs = getTimestamp("created_ts").toInstant(),
    )
}
