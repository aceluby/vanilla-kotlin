package vanillakotlin.kafka.producer

import com.fasterxml.jackson.module.kotlin.readValue
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.producer.RecordMetadata
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.fail
import vanillakotlin.kafka.MockMetricsPublisher
import vanillakotlin.kafka.collectMessages
import vanillakotlin.kafka.createTestTopic
import vanillakotlin.kafka.deleteTestTopic
import vanillakotlin.kafka.models.KafkaOutputMessage
import vanillakotlin.kafka.provenance.PROVENANCES_HEADER_NAME
import vanillakotlin.kafka.provenance.Provenance
import vanillakotlin.kafka.provenance.SPAN_ID_HEADER_NAME
import vanillakotlin.random.randomString
import vanillakotlin.serde.mapper
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KafkaProducerTest {

    private val broker = "localhost:9092"
    private lateinit var adminClient: AdminClient
    private val testTopics = mutableSetOf<String>()

    @BeforeAll fun setup() {
        adminClient = AdminClient.create(mapOf("bootstrap.servers" to broker))
    }

    @AfterAll fun cleanup() {
        testTopics.forEach { topic ->
            try {
                adminClient.deleteTestTopic(topic)
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
        adminClient.close()
    }

    private fun createTestTopic(): String {
        val topicName = "test-producer-${randomString()}"
        adminClient.createTestTopic(topicName)
        testTopics.add(topicName)
        return topicName
    }

    @Test fun `should create producer with valid configuration`() {
        val topicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()

        val producer = KafkaProducer<String>(
            config = KafkaProducer.Config(
                broker = broker,
                topic = topicName,
            ),
            publishTimerMetric = metricsPublisher,
        )

        producer.start()

        assertTrue(producer.partitionCount > 0, "Producer should have partition count > 0")

        producer.close()
    }

    @Test fun `should send message synchronously to Kafka topic`() {
        val topicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()

        val producer = KafkaProducer<String>(
            config = KafkaProducer.Config(
                broker = broker,
                topic = topicName,
            ),
            publishTimerMetric = metricsPublisher,
        ).start()

        val message = KafkaOutputMessage(
            key = "test-key",
            value = "test-value",
        )

        val recordMetadata = producer.send(message)

        assertNotNull(recordMetadata)
        assertEquals(topicName, recordMetadata.topic())
        assertTrue(recordMetadata.offset() >= 0)

        producer.close()

        // Verify message was actually sent
        val receivedMessages = collectMessages(
            broker = broker,
            topic = topicName,
            timeoutMs = 5000L,
        )

        assertEquals(1, receivedMessages.size)
        assertEquals("test-key", receivedMessages[0].key)
        assertEquals("\"test-value\"", String(receivedMessages[0].body ?: fail { "receivedMessages[0].body should not be null" })) // JSON serialized
    }

    @Test fun `should send message asynchronously to Kafka topic`() {
        val topicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()

        val producer = KafkaProducer<String>(
            config = KafkaProducer.Config(
                broker = broker,
                topic = topicName,
            ),
            publishTimerMetric = metricsPublisher,
        ).start()

        val message = KafkaOutputMessage(
            key = "async-key",
            value = "async-value",
        )

        val future = producer.sendAsync(message)
        val recordMetadata = future.get(10, TimeUnit.SECONDS)

        assertNotNull(recordMetadata)
        assertEquals(topicName, recordMetadata.topic())
        assertTrue(recordMetadata.offset() >= 0)

        producer.close()

        // Verify message was actually sent
        val receivedMessages = collectMessages(
            broker = broker,
            topic = topicName,
            timeoutMs = 5000L,
        )

        assertEquals(1, receivedMessages.size)
        assertEquals("async-key", receivedMessages[0].key)
    }

    @Test fun `should handle partition calculation with partition key`() {
        val topicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()

        val producer = KafkaProducer<String>(
            config = KafkaProducer.Config(
                broker = broker,
                topic = topicName,
            ),
            publishTimerMetric = metricsPublisher,
        ).start()

        val message = KafkaOutputMessage(
            key = "partition-key-test",
            value = "partition-value",
            partitionKey = "specific-partition-key",
        )

        val recordMetadata = producer.send(message)

        assertNotNull(recordMetadata)
        assertTrue(recordMetadata.partition() >= 0)
        assertTrue(recordMetadata.partition() < producer.partitionCount)

        producer.close()
    }

    @Test fun `should handle explicit partition assignment`() {
        val topicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()

        val producer = KafkaProducer<String>(
            config = KafkaProducer.Config(
                broker = broker,
                topic = topicName,
            ),
            publishTimerMetric = metricsPublisher,
        ).start()

        val message = KafkaOutputMessage(
            key = "explicit-partition-key",
            value = "explicit-partition-value",
            // Explicit partition
            partition = 0,
        )

        val recordMetadata = producer.send(message)

        assertNotNull(recordMetadata)
        assertEquals(0, recordMetadata.partition())

        producer.close()
    }

    @Test fun `should add headers to messages`() {
        val topicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()

        val producer = KafkaProducer<String>(
            config = KafkaProducer.Config(
                broker = broker,
                topic = topicName,
            ),
            publishTimerMetric = metricsPublisher,
        ).start()

        val customHeaders = mapOf(
            "custom-header" to "custom-value".toByteArray(),
            "another-header" to "another-value".toByteArray(),
        )

        val message = KafkaOutputMessage(
            key = "header-test",
            value = "header-value",
            headers = customHeaders,
        )

        producer.send(message)
        producer.close()

        val receivedMessages = collectMessages(
            broker = broker,
            topic = topicName,
            timeoutMs = 5000L,
        )

        assertEquals(1, receivedMessages.size)
        val receivedMessage = receivedMessages[0]

        // Check custom headers
        assertEquals("custom-value", String(receivedMessage.headers["custom-header"] ?: fail { "custom-header should not be null" }))
        assertEquals("another-value", String(receivedMessage.headers["another-header"] ?: fail { "another-header should not be null" }))

        // Check automatically added headers
        assertNotNull(receivedMessage.headers[SPAN_ID_HEADER_NAME])
    }

    @Test fun `should add provenance information to messages`() {
        val topicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()

        val producer = KafkaProducer<String>(
            config = KafkaProducer.Config(
                broker = broker,
                topic = topicName,
            ),
            publishTimerMetric = metricsPublisher,
        ).start()

        val provenance = Provenance(
            spanId = "test-span-id",
            timestamp = Instant.now(),
            entity = "test-entity",
        )

        val message = KafkaOutputMessage(
            key = "provenance-test",
            value = "provenance-value",
            provenances = mutableListOf(provenance),
        )

        producer.send(message)
        producer.close()

        val receivedMessages = collectMessages(
            broker = broker,
            topic = topicName,
            timeoutMs = 5000L,
        )

        assertEquals(1, receivedMessages.size)
        val receivedMessage = receivedMessages[0]

        // Check provenance header
        val provenanceBytes = receivedMessage.headers[PROVENANCES_HEADER_NAME] ?: fail { "Provenance header not found" }
        val deserializedProvenances = mapper.readValue<List<Provenance>>(provenanceBytes)

        assertEquals(1, deserializedProvenances.size)
        assertEquals("test-span-id", deserializedProvenances[0].spanId)
        assertEquals("test-entity", deserializedProvenances[0].entity)
    }

    @Test fun `should automatically generate span ID if not provided`() {
        val topicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()

        val producer = KafkaProducer<String>(
            config = KafkaProducer.Config(
                broker = broker,
                topic = topicName,
            ),
            publishTimerMetric = metricsPublisher,
        ).start()

        val message = KafkaOutputMessage(
            key = "span-id-test",
            value = "span-id-value",
        )

        producer.send(message)
        producer.close()

        val receivedMessages = collectMessages(
            broker = broker,
            topic = topicName,
            timeoutMs = 5000L,
        )

        assertEquals(1, receivedMessages.size)
        val receivedMessage = receivedMessages[0]

        // Should have automatically generated span ID
        assertNotNull(receivedMessage.headers[SPAN_ID_HEADER_NAME])
        assertTrue(String(receivedMessage.headers[SPAN_ID_HEADER_NAME] ?: fail { "SPAN_ID_HEADER_NAME should not be null" }).isNotEmpty())
    }

    @Test fun `should handle null values`() {
        val topicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()

        val producer = KafkaProducer<String?>(
            config = KafkaProducer.Config(
                broker = broker,
                topic = topicName,
            ),
            publishTimerMetric = metricsPublisher,
        ).start()

        val message = KafkaOutputMessage<String?>(
            key = "null-value-test",
            value = null,
        )

        val recordMetadata = producer.send(message)

        assertNotNull(recordMetadata)

        producer.close()

        val receivedMessages = collectMessages(
            broker = broker,
            topic = topicName,
            timeoutMs = 5000L,
        )

        assertEquals(1, receivedMessages.size)
        val receivedMessage = receivedMessages[0]
        assertEquals("null-value-test", receivedMessage.key)
        // Body should be null or "null" depending on JSON serialization
        assertTrue(receivedMessage.body == null || String(receivedMessage.body ?: fail { "receivedMessage.body should not be null when checking null value" }).equals("null"))
    }

    @Test fun `should handle custom partition calculator`() {
        val topicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()

        // Custom partition calculator that always returns partition 0
        val customPartitionCalculator = { _: String?, _: Int -> 0 }

        val producer = KafkaProducer<String>(
            config = KafkaProducer.Config(
                broker = broker,
                topic = topicName,
            ),
            publishTimerMetric = metricsPublisher,
            partitionFor = customPartitionCalculator,
        ).start()

        val message = KafkaOutputMessage(
            key = "custom-partition-test",
            value = "custom-partition-value",
            partitionKey = "any-key",
        )

        val recordMetadata = producer.send(message)

        assertEquals(0, recordMetadata.partition())

        producer.close()
    }

    @Test fun `should publish metrics for successful sends`() {
        val topicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()

        val producer = KafkaProducer<String>(
            config = KafkaProducer.Config(
                broker = broker,
                topic = topicName,
            ),
            publishTimerMetric = metricsPublisher,
        ).start()

        val message = KafkaOutputMessage(
            key = "metrics-test",
            value = "metrics-value",
        )

        producer.send(message)
        producer.close()

        assertTrue(metricsPublisher.publishedMetrics.isNotEmpty(), "Metrics should be published")

        val metric = metricsPublisher.publishedMetrics.find { it.first == "kafka.send" }
        assertNotNull(metric, "kafka.send metric should be published")

        val tags = metric?.second ?: fail { "metric should not be null" }
        assertEquals(topicName, tags["topic"])
        assertEquals("true", tags["success"])
    }

    @Test fun `should handle multiple concurrent sends`() {
        val topicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()

        val producer = KafkaProducer<String>(
            config = KafkaProducer.Config(
                broker = broker,
                topic = topicName,
            ),
            publishTimerMetric = metricsPublisher,
        ).start()

        val futures = mutableListOf<CompletableFuture<RecordMetadata>>()
        val messageCount = 10

        // Send multiple messages concurrently
        repeat(messageCount) { i ->
            val message = KafkaOutputMessage(
                key = "concurrent-$i",
                value = "concurrent-value-$i",
            )
            futures.add(producer.sendAsync(message))
        }

        // Wait for all to complete
        CompletableFuture.allOf(*futures.toTypedArray()).get(30, TimeUnit.SECONDS)

        producer.close()

        // Verify all messages were sent
        val receivedMessages = collectMessages(
            broker = broker,
            topic = topicName,
            timeoutMs = 10000L,
        )

        assertEquals(messageCount, receivedMessages.size)

        // Verify all keys are present
        val receivedKeys = receivedMessages.map { it.key }.toSet()
        repeat(messageCount) { i ->
            assertTrue(receivedKeys.contains("concurrent-$i"))
        }
    }
}
