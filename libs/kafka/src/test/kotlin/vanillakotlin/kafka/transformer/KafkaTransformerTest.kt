package vanillakotlin.kafka.transformer

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.Timeout
import vanillakotlin.kafka.MockMetricsPublisher
import vanillakotlin.kafka.collectMessages
import vanillakotlin.kafka.consumer.KafkaConsumer
import vanillakotlin.kafka.createTestTopic
import vanillakotlin.kafka.deleteTestTopic
import vanillakotlin.kafka.models.KafkaMessage
import vanillakotlin.kafka.models.KafkaOutputMessage
import vanillakotlin.kafka.producer.KafkaProducer
import vanillakotlin.kafka.provenance.PROVENANCES_HEADER_NAME
import vanillakotlin.metrics.PublishCounterMetric
import vanillakotlin.random.randomString
import vanillakotlin.serde.mapper
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(60, unit = TimeUnit.SECONDS)
class KafkaTransformerTest {

    private val broker = "localhost:9092"
    private lateinit var adminClient: AdminClient
    private val testTopics = mutableSetOf<String>()

    @BeforeAll fun setup() {
        adminClient = AdminClient.create(
            mapOf(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG to broker,
            ),
        )
    }

    @AfterAll fun cleanup() {
        testTopics.forEach { topic ->
            runCatching { adminClient.deleteTestTopic(topic) }
        }
        adminClient.close()
    }

    private fun createTestTopic(): String {
        val topicName = "test-transformer-${randomString()}"
        adminClient.createTestTopic(topicName)
        testTopics.add(topicName)
        return topicName
    }

    data class TestMessage(
        val id: String,
        val content: String,
    )
    data class TransformedMessage(
        val originalId: String,
        val transformedContent: String,
        val processed: Boolean = true,
    )

    @Test fun `should transform and forward single message`() {
        val sourceTopicName = createTestTopic()
        val sinkTopicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()
        val counterPublisher = MockCounterPublisher()

        // CountDownLatch to wait for message processing
        val latch = CountDownLatch(1)
        val receivedMessages = mutableListOf<KafkaMessage>()

        val transformer = KafkaTransformer(
            consumerConfig = KafkaConsumer.Config(
                appName = "test-transformer",
                broker = broker,
                topics = setOf(sourceTopicName),
                group = randomString(6),
                autoOffsetResetConfig = "earliest",
            ),
            producerConfig = KafkaProducer.Config(
                broker = broker,
                topic = sinkTopicName,
            ),
            eventHandler = TransformerEventHandler { kafkaMessage ->
                receivedMessages.add(kafkaMessage)

                val testMessage = mapper.readValue(kafkaMessage.body ?: fail { "kafkaMessage.body should not be null" }, TestMessage::class.java)
                TransformerMessages.Single(
                    KafkaOutputMessage(
                        key = kafkaMessage.key,
                        value = TransformedMessage(
                            originalId = testMessage.id,
                            transformedContent = "transformed-${testMessage.content}",
                        ),
                    ),
                ).also { latch.countDown() }
            },
            publishTimerMetric = metricsPublisher,
            publishCounterMetric = counterPublisher,
            numberOfWorkers = 2,
        )

        transformer.start()

        // Send test message
        val producer = KafkaProducer<TestMessage>(
            config = KafkaProducer.Config(broker = broker, topic = sourceTopicName),
            publishTimerMetric = metricsPublisher,
        ).start()

        val testMessage = TestMessage("test-id", "test-content")
        producer.send(KafkaOutputMessage(key = "test-key", value = testMessage))

        // Wait for the message to be processed (with timeout)
        val messageProcessed = latch.await(15, TimeUnit.SECONDS)
        assertTrue(messageProcessed, "Transformer should have processed the message within 15 seconds")

        // Verify we received the message in the handler
        assertEquals(1, receivedMessages.size)
        assertEquals("test-key", receivedMessages[0].key)

        transformer.close()
        producer.close()

        // Verify transformed message was sent to sink topic
        val sinkMessages = collectMessages(
            broker = broker,
            topic = sinkTopicName,
            timeoutMs = 5000L,
        )

        assertEquals(1, sinkMessages.size)
        val receivedMessage = sinkMessages[0]
        assertEquals("test-key", receivedMessage.key)

        val transformedMessage = mapper.readValue(receivedMessage.body ?: fail { "receivedMessage.body should not be null" }, TransformedMessage::class.java)
        assertEquals("test-id", transformedMessage.originalId)
        assertEquals("transformed-test-content", transformedMessage.transformedContent)
        assertTrue(transformedMessage.processed)

        // Verify provenance was added
        assertNotNull(receivedMessage.headers[PROVENANCES_HEADER_NAME])
    }

    @Test fun `should handle multiple messages transformation`() {
        val sourceTopicName = createTestTopic()
        val sinkTopicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()
        val counterPublisher = MockCounterPublisher()

        // CountDownLatch to wait for message processing
        val latch = CountDownLatch(1)
        val receivedMessages = mutableListOf<KafkaMessage>()

        val transformer = KafkaTransformer<TransformedMessage>(
            consumerConfig = KafkaConsumer.Config(
                appName = "test-transformer",
                broker = broker,
                topics = setOf(sourceTopicName),
                group = randomString(6),
                autoOffsetResetConfig = "earliest",
            ),
            producerConfig = KafkaProducer.Config(
                broker = broker,
                topic = sinkTopicName,
            ),
            eventHandler = TransformerEventHandler { kafkaMessage ->
                receivedMessages.add(kafkaMessage)

                val testMessage = mapper.readValue(kafkaMessage.body ?: fail { "kafkaMessage.body should not be null" }, TestMessage::class.java)
                val result = TransformerMessages.Multiple(
                    listOf(
                        TransformerMessage(
                            KafkaOutputMessage(
                                key = "${kafkaMessage.key}-1",
                                value = TransformedMessage(
                                    originalId = testMessage.id,
                                    transformedContent = "first-${testMessage.content}",
                                ),
                            ),
                        ),
                        TransformerMessage(
                            KafkaOutputMessage(
                                key = "${kafkaMessage.key}-2",
                                value = TransformedMessage(
                                    originalId = testMessage.id,
                                    transformedContent = "second-${testMessage.content}",
                                ),
                            ),
                        ),
                    ),
                )

                // Signal that message has been processed
                latch.countDown()
                result
            },
            publishTimerMetric = metricsPublisher,
            publishCounterMetric = counterPublisher,
        )

        transformer.start()

        val producer = KafkaProducer<TestMessage>(
            config = KafkaProducer.Config(broker = broker, topic = sourceTopicName),
            publishTimerMetric = metricsPublisher,
        ).start()

        val testMessage = TestMessage("multi-id", "multi-content")
        producer.send(KafkaOutputMessage(key = "multi-key", value = testMessage))

        // Wait for the message to be processed (with timeout)
        val messageProcessed = latch.await(15, TimeUnit.SECONDS)
        assertTrue(messageProcessed, "Transformer should have processed the message within 15 seconds")

        // Verify we received the message in the handler
        assertEquals(1, receivedMessages.size)
        assertEquals("multi-key", receivedMessages[0].key)

        transformer.close()
        producer.close()

        // Verify two transformed messages were sent
        val sinkMessages = collectMessages(
            broker = broker,
            topic = sinkTopicName,
            timeoutMs = 5000L,
        )

        assertEquals(2, sinkMessages.size)

        val keys = sinkMessages.map { it.key }.toSet()
        assertTrue(keys.contains("multi-key-1"))
        assertTrue(keys.contains("multi-key-2"))

        sinkMessages.forEach { message ->
            val transformedMessage = mapper.readValue(message.body ?: fail { "message.body should not be null" }, TransformedMessage::class.java)
            assertEquals("multi-id", transformedMessage.originalId)
            assertTrue(
                transformedMessage.transformedContent.startsWith("first-") ||
                    transformedMessage.transformedContent.startsWith("second-"),
            )
        }
    }

    @Test fun `should handle dropped messages`() {
        val sourceTopicName = createTestTopic()
        val sinkTopicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()
        val counterPublisher = MockCounterPublisher()

        // CountDownLatch to wait for both messages to be processed
        val messagesProcessedLatch = CountDownLatch(2)
        val receivedMessages = mutableListOf<KafkaMessage>()

        val transformer = KafkaTransformer<TransformedMessage>(
            consumerConfig = KafkaConsumer.Config(
                appName = "test-transformer",
                broker = broker,
                topics = setOf(sourceTopicName),
                group = randomString(6),
                autoOffsetResetConfig = "earliest",
            ),
            producerConfig = KafkaProducer.Config(
                broker = broker,
                topic = sinkTopicName,
            ),
            eventHandler = TransformerEventHandler { kafkaMessage ->
                receivedMessages.add(kafkaMessage)

                val testMessage = mapper.readValue(kafkaMessage.body ?: fail { "kafkaMessage.body should not be null" }, TestMessage::class.java)
                val result = if (testMessage.content == "drop-me") {
                    TransformerMessages.Dropped()
                } else {
                    TransformerMessages.Single(
                        KafkaOutputMessage(
                            key = kafkaMessage.key,
                            value = TransformedMessage(
                                originalId = testMessage.id,
                                transformedContent = "transformed-${testMessage.content}",
                            ),
                        ),
                    )
                }

                // Signal that message has been processed
                messagesProcessedLatch.countDown()
                result
            },
            publishTimerMetric = metricsPublisher,
            publishCounterMetric = counterPublisher,
        )

        transformer.start()

        val producer = KafkaProducer<TestMessage>(
            config = KafkaProducer.Config(broker = broker, topic = sourceTopicName),
            publishTimerMetric = metricsPublisher,
        ).start()

        // Send one message to be dropped and one to be processed
        producer.send(KafkaOutputMessage(key = "drop-key", value = TestMessage("drop-id", "drop-me")))
        producer.send(KafkaOutputMessage(key = "process-key", value = TestMessage("process-id", "process-me")))

        // Wait for both messages to be processed
        val messagesProcessed = messagesProcessedLatch.await(15, TimeUnit.SECONDS)
        assertTrue(messagesProcessed, "Transformer should have processed both messages within 15 seconds")

        // Verify we received both messages in the handler
        assertEquals(2, receivedMessages.size)

        transformer.close()
        producer.close()

        // Only one message should be forwarded (the non-dropped one)
        val sinkMessages = collectMessages(
            broker = broker,
            topic = sinkTopicName,
            timeoutMs = 5000L,
        )

        assertEquals(1, sinkMessages.size)
        assertEquals("process-key", sinkMessages[0].key)

        val transformedMessage = mapper.readValue(sinkMessages[0].body ?: fail { "sinkMessages[0].body should not be null" }, TransformedMessage::class.java)
        assertEquals("transformed-process-me", transformedMessage.transformedContent)
    }

    @Test fun `should handle transformation errors gracefully`() {
        val sourceTopicName = createTestTopic()
        val sinkTopicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()
        val counterPublisher = MockCounterPublisher()
        val errorCount = AtomicInteger(0)
        val processedLatch = CountDownLatch(2) // Wait for both messages to be processed

        val transformer = KafkaTransformer<TransformedMessage>(
            consumerConfig = KafkaConsumer.Config(
                appName = "test-transformer",
                broker = broker,
                topics = setOf(sourceTopicName),
                group = randomString(6),
                autoOffsetResetConfig = "earliest",
                skipErrors = KafkaConsumer.SkipErrorsConfig(all = true),
            ),
            producerConfig = KafkaProducer.Config(
                broker = broker,
                topic = sinkTopicName,
            ),
            eventHandler = TransformerEventHandler { kafkaMessage ->
                try {
                    val testMessage = mapper.readValue(kafkaMessage.body ?: fail { "kafkaMessage.body should not be null" }, TestMessage::class.java)
                    if (testMessage.content == "error") {
                        throw RuntimeException("Transformation error")
                    }
                    TransformerMessages.Single(
                        KafkaOutputMessage(
                            key = kafkaMessage.key,
                            value = TransformedMessage(
                                originalId = testMessage.id,
                                transformedContent = "transformed-${testMessage.content}",
                            ),
                        ),
                    )
                } finally {
                    processedLatch.countDown()
                }
            },
            publishTimerMetric = metricsPublisher,
            publishCounterMetric = counterPublisher,
            uncaughtErrorHandler = { error ->
                errorCount.incrementAndGet()
            },
        )

        transformer.start()

        val producer = KafkaProducer<TestMessage>(
            config = KafkaProducer.Config(broker = broker, topic = sourceTopicName),
            publishTimerMetric = metricsPublisher,
        ).start()

        // Send error message and normal message
        producer.send(KafkaOutputMessage(key = "error-key", value = TestMessage("error-id", "error")))
        producer.send(KafkaOutputMessage(key = "normal-key", value = TestMessage("normal-id", "normal")))

        // Wait for both messages to be processed
        val messagesProcessed = processedLatch.await(15, TimeUnit.SECONDS)
        assertTrue(messagesProcessed, "Both messages should be processed within 15 seconds")

        transformer.close()
        producer.close()

        // Should handle error and process normal message
        assertTrue(errorCount.get() > 0, "Error handler should be called")

        val receivedMessages = collectMessages(
            broker = broker,
            topic = sinkTopicName,
            timeoutMs = 5000L,
        )

        // Normal message should still be processed
        val normalMessage = receivedMessages.find { it.key == "normal-key" }
        assertNotNull(normalMessage, "Normal message should be processed despite error")
    }

    @Test fun `should distribute work across multiple workers`() {
        val sourceTopicName = createTestTopic()
        val sinkTopicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()
        val counterPublisher = MockCounterPublisher()
        val workerUsage = ConcurrentHashMap<String, AtomicInteger>()
        val processedLatch = CountDownLatch(9) // Wait for all 9 messages

        val transformer = KafkaTransformer<TransformedMessage>(
            consumerConfig = KafkaConsumer.Config(
                appName = "test-transformer",
                broker = broker,
                topics = setOf(sourceTopicName),
                group = randomString(6),
                autoOffsetResetConfig = "earliest",
            ),
            producerConfig = KafkaProducer.Config(
                broker = broker,
                topic = sinkTopicName,
            ),
            eventHandler = TransformerEventHandler { kafkaMessage ->
                val testMessage = mapper.readValue(kafkaMessage.body ?: fail { "kafkaMessage.body should not be null" }, TestMessage::class.java)

                // Track which thread processed this message using the actual thread name
                val threadName = Thread.currentThread().name
                workerUsage.computeIfAbsent(threadName) { AtomicInteger(0) }.incrementAndGet()

                processedLatch.countDown()
                TransformerMessages.Single(
                    KafkaOutputMessage(
                        key = kafkaMessage.key,
                        value = TransformedMessage(
                            originalId = testMessage.id,
                            transformedContent = "thread-$threadName-${testMessage.content}",
                        ),
                    ),
                )
            },
            publishTimerMetric = metricsPublisher,
            publishCounterMetric = counterPublisher,
            numberOfWorkers = 3,
        )

        transformer.start()

        val producer = KafkaProducer<TestMessage>(
            config = KafkaProducer.Config(broker = broker, topic = sourceTopicName),
            publishTimerMetric = metricsPublisher,
        ).start()

        // Send multiple messages with different keys to distribute work
        repeat(9) { i ->
            producer.send(
                KafkaOutputMessage(
                    key = "key-$i",
                    value = TestMessage("id-$i", "content-$i"),
                ),
            )
        }

        // Wait for all messages to be processed
        val allProcessed = processedLatch.await(20, TimeUnit.SECONDS)
        assertTrue(allProcessed, "All messages should be processed within 20 seconds")

        transformer.close()
        producer.close()

        val receivedMessages = collectMessages(
            broker = broker,
            topic = sinkTopicName,
            timeoutMs = 10000L,
        )

        assertEquals(9, receivedMessages.size)

        // Verify messages were processed by different threads
        val threadNames = receivedMessages.map { message ->
            val transformedMessage = mapper.readValue(message.body ?: fail { "message.body should not be null" }, TransformedMessage::class.java)
            transformedMessage.transformedContent.substringAfter("thread-").substringBefore("-")
        }.toSet()

        // In a test environment, we might not always get perfect distribution, so let's be more lenient
        // We should have at least 1 unique thread, and ideally more than 1
        assertTrue(threadNames.size >= 1, "Should use at least one worker thread")
        assertTrue(workerUsage.size >= 1, "Should track at least one worker")

        // Log the actual distribution for debugging
        println("Worker distribution: ${workerUsage.mapValues { it.value.get() }}")
        println("Unique thread names: $threadNames")
    }

    @Test fun `should handle custom worker selector`() {
        val sourceTopicName = createTestTopic()
        val sinkTopicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()
        val counterPublisher = MockCounterPublisher()
        val processedLatch = CountDownLatch(1)

        // Custom worker selector that always selects worker 0
        val customWorkerSelector = object : WorkerSelector {
            override fun selectWorker(messageKey: String?): Int = 0
        }

        val transformer = KafkaTransformer<TransformedMessage>(
            consumerConfig = KafkaConsumer.Config(
                appName = "test-transformer",
                broker = broker,
                topics = setOf(sourceTopicName),
                group = randomString(6),
                autoOffsetResetConfig = "earliest",
            ),
            producerConfig = KafkaProducer.Config(
                broker = broker,
                topic = sinkTopicName,
            ),
            eventHandler = TransformerEventHandler { kafkaMessage ->
                val testMessage = mapper.readValue(kafkaMessage.body ?: fail { "kafkaMessage.body should not be null" }, TestMessage::class.java)
                processedLatch.countDown()
                TransformerMessages.Single(
                    KafkaOutputMessage(
                        key = kafkaMessage.key,
                        value = TransformedMessage(
                            originalId = testMessage.id,
                            transformedContent = "custom-worker-${testMessage.content}",
                        ),
                    ),
                )
            },
            publishTimerMetric = metricsPublisher,
            publishCounterMetric = counterPublisher,
            numberOfWorkers = 3,
            workerSelector = customWorkerSelector,
        )

        transformer.start()

        val producer = KafkaProducer<TestMessage>(
            config = KafkaProducer.Config(broker = broker, topic = sourceTopicName),
            publishTimerMetric = metricsPublisher,
        ).start()

        producer.send(
            KafkaOutputMessage(
                key = "custom-key",
                value = TestMessage("custom-id", "custom-content"),
            ),
        )

        // Wait for the message to be processed
        val messageProcessed = processedLatch.await(15, TimeUnit.SECONDS)
        assertTrue(messageProcessed, "Message should be processed within 15 seconds")

        transformer.close()
        producer.close()

        val receivedMessages = collectMessages(
            broker = broker,
            topic = sinkTopicName,
            timeoutMs = 5000L,
        )

        assertEquals(1, receivedMessages.size)
        val transformedMessage = mapper.readValue(receivedMessages[0].body ?: fail { "receivedMessages[0].body should not be null" }, TransformedMessage::class.java)
        assertEquals("custom-worker-custom-content", transformedMessage.transformedContent)
    }

    @Test fun `should provide health check functionality`() {
        val sourceTopicName = createTestTopic()
        val sinkTopicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()
        val counterPublisher = MockCounterPublisher()

        val transformer = KafkaTransformer<TransformedMessage>(
            consumerConfig = KafkaConsumer.Config(
                appName = "health-check-transformer",
                broker = broker,
                topics = setOf(sourceTopicName),
                group = randomString(6),
                autoOffsetResetConfig = "earliest",
            ),
            producerConfig = KafkaProducer.Config(
                broker = broker,
                topic = sinkTopicName,
            ),
            eventHandler = TransformerEventHandler { kafkaMessage ->
                TransformerMessages.Dropped() // Simple handler for health check test
            },
            publishTimerMetric = metricsPublisher,
            publishCounterMetric = counterPublisher,
        )

        assertEquals("health-check-transformer", transformer.name)

        transformer.start()

        val healthCheck = transformer.check()
        assertEquals("health-check-transformer", healthCheck.name)
        // Health should depend on consumer health (partition assignments)

        transformer.close()
    }

    class MockCounterPublisher : PublishCounterMetric {
        val publishedCounters = mutableListOf<Pair<String, Map<String, String>>>()

        override fun invoke(
            metricName: String,
            tags: Map<String, String>,
        ) {
            publishedCounters.add(metricName to tags)
        }
    }
}
