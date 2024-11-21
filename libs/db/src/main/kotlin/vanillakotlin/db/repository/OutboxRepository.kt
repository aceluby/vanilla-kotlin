package vanillakotlin.db.repository

import java.sql.ResultSet

/**
 * These operations must be executed within the context of a transaction and can't be executed on their own,
 * so we're requiring a transaction argument.
 */

fun insertOutbox(
    tx: Transaction,
    outbox: Outbox,
): Outbox {
    return checkNotNull(
        tx.executeQuery(
            sql =
                """
                INSERT INTO outbox (message_key, headers, body) 
                VALUES (:messageKey, :headers, :body) 
                RETURNING *
                """.trimIndent(),
            args =
                mapOf(
                    "messageKey" to outbox.messageKey,
                    "headers" to mapper.writeValueAsBytes(outbox.headers),
                    "body" to outbox.body,
                ),
            rowMapper = ::mapToOutbox,
        ),
    ) { "Unexpected state: Insert didn't return a result." }
}

fun popOutbox(
    tx: Transaction,
    limit: Int = Int.MAX_VALUE,
): List<Outbox> =
    tx.findAll(
        sql =
            """
            DELETE FROM outbox WHERE id = ANY (
              SELECT id FROM outbox
              ORDER BY created_ts
              FOR UPDATE SKIP LOCKED
              LIMIT :limit
            ) RETURNING *
            """.trimIndent(),
        args = mapOf("limit" to limit),
        rowMapper = ::mapToOutbox,
    )

fun mapToOutbox(resultSet: ResultSet) =
    with(resultSet) {
        Outbox(
            id = getLong("id"),
            messageKey = getString("message_key"),
            headers = mapper.readValue(getBytes("headers")),
            body = getBytes("body"),
            createdTs = getInstant("created_ts"),
        )
    }
