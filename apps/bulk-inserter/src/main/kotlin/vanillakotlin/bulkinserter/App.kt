package vanillakotlin.bulkinserter


class App : ReferenceApp {
    private val log = LoggerFactory.getLogger(javaClass)

    private val config = loadConfig<Config>()

    private val metricsPublisher = OtelMetricsPublisher(config.metrics)

    // internal so we can leverage it in tests
    internal val repository = UserFavoriteTcinRepository(Db(config.db))

    private val kafkaConsumer =
        KafkaConsumer(
            config = config.kafka.consumer,
            eventHandler =
                BulkInserterEventHandler(
                    addToBatch = repository::addToBatch,
                    runBatch = repository::runBatch,
                ),
            publishTimerMetric = metricsPublisher::publishTimerMetric,
            createGaugeMetric = metricsPublisher::createGaugeMetric,
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
