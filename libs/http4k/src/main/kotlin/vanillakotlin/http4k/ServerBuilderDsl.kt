package vanillakotlin.http4k

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.http4k.contract.ContractRoute
import org.http4k.contract.openapi.ApiInfo
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.filter.ServerFilters
import org.http4k.format.ConfigurableJackson
import org.http4k.format.Jackson
import org.http4k.routing.RoutingHttpHandler
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.http4k.server.Http4kServer
import org.http4k.server.Undertow
import org.http4k.server.asServer
import vanillakotlin.models.HealthMonitor
import vanillakotlin.serde.mapper

@DslMarker
annotation class Http4kServerMarker

@Http4kServerMarker
class ServerBuilder internal constructor(private val host: String, private val port: Int) {
    init {
        Jackson.mapper
            // This the default naming strategy that should be used for most JSON payloads at Target unless there's a reason otherwise.
            // For example, one exception to this rule is GraphQL, which uses a lowerCamelCase naming convention
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .deactivateDefaultTyping()
            // only include non-null properties in output is a typically preferred option. It results in a smaller payload
            .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
            // writing textual dates is a little more verbose, but it provides a big advantage for human readability and debugging.
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            // This can be handy for human readability and consistency. It does add a small computational cost to serialization, but typically not
            // a substantial amount
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
            // not failing on unknown properties is a good default when you may want to only deserialize a subset of a JSON body
            // for example when you only need a few properties
            // it IS important to fail on unknown properties when you're working with objects for which you expect to receive
            // the entire object. for example configuration files or other payloads that must be completely specified.
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES, false)
            .configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true)
            .configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true)
            // all newer projects will set this, which sets some base minimum version feature settings
            .registerModule(Jdk8Module())
            // enables support for date objects used in java.time, which is an extremely valuable addition
            .registerModule(JavaTimeModule())
            .registerModule(KotlinModule.Builder().build())
    }

    private var healthMonitors: List<HealthMonitor> = DEFAULT_HEALTH_CHECKS
    private var routeHandlers: List<RoutingHttpHandler> = DEFAULT_ROUTE_HANDLERS
    private var globalFilters: List<Filter> = DEFAULT_GLOBAL_FILTERS
    private var contractRoutes: RoutingHttpHandler? = null

    var corsMode: CorsMode = DEFAULT_CORS_MODE

    @Http4kServerMarker
    fun healthMonitors(action: ListBuilder<HealthMonitor>.() -> Unit) {
        healthMonitors = ListBuilder<HealthMonitor>().apply(action)
    }

    @Http4kServerMarker
    fun routeHandlers(action: ListBuilder<RoutingHttpHandler>.() -> Unit) {
        routeHandlers = ListBuilder<RoutingHttpHandler>().apply(action)
    }

    @Http4kServerMarker
    fun filters(action: FilterBuilder.() -> Unit) {
        globalFilters = FilterBuilder().apply(action).build()
    }

    @Http4kServerMarker
    fun contract(action: ContractBuilder.() -> Unit) {
        contractRoutes = ContractBuilder().apply(action).build()
    }

    fun buildHandler(): HttpHandler {
        val routesList = buildList {
            add("health" bind Method.GET to healthCheckHandler(healthMonitors))
            addAll(routeHandlers)
            contractRoutes?.let(::add)
        }

        return CatchAllFailure.then(globalFilters)
            .then(ServerFilters.CatchLensFailure)
            .then(corsMode.filter)
            .then(routes(routesList))
    }

    fun build(): Http4kServer = buildHandler().asServer(Undertow(port))

    @Http4kServerMarker
    open class ListBuilder<T> internal constructor() : MutableList<T> by mutableListOf() {
        operator fun T.unaryPlus() = add(this)

        operator fun Collection<T>.unaryPlus() = addAll(this)

        operator fun T.unaryMinus() = remove(this)

        operator fun Collection<T>.unaryMinus() = removeAll(this)
    }

    @Http4kServerMarker
    class FilterBuilder internal constructor() : ListBuilder<Filter>() {
        var includeDefaults = true

        fun build(): List<Filter> = apply {
            if (includeDefaults) {
                add(DEFAULT_LOGGING_FILTER)
                add(DEFAULT_EXCEPTION_FILTER)
            }
        }
    }

    @Http4kServerMarker
    class ContractBuilder internal constructor() {
        lateinit var apiInfo: ApiInfo
        var configurableJackson: ConfigurableJackson = ConfigurableJackson(mapper = mapper)
        var contractRoutesBasePath: String = DEFAULT_BASE_PATH
        var swaggerPath: String = DEFAULT_SWAGGER_PATH
        private var routes: List<ContractRoute> = emptyList()

        @Http4kServerMarker
        fun routes(action: ListBuilder<ContractRoute>.() -> Unit) {
            routes = ListBuilder<ContractRoute>()
                .apply(action)
                .also { require(it.isNotEmpty()) { "Contract routes should not be empty if a contract is being specified." } }
        }

        fun build(): RoutingHttpHandler {
            require(::apiInfo.isInitialized) { "apiInfo must be set when using contract routes" }
            require(routes.isNotEmpty()) { "Contract routes should not be empty if a contract is being specified." }

            // TODO: Properly implement contract route handling
            // For now, return a simple placeholder route
            return org.http4k.routing.routes(
                contractRoutesBasePath bind Method.GET to {
                    Response(Status.OK).body("Contract routes configured with ${routes.size} routes")
                },
            )
        }
    }
}

@Http4kServerMarker
fun buildServer(
    host: String = DEFAULT_HOST,
    port: Int = DEFAULT_PORT,
    action: ServerBuilder.() -> Unit,
) = ServerBuilder(host, port).apply(action).build()

@Http4kServerMarker
fun buildServerHandler(
    host: String = DEFAULT_HOST,
    port: Int = DEFAULT_PORT,
    action: ServerBuilder.() -> Unit,
): HttpHandler = ServerBuilder(host, port).apply(action).buildHandler()
