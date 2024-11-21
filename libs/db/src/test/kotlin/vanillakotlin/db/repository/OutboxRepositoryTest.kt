package vanillakotlin.db.repository

class OutboxRepositoryTest {
    private val db by lazy { buildTestDb() }

    @Test fun `pop outbox`() {
        // we'll use the same message key for our test messages to uniquely identify them within other possible test data
        // that might exist
        val messageKey1 = randomString()
        val messageKey2 = randomString()
        val testHeaders = mapOf(randomString() to randomByteArray())
        val testBody = randomByteArray()
        val outbox =
            Outbox(
                messageKey = messageKey1,
                headers = testHeaders,
                body = testBody,
            )

        db.withTransaction { tx ->
            insertOutbox(tx, outbox)
            insertOutbox(tx, outbox.copy(messageKey = messageKey2))
        }

        // pop all the messages off the outbox and find the one we're interested in
        db.withTransaction { tx ->
            val outboxes = popOutbox(tx).filter { it.messageKey in listOf(messageKey1, messageKey2) }
            outboxes.size shouldBe 2
            assertSoftly(outboxes.first()) {
                messageKey shouldBe messageKey1
                createdTs shouldNotBe null
                headers shouldBe testHeaders
                body shouldBe testBody
            }
        }

        // there shouldn't be any more messages in the outbox
        db.withTransaction { tx ->
            popOutbox(tx).size shouldBe 0
        }
    }
}
