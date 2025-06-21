package vanillakotlin.kafka

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import vanillakotlin.kafka.models.KafkaMessage
import vanillakotlin.metrics.PublishTimerMetric
import vanillakotlin.random.randomString
import java.time.Duration
import java.util.Properties
import java.util.concurrent.TimeUnit

/**
 * Test utility to collect messages from a Kafka topic for testing purposes.
 * This function polls the topic for a specified duration and returns all messages found.
 */
fun collectMessages(
    broker: String,
    topic: String,
    metricsPublisher: PublishTimerMetric? = null,
    filter: (KafkaMessage) -> Boolean = { true },
    stopWhen: (MutableList<KafkaMessage>) -> Boolean = { false },
    timeoutMs: Long = 10000L,
    pollTimeoutMs: Long = 1000L,
): List<KafkaMessage> {
    val messages = mutableListOf<KafkaMessage>()
    val consumerGroup = "test-consumer-${randomString()}"

    val consumerProperties = Properties().apply {
        put(ConsumerConfig.GROUP_ID_CONFIG, consumerGroup)
        put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker)
        put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer::class.java.name)
        put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer::class.java.name)
        put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")
        put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
        put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 10000)
        put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 3000)
        put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 30000)
        put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500)
    }

    var consecutiveEmptyPolls = 0
    val maxEmptyPolls = 5 // Stop after 5 consecutive empty polls to prevent infinite loops

    KafkaConsumer<String, ByteArray>(consumerProperties).use { consumer ->
        try {
            consumer.subscribe(listOf(topic))

            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs && consecutiveEmptyPolls < maxEmptyPolls) {
                val records = consumer.poll(Duration.ofMillis(pollTimeoutMs))

                if (records.isEmpty) {
                    consecutiveEmptyPolls++
                } else {
                    consecutiveEmptyPolls = 0
                    records.forEach { record ->
                        val kafkaMessage = KafkaMessage(
                            broker = broker,
                            topic = record.topic(),
                            key = record.key(),
                            partition = record.partition(),
                            offset = record.offset(),
                            headers = record.headers().associate { it.key() to it.value() },
                            timestamp = record.timestamp(),
                            body = record.value(),
                            endOfBatch = false,
                        )

                        if (filter(kafkaMessage)) {
                            messages.add(kafkaMessage)
                        }
                    }
                }

                if (stopWhen(messages)) {
                    break
                }
            }
        } catch (e: Exception) {
            // Log the exception but don't let it break the test
            println("Warning: Error collecting messages from topic $topic: ${e.message}")
        }
    }

    return messages
}

/**
 * Test utility to create a Kafka topic for testing
 */
fun AdminClient.createTestTopic(
    topicName: String,
    partitions: Int = 1,
    replicationFactor: Short = 1,
) {
    createTopics(listOf(NewTopic(topicName, partitions, replicationFactor)))
        .all()
        .get(10, TimeUnit.SECONDS)
}

/**
 * Test utility to delete a Kafka topic for testing
 */
fun AdminClient.deleteTestTopic(topicName: String) {
    deleteTopics(listOf(topicName))
        .all()
        .get(10, TimeUnit.SECONDS)
}

/**
 * Mock metrics publisher for testing
 */
class MockMetricsPublisher : PublishTimerMetric {
    val publishedMetrics = mutableListOf<Pair<String, Map<String, String>>>()

    override fun invoke(
        metricName: String,
        tags: Map<String, String>,
        duration: kotlin.time.Duration,
    ) {
        publishedMetrics.add(metricName to tags)
    }
}
