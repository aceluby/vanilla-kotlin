package vanillakotlin.kafka.producer

import org.apache.kafka.clients.producer.Callback
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.header.Headers
import org.apache.kafka.common.header.internals.RecordHeaders
import org.apache.kafka.common.serialization.ByteArraySerializer
import org.apache.kafka.common.serialization.StringSerializer
import org.slf4j.LoggerFactory
import vanillakotlin.kafka.KAFKA_SEND_METRIC
import vanillakotlin.kafka.PARTITION_TAG
import vanillakotlin.kafka.SUCCESS_TAG
import vanillakotlin.kafka.TOPIC_TAG
import vanillakotlin.kafka.models.KafkaOutputMessage
import vanillakotlin.kafka.provenance.PROVENANCES_HEADER_NAME
import vanillakotlin.kafka.provenance.SPAN_ID_HEADER_NAME
import vanillakotlin.kafka.provenance.generateSpanId
import vanillakotlin.metrics.PublishTimerMetric
import vanillakotlin.serde.mapper
import java.util.Properties
import java.util.concurrent.CompletableFuture
import kotlin.time.Duration.Companion.milliseconds

// code using this class should inject either a KafkaSend or KafkaSendAsync function into their classes using it.
// then unit tests can inject a mocked function, and integration/real classes can inject the actual function
typealias KafkaSend<V> = (kafkaMessage: KafkaOutputMessage<V>) -> RecordMetadata
typealias KafkaSendAsync<V> = (kafkaMessage: KafkaOutputMessage<V>) -> CompletableFuture<RecordMetadata>

const val AGENT_HEADER_NAME = "agent"

class KafkaProducer<V>(
    private val config: Config,
    private val publishTimerMetric: PublishTimerMetric,
    private val partitionFor: (String?, Int) -> Int? = PartitionCalculator.Companion::partitionFor,
    private val agentName: String? = null,
) : AutoCloseable {
    private val log = LoggerFactory.getLogger(javaClass)

    data class Config(
        val broker: String,
        val topic: String,
        val enableStandardMetrics: Boolean = false,
        val driverProperties: Map<String, String>? = emptyMap(),
    )

    private val topic = config.topic

    private lateinit var producer: org.apache.kafka.clients.producer.KafkaProducer<String, ByteArray>

    val partitionCount: Int by lazy { producer.partitionsFor(config.topic).size }

    private val producerProperties = Properties().apply {
        put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.broker)
        put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
        put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java.name)
        put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000L)
        // see https://www.naleid.com/2021/05/02/kafka-topic-partitioning-replication.html for a detailed description of these settings
        put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4")
        put(ProducerConfig.ACKS_CONFIG, "all")
        put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 1024 * 1024 * 4)
        put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1)
        put(ProducerConfig.BATCH_SIZE_CONFIG, 1024 * 851)
        put(ProducerConfig.BUFFER_MEMORY_CONFIG, 1024 * 1024 * 64)
        put(ProducerConfig.LINGER_MS_CONFIG, 100)
        // otherwise allow full customization of the properties for things like cert-based SSL or other tuning
        config.driverProperties?.entries?.forEach {
            this[it.key] = it.value
        }
    }

    fun start(): KafkaProducer<V> {
        check(!this::producer.isInitialized) { "producer is already started" }
        producer = org.apache.kafka.clients.producer.KafkaProducer(producerProperties)

        log.atInfo().log("started kafka producer")

        return this
    }

    // synchronous version of the send function. will block until it receives a response from the broker
    // only use this for lower-volume and throughput scenarios
    fun send(kafkaMessage: KafkaOutputMessage<V>): RecordMetadata = sendAsync(kafkaMessage).get()

    fun sendAsync(kafkaMessage: KafkaOutputMessage<V>): CompletableFuture<RecordMetadata> = with(kafkaMessage) {
        val calculatedPartition = partition ?: partitionKey?.let { partitionFor(it, partitionCount) }
        val serializedValue = when (value) {
            is ByteArray -> value
            null -> null
            else -> mapper.writeValueAsBytes(value)
        }
        internalSendAsync(
            ProducerRecord(topic, calculatedPartition, key, serializedValue, toRecordHeaders()),
        )
    }

    private fun internalSendAsync(record: ProducerRecord<String, ByteArray?>): CompletableFuture<RecordMetadata> {
        val callback = CompletableCallback(
            metricsTags = mapOf(TOPIC_TAG to record.topic()).let {
                when (val partition = record.partition()?.toString()) {
                    null -> it
                    else -> it + (PARTITION_TAG to partition)
                }
            },
        )

        producer.send(record, callback)
        return callback.completableFuture
    }

    private fun KafkaOutputMessage<V>.toRecordHeaders(): Headers = RecordHeaders().apply {
        headers.forEach { (key, value) -> add(key, value) }
        if (!headers.containsKey(SPAN_ID_HEADER_NAME)) {
            add(SPAN_ID_HEADER_NAME, generateSpanId().toByteArray())
        }
        if (!headers.containsKey(PROVENANCES_HEADER_NAME) && provenances.isNotEmpty()) {
            add(PROVENANCES_HEADER_NAME, mapper.writeValueAsBytes(provenances))
        }
        if (!headers.containsKey(AGENT_HEADER_NAME) && agentName != null) {
            add(AGENT_HEADER_NAME, agentName.toByteArray())
        }
    }

    inner class CompletableCallback(private val metricsTags: Map<String, String>) : Callback {
        val completableFuture = CompletableFuture<RecordMetadata>()
        private val instantiatedAt = System.currentTimeMillis()

        override fun onCompletion(
            metadata: RecordMetadata?,
            exception: java.lang.Exception?,
        ) {
            val success = exception == null
            publishTimerMetric(
                KAFKA_SEND_METRIC,
                metricsTags + mapOf(SUCCESS_TAG to success.toString()),
                (System.currentTimeMillis() - instantiatedAt).milliseconds,
            )

            if (success) {
                completableFuture.complete(metadata)
            } else {
                completableFuture.completeExceptionally(exception)
            }
        }
    }

    override fun close() {
        producer.close()
    }
}
