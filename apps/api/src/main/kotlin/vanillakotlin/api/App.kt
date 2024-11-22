package vanillakotlin.api

import com.target.liteforjdbc.Db
import com.target.liteforjdbc.health.DatabaseHealthMonitor
import org.http4k.contract.openapi.ApiInfo
import org.slf4j.LoggerFactory
import vanillakotlin.api.favoriteitems.FavoriteItemsService
import vanillakotlin.api.favoriteitems.deleteFavoriteTcinsRoute
import vanillakotlin.api.favoriteitems.getFavoriteItemIdsRoute
import vanillakotlin.api.favoriteitems.getFavoriteItemsRoute
import vanillakotlin.api.favoriteitems.postFavoriteTcinsRoute
import vanillakotlin.app.VanillaApp
import vanillakotlin.app.runApplication
import vanillakotlin.config.loadConfig
import vanillakotlin.db.repository.FavoriteItemRepository
import vanillakotlin.db.toHealthMonitor
import vanillakotlin.http.clients.initializeHttpClient
import vanillakotlin.http.clients.item.ItemGateway
import vanillakotlin.http.interceptors.RetryInterceptor
import vanillakotlin.http.interceptors.TelemetryInterceptor
import vanillakotlin.http4k.buildServer
import vanillakotlin.metrics.OtelMetrics
import vanillakotlin.models.HealthMonitor

// This is the main entrypoint for the api application. It's a regular class with a main method.
// It doesn't contain business logic; it only wires up the dependencies with code.
// This is in contrast to a dependency management tool like Spring or Guice. With massive applications where it's
// impractical to wire up all the dependencies, those dependency injection frameworks may be more suitable, but especially in a
// microservice architecture, it's simpler to create the dependencies in code.
class App : VanillaApp {
    private val log = LoggerFactory.getLogger(javaClass)

    private val config = loadConfig<Config>()

    private val db = Db(config.db)

    private val metricsPublisher = OtelMetrics(config.metrics)

    private val itemClient = initializeHttpClient(
        config = config.http.client.item.connection,
        publishGaugeMetric = metricsPublisher::publishGaugeMetric,
        RetryInterceptor(config.http.client.item.retry, metricsPublisher::publishCounterMetric),
        TelemetryInterceptor(metricsPublisher::publishTimerMetric),
    )

    private val itemGateway = ItemGateway(
        httpClient = itemClient,
        config = config.http.client.item.gateway,
    )

    private val favoriteItemsService = FavoriteItemsService(
        userFavoriteTcinRepository = FavoriteItemRepository(db),
        getItemDetails = itemGateway::getItemDetails,
    )

    private val healthMonitors: List<HealthMonitor> = listOf(
        DatabaseHealthMonitor(db).toHealthMonitor(),
    )

    private val httpServer = buildServer(port = config.http.server.port) {
        healthMonitors { +healthMonitors }
        contract {
            apiInfo = ApiInfo("Favorite Items API", "1")
            routes {
                +postFavoriteTcinsRoute(saveFavoriteItem = favoriteItemsService::saveFavoriteTcin)
                +deleteFavoriteTcinsRoute(deleteFavoriteItem = favoriteItemsService::deleteFavoriteTcin)
                +getFavoriteItemIdsRoute(getFavoriteItemIds = favoriteItemsService::getFavoriteItems)
                +getFavoriteItemsRoute(getFavoriteItems = favoriteItemsService::getFavoriteItemsDetails)
            }
        }
        corsMode = config.http.server.corsMode
    }

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
