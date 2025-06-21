package vanillakotlin.outboxprocessor

import org.slf4j.LoggerFactory
import vanillakotlin.app.VanillaApp
import vanillakotlin.app.runApplication
import vanillakotlin.config.loadConfig
import vanillakotlin.db.createJdbi
import vanillakotlin.http4k.buildServer
import vanillakotlin.kafka.producer.KafkaProducer
import vanillakotlin.metrics.OtelMetrics
import vanillakotlin.models.HealthCheckResponse
import vanillakotlin.models.HealthMonitor
import vanillakotlin.serde.mapper

class App : VanillaApp {
    private val log = LoggerFactory.getLogger(javaClass)

    private val config = loadConfig<Config>()

    private val jdbi = createJdbi(config.db, mapper)

    private val metricsPublisher = OtelMetrics(config.metrics)

    private val kafkaProducer =
        KafkaProducer<ByteArray?>(
            config = config.kafka.producer,
            publishTimerMetric = metricsPublisher::publishTimerMetric,
        )

    // Create a simple health monitor that checks database connectivity
    private val healthMonitors: List<HealthMonitor> = listOf(
        object : HealthMonitor {
            override val name = "database"
            override fun check(): HealthCheckResponse = try {
                val isHealthy = jdbi.withHandle<Boolean, Exception> { handle ->
                    handle.createQuery("SELECT 1").mapTo(Int::class.java).single() == 1
                }
                HealthCheckResponse(
                    name = name,
                    isHealthy = isHealthy,
                    details = "Database connection successful",
                )
            } catch (e: Exception) {
                log.warn("Database health check failed", e)
                HealthCheckResponse(
                    name = name,
                    isHealthy = false,
                    details = "Database connection failed: ${e.message}",
                )
            }
        },
    )

    // since this app doesn't provide any APIs other than a health endpoint, use a more convenient function to build an httpserver
    private val httpServer = buildServer(port = config.http.server.port) {
        healthMonitors { +healthMonitors }
    }

    // this port is made available for testing, because in testing we'll grab the first available open port and need to know what that is
    val httpServerPort: Int
        get() = httpServer.port()

    private val outboxProcessor = OutboxProcessor(
        config = config.outbox,
        jdbi = jdbi,
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
