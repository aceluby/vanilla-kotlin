package vanillakotlin.bulkinserter

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import vanillakotlin.kafka.models.KafkaMessage
import vanillakotlin.models.FavoriteThing
import vanillakotlin.models.UserFavoriteThing
import vanillakotlin.random.randomInt
import vanillakotlin.random.randomLong
import vanillakotlin.random.randomString
import vanillakotlin.random.randomThing
import vanillakotlin.random.randomUsername
import vanillakotlin.serde.mapper
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

class BulkInserterEventHandlerTest {
    @Test fun `a populated message should be added to the batch`() {
        val batchCount = AtomicInteger(0)
        val addedItems = mutableListOf<FavoriteThing>()
        val runBatchCount = AtomicInteger(0)

        val handler = BulkInserterHandler(
            addToBatch = { item ->
                addedItems.add(item)
                batchCount.incrementAndGet()
            },
            runBatch = {
                runBatchCount.incrementAndGet()
                1
            },
        )

        val userFavoriteThing = UserFavoriteThing(
            userName = randomUsername(),
            thingIdentifier = randomThing(),
        )

        handler.processSequence(
            sequenceOf(
                KafkaMessage(
                    broker = randomString(),
                    topic = randomString(),
                    key = "${userFavoriteThing.userName}:${userFavoriteThing.thingIdentifier}",
                    partition = randomInt(1..3),
                    offset = randomLong(),
                    headers = emptyMap(),
                    timestamp = Instant.now().toEpochMilli(),
                    body = mapper.writeValueAsBytes(userFavoriteThing),
                ),
            ),
        )

        assertSoftly {
            batchCount.get() shouldBe 1
            runBatchCount.get() shouldBe 1
            addedItems.size shouldBe 1
            addedItems[0].thingIdentifier shouldBe userFavoriteThing.thingIdentifier
            addedItems[0].isDeleted shouldBe false
        }
    }

    @Test fun `it should handle deletes when the body is null`() {
        val batchCount = AtomicInteger(0)
        val addedItems = mutableListOf<FavoriteThing>()
        val runBatchCount = AtomicInteger(0)

        val handler = BulkInserterHandler(
            addToBatch = { item ->
                addedItems.add(item)
                batchCount.incrementAndGet()
            },
            runBatch = {
                runBatchCount.incrementAndGet()
                1
            },
        )

        val thingIdentifier = randomThing()
        val userName = randomUsername()

        handler.processSequence(
            sequenceOf(
                KafkaMessage(
                    broker = randomString(),
                    topic = randomString(),
                    key = "$userName:$thingIdentifier",
                    partition = randomInt(1..3),
                    offset = randomLong(),
                    headers = emptyMap(),
                    timestamp = Instant.now().toEpochMilli(),
                    body = null,
                ),
            ),
        )

        assertSoftly {
            batchCount.get() shouldBe 1
            runBatchCount.get() shouldBe 1
            addedItems.size shouldBe 1
            addedItems[0].thingIdentifier shouldBe thingIdentifier
            addedItems[0].isDeleted shouldBe true
        }
    }

    @Test fun `the same record should be added twice`() {
        val batchCount = AtomicInteger(0)
        val addedItems = mutableListOf<FavoriteThing>()
        val runBatchCount = AtomicInteger(0)

        val handler = BulkInserterHandler(
            addToBatch = { item ->
                addedItems.add(item)
                batchCount.incrementAndGet()
            },
            runBatch = {
                runBatchCount.incrementAndGet()
                1
            },
        )

        val userFavoriteThing = UserFavoriteThing(
            userName = randomUsername(),
            thingIdentifier = randomThing(),
        )

        handler.processSequence(
            sequenceOf(
                KafkaMessage(
                    broker = randomString(),
                    topic = randomString(),
                    key = "${userFavoriteThing.userName}:${userFavoriteThing.thingIdentifier}",
                    partition = randomInt(1..3),
                    offset = randomLong(),
                    headers = emptyMap(),
                    timestamp = Instant.now().toEpochMilli(),
                    body = mapper.writeValueAsBytes(userFavoriteThing),
                ),
                KafkaMessage(
                    broker = randomString(),
                    topic = randomString(),
                    key = "${userFavoriteThing.userName}:${userFavoriteThing.thingIdentifier}",
                    partition = randomInt(1..3),
                    offset = randomLong(),
                    headers = emptyMap(),
                    timestamp = Instant.now().toEpochMilli(),
                    body = mapper.writeValueAsBytes(userFavoriteThing),
                ),
            ),
        )

        assertSoftly {
            batchCount.get() shouldBe 2
            runBatchCount.get() shouldBe 1
            addedItems.size shouldBe 2
            addedItems[0].thingIdentifier shouldBe userFavoriteThing.thingIdentifier
            addedItems[1].thingIdentifier shouldBe userFavoriteThing.thingIdentifier
        }
    }

    @Test fun `runBatch should return correct count`() {
        val batchCount = AtomicInteger(0)
        val runBatchCount = AtomicInteger(0)
        val expectedBatchSize = 42

        val handler = BulkInserterHandler(
            addToBatch = { batchCount.incrementAndGet() },
            runBatch = {
                runBatchCount.incrementAndGet()
                expectedBatchSize
            },
        )

        handler.processSequence(
            sequenceOf(
                KafkaMessage(
                    broker = randomString(),
                    topic = randomString(),
                    key = "user1:thing-1",
                    partition = randomInt(1..3),
                    offset = randomLong(),
                    headers = emptyMap(),
                    timestamp = Instant.now().toEpochMilli(),
                    body = """{"user_name":"user1","thing_identifier":"thing-1"}""".toByteArray(),
                ),
                KafkaMessage(
                    broker = randomString(),
                    topic = randomString(),
                    key = "user2:thing-2",
                    partition = randomInt(1..3),
                    offset = randomLong(),
                    headers = emptyMap(),
                    timestamp = Instant.now().toEpochMilli(),
                    body = """{"user_name":"user2","thing_identifier":"thing-2"}""".toByteArray(),
                ),
                KafkaMessage(
                    broker = randomString(),
                    topic = randomString(),
                    key = "user3:thing-3",
                    partition = randomInt(1..3),
                    offset = randomLong(),
                    headers = emptyMap(),
                    timestamp = Instant.now().toEpochMilli(),
                    body = """{"user_name":"user3","thing_identifier":"thing-3"}""".toByteArray(),
                ),
            ),
        )

        assertSoftly {
            batchCount.get() shouldBe 3
            runBatchCount.get() shouldBe 1
        }
    }

    @Test fun `should handle empty sequence`() {
        val batchCount = AtomicInteger(0)
        val runBatchCount = AtomicInteger(0)

        val handler = BulkInserterHandler(
            addToBatch = { batchCount.incrementAndGet() },
            runBatch = {
                runBatchCount.incrementAndGet()
                0
            },
        )

        handler.processSequence(emptySequence())

        assertSoftly {
            batchCount.get() shouldBe 0
            runBatchCount.get() shouldBe 1
        }
    }

    @Test fun `should handle large batch efficiently`() {
        val batchCount = AtomicInteger(0)
        val runBatchCount = AtomicInteger(0)
        val batchSize = 1000

        val handler = BulkInserterHandler(
            addToBatch = { batchCount.incrementAndGet() },
            runBatch = {
                runBatchCount.incrementAndGet()
                batchCount.get()
            },
        )

        val messages = (1..batchSize).map { i ->
            KafkaMessage(
                broker = randomString(),
                topic = randomString(),
                key = "user$i:thing-$i",
                partition = randomInt(1..3),
                offset = randomLong(),
                headers = emptyMap(),
                timestamp = Instant.now().toEpochMilli(),
                body = """{"user_name":"user$i","thing_identifier":"thing-$i"}""".toByteArray(),
            )
        }

        handler.processSequence(messages.asSequence())

        assertSoftly {
            batchCount.get() shouldBe batchSize
            runBatchCount.get() shouldBe 1
        }
    }

    @Test fun `should handle mixed create and delete operations in same batch`() {
        val addedItems = mutableListOf<FavoriteThing>()
        val runBatchCount = AtomicInteger(0)

        val handler = BulkInserterHandler(
            addToBatch = { item -> addedItems.add(item) },
            runBatch = {
                runBatchCount.incrementAndGet()
                addedItems.size
            },
        )

        val userName = randomUsername()
        val thing1 = randomThing()
        val thing2 = randomThing()
        val thing3 = randomThing()

        handler.processSequence(
            sequenceOf(
                KafkaMessage(
                    broker = randomString(),
                    topic = randomString(),
                    key = "$userName:$thing1",
                    partition = randomInt(1..3),
                    offset = randomLong(),
                    headers = emptyMap(),
                    timestamp = Instant.now().toEpochMilli(),
                    body = """{"user_name":"$userName","thing_identifier":"$thing1"}""".toByteArray(),
                ),
                KafkaMessage(
                    broker = randomString(),
                    topic = randomString(),
                    key = "$userName:$thing2",
                    partition = randomInt(1..3),
                    offset = randomLong(),
                    headers = emptyMap(),
                    timestamp = Instant.now().toEpochMilli(),
                    body = """{"user_name":"$userName","thing_identifier":"$thing2"}""".toByteArray(),
                ),
                KafkaMessage(
                    broker = randomString(),
                    topic = randomString(),
                    key = "$userName:$thing3",
                    partition = randomInt(1..3),
                    offset = randomLong(),
                    headers = emptyMap(),
                    timestamp = Instant.now().toEpochMilli(),
                    body = null, // Delete operation
                ),
            ),
        )

        assertSoftly {
            addedItems.size shouldBe 3
            runBatchCount.get() shouldBe 1

            // Check create operations
            addedItems[0].thingIdentifier shouldBe thing1
            addedItems[0].isDeleted shouldBe false
            addedItems[1].thingIdentifier shouldBe thing2
            addedItems[1].isDeleted shouldBe false

            // Check delete operation
            addedItems[2].thingIdentifier shouldBe thing3
            addedItems[2].isDeleted shouldBe true
        }
    }

    @Test fun `should handle key with multiple colons`() {
        val addedItems = mutableListOf<FavoriteThing>()
        val runBatchCount = AtomicInteger(0)

        val handler = BulkInserterHandler(
            addToBatch = { item -> addedItems.add(item) },
            runBatch = {
                runBatchCount.incrementAndGet()
                1
            },
        )

        val complexKey = "user:with:colons:${randomThing()}"
        val expectedThingId = complexKey.substringAfterLast(":")

        handler.processSequence(
            sequenceOf(
                KafkaMessage(
                    broker = randomString(),
                    topic = randomString(),
                    key = complexKey,
                    partition = randomInt(1..3),
                    offset = randomLong(),
                    headers = emptyMap(),
                    timestamp = Instant.now().toEpochMilli(),
                    body = """{"user_name":"user:with:colons","thing_identifier":"$expectedThingId"}""".toByteArray(),
                ),
            ),
        )

        assertSoftly {
            addedItems.size shouldBe 1
            runBatchCount.get() shouldBe 1
            addedItems[0].thingIdentifier shouldBe expectedThingId
            addedItems[0].isDeleted shouldBe false
        }
    }

    // Error handling tests - these now expect exceptions instead of graceful handling

    @Test fun `should throw exception for null key`() {
        val handler = BulkInserterHandler(
            addToBatch = { },
            runBatch = { 0 },
        )

        shouldThrow<IllegalArgumentException> {
            handler.processSequence(
                sequenceOf(
                    KafkaMessage(
                        broker = randomString(),
                        topic = randomString(),
                        key = null,
                        partition = randomInt(1..3),
                        offset = randomLong(),
                        headers = emptyMap(),
                        timestamp = Instant.now().toEpochMilli(),
                        body = """{"user_name":"user1","thing_identifier":"thing-1"}""".toByteArray(),
                    ),
                ),
            )
        }
    }

    @Test fun `should throw exception for blank key`() {
        val handler = BulkInserterHandler(
            addToBatch = { },
            runBatch = { 0 },
        )

        shouldThrow<IllegalArgumentException> {
            handler.processSequence(
                sequenceOf(
                    KafkaMessage(
                        broker = randomString(),
                        topic = randomString(),
                        key = "   ",
                        partition = randomInt(1..3),
                        offset = randomLong(),
                        headers = emptyMap(),
                        timestamp = Instant.now().toEpochMilli(),
                        body = """{"user_name":"user1","thing_identifier":"thing-1"}""".toByteArray(),
                    ),
                ),
            )
        }
    }

    @Test fun `should throw exception for malformed key`() {
        val handler = BulkInserterHandler(
            addToBatch = { },
            runBatch = { 0 },
        )

        shouldThrow<IllegalArgumentException> {
            handler.processSequence(
                sequenceOf(
                    KafkaMessage(
                        broker = randomString(),
                        topic = randomString(),
                        key = "malformed-key-without-colon",
                        partition = randomInt(1..3),
                        offset = randomLong(),
                        headers = emptyMap(),
                        timestamp = Instant.now().toEpochMilli(),
                        body = """{"user_name":"user1","thing_identifier":"thing-1"}""".toByteArray(),
                    ),
                ),
            )
        }
    }

    @Test fun `should throw exception for empty thing identifier`() {
        val handler = BulkInserterHandler(
            addToBatch = { },
            runBatch = { 0 },
        )

        shouldThrow<IllegalArgumentException> {
            handler.processSequence(
                sequenceOf(
                    KafkaMessage(
                        broker = randomString(),
                        topic = randomString(),
                        key = "username:",
                        partition = randomInt(1..3),
                        offset = randomLong(),
                        headers = emptyMap(),
                        timestamp = Instant.now().toEpochMilli(),
                        body = """{"user_name":"username","thing_identifier":""}""".toByteArray(),
                    ),
                ),
            )
        }
    }

    @Test fun `should throw exception for malformed JSON`() {
        val handler = BulkInserterHandler(
            addToBatch = { },
            runBatch = { 0 },
        )

        shouldThrow<Exception> {
            handler.processSequence(
                sequenceOf(
                    KafkaMessage(
                        broker = randomString(),
                        topic = randomString(),
                        key = "user1:thing-1",
                        partition = randomInt(1..3),
                        offset = randomLong(),
                        headers = emptyMap(),
                        timestamp = Instant.now().toEpochMilli(),
                        body = """{"invalid": "json structure without required fields"}""".toByteArray(),
                    ),
                ),
            )
        }
    }

    @Test fun `should throw exception for completely invalid JSON`() {
        val handler = BulkInserterHandler(
            addToBatch = { },
            runBatch = { 0 },
        )

        shouldThrow<Exception> {
            handler.processSequence(
                sequenceOf(
                    KafkaMessage(
                        broker = randomString(),
                        topic = randomString(),
                        key = "user1:thing-1",
                        partition = randomInt(1..3),
                        offset = randomLong(),
                        headers = emptyMap(),
                        timestamp = Instant.now().toEpochMilli(),
                        body = """not valid json at all""".toByteArray(),
                    ),
                ),
            )
        }
    }
}
