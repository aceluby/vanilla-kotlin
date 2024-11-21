package vanillakotlin.api


import vanillakotlin.api.favoriteitems.FavoriteItemsService
import vanillakotlin.api.favoriteitems.adminGetFavoriteTcinsRoute
import vanillakotlin.api.favoriteitems.deleteFavoriteTcinsRoute
import vanillakotlin.api.favoriteitems.getFavoriteItemsRoute
import vanillakotlin.api.favoriteitems.getFavoriteTcinsRoute
import vanillakotlin.api.favoriteitems.postFavoriteTcinsRoute
import vanillakotlin.http.clients.item.ItemGateway
import org.slf4j.LoggerFactory

// This is the main entrypoint for the api application. It's a regular class with a main method.
// It doesn't contain business logic; it only wires up the dependencies with code.
// This is in contrast to a dependency management tool like Spring or Guice. With massive applications where it's
// impractical to wire up all the dependencies, those dependency injection frameworks may be more suitable, but especially in a
// microservice architecture, it's simpler to create the dependencies in code.
class App : ReferenceApp {
    private val log = LoggerFactory.getLogger(javaClass)

    private val config = loadTargetDefaultConfig<Config>("conf")

    private val db = Db(config.db)

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

    private val favoriteItemsService =
        FavoriteItemsService(
            userFavoriteTcinRepository = UserFavoriteTcinRepository(db),
            getItemDetails = itemGateway::getItemDetails,
        )

    private val healthMonitors: List<HealthMonitor> =
        listOf(
            DatabaseHealthMonitor(db),
        )

    // configure the http4k jackson object mapper to use a typical Target convention
    init {
        modifyHttp4kMapperToTargetDefault()
    }

    private val httpServer =
        buildServer(
            healthMonitors = healthMonitors,
            contractRoutes =
            listOf(
                postFavoriteTcinsRoute(saveFavoriteTcin = favoriteItemsService::saveFavoriteTcin),
                deleteFavoriteTcinsRoute(deleteFavoriteTcin = favoriteItemsService::deleteFavoriteTcin),
                getFavoriteTcinsRoute(getFavoriteTcins = favoriteItemsService::getFavoriteTcins),
                getFavoriteItemsRoute(getFavoriteItems = favoriteItemsService::getFavoriteItems),
                adminGetFavoriteTcinsRoute(getFavoriteTcins = favoriteItemsService::getFavoriteTcins),
            ),
            apiMetadata = apiMetadata,
            corsMode = config.http.server.corsMode,
            port = config.http.server.port,
        )

    // this port is made available for testing, because in testing we'll grab the first available open port and need to know what that is
    val httpServerPort: Int
        get() = httpServer.port()

    override fun start() {
        // in this function we start up all the processes that are needed for the application.
        // they should all be non-blocking / daemons
        log.atInfo().log("Starting app")
        httpServer.start()
        log.atInfo().log("Started app on port $httpServerPort")
    }

    override fun close() {
        log.atInfo().log("Closing app")
        httpServer.stop()
        log.atInfo().log("Closed app")
    }
}

// the main function is invoked via the gradle application plugin, and is configured in the `api.gradle.kts` file with
// `mainClass.set("vanillakotlin.api.AppKt")`  See vanillakotlin.app.runApplication for more details.
fun main() = runApplication { App() }
