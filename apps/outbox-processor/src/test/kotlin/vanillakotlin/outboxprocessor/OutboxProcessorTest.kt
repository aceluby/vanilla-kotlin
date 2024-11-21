package vanillakotlin.outboxprocessor

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture

class OutboxProcessorTest {
    private val db by lazy { buildTestDb() }

    @Test fun `pop and send normal path`() {
        val messageKey = randomString()

        db.withTransaction { tx -> insertOutbox(tx, buildOutbox(messageKey = messageKey)) }
        getRowCount(db, messageKey) shouldBe 1

        val processor =
            OutboxProcessor(
                config = OutboxProcessor.Config(pollEvery = Duration.ofSeconds(1), popMessageLimit = 100),
                db = db,
                kafkaSendAsync = { CompletableFuture.supplyAsync { buildTestRecordMetadata() } },
            )

        processor.popAndSend()

        getRowCount(db, messageKey) shouldBe 0
    }

    @Test fun `pop and send failure should roll back the transaction`() {
        val messageKey = randomString()

        db.withTransaction { tx -> insertOutbox(tx, buildOutbox(messageKey = messageKey)) }
        getRowCount(db, messageKey) shouldBe 1

        val processor =
            OutboxProcessor(
                config = OutboxProcessor.Config(pollEvery = Duration.ofSeconds(1), popMessageLimit = 100),
                db = db,
                kafkaSendAsync = {
                    CompletableFuture.supplyAsync {
                        throw Exception("expected")
                    }
                },
            )

        shouldThrow<Exception> {
            processor.popAndSend()
        }

        getRowCount(db, messageKey) shouldBe 1
    }

    private fun buildTestRecordMetadata() =
        RecordMetadata(
            TopicPartition(randomString(), 0),
            0,
            0,
            Instant.now().toEpochMilli(),
            randomInt(),
            randomInt(),
        )
}
