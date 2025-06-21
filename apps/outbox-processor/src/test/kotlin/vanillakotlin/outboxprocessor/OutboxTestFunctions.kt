package vanillakotlin.outboxprocessor

import org.jdbi.v3.core.Jdbi
import vanillakotlin.models.Outbox
import vanillakotlin.random.randomByteArray
import vanillakotlin.random.randomString

fun getRowCount(
    jdbi: Jdbi,
    messageKey: String,
) = // you can't combine an aggregate function with skip locked, so we use a CTE.
    // `SKIP LOCKED` is important to avoid test race conditions.
    jdbi.withHandle<Long, Exception> { handle ->
        handle.createQuery(
            """
            SELECT COUNT(id) FROM (
                SELECT id FROM outbox WHERE message_key = :messageKey FOR UPDATE SKIP LOCKED
            ) count
            """.trimIndent(),
        )
            .bind("messageKey", messageKey)
            .mapTo(Long::class.java)
            .single()
    }

fun buildOutbox(messageKey: String = randomString()): Outbox = Outbox(
    messageKey = messageKey,
    headers = mapOf(randomString() to randomByteArray()),
    body = randomByteArray(),
)
