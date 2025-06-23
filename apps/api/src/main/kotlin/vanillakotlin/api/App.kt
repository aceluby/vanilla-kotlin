package vanillakotlin.api

import org.slf4j.LoggerFactory
import vanillakotlin.api.favoritethings.FavoriteThingsService
import vanillakotlin.api.favoritethings.deleteFavoriteThingsRoute
import vanillakotlin.api.favoritethings.getAdminFavoriteThingsRoute
import vanillakotlin.api.favoritethings.getFavoriteThingIdsRoute
import vanillakotlin.api.favoritethings.getFavoriteThingsRoute
import vanillakotlin.api.favoritethings.postFavoriteThingsRoute
import vanillakotlin.app.VanillaApp
import vanillakotlin.app.runApplication
import vanillakotlin.config.loadConfig
import vanillakotlin.db.createJdbi
import vanillakotlin.db.repository.FavoriteThingRepository
import vanillakotlin.http.clients.initializeHttpClient
import vanillakotlin.http.clients.thing.ThingGateway
import vanillakotlin.http.interceptors.RetryInterceptor
import vanillakotlin.http.interceptors.TelemetryInterceptor
import vanillakotlin.http4k.buildServer
import vanillakotlin.metrics.OtelMetrics
import vanillakotlin.serde.mapper
import java.lang.invoke.MethodHandles

private val logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass())

class App : VanillaApp {

    private val config = loadConfig<Config>()
    private val jdbi = createJdbi(config.db, mapper)

    // Initialize metrics
    private val metrics = OtelMetrics(config.metrics)

    private val httpClient = initializeHttpClient(
        config = config.http.client.connectionConfig,
        publishGaugeMetric = metrics::publishGaugeMetric,
        RetryInterceptor(
            config = config.http.client.retryConfig,
            publishCounterMetric = metrics::publishCounterMetric,
        ),
        TelemetryInterceptor(
            publishTimerMetric = metrics::publishTimerMetric,
        ),
    )

    // Initialize ThingGateway
    val thingGateway = ThingGateway(
        httpClient = httpClient,
        config = config.http.client.thing,
    )

    // Initialize FavoriteThingsService with real ThingGateway
    val favoriteThingRepository = FavoriteThingRepository(jdbi)
    val favoriteThingsService = FavoriteThingsService(
        upsertFavoriteThing = favoriteThingRepository::upsert,
        deleteFavoriteThingRepository = favoriteThingRepository::deleteItem,
        findAllFavoriteThings = favoriteThingRepository::findAll,
        getThingDetails = thingGateway::getThingDetails,
    )

    private val httpServer = buildServer(
        host = config.http.server.host,
        port = config.http.server.port,
    ) {
        routeHandlers {
            +postFavoriteThingsRoute(favoriteThingsService::saveFavoriteThing)
            +deleteFavoriteThingsRoute(favoriteThingsService::deleteFavoriteThing)
            +getFavoriteThingIdsRoute(favoriteThingsService::getFavoriteThings)
            +getFavoriteThingsRoute(favoriteThingsService::getFavoriteThingsDetails)
            +getAdminFavoriteThingsRoute(favoriteThingsService::getFavoriteThings)
        }
    }

    val httpServerPort: Int?
        get() = httpServer.port()

    override fun start() {
        logger.info("Starting app")
        httpServer.start()
        logger.info("Started app on port ${httpServer?.port()}")
    }

    override fun close() {
        logger.info("Closing app")
        httpServer.stop()
        logger.info("Closed app")
    }
}

fun main() = runApplication { App() }
