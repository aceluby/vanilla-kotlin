# Kafka Guide

This document explains the Kafka infrastructure used in the vanilla Kotlin project, covering the consumer wrapper that
handles Kafka internals, the producer wrapper with production-ready configurations, and the transformer that uses
coroutines and the actor pattern to create high-performance streaming pipelines.

## Overview

The project provides three main Kafka components:

- **KafkaConsumer**: Wrapper that abstracts Kafka polling, partition management, and error handling
- **KafkaProducer**: Production-ready producer with optimized configurations based
  on [Ted Naleid's recommendations](https://www.naleid.com/2021/05/02/kafka-topic-partitioning-replication.html)
- **KafkaTransformer**: Actor-pattern implementation for `consumer → business logic → producer` pipelines

## Consumer Wrapper

The `KafkaConsumer` wrapper handles all the complex Kafka internals, providing a simple interface for message
processing.

### Core Features

**1. Simplified Configuration**

```kotlin
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
```

**2. Thread-Safe Operation**

```kotlin
// Single-threaded context ensures thread safety
private val consumerContext = newSingleThreadContext("kafka-consumer-${config.appName}")
private val consumerScope = CoroutineScope(consumerContext)
```

**3. Automatic Health Monitoring**

```kotlin
override fun check(): HealthCheckResponse = HealthCheckResponse(
    name = name,
    isHealthy = _assignment.isNotEmpty(), // Healthy when partitions assigned
    details = "kafka consumer connection to ${config.topics}",
)
```

### Usage Pattern

```kotlin
val consumer = KafkaConsumer(
    config = KafkaConsumer.Config(
        appName = "my-service",
        broker = "localhost:9092",
        topics = setOf("user-events"),
        group = "user-processor-group",
        autoOffsetResetConfig = "earliest",
    ),
    eventHandler = KafkaConsumerSequenceHandler { messages ->
        messages.forEach { message ->
            processUserEvent(message)
        }
    },
)

consumer.start()
```

### Error Handling Strategy

The consumer provides sophisticated error handling with configurable skip patterns:

```kotlin
data class SkipErrorsConfig(
    val all: Boolean = false, // Skip all errors (for testing)
    val partitionOffsets: List<TopicPartitionOffset> = emptyList(), // Skip specific offsets
)

// Default error handler: log and exit (container restart)
fun runtimeErrorHandler(): ErrorHandler = { kafkaError ->
    if (shouldSkip(kafkaError)) {
        log.warn("Skipping message: ${kafkaError.kafkaMessage}")
    } else {
        log.error("Fatal error, exiting", kafkaError.throwable)
        exitProcess(1) // Let container orchestrator restart
    }
}
```

**Benefits of Exit Strategy:**

- Prevents out-of-order processing
- Automatic recovery from transient issues
- Clear failure signals for monitoring
- Avoids poison message infinite loops
- Errors that cannot be corrected through restarts to be alerted on from tools like Grafana

### Partition Management

**Automatic Assignment:**

```kotlin
// Subscribe to topics with automatic partition assignment
consumer.subscribe(config.topics, rebalanceListener)
```

**Manual Assignment:**

```kotlin
// Assign specific partitions for precise control
if (config.partitions.isNotEmpty()) {
    consumer.assign(
        config.topics.flatMap { topic ->
            config.partitions.map { TopicPartition(topic, it) }
        }
    )
}
```

**Rebalance Handling:**

```kotlin
private val rebalanceListener = object : ConsumerRebalanceListener {
    override fun onPartitionsAssigned(partitions: Collection<TopicPartition>) {
        log.info("Partitions assigned: ${partitions.map { it.partition() }.sorted()}")
        _assignment.addAll(partitions)
    }

    override fun onPartitionsRevoked(partitions: Collection<TopicPartition>) {
        log.info("Partitions revoked: ${partitions.map { it.partition() }.sorted()}")
        _assignment.removeAll(partitions.toSet())
    }
}
```

## Producer Wrapper

The `KafkaProducer` implements production-ready configurations based
on [Ted Naleid's Kafka recommendations](https://www.naleid.com/2021/05/02/kafka-topic-partitioning-replication.html).

### Production-Ready Configuration

```kotlin
private val producerProperties = Properties().apply {
    put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.broker)
    put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer::class.java.name)
    put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer::class.java.name)

    // Ted Naleid's recommendations for production reliability
    put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4")           // Efficient compression
    put(ProducerConfig.ACKS_CONFIG, "all")                      // Wait for all replicas
    put(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, 1024 * 1024 * 4) // 4MB max request
    put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 1) // Preserve order
    put(ProducerConfig.BATCH_SIZE_CONFIG, 1024 * 851)           // ~95% of message.max.bytes
    put(ProducerConfig.BUFFER_MEMORY_CONFIG, 1024 * 1024 * 64)  // 64MB buffer
    put(ProducerConfig.LINGER_MS_CONFIG, 100)                   // Batch for 100ms
    put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 1000L)          // Retry backoff
}
```

### Configuration Rationale

Based on [Ted Naleid's analysis](https://www.naleid.com/2021/05/02/kafka-topic-partitioning-replication.html):

**1. Compression (`lz4`)**

- Better performance than `gzip`
- Good compression ratio vs CPU trade-off
- Works well with batching

**2. Acknowledgments (`all`)**

- Waits for leader + all in-sync replicas
- Prevents data loss if leader fails
- Works with `min.insync.replicas=2` broker setting

**3. Ordering Guarantees (`max.in.flight.requests.per.connection=1`)**

- Ensures messages arrive in order
- Prevents race conditions during retries
- Critical for event sourcing and state updates

**4. Batching Optimization**

- `batch.size=972800` (~95% of typical `message.max.bytes`)
- `linger.ms=100` allows batching without excessive delay
- `buffer.memory=67108864` (64MB) for high-throughput apps

### Usage Patterns

**Synchronous Send (Low Volume):**

```kotlin
val producer = KafkaProducer<String>(
    config = KafkaProducer.Config(
        broker = "localhost:9092",
        topic = "user-events"
    ),
    publishTimerMetric = metricsPublisher
).start()

val metadata = producer.send(
    KafkaOutputMessage(
        key = "user-123",
        value = "user-created",
        headers = mapOf("version" to "1.0".toByteArray())
    )
)
```

**Asynchronous Send (High Volume):**

```kotlin
val future = producer.sendAsync(
    KafkaOutputMessage(
        key = "user-123",
        value = userEvent,
        provenances = mutableListOf(currentProvenance)
    )
)

future.thenAccept { metadata ->
    log.info("Message sent to partition ${metadata.partition()} at offset ${metadata.offset()}")
}
```

### Automatic Features

**1. Serialization Handling**

```kotlin
// Automatic JSON serialization for non-ByteArray values
val serializedValue = when (value) {
    is ByteArray -> value
    null -> null
    else -> mapper.writeValueAsBytes(value) // Jackson serialization
}
```

**2. Provenance Tracking**

**Provenance** is the chronology of ownership, custody, or location of data as it moves through a system. In the context
of Kafka messaging, provenance tracking creates an audit trail that records where data came from, how it was
transformed, and where it's going. This enables debugging, compliance, and understanding data lineage in complex
distributed systems.

```kotlin
private fun KafkaOutputMessage<V>.toRecordHeaders(): Headers = RecordHeaders().apply {
    // Add custom headers
    headers.forEach { (key, value) -> add(key, value) }

    // Auto-generate span ID for tracing
    if (!headers.containsKey(SPAN_ID_HEADER_NAME)) {
        add(SPAN_ID_HEADER_NAME, generateSpanId().toByteArray())
    }

    // Add provenance chain
    if (!headers.containsKey(PROVENANCES_HEADER_NAME) && provenances.isNotEmpty()) {
        add(PROVENANCES_HEADER_NAME, mapper.writeValueAsBytes(provenances))
    }

    // Add agent identification
    if (!headers.containsKey(AGENT_HEADER_NAME) && agentName != null) {
        add(AGENT_HEADER_NAME, agentName.toByteArray())
    }
}
```

**3. Metrics Integration**

```kotlin
inner class CompletableCallback(private val metricsTags: Map<String, String>) : Callback {
    override fun onCompletion(metadata: RecordMetadata?, exception: Exception?) {
        val success = exception == null
        publishTimerMetric(
            KAFKA_SEND_METRIC,
            metricsTags + mapOf(SUCCESS_TAG to success.toString()),
            (System.currentTimeMillis() - instantiatedAt).milliseconds,
        )
        // Complete future...
    }
}
```

## Transformer: Actor Pattern Pipeline

The `KafkaTransformer` is the crown jewel of the Kafka infrastructure, implementing an actor pattern that creates a
continuous pipeline for `consumer → business logic → producer` workflows.

### Architecture Overview

The transformer uses coroutines and channels to create separate actors for each stage:

```
┌─────────────┐    ┌──────────────┐    ┌────────────────┐    ┌─────────────┐
│   Consumer  │───▶│ Worker Actors │───▶│ Kafka Publisher │───▶│ Ack Handler │
│   (Polling) │    │ (Transform)   │    │   (Produce)     │    │  (Commit)   │
└─────────────┘    └──────────────┘    └────────────────┘    └─────────────┘
```

### Key Benefits

**1. No Delays**: Each stage works independently without blocking others
**2. Ordering Preservation**: Messages are processed and committed in order
**3. Backpressure Handling**: Channels provide natural flow control
**4. Scalable Workers**: Multiple transformation workers based on message keys
**5. Fault Tolerance**: Isolated error handling per stage

### Core Implementation

**Worker Actor Pattern:**

```kotlin
private fun CoroutineScope.launchEventWorker(
    workerChannel: Channel<Pair<KafkaMessage, CompletableDeferred<TransformResult<OUT>>>>,
    workerId: Int,
) = launch(Dispatchers.IO) {
    for ((kafkaMessage, completion) in workerChannel) {
        publishTimerMetric.time(
            metricName = KAFKA_PROCESS_TIMER_METRIC,
            tags = mapOf(
                APP_TAG to consumerConfig.appName,
                TOPIC_TAG to kafkaMessage.topic,
                WORKER_TAG to workerId.toString(),
            ),
        ) {
            try {
                val transformResult = eventHandler.transform(kafkaMessage)
                completion.complete(
                    TransformResult(
                        topicPartitionOffset = TopicPartitionOffset(
                            kafkaMessage.topic,
                            kafkaMessage.partition,
                            kafkaMessage.offset,
                        ),
                        outboundMessage = transformResult,
                    )
                )
            } catch (throwable: Throwable) {
                completion.completeExceptionally(throwable)
            }
        }
    }
}
```

**Publisher Actor:**

```kotlin
private fun CoroutineScope.launchKafkaPublisher(
    kafkaPublisherChannel: ReceiveChannel<CompletableDeferred<TransformResult<OUT>>>,
    kafkaAckChannel: Channel<EventResult<OUT>>,
) = launch(Dispatchers.IO) {
    for (deferred in kafkaPublisherChannel) {
        val (topicPartitionOffset, transformerMessage) = deferred.await()
        val transformResult = when (transformerMessage) {
            is TransformerMessages.Dropped -> {
                // Handle dropped messages
                listOf(
                    transformerMessage.messages.first().addStatusTag(DROPPED_TAG) to
                        CompletableFuture.completedFuture(null)
                )
            }
            else -> {
                // Send to Kafka
                transformerMessage.messages.map {
                    it.addStatusTag(FORWARDED_TAG) to sinkProducer.sendAsync(it.kafkaOutputMessage)
                }
            }
        }
        kafkaAckChannel.send(EventResult(topicPartitionOffset, transformResult))
    }
}
```

**Acknowledgment Actor:**

```kotlin
private fun CoroutineScope.launchKafkaAckJob(kafkaAckChannel: Channel<EventResult<OUT>>) =
    launch(Dispatchers.IO) {
        for ((topicPartitionOffset, transformerResult) in kafkaAckChannel) {
            // Wait for all Kafka sends to complete
            transformerResult.forEach { (message, future) ->
                future.join() // Wait for completion
                publishCounterMetric(TRANSFORMER_EVENT, message.metricTags)
            }
            // Save offset for later commit
            sourceConsumer.offsets[topicPartitionOffset.getTopicPartition()] =
                topicPartitionOffset.getOffsetAndMetadata()
        }
    }
```

### Worker Distribution

The transformer intelligently distributes work across multiple workers:

```kotlin
class DefaultWorkerSelector(private val numberOfWorkers: Int) : WorkerSelector {
    override fun selectWorker(key: String?): Int =
        key?.hashCode()?.absoluteValue?.rem(numberOfWorkers) ?: 0
}

// Messages with the same key always go to the same worker
// This preserves ordering for related events
val workerId = workerSelector.selectWorker(kafkaMessage.key)
workersChannels[workerId].send(kafkaMessage to deferred)
```

### Transformation Interface

Simple interface for business logic:

```kotlin
fun interface TransformerEventHandler<V> {
    fun transform(kafkaMessage: KafkaMessage): TransformerMessages<V>
}

// Transformation results
sealed class TransformerMessages<V>(val messages: List<TransformerMessage<V>>) {
    // Single output message
    class Single<V>(kafkaOutputMessage: KafkaOutputMessage<V>) : TransformerMessages<V>(...)

    // Multiple output messages (fan-out)
    class Multiple<V>(messages: List<TransformerMessage<V>>) : TransformerMessages<V>(messages)

    // Drop message (no output)
    class Dropped<V>(metricTags: Map<String, String> = emptyMap()) : TransformerMessages<V>(...)
}
```

### Usage Example

```kotlin
val transformer = KafkaTransformer<UserEvent>(
    consumerConfig = KafkaConsumer.Config(
        appName = "user-transformer",
        broker = "localhost:9092",
        topics = setOf("raw-events"),
        group = "user-processor",
        autoOffsetResetConfig = "earliest"
    ),
    producerConfig = KafkaProducer.Config(
        broker = "localhost:9092",
        topic = "processed-events"
    ),
    eventHandler = TransformerEventHandler { message ->
        val rawEvent = parseRawEvent(message.body)
        when {
            rawEvent.isValid() -> {
                val userEvent = enrichUserEvent(rawEvent)
                TransformerMessages.Single(
                    KafkaOutputMessage(
                        key = userEvent.userId,
                        value = userEvent,
                        provenances = mutableListOf(message.buildProvenance())
                    )
                )
            }
            rawEvent.shouldDrop() -> TransformerMessages.Dropped()
            else -> {
                // Fan-out: create multiple events
                val events = splitEvent(rawEvent)
                TransformerMessages.Multiple(
                    events.map { TransformerMessage(KafkaOutputMessage(it.key, it)) }
                )
            }
        }
    },
    publishTimerMetric = metricsPublisher,
    publishCounterMetric = metricsPublisher,
    numberOfWorkers = 8 // Scale based on CPU cores
)

transformer.start()
```

### Performance Characteristics

**1. Continuous Pipeline**

- No blocking between stages
- Consumer polling doesn't wait for transformation
- Producer sending doesn't wait for commits

**2. Backpressure Management**

```kotlin
companion object {
    private const val ACK_CHANNEL_SIZE = 10_000      // Large buffer for acks
    private const val WORKER_CHANNEL_SIZE = 500      // Smaller worker buffers
    private const val COMMITTER_PERIOD_MS = 2000L    // Commit every 2 seconds
}
```

**3. Ordered Processing**

- Messages processed in parallel by workers
- Publishing and acknowledgment maintain order
- Commits happen in sequence

**4. Graceful Shutdown**

```kotlin
override fun close() = runBlocking {
    log.info("Shutting down transformer")

    // 1. Pause consumer
    sourceConsumer.togglePause()

    // 2. Drain worker channels
    workersChannels.forEach { it.close() }
    eventWorkers.forEach { it.join() }

    // 3. Finish publishing
    kafkaPublisherChannel.close()
    kafkaPublishJob.join()

    // 4. Complete acknowledgments
    kafkaAckChannel.close()
    kafkaAckJob.join()

    // 5. Final commit with timeout
    try {
        withTimeout(5000) {
            commitJob.join()
        }
    } catch (e: TimeoutCancellationException) {
        log.warn("Committer timeout, canceling")
        commitJob.cancel()
    }

    // 6. Close resources
    sourceConsumer.stop()
    sinkProducer.close()
}
```

## Production Deployment Considerations

### Broker Configuration

Based on [Ted Naleid's recommendations](https://www.naleid.com/2021/05/02/kafka-topic-partitioning-replication.html):

```properties
# Broker/Topic Configs
min.insync.replicas=2              # Require 2 replicas for writes
compression.type=producer          # Let producer handle compression
```

**Topic Creation:**

```bash
kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic user-events \
  --partitions 12 \
  --replication-factor 3 \
  --config min.insync.replicas=2
```

### Consumer Group Management

**Partition Assignment Strategy:**

```kotlin
// For ordered processing within a key
val config = KafkaConsumer.Config(
    topics = setOf("user-events"),
    group = "user-processor",
    // Partitions assigned based on consumer group size
)

// For specific partition assignment
val config = KafkaConsumer.Config(
    topics = setOf("user-events"),
    partitions = setOf(0, 1, 2), // Specific partitions
    // No group - manual assignment
)
```

### Monitoring and Metrics

**Built-in Metrics:**

```kotlin
// Consumer metrics
publishCounterMetric("kafka.consumer.messages", tags)
publishTimerMetric("kafka.consumer.processing_time", tags, duration)

// Producer metrics  
publishTimerMetric("kafka.producer.send_time", tags, duration)
publishCounterMetric("kafka.producer.messages", tags + ("success" to success.toString()))

// Transformer metrics
publishTimerMetric("kafka.transformer.process_time", tags, duration)
publishCounterMetric("kafka.transformer.events", tags + ("status" to status))
```

**Health Checks:**

```kotlin
// All Kafka components implement HealthMonitor
val healthChecks = listOf(
    kafkaConsumer,
    kafkaTransformer,
    // Other components...
)

// Automatic health endpoint
get("/health") {
    val responses = healthCheckAll(healthChecks)
    val isHealthy = responses.all { it.isHealthy }
    call.respond(
        if (isHealthy) HttpStatusCode.OK else HttpStatusCode.InternalServerError,
        responses
    )
}
```

### Error Handling Strategies

**1. Poison Message Handling**

```kotlin
// Skip specific problematic offsets
val skipConfig = SkipErrorsConfig(
    partitionOffsets = listOf(
        TopicPartitionOffset("user-events", 2, 12345)
    )
)
```

**2. Dead Letter Queue Pattern**

```kotlin
val transformer = KafkaTransformer(
    // ... config
    uncaughtErrorHandler = { error ->
        // Send to DLQ instead of crashing
        deadLetterProducer.send(
            KafkaOutputMessage(
                key = error.kafkaMessage?.key,
                value = error.kafkaMessage,
                headers = mapOf("error" to error.throwable.message?.toByteArray())
            )
        )
    }
)
```

**3. Circuit Breaker Pattern**

```kotlin
class CircuitBreakerErrorHandler(
    private val maxErrors: Int = 10,
    private val timeWindowMs: Long = 60000
) : ErrorHandler {
    private val errorCount = AtomicInteger(0)
    private val windowStart = AtomicLong(System.currentTimeMillis())

    override fun invoke(error: KafkaError) {
        val now = System.currentTimeMillis()
        if (now - windowStart.get() > timeWindowMs) {
            windowStart.set(now)
            errorCount.set(0)
        }

        if (errorCount.incrementAndGet() > maxErrors) {
            log.error("Circuit breaker opened, too many errors")
            exitProcess(1)
        }
    }
}
```

## Testing Strategies

### Unit Testing

**Consumer Testing:**

```kotlin
@Test fun `should process messages correctly`() {
    val processedMessages = mutableListOf<KafkaMessage>()
    val consumer = KafkaConsumer(
        config = testConfig,
        eventHandler = KafkaConsumerSequenceHandler { messages ->
            processedMessages.addAll(messages)
        }
    )

    // Test with test containers or embedded Kafka
}
```

**Producer Testing:**

```kotlin
@Test fun `should send message with correct configuration`() {
    val producer = KafkaProducer<String>(
        config = testConfig,
        publishTimerMetric = mockMetrics
    ).start()

    val future = producer.sendAsync(
        KafkaOutputMessage(key = "test", value = "message")
    )

    val metadata = future.get(5, TimeUnit.SECONDS)
    assertEquals("test-topic", metadata.topic())
}
```

**Transformer Testing:**

```kotlin
@Test fun `should transform and forward messages`() {
    val transformer = KafkaTransformer(
        consumerConfig = testConsumerConfig,
        producerConfig = testProducerConfig,
        eventHandler = TransformerEventHandler { message ->
            TransformerMessages.Single(
                KafkaOutputMessage(
                    key = message.key,
                    value = transformMessage(message.body)
                )
            )
        },
        publishTimerMetric = mockMetrics,
        publishCounterMetric = mockMetrics
    )

    // Use collectMessages utility to verify output
    val outputMessages = collectMessages(
        broker = testBroker,
        topic = "output-topic",
        timeoutMs = 10000L
    )

    assertEquals(expectedCount, outputMessages.size)
}
```

### Integration Testing

The project includes `KafkaTestUtilities` for integration testing:

```kotlin
fun collectMessages(
    broker: String,
    topic: String,
    filter: (KafkaMessage) -> Boolean = { true },
    stopWhen: (MutableList<KafkaMessage>) -> Boolean = { false },
    timeoutMs: Long = 10000L,
): List<KafkaMessage>
```

**Example Integration Test:**

```kotlin
@Test fun `should process messages end-to-end`() {
    val inputTopic = "input-${randomString()}"
    val outputTopic = "output-${randomString()}"

    // Create test topics
    adminClient.createTestTopic(inputTopic)
    adminClient.createTestTopic(outputTopic)

    // Start transformer
    val transformer = KafkaTransformer<ProcessedEvent>(
        consumerConfig = KafkaConsumer.Config(
            appName = "test-transformer",
            broker = testBroker,
            topics = setOf(inputTopic),
            group = "test-group",
            autoOffsetResetConfig = "earliest"
        ),
        producerConfig = KafkaProducer.Config(
            broker = testBroker,
            topic = outputTopic
        ),
        eventHandler = TransformerEventHandler { message ->
            val event = parseEvent(message.body)
            TransformerMessages.Single(
                KafkaOutputMessage(
                    key = event.id,
                    value = ProcessedEvent(event.id, event.data.uppercase())
                )
            )
        },
        publishTimerMetric = mockMetrics,
        publishCounterMetric = mockMetrics
    )

    transformer.start()

    // Send test messages
    val inputProducer = KafkaProducer<RawEvent>(
        config = KafkaProducer.Config(broker = testBroker, topic = inputTopic),
        publishTimerMetric = mockMetrics
    ).start()

    val testEvents = listOf(
        RawEvent("1", "hello"),
        RawEvent("2", "world")
    )

    testEvents.forEach { event ->
        inputProducer.send(KafkaOutputMessage(key = event.id, value = event))
    }

    // Collect output messages
    val outputMessages = collectMessages(
        broker = testBroker,
        topic = outputTopic,
        stopWhen = { it.size >= testEvents.size },
        timeoutMs = 15000L
    )

    // Verify transformation
    assertEquals(2, outputMessages.size)
    val processedEvents = outputMessages.map {
        mapper.readValue(it.body, ProcessedEvent::class.java)
    }

    assertEquals("HELLO", processedEvents.find { it.id == "1" }?.data)
    assertEquals("WORLD", processedEvents.find { it.id == "2" }?.data)

    // Cleanup
    transformer.close()
    inputProducer.close()
}
```

## Real-World Application Examples

### 1. Event Sourcing System

```kotlin
// Consumer for processing domain events
val eventConsumer = KafkaConsumer(
    config = KafkaConsumer.Config(
        appName = "event-processor",
        broker = kafkaBroker,
        topics = setOf("domain-events"),
        group = "event-sourcing-group",
        autoOffsetResetConfig = "earliest"
    ),
    eventHandler = KafkaConsumerSequenceHandler { messages ->
        messages.forEach { message ->
            val event = parseEvent(message.body)
            eventStore.append(event)
            updateProjections(event)
        }
    }
)
```

### 2. Data Pipeline with Transformation

```kotlin
// Transform raw logs to structured events
val logTransformer = KafkaTransformer<StructuredLog>(
    consumerConfig = KafkaConsumer.Config(
        appName = "log-transformer",
        broker = kafkaBroker,
        topics = setOf("raw-logs"),
        group = "log-processing",
        autoOffsetResetConfig = "latest"
    ),
    producerConfig = KafkaProducer.Config(
        broker = kafkaBroker,
        topic = "structured-logs"
    ),
    eventHandler = TransformerEventHandler { message ->
        try {
            val rawLog = String(message.body ?: ByteArray(0))
            val structured = parseLogEntry(rawLog)

            when {
                structured.isError() -> {
                    // Fan-out: send to both structured logs and alerts
                    TransformerMessages.Multiple(
                        listOf(
                            TransformerMessage(
                                KafkaOutputMessage(
                                    key = structured.requestId,
                                    value = structured,
                                    partition = 0 // Structured logs partition
                                )
                            ),
                            TransformerMessage(
                                KafkaOutputMessage(
                                    key = structured.requestId,
                                    value = AlertEvent(structured),
                                    partition = 1 // Alerts partition
                                )
                            )
                        )
                    )
                }
                structured.isValid() -> {
                    TransformerMessages.Single(
                        KafkaOutputMessage(
                            key = structured.requestId,
                            value = structured
                        )
                    )
                }
                else -> TransformerMessages.Dropped()
            }
        } catch (e: Exception) {
            log.warn("Failed to parse log entry", e)
            TransformerMessages.Dropped(mapOf("error" to "parse_failure"))
        }
    },
    publishTimerMetric = metricsPublisher,
    publishCounterMetric = metricsPublisher,
    numberOfWorkers = 12 // High throughput
)
```

### 3. Outbox Pattern Implementation

```kotlin
// Process outbox events and publish to Kafka
val outboxProcessor = KafkaTransformer<DomainEvent>(
    consumerConfig = KafkaConsumer.Config(
        appName = "outbox-processor",
        broker = kafkaBroker,
        topics = setOf("outbox-events"),
        group = "outbox-group",
        autoOffsetResetConfig = "earliest"
    ),
    producerConfig = KafkaProducer.Config(
        broker = kafkaBroker,
        topic = "domain-events"
    ),
    eventHandler = TransformerEventHandler { message ->
        val outboxEvent = parseOutboxEvent(message.body)

        // Mark as processed in database
        outboxRepository.markProcessed(outboxEvent.id)

        // Forward to domain events topic
        TransformerMessages.Single(
            KafkaOutputMessage(
                key = outboxEvent.aggregateId,
                value = outboxEvent.domainEvent,
                provenances = mutableListOf(message.buildProvenance())
            )
        )
    },
    publishTimerMetric = metricsPublisher,
    publishCounterMetric = metricsPublisher
)
```

## Performance Tuning

### Consumer Tuning

```kotlin
val highThroughputConfig = KafkaConsumer.Config(
    appName = "high-throughput-consumer",
    broker = kafkaBroker,
    topics = setOf("high-volume-topic"),
    group = "processing-group",
    pollTimeoutMs = 100L,        // Shorter polling for responsiveness
    maxPollRecords = 1000,       // Larger batches
    autoOffsetResetConfig = "latest",
    driverProperties = mapOf(
        "fetch.min.bytes" to "1024",           // Wait for at least 1KB
        "fetch.max.wait.ms" to "100",          // Max wait 100ms
        "max.partition.fetch.bytes" to "1048576", // 1MB per partition
        "session.timeout.ms" to "30000",       // 30 second session timeout
        "heartbeat.interval.ms" to "3000"      // Heartbeat every 3 seconds
    )
)
```

### Producer Tuning

```kotlin
val highThroughputProducerConfig = KafkaProducer.Config(
    broker = kafkaBroker,
    topic = "output-topic",
    driverProperties = mapOf(
        // Override defaults for even higher throughput
        "batch.size" to "1048576",                    // 1MB batches
        "linger.ms" to "50",                          // Shorter linger time
        "buffer.memory" to "134217728",               // 128MB buffer
        "compression.type" to "zstd",                 // Better compression
        "max.in.flight.requests.per.connection" to "5" // Higher throughput (sacrifices ordering)
    )
)
```

### Transformer Tuning

```kotlin
val optimizedTransformer = KafkaTransformer<OutputEvent>(
    consumerConfig = consumerConfig,
    producerConfig = producerConfig,
    eventHandler = eventHandler,
    publishTimerMetric = metricsPublisher,
    publishCounterMetric = metricsPublisher,
    numberOfWorkers = 16,  // More workers for CPU-intensive transformations
    workerSelector = DefaultWorkerSelector(16) // Distribute by key hash
)
```

## Conclusion

The vanilla Kotlin Kafka infrastructure provides:

- **Production-Ready**: Configurations based
  on [Ted Naleid's proven best practices](https://www.naleid.com/2021/05/02/kafka-topic-partitioning-replication.html)
- **High Performance**: Actor pattern eliminates pipeline delays and maximizes throughput
- **Fault Tolerant**: Comprehensive error handling with configurable recovery strategies
- **Observable**: Built-in metrics and health monitoring for operational excellence
- **Testable**: Comprehensive utilities and patterns for reliable testing
- **Flexible**: Support for various processing patterns (1:1, 1:N, filtering, fan-out)

The combination of the consumer wrapper's reliability, producer's optimized configuration, and transformer's actor
pattern creates a powerful foundation for building scalable Kafka-based applications that can handle high-throughput
scenarios while maintaining data consistency and operational excellence.

Whether you're building event sourcing systems, data pipelines, or microservice communication patterns, these components
provide the reliability and performance needed for production workloads.
