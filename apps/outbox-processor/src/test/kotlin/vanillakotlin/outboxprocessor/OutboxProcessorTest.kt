package vanillakotlin.outboxprocessor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition
import org.junit.jupiter.api.Test
import vanillakotlin.db.repository.buildTestDb
import vanillakotlin.db.repository.insertOutbox
import vanillakotlin.random.randomInt
import vanillakotlin.random.randomString
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture

class OutboxProcessorTest {
    private val jdbi by lazy { buildTestDb() }

    @Test fun `pop and send normal path`() {
        val messageKey = randomString()

        jdbi.inTransaction<Unit, Exception> { handle -> insertOutbox(handle, buildOutbox(messageKey = messageKey)) }
        getRowCount(jdbi, messageKey) shouldBe 1

        val processor =
            OutboxProcessor(
                config = OutboxProcessor.Config(pollEvery = Duration.ofSeconds(1), popMessageLimit = 100),
                jdbi = jdbi,
                kafkaSendAsync = { CompletableFuture.supplyAsync { buildTestRecordMetadata() } },
            )

        processor.popAndSend()

        getRowCount(jdbi, messageKey) shouldBe 0
    }

    @Test fun `pop and send failure should roll back the transaction`() {
        val messageKey = randomString()

        jdbi.inTransaction<Unit, Exception> { handle -> insertOutbox(handle, buildOutbox(messageKey = messageKey)) }
        getRowCount(jdbi, messageKey) shouldBe 1

        val processor =
            OutboxProcessor(
                config = OutboxProcessor.Config(pollEvery = Duration.ofSeconds(1), popMessageLimit = 100),
                jdbi = jdbi,
                kafkaSendAsync = {
                    CompletableFuture.supplyAsync {
                        throw Exception("expected")
                    }
                },
            )

        shouldThrow<Exception> {
            processor.popAndSend()
        }

        getRowCount(jdbi, messageKey) shouldBe 1
    }

    private fun buildTestRecordMetadata() = RecordMetadata(
        TopicPartition(randomString(), 0),
        0,
        0,
        Instant.now().toEpochMilli(),
        randomInt(),
        randomInt(),
    )
}
