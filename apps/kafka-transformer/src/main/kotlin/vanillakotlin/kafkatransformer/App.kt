package vanillakotlin.kafkatransformer

import vanillakotlin.http.clients.item.ItemGateway
import org.slf4j.LoggerFactory

// See vanillakotlin.api.App for an annotated version of this type of class

class App : ReferenceApp {
    private val log = LoggerFactory.getLogger(javaClass)

    private val config = loadConfig<Config>()

    private val metricsPublisher = OtelMetricsPublisher(config.metrics)

    private val itemClient =
        initializeHttpClient(
            config = config.http.client.item.connection,
            CachingInterceptor(config.http.client.item.cache, metricsPublisher::publishTimerMetric),
            RetryInterceptor(config.http.client.item.retry, metricsPublisher::publishCounterMetric),
            TelemetryInterceptor(metricsPublisher::publishTimerMetric),
        )

    private val itemGateway =
        ItemGateway(
            httpClient = itemClient,
            config = config.http.client.item.gateway,
        )

    private val kafkaTransformer =
        KafkaTransformer(
            consumerConfig = config.kafka.consumer,
            producerConfig = config.kafka.producer,
            eventHandler =
                FavoriteItemsEventHandler(
                    getItemDetails = itemGateway::getItemDetails,
                ),
            publishTimerMetric = metricsPublisher::publishTimerMetric,
            createGaugeMetric = metricsPublisher::createGaugeMetric,
            publishCounterMetric = metricsPublisher::publishCounterMetric,
        )

    private val healthMonitors: List<HealthMonitor> = emptyList()

    // since this app doesn't provide any APIs other than a health endpoint, use a more convenient function to build an httpserver
    private val httpServer =
        buildHealthOnlyServer(
            healthMonitors = healthMonitors,
            port = config.http.server.port,
        )

    // httpServerPort is used for testing
    val httpServerPort: Int
        get() = httpServer.port()

    override fun start() {
        // in this function we start up all the processes that are needed for the application.
        // they should all be non-blocking / daemons
        log.atInfo().log("Starting app")
        kafkaTransformer.start()
        httpServer.start()
        log.atInfo().log("Started app")
    }

    override fun close() {
        log.atInfo().log("Closing app")
        kafkaTransformer.close()
        httpServer.stop()
        log.atInfo().log("Closed app")
    }
}

fun main() = runApplication { App() }
