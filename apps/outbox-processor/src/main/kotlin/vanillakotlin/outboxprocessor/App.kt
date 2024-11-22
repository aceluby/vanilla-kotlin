package vanillakotlin.outboxprocessor

import com.target.liteforjdbc.Db
import com.target.liteforjdbc.health.DatabaseHealthMonitor
import org.slf4j.LoggerFactory
import vanillakotlin.app.VanillaApp
import vanillakotlin.app.runApplication
import vanillakotlin.config.loadConfig
import vanillakotlin.db.toHealthMonitor
import vanillakotlin.http4k.buildServer
import vanillakotlin.kafka.producer.KafkaProducer
import vanillakotlin.metrics.OtelMetrics
import vanillakotlin.models.HealthMonitor

class App : VanillaApp {
    private val log = LoggerFactory.getLogger(javaClass)

    private val config = loadConfig<Config>()

    private val db = Db(config.db)

    private val metricsPublisher = OtelMetrics(config.metrics)

    private val kafkaProducer =
        KafkaProducer<ByteArray?>(
            config = config.kafka.producer,
            publishTimerMetric = metricsPublisher::publishTimerMetric,
        )

    private val healthMonitors: List<HealthMonitor> = listOf(DatabaseHealthMonitor(db).toHealthMonitor())

    // since this app doesn't provide any APIs other than a health endpoint, use a more convenient function to build an httpserver
    private val httpServer = buildServer(port = config.http.server.port) {
        healthMonitors { +healthMonitors }
    }

    // this port is made available for testing, because in testing we'll grab the first available open port and need to know what that is
    val httpServerPort: Int
        get() = httpServer.port()

    private val outboxProcessor = OutboxProcessor(
        config = config.outbox,
        db = db,
        kafkaSendAsync = kafkaProducer::sendAsync,
    )

    override fun start() {
        // in this function we start up all the processes that are needed for the application.
        // they should all be non-blocking / daemons
        log.atInfo().log("Starting app")
        kafkaProducer.start()
        httpServer.start()
        outboxProcessor.start()
        log.atInfo().log("Started app")
    }

    override fun close() {
        log.atInfo().log("Closing app")
        httpServer.stop()
        log.atInfo().log("Closed app")
    }
}

// the main function is invoked via the gradle application plugin, and is configured in the `api.gradle.kts` file with
// `mainClass.set("vanillakotlin.outboxprocessor.AppKt")`
fun main() = runApplication { App() }
