package vanillakotlin.kafka.transformer

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.ticker
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.apache.kafka.clients.producer.RecordMetadata
import org.slf4j.LoggerFactory
import vanillakotlin.kafka.APP_TAG
import vanillakotlin.kafka.DROPPED_TAG
import vanillakotlin.kafka.FORWARDED_TAG
import vanillakotlin.kafka.KAFKA_PROCESS_TIMER_METRIC
import vanillakotlin.kafka.SKIPPED_TAG
import vanillakotlin.kafka.STATUS_TAG
import vanillakotlin.kafka.TOPIC_TAG
import vanillakotlin.kafka.TRANSFORMER_EVENT
import vanillakotlin.kafka.WORKER_TAG
import vanillakotlin.kafka.consumer.ErrorHandler
import vanillakotlin.kafka.consumer.KafkaConsumer
import vanillakotlin.kafka.consumer.KafkaError
import vanillakotlin.kafka.consumer.runtimeErrorHandler
import vanillakotlin.kafka.models.KafkaMessage
import vanillakotlin.kafka.models.SequenceHandler
import vanillakotlin.kafka.models.TopicPartitionOffset
import vanillakotlin.kafka.producer.KafkaProducer
import vanillakotlin.kafka.producer.PartitionCalculator
import vanillakotlin.metrics.PublishCounterMetric
import vanillakotlin.metrics.PublishTimerMetric
import vanillakotlin.metrics.time
import vanillakotlin.models.HealthCheckResponse
import vanillakotlin.models.HealthMonitor
import java.util.concurrent.CompletableFuture

private val log = LoggerFactory.getLogger(KafkaTransformer::class.java)

class KafkaTransformer<OUT>(
    private val consumerConfig: KafkaConsumer.Config,
    private val producerConfig: KafkaProducer.Config,
    private val eventHandler: TransformerEventHandler<OUT>,
    private val publishTimerMetric: PublishTimerMetric,
    private val publishCounterMetric: PublishCounterMetric,
    // Defaults to the number of cores
    private val numberOfWorkers: Int = Runtime.getRuntime().availableProcessors(),
    private val workerSelector: WorkerSelector = DefaultWorkerSelector(numberOfWorkers),
    // Handles uncaught transformation errors, defaults to shutting down the app
    // Overwrite if you would like to implement a poison/DLQ pattern
    private val uncaughtErrorHandler: ErrorHandler = runtimeErrorHandler(),
    partitionFor: (String?, Int) -> Int? = PartitionCalculator.Companion::partitionFor,
) : HealthMonitor, AutoCloseable {

    override val name = consumerConfig.appName
    override fun check(): HealthCheckResponse = sourceConsumer.check()

    private lateinit var eventWorkers: List<Job>
    private lateinit var kafkaPublishJob: Job
    private lateinit var kafkaAckJob: Job
    private lateinit var tickerJob: Job
    private lateinit var commitJob: Job

    data class TransformResult<OUT>(
        val topicPartitionOffset: TopicPartitionOffset,
        val outboundMessage: TransformerMessages<OUT>,
    )

    data class EventResult<OUT>(
        val topicPartitionOffset: TopicPartitionOffset,
        val transformerResult: List<Pair<TransformerMessage<OUT>, CompletableFuture<out RecordMetadata?>>>,
    )

    private lateinit var workersChannels: List<Channel<Pair<KafkaMessage, CompletableDeferred<TransformResult<OUT>>>>>
    private lateinit var kafkaPublisherChannel: Channel<CompletableDeferred<TransformResult<OUT>>>
    private lateinit var kafkaAckChannel: Channel<EventResult<OUT>>
    private lateinit var tickerChannel: Channel<Unit>

    private val consumerEventHandler = SequenceHandler { seq ->
        seq.forEach { kafkaMessage ->
            val deferred = CompletableDeferred<TransformResult<OUT>>()
            runBlocking {
                workersChannels[workerSelector.selectWorker(kafkaMessage.key)].send(kafkaMessage to deferred)
                kafkaPublisherChannel.send(deferred)
            }
        }
    }
    private val sourceConsumer = KafkaConsumer(
        config = consumerConfig.copy(autoCommitOffsets = false),
        uncaughtErrorHandler = uncaughtErrorHandler,
        eventHandler = consumerEventHandler,
    )
    private val sinkProducer = KafkaProducer<OUT>(
        config = producerConfig,
        publishTimerMetric = publishTimerMetric,
        partitionFor = partitionFor,
    )

    // Worker that transforms the event and completes the transformation event
    private fun CoroutineScope.launchEventWorker(
        workerChannel: Channel<Pair<KafkaMessage, CompletableDeferred<TransformResult<OUT>>>>,
        workerId: Int,
    ) = launch(Dispatchers.IO) {
        log.atDebug().log { "Launching event worker on thread ${Thread.currentThread()} for ${consumerConfig.topics}" }
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
                    val outboundMessage = eventHandler.transform(kafkaMessage)
                    // add provenance if it hasn't been explicitly set by the event handler
                    outboundMessage.messages.forEach { transformerMessage ->
                        if (transformerMessage.kafkaOutputMessage.provenances.isEmpty()) {
                            transformerMessage.kafkaOutputMessage.provenances.add(kafkaMessage.buildProvenance())
                        }
                    }

                    val topicPartitionOffset = TopicPartitionOffset(
                        topic = kafkaMessage.topic,
                        partition = kafkaMessage.partition,
                        offset = kafkaMessage.offset,
                    )
                    val transformationResult = TransformResult(topicPartitionOffset, outboundMessage)
                    completion.complete(transformationResult)
                } catch (throwable: Throwable) {
                    uncaughtErrorHandler(KafkaError(throwable, kafkaMessage, consumerConfig.skipErrors))
                    // If the exception is skipped due to the skip configs, we still need to complete the deferred.
                    // This ensures that the KafkaPublisher job can continue processing the next messages in order
                    publishCounterMetric(TRANSFORMER_EVENT, mapOf(STATUS_TAG to SKIPPED_TAG))
                    completion.complete(
                        TransformResult(
                            TopicPartitionOffset(
                                topic = kafkaMessage.topic,
                                partition = kafkaMessage.partition,
                                offset = kafkaMessage.offset,
                            ),
                            TransformerMessages.Dropped(),
                        ),
                    )
                }
            }
        }
    }

    private fun TransformerMessage<OUT>.addStatusTag(status: String): TransformerMessage<OUT> {
        val tags = mapOf(STATUS_TAG to status) + metricTags
        return copy(metricTags = metricTags + tags)
    }

    // Job that receives a deferred transformation event in the order it was received from the poller, awaits the
    // worker to complete the transformation, sends the outbound messages to kafka, and sends the futures to the ack
    // channel to be acked in order
    private fun CoroutineScope.launchKafkaPublisher(
        kafkaPublisherChannel: ReceiveChannel<CompletableDeferred<TransformResult<OUT>>>,
        kafkaAckChannel: Channel<EventResult<OUT>>,
    ) = launch(Dispatchers.IO) {
        log.atDebug().log { "Launching kafkaPublisher on thread ${Thread.currentThread()} producing to ${producerConfig.topic}" }
        for (deferred in kafkaPublisherChannel) {
            val (topicPartitionOffset, transformerMessage) = deferred.await()
            val transformResult =
                if (transformerMessage is TransformerMessages.Dropped) {
                    val modifiedMessage = transformerMessage.messages.first().addStatusTag(DROPPED_TAG)
                    listOf(modifiedMessage to CompletableFuture.completedFuture(null))
                } else {
                    transformerMessage.messages.map { it.addStatusTag(FORWARDED_TAG) to sinkProducer.sendAsync(it.kafkaOutputMessage) }
                }
            kafkaAckChannel.send(EventResult(topicPartitionOffset, transformResult))
        }
    }

    // Listen to the ack channel and ack the messages sent to kafka. These will always be in order from the kafka
    // publisher job.  The offsets are saved to be committed later
    private fun CoroutineScope.launchKafkaAckJob(kafkaAckChannel: Channel<EventResult<OUT>>) = launch(Dispatchers.IO) {
        log.atDebug().log { "Launching kafkaAckJob on thread ${Thread.currentThread()} for ${producerConfig.topic}" }
        for ((topicPartitionOffset, transformerResult) in kafkaAckChannel) {
            transformerResult.forEach { (message, future) ->
                future.join()
                publishCounterMetric(TRANSFORMER_EVENT, message.metricTags)
            }
            sourceConsumer.offsets[topicPartitionOffset.getTopicPartition()] = topicPartitionOffset.getOffsetAndMetadata()
        }
    }

    // allows us to have a `ticker` that we can also poke to send an event to it, such as when we want to shut down
    // gracefully and commit any remaining progress
    @OptIn(ObsoleteCoroutinesApi::class)
    fun CoroutineScope.pokableTicker(delayMillis: Long): Pair<Channel<Unit>, Job> {
        val tickerChannel = ticker(delayMillis = delayMillis)
        val mergedChannel = Channel<Unit>()

        val tickerJob = launch(Dispatchers.Default) {
            log.atDebug().log { "Launching ticker on thread ${Thread.currentThread()}" }
            for (tick in tickerChannel) {
                mergedChannel.send(Unit)
            }
        }

        return mergedChannel to tickerJob
    }

    // Periodically commits the offsets for the next message to be consumed to kafka using the same consumer thread as the polling job
    private fun CoroutineScope.launchPeriodicCommitter(
        tickerChannel: ReceiveChannel<Unit>,
    ) = launch(sourceConsumer.consumerThread.asCoroutineDispatcher()) {
        log.atDebug().log { "Launching periodicCommitter on thread ${Thread.currentThread()} for ${consumerConfig.topics}" }
        for (unit in tickerChannel) {
            sourceConsumer.commitOffsets()
        }
    }

    private fun start() = runBlocking {
        log.atInfo().log("Starting transformer consumer for ${consumerConfig.topics} with $numberOfWorkers workers")
        // Start up the channels
        kafkaPublisherChannel = Channel(numberOfWorkers * WORKER_CHANNEL_SIZE)
        kafkaAckChannel = Channel(ACK_CHANNEL_SIZE)
        val ticker = pokableTicker(COMMITTER_PERIOD_MS)
        tickerChannel = ticker.first
        tickerJob = ticker.second
        if (workerSelector is RoundRobinWorkerSelector) {
            // only use a single channel and have all workers listen to it
            // RoundRobinWorkerSelector will always send to worker channel 0
            workersChannels = listOf(Channel(WORKER_CHANNEL_SIZE * numberOfWorkers))
            eventWorkers = (0..<numberOfWorkers).map { workerIndex ->
                launchEventWorker(workersChannels[0], workerIndex)
            }
        } else {
            workersChannels = List(numberOfWorkers) { Channel(WORKER_CHANNEL_SIZE) }
            eventWorkers = workersChannels.mapIndexed { index, channel -> launchEventWorker(channel, index) }
        }

        // Start the jobs
        commitJob = launchPeriodicCommitter(tickerChannel)
        kafkaAckJob = launchKafkaAckJob(kafkaAckChannel)
        kafkaPublishJob = launchKafkaPublisher(kafkaPublisherChannel, kafkaAckChannel)
        // Start the consumer and producer
        sourceConsumer.start()
        sinkProducer.start()
    }

    override fun close() = runBlocking {
        log.atInfo().log { "Shutting down transformer" }
        sourceConsumer.stop()
        workersChannels.forEach { it.close() }
        eventWorkers.forEach { it.join() }
        kafkaPublisherChannel.close()
        kafkaPublishJob.join()
        kafkaAckChannel.close()
        kafkaAckJob.join()
        tickerJob.cancel()
        tickerChannel.send(Unit)
        tickerChannel.close()
        commitJob.join()
        sourceConsumer.stop()
        sinkProducer.close()
        log.atInfo().log { "Transformer shut down" }
    }

    companion object {
        private const val ACK_CHANNEL_SIZE = 10_000
        private const val WORKER_CHANNEL_SIZE = 500
        private const val COMMITTER_PERIOD_MS = 2000L
    }
}
