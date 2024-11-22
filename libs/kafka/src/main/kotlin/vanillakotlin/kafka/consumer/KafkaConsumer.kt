package vanillakotlin.kafka.consumer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer
import org.slf4j.LoggerFactory
import vanillakotlin.kafka.models.KafkaMessage
import vanillakotlin.kafka.models.Partition
import vanillakotlin.kafka.models.SequenceHandler
import vanillakotlin.kafka.models.TopicPartitionOffset
import vanillakotlin.models.HealthCheckResponse
import vanillakotlin.models.HealthMonitor
import java.lang.Thread.sleep
import java.time.Duration
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.system.exitProcess

private val log = LoggerFactory.getLogger(vanillakotlin.kafka.consumer.KafkaConsumer::class.java.name)

class KafkaConsumer(
    private val config: Config,
    private val eventHandler: SequenceHandler,
    private val uncaughtErrorHandler: ErrorHandler = runtimeErrorHandler(),
) : HealthMonitor {
    data class Config(
        val appName: String,
        val broker: String,
        val topics: Set<String>,
        val group: String,
        val pollTimeoutMs: Long = 1000L,
        val maxPollRecords: Int = 500,
        val autoOffsetResetConfig: String,
        val maxCommitErrors: Int = 10,
        val autoCommitOffsets: Boolean = true,
        val driverProperties: Map<String, String>? = emptyMap(),
        val partitions: Set<Partition> = emptySet(),
        val skipErrors: SkipErrorsConfig = SkipErrorsConfig(),
    )

    data class SkipErrorsConfig(
        val all: Boolean = false,
        val partitionOffsets: List<TopicPartitionOffset> = emptyList(),
    )

    private lateinit var _consumer: KafkaConsumer<String, ByteArray>
    private lateinit var kafkaMessage: KafkaMessage
    private var commitErrorCount = 0
    private var stopRequested = false
    private val stopCompleted = CompletableDeferred<Unit>()
    private val _assignment: MutableSet<TopicPartition> = mutableSetOf()

    val offsets = ConcurrentHashMap<TopicPartition, OffsetAndMetadata>()

    /**
     * non-mutable public references to internal values
     */
    val consumer: KafkaConsumer<String, ByteArray> get() = _consumer
    val assignment: Set<TopicPartition> get() = _assignment

    override val name = config.appName

    override fun check(): HealthCheckResponse = HealthCheckResponse(
        name = name,
        isHealthy = _assignment.isNotEmpty(),
        details = "kafka consumer connection to ${config.topics}",
    )
    val consumerThread = Executors.newSingleThreadExecutor()
    fun start() {
        consumerThread.execute { runConsumer() }
    }

    fun stop() = runBlocking {
        stopRequested = true
        stopCompleted.await()
    }

    fun commitOffsets() {
        val nextMessageOffsetsToCommit = synchronized(offsets) {
            offsets
                .toMap()
                .mapValues { (_, offset) ->
                    OffsetAndMetadata(offset.offset() + 1, offset.leaderEpoch(), offset.metadata())
                }
                .also { offsets.clear() }
        }
        _consumer.commitAsync(nextMessageOffsetsToCommit, offsetCommitErrorHandler)
    }

    private fun runConsumer() {
        // initialize the consumer based on the configuration
        val producerProperties = Properties().apply {
            put(ConsumerConfig.GROUP_ID_CONFIG, config.group)
            put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, config.broker)
            put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer().javaClass)
            put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer().javaClass)
            put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, config.maxPollRecords)
            put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false)
            put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, config.autoOffsetResetConfig)
            // otherwise allow full customization of the properties for things like cert-based SSL or other tuning
            config.driverProperties?.entries?.forEach {
                this[it.key] = it.value
            }
        }
        _consumer = KafkaConsumer(producerProperties)
        if (config.partitions.isNotEmpty()) {
            _consumer.assign(
                config.topics.flatMap { topic ->
                    config.partitions.map { TopicPartition(topic, it) }
                },
            )
        } else {
            _consumer.subscribe(config.topics, rebalanceListener)
        }
        _consumer.use { consumer ->
            while (true) {
                val records: ConsumerRecords<String, ByteArray> = consumer.poll(Duration.ofMillis(config.pollTimeoutMs))
                val batchCount = records.count()

                try {
                    eventHandler.processSequence(
                        records.asSequence().mapIndexed { index, it ->
                            KafkaMessage(
                                broker = config.broker,
                                topic = it.topic(),
                                key = it.key(),
                                partition = it.partition(),
                                offset = it.offset(),
                                headers = it.headers().associate { it.key() to it.value() },
                                timestamp = it.timestamp(),
                                body = it.value(),
                                endOfBatch = index + 1 == batchCount,
                            )
                        },
                    )
                } catch (throwable: Throwable) {
                    uncaughtErrorHandler(KafkaError(throwable, kafkaMessage, config.skipErrors))
                }

                consumer.takeIf { config.autoCommitOffsets && batchCount > 0 }?.commitAsync(offsetCommitErrorHandler)

                if (stopRequested) {
                    stopCompleted.complete(Unit)
                    return
                }
            }
        }
    }

    private val offsetCommitErrorHandler = { _: Map<TopicPartition, OffsetAndMetadata>, exception: Exception ->
        // if too many consecutive errors occur committing, shut down and let the container restart it
        if (exception != null) commitErrorCount++ else commitErrorCount = 0
        if (commitErrorCount > config.maxCommitErrors) {
            log.atError().setCause(exception).log("Too many consecutive errors occurred committing offsets; will shut down")
            // briefly wait to ensure the log message is flushed before halting. this can happen with Spring Boot usage.
            sleep(100)
            Runtime.getRuntime().halt(1)
        }

    }

    private val rebalanceListener =
        object : ConsumerRebalanceListener {
            override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
                log.atInfo().log("partitions assigned: ${partitions.map { it.partition() }.sorted()}")
                _assignment.addAll(partitions)
            }

            override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) {
                log.atInfo().log("partitions revoked: ${partitions.map { it.partition() }.sorted()}")
                _assignment.removeAll(partitions.toSet())
            }
        }
}

/**
 * Error class for handling kafka errors.  If batch processing and wanting to write your own error handling, the current batch and the index
 * that caused the error are included in the error.  This allows the processor to know which batch failed, which record failed, and
 * allow for special processing, if needed.  See below for considerations on this approach.
 */
data class KafkaError(
    val throwable: Throwable,
    val kafkaMessage: KafkaMessage?,
    val skipErrorsConfig: vanillakotlin.kafka.consumer.KafkaConsumer.SkipErrorsConfig,
)
typealias ErrorHandler = (KafkaError) -> Unit

/**
 * Function serving as the default error handler when things go wrong processing a message.
 * This logs then kills the runtime, with the expectation that the container will restart the process. Temporary hiccups like network
 * connections or broker failures would recover automatically. Any permanent errors like poison messages would cause an infinite error
 * loop, presumably resulting in an alert that the engineers then fix.
 * This technique works well for situations where you can stop processing and have some time to fix the problem, as it avoids out-of-order
 * processing issues.  It would not work for situations where you need to continue processing despite errors. For those situations, you
 * could pass in a custom error handler function that does what you want.
 * If a specific offset in a partition need to be skipped due to a bad message, the offset can be added to the skipPartitionOffsets list.
 */
fun runtimeErrorHandler(exitProcess: (Int) -> Unit = ::exitProcess): ErrorHandler = { kafkaError ->
    with(kafkaError) {
        kafkaMessage?.let { message ->
            val partitionOffset = TopicPartitionOffset(
                topic = message.topic,
                partition = message.partition,
                offset = message.offset,
            )
            if (skipErrorsConfig.all || skipErrorsConfig.partitionOffsets.contains(partitionOffset)) {
                log.atWarn().log("Skipping message with offset ${message.offset} for partition ${message.partition}.  Message: $message")
            } else {
                // briefly wait to ensure the log message is flushed before halting. this can happen with Spring Boot usage.
                log.atError().setCause(throwable).log("Fatal error encountered during message processing. Exiting process. Message: $kafkaMessage")
                sleep(100)
                exitProcess(1)
            }
        } ?: exitProcess(1)
    }
}
