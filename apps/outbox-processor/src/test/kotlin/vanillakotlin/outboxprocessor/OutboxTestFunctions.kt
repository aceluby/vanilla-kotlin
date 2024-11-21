package vanillakotlin.outboxprocessor

fun getRowCount(
    db: Db,
    messageKey: String,
) = // you can't combine an aggregate function with skip locked, so we use a CTE.
    // `SKIP LOCKED` is important to avoid test race conditions.
    db.executeQuery(
        sql =
            """
            SELECT COUNT(id) FROM (
                SELECT id FROM outbox WHERE message_key = :messageKey FOR UPDATE SKIP LOCKED
            ) count
            """.trimIndent(),
        args = mapOf("messageKey" to messageKey),
        rowMapper = { resultSet -> resultSet.getLong("count") },
    )

fun buildOutbox(messageKey: String = randomString()): Outbox =
    Outbox(
        messageKey = messageKey,
        headers = mapOf(randomString() to randomByteArray()),
        body = randomByteArray(),
    )
