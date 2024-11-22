package vanillakotlin.kafkatransformer

import org.slf4j.LoggerFactory
import vanillakotlin.app.VanillaApp
import vanillakotlin.app.runApplication
import vanillakotlin.config.loadConfig
import vanillakotlin.http.clients.initializeHttpClient
import vanillakotlin.http.clients.item.ItemGateway
import vanillakotlin.http.interceptors.RetryInterceptor
import vanillakotlin.http.interceptors.TelemetryInterceptor
import vanillakotlin.http4k.buildServer
import vanillakotlin.kafka.transformer.KafkaTransformer
import vanillakotlin.metrics.OtelMetrics
import vanillakotlin.models.HealthMonitor

// See vanillakotlin.api.App for an annotated version of this type of class

class App : VanillaApp {
    private val log = LoggerFactory.getLogger(javaClass)

    private val config = loadConfig<Config>()

    private val metricsPublisher = OtelMetrics(config.metrics)

    private val itemClient =
        initializeHttpClient(
            config = config.http.client.item.connection,
            publishGaugeMetric = metricsPublisher::publishGaugeMetric,
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
            publishCounterMetric = metricsPublisher::publishCounterMetric,
        )

    private val healthMonitors: List<HealthMonitor> = emptyList()

    // since this app doesn't provide any APIs other than a health endpoint, use a more convenient function to build an httpserver
    private val httpServer = buildServer(port = config.http.server.port) {
        healthMonitors { +healthMonitors }
    }

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
