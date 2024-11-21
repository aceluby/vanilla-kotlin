package vanillakotlin.bulkinserter

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant

class BulkInserterEventHandlerTest {
    @Test fun `a populated message should be added to the batch`() {
        val userName = randomUsername()
        val tcin = randomTcin()
        val kafkaMessage = buildTestMessage(username = userName, itemIdentifier = tcin)

        val batch = mutableListOf<UserFavoriteTcin>()
        var runBatch = false

        val handler =
            BulkInserterEventHandler(
                addToBatch = { batch.add(it) },
                runBatch = {
                    runBatch = true
                    1
                },
            )

        handler.processEvent(kafkaMessage)

        assertSoftly(batch) {
            size shouldBe 1
            first().userName shouldBe userName
            first().itemIdentifier shouldBe tcin
            first().isDeleted shouldBe false
            runBatch shouldBe false
        }
    }

    @Test fun `the same record should be added twice`() {
        val userName = randomUsername()
        val tcin = randomTcin()
        val kafkaMessage = buildTestMessage(username = userName, itemIdentifier = tcin)

        val batch = mutableListOf<UserFavoriteTcin>()
        var runBatch = false

        val handler =
            BulkInserterEventHandler(
                addToBatch = { batch.add(it) },
                runBatch = {
                    runBatch = true
                    1
                },
            )

        handler.processEvent(kafkaMessage)
        handler.processEvent(kafkaMessage)

        assertSoftly(batch) {
            size shouldBe 2
            get(0) shouldBe get(1)
            runBatch shouldBe false
        }
    }

    @Test fun `it should handle deletes when the body is null`() {
        val userName = randomUsername()
        val tcin = randomTcin()
        val kafkaMessage =
            KafkaMessage(
                broker = randomString(),
                topic = randomString(),
                key = "$userName:$tcin",
                partition = randomInt(1..3),
                offset = randomLong(),
                headers = emptyMap(),
                timestamp = Instant.now().toEpochMilli(),
                body = null,
            )

        val batch = mutableListOf<UserFavoriteTcin>()

        val handler =
            BulkInserterEventHandler(
                addToBatch = { batch.add(it) },
                runBatch = { 1 },
            )

        handler.processEvent(kafkaMessage)

        assertSoftly(batch) {
            size shouldBe 1
            first().userName shouldBe userName
            first().itemIdentifier shouldBe tcin
            first().isDeleted shouldBe true
        }
    }

    @Test fun `it should run the batch at the end of the consumer batch`() {
        val userName = randomUsername()
        val tcin = randomTcin()
        val kafkaMessage = buildTestMessage(username = userName, itemIdentifier = tcin, endOfBatch = true)

        val batch = mutableListOf<UserFavoriteTcin>()
        var runBatch = false

        val handler =
            BulkInserterEventHandler(
                addToBatch = { batch.add(it) },
                runBatch = {
                    runBatch = true
                    1
                },
            )

        handler.processEvent(kafkaMessage)

        assertSoftly(batch) {
            size shouldBe 1
            first().userName shouldBe userName
            first().itemIdentifier shouldBe tcin
            first().isDeleted shouldBe false
            runBatch shouldBe true
        }
    }

    private fun buildTestMessage(
        username: String = randomUsername(),
        itemIdentifier: ItemIdentifier = randomTcin(),
        endOfBatch: Boolean = false,
    ): KafkaMessage {
        return KafkaMessage(
            broker = randomString(),
            topic = randomString(),
            key = "$username:$itemIdentifier",
            partition = randomInt(1..3),
            offset = randomLong(),
            headers = emptyMap(),
            timestamp = Instant.now().toEpochMilli(),
            body = mapper.writeValueAsBytes(UserFavoriteTcin(userName = username, itemIdentifier = itemIdentifier)),
            endOfBatch = endOfBatch,
        )
    }
}
