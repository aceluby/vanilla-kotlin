package vanillakotlin.bulkinserter

import com.target.liteforjdbc.Db
import org.slf4j.LoggerFactory
import vanillakotlin.app.VanillaApp
import vanillakotlin.app.runApplication
import vanillakotlin.config.loadConfig
import vanillakotlin.db.repository.FavoriteItemRepository
import vanillakotlin.http4k.buildServer
import vanillakotlin.kafka.consumer.KafkaConsumer
import vanillakotlin.metrics.OtelMetrics
import vanillakotlin.models.HealthMonitor

class App : VanillaApp {
    private val log = LoggerFactory.getLogger(javaClass)

    private val config = loadConfig<Config>()

    private val metricsPublisher = OtelMetrics(config.metrics)

    // internal so we can leverage it in tests
    internal val repository = FavoriteItemRepository(Db(config.db))

    private val kafkaConsumer =
        KafkaConsumer(
            config = config.kafka.consumer,
            eventHandler = BulkInserterHandler(
                    addToBatch = repository::addToBatch,
                    runBatch = repository::runBatch,
                ),
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
        kafkaConsumer.start()
        httpServer.start()
        log.atInfo().log("Started app")
    }

    override fun close() {
        log.atInfo().log("Closing app")
        kafkaConsumer.stop()
        httpServer.stop()
        log.atInfo().log("Closed app")
    }
}

fun main() = runApplication { App() }
