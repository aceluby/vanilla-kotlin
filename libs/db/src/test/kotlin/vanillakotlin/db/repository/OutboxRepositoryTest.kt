package vanillakotlin.db.repository

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import vanillakotlin.models.Outbox
import vanillakotlin.random.randomByteArray
import vanillakotlin.random.randomString

class OutboxRepositoryTest {
    private val jdbi by lazy { buildTestDb() }

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

        jdbi.inTransaction<Unit, Exception> { handle ->
            insertOutbox(handle, outbox)
            insertOutbox(handle, outbox.copy(messageKey = messageKey2))
        }

        // pop all the messages off the outbox and find the one we're interested in
        jdbi.inTransaction<Unit, Exception> { handle ->
            val outboxes = popOutbox(handle).filter { it.messageKey in listOf(messageKey1, messageKey2) }
            outboxes.size shouldBe 2
            assertSoftly(outboxes.first()) {
                messageKey shouldBe messageKey1
                createdTs shouldNotBe null
                headers shouldBe testHeaders
                body shouldBe testBody
            }
        }

        // there shouldn't be any more messages in the outbox
        jdbi.inTransaction<Unit, Exception> { handle ->
            popOutbox(handle).size shouldBe 0
        }
    }
}
