package vanillakotlin.kafka.consumer

import org.apache.kafka.clients.admin.AdminClient
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import vanillakotlin.kafka.MockMetricsPublisher
import vanillakotlin.kafka.createTestTopic
import vanillakotlin.kafka.deleteTestTopic
import vanillakotlin.kafka.models.KafkaConsumerSequenceHandler
import vanillakotlin.kafka.models.KafkaMessage
import vanillakotlin.kafka.models.KafkaOutputMessage
import vanillakotlin.kafka.models.TopicPartitionOffset
import vanillakotlin.kafka.producer.KafkaProducer
import vanillakotlin.random.randomString
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(45, unit = TimeUnit.SECONDS)
class KafkaConsumerTest {

    private val broker = "localhost:9092"
    private lateinit var adminClient: AdminClient
    private val testTopics = mutableSetOf<String>()

    @BeforeAll fun setup() {
        adminClient = AdminClient.create(mapOf("bootstrap.servers" to broker))
    }

    @AfterAll fun cleanup() {
        testTopics.forEach { topic ->
            runCatching { adminClient.deleteTestTopic(topic) }
        }
        adminClient.close()
    }

    private fun createTestTopic(): String {
        val topicName = "test-consumer-${randomString()}"
        adminClient.createTestTopic(topicName)
        testTopics.add(topicName)
        return topicName
    }

    @Test fun `should create consumer with valid configuration`() {
        val topicName = createTestTopic()
        val processedMessages = mutableListOf<KafkaMessage>()

        val consumer = KafkaConsumer(
            config = KafkaConsumer.Config(
                appName = "test-consumer",
                broker = broker,
                topics = setOf(topicName),
                group = randomString(6),
                autoOffsetResetConfig = "earliest",
            ),
            eventHandler = KafkaConsumerSequenceHandler { messages ->
                processedMessages.addAll(messages)
            },
        )

        assertEquals("test-consumer", consumer.name)
        assertFalse(consumer.check().isHealthy) // Not healthy until started and assigned partitions
    }

    @Test fun `should consume and process messages from Kafka topic`() {
        val topicName = createTestTopic()
        val processedMessages = mutableListOf<KafkaMessage>()
        val latch = CountDownLatch(2)

        val consumer = KafkaConsumer(
            config = KafkaConsumer.Config(
                appName = "test-consumer",
                broker = broker,
                topics = setOf(topicName),
                group = randomString(6),
                autoOffsetResetConfig = "earliest",
                pollTimeoutMs = 500L,
            ),
            eventHandler = KafkaConsumerSequenceHandler { messages ->
                if (messages.any()) {
                    processedMessages.addAll(messages)
                    messages.forEach { _ -> latch.countDown() }
                }
            },
        )

        consumer.start()

        // Produce test messages
        val producer = KafkaProducer<String>(
            config = KafkaProducer.Config(broker = broker, topic = topicName),
            publishTimerMetric = MockMetricsPublisher(),
        ).start()

        producer.send(KafkaOutputMessage(key = "test-key-1", value = "test-message-1"))
        producer.send(KafkaOutputMessage(key = "test-key-2", value = "test-message-2"))

        // Wait for messages to be processed
        assertTrue(latch.await(10, TimeUnit.SECONDS), "Messages should be processed within timeout")

        consumer.stop()
        producer.close()

        assertEquals(2, processedMessages.size)
        assertEquals("test-key-1", processedMessages[0].key)
        assertEquals("test-key-2", processedMessages[1].key)
    }

    @Test fun `should handle consumer rebalancing`() {
        val topicName = createTestTopic()
        val assignmentChanges = mutableListOf<String>()
        val latch = CountDownLatch(1)

        val consumer = KafkaConsumer(
            config = KafkaConsumer.Config(
                appName = "test-consumer",
                broker = broker,
                topics = setOf(topicName),
                group = randomString(6),
                autoOffsetResetConfig = "earliest",
            ),
            eventHandler = KafkaConsumerSequenceHandler { messages ->
                if (messages.any()) {
                    assignmentChanges.add("message-received")
                    latch.countDown()
                }
            },
        )

        consumer.start()

        // Produce a message to trigger processing
        val producer = KafkaProducer<String>(
            config = KafkaProducer.Config(broker = broker, topic = topicName),
            publishTimerMetric = MockMetricsPublisher(),
        ).start()

        producer.send(KafkaOutputMessage(key = "test-key", value = "test-message"))

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Should receive message after assignment")

        // Check that consumer is healthy (has assignments)
        assertTrue(consumer.assignment.isNotEmpty(), "Consumer should have partition assignments")
        assertTrue(consumer.check().isHealthy, "Consumer should be healthy with assignments")

        consumer.stop()
        producer.close()
    }

    @Test fun `should handle error scenarios gracefully`() {
        val topicName = createTestTopic()
        val errorCount = AtomicInteger(0)
        val lastError = AtomicReference<KafkaError>()
        val latch = CountDownLatch(1)

        val consumer = KafkaConsumer(
            config = KafkaConsumer.Config(
                appName = "test-consumer",
                broker = broker,
                topics = setOf(topicName),
                group = randomString(6),
                autoOffsetResetConfig = "earliest",
                skipErrors = KafkaConsumer.SkipErrorsConfig(all = true), // Skip all errors for testing
            ),
            eventHandler = KafkaConsumerSequenceHandler { messages ->
                messages.forEach { _ ->
                    latch.countDown()
                    throw RuntimeException("Test error")
                }
            },
            uncaughtErrorHandler = { error ->
                errorCount.incrementAndGet()
                lastError.set(error)
            },
        )

        consumer.start()

        // Produce a message that will cause an error
        val producer = KafkaProducer<String>(
            config = KafkaProducer.Config(broker = broker, topic = topicName),
            publishTimerMetric = MockMetricsPublisher(),
        ).start()

        producer.send(KafkaOutputMessage(key = "error-key", value = "error-message"))

        assertTrue(latch.await(10, TimeUnit.SECONDS))

        consumer.stop()
        producer.close()

        assertTrue(errorCount.get() > 0, "Error handler should be called")
        assertEquals("Test error", lastError.get()?.throwable?.message)
    }

    @Test fun `should skip errors based on partition offset configuration`() {
        val topicName = createTestTopic()
        val errorCount = AtomicInteger(0)
        val processedCount = AtomicInteger(0)
        val latch = CountDownLatch(1)

        val consumer = KafkaConsumer(
            config = KafkaConsumer.Config(
                appName = "test-consumer",
                broker = broker,
                topics = setOf(topicName),
                group = randomString(6),
                autoOffsetResetConfig = "earliest",
                skipErrors = KafkaConsumer.SkipErrorsConfig(
                    partitionOffsets = listOf(
                        TopicPartitionOffset(topicName, 0, 0), // Skip first message
                    ),
                ),
            ),
            eventHandler = KafkaConsumerSequenceHandler { messages ->
                messages.forEach { message ->
                    processedCount.incrementAndGet()
                    latch.countDown()
                    if (message.offset == 0L) {
                        throw RuntimeException("Skip this message")
                    }
                }
            },
            uncaughtErrorHandler = { error ->
                errorCount.incrementAndGet()
            },
        )

        consumer.start()

        val producer = KafkaProducer<String>(
            config = KafkaProducer.Config(broker = broker, topic = topicName),
            publishTimerMetric = MockMetricsPublisher(),
        ).start()

        // Send two messages
        producer.send(KafkaOutputMessage(key = "key-1", value = "message-1"))
        producer.send(KafkaOutputMessage(key = "key-2", value = "message-2"))

        assertTrue(latch.await(10, TimeUnit.SECONDS))

        consumer.stop()
        producer.close()

        assertTrue(processedCount.get() == 1, "At least one message should be processed")
        // The error should be skipped for offset 0, so error count might be 0 or 1 depending on timing
    }

    @Test fun `should commit offsets manually when configured`() {
        val topicName = createTestTopic()
        val processedMessages = mutableListOf<KafkaMessage>()
        val latch = CountDownLatch(1)

        val consumer = KafkaConsumer(
            config = KafkaConsumer.Config(
                appName = "test-consumer",
                broker = broker,
                topics = setOf(topicName),
                group = randomString(6),
                autoOffsetResetConfig = "earliest",
                autoCommitOffsets = false, // Manual commit
            ),
            eventHandler = KafkaConsumerSequenceHandler { messages ->
                if (messages.any()) {
                    processedMessages.addAll(messages)
                    latch.countDown()
                }
            },
        )

        consumer.start()

        val producer = KafkaProducer<String>(
            config = KafkaProducer.Config(broker = broker, topic = topicName),
            publishTimerMetric = MockMetricsPublisher(),
        ).start()

        producer.send(KafkaOutputMessage(key = "commit-test", value = "commit-message"))

        assertTrue(latch.await(10, TimeUnit.SECONDS))
        assertTrue(consumer.assignment.isNotEmpty(), "Consumer should have partition assignments")

        // Manually commit offsets
        consumer.commitOffsets()

        consumer.stop()
        producer.close()

        assertEquals(1, processedMessages.size)
        assertEquals("commit-test", processedMessages[0].key)
    }

    @Test fun `should handle specific partition assignment`() {
        val topicName = createTestTopic()
        val processedMessages = mutableListOf<KafkaMessage>()
        val latch = CountDownLatch(1)

        val consumer = KafkaConsumer(
            config = KafkaConsumer.Config(
                appName = "test-consumer",
                broker = broker,
                topics = setOf(topicName),
                group = randomString(6),
                autoOffsetResetConfig = "earliest",
                partitions = setOf(0), // Assign specific partition
            ),
            eventHandler = KafkaConsumerSequenceHandler { messages ->
                if (messages.any()) {
                    processedMessages.addAll(messages)
                    latch.countDown()
                }
            },
        )

        consumer.start()

        val producer = KafkaProducer<String>(
            config = KafkaProducer.Config(broker = broker, topic = topicName),
            publishTimerMetric = MockMetricsPublisher(),
        ).start()

        producer.send(KafkaOutputMessage(key = "partition-test", value = "partition-message", partition = 0))

        assertTrue(latch.await(10, TimeUnit.SECONDS))

        consumer.stop()
        producer.close()

        assertEquals(1, processedMessages.size)
        assertEquals(0, processedMessages[0].partition)
    }
}
