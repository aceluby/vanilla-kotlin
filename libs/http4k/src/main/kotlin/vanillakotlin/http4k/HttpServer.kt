package vanillakotlin.http4k

import net.pearx.kasechange.toSnakeCase
import org.http4k.contract.ContractRoute
import org.http4k.contract.jsonschema.v3.AutoJsonToJsonSchema
import org.http4k.contract.jsonschema.v3.FieldRetrieval
import org.http4k.contract.jsonschema.v3.JacksonJsonNamingAnnotated
import org.http4k.contract.jsonschema.v3.JacksonJsonPropertyAnnotated
import org.http4k.contract.jsonschema.v3.SimpleLookup
import org.http4k.core.ContentType
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.HttpTransaction
import org.http4k.core.Method
import org.http4k.core.NoOp
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.filter.AllowAll
import org.http4k.filter.CorsPolicy
import org.http4k.filter.OriginPolicy
import org.http4k.filter.ResponseFilters
import org.http4k.filter.ServerFilters
import org.http4k.format.ConfigurableJackson
import org.http4k.routing.RoutingHttpHandler
import org.http4k.server.Http4kServer
import org.http4k.server.Undertow
import org.http4k.server.asServer
import org.slf4j.LoggerFactory
import vanillakotlin.extensions.toJsonString
import vanillakotlin.models.HealthMonitor
import vanillakotlin.models.healthCheckAll
import vanillakotlin.serde.mapper
import java.lang.invoke.MethodHandles

private val log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass().name)

val DEFAULT_HEALTH_CHECKS: List<HealthMonitor> = emptyList()
val DEFAULT_ROUTE_HANDLERS: List<RoutingHttpHandler> = emptyList()
val DEFAULT_CONTRACT_ROUTES: List<ContractRoute> = emptyList()
val DEFAULT_CORS_MODE = CorsMode.NO_OP
val DEFAULT_LOGGING_FILTER = simpleLoggingFilter()
val DEFAULT_EXCEPTION_FILTER = simpleExceptionFilter()
val DEFAULT_GLOBAL_FILTERS = listOf(DEFAULT_LOGGING_FILTER, DEFAULT_EXCEPTION_FILTER)
const val DEFAULT_SWAGGER_PATH = "/swagger.json"
const val DEFAULT_BASE_PATH = "/"
const val DEFAULT_PORT = 8080
const val DEFAULT_HOST = "127.0.0.1"

private fun simpleLoggingFilter() = ResponseFilters.ReportHttpTransaction { tx: HttpTransaction ->
    log.atDebug().log { "uri=${tx.request.uri} status=${tx.response.status} elapsed_ms=${tx.duration.toMillis()}" }
}

private fun simpleExceptionFilter() = Filter { next ->
    {
        try {
            next(it)
        } catch (t: Throwable) {
            log.error("Failed to process request", t)
            Response(Status.INTERNAL_SERVER_ERROR)
        }
    }
}

internal fun autoJsonToJsonSchema(json: ConfigurableJackson) = AutoJsonToJsonSchema(
    json,
    FieldRetrieval.compose(
        SimpleLookup(renamingStrategy = { it.toSnakeCase() }),
        FieldRetrieval.compose(JacksonJsonPropertyAnnotated, JacksonJsonNamingAnnotated(json)),
    ),
)

/**
 * There is an UnsafeGlobalPermissive policy given by http4k, but unfortunately the only header allowed is
 * content-type. For any server that is serving a UI backend this is very constricting as common Target
 * headers such as x-api-key, x-profile-id, and any custom headers are not allowed. Therefore, this custom
 * ALLOW_ALL policy is created to allow all headers, methods, and credentials. This is not recommended for
 * deployed use and go-proxy should be used instead for CORS, but is useful for local development.
 */
enum class CorsMode(val filter: Filter) {
    ALLOW_ALL(
        ServerFilters.Cors(
            CorsPolicy(
                originPolicy = OriginPolicy.AllowAll(),
                headers = listOf("*"),
                methods = Method.entries,
                credentials = true,
            ),
        ),
    ),
    NO_OP(Filter.NoOp),
}

/**
 * This is a small customization over one of the choices available in http4k.
 * The key difference is that it allows for a `host` parameter, which lets you bind to a loopback address (127.0.0.1) instead of 0.0.0.0
 * to prevent direct external access. This is primarily when using the go-proxy, which handles external access and auth, and then
 * forwards unsecured auth headers to the app.  If you aren't using the go-proxy and want your application host to be directly addressable,
 * you would want to set the host to 0.0.0.0. Be sure that none of your endpoints need auth if you do this.
 */
class Server(
    val port: Int,
    val host: String,
) {
    fun toServer(http: HttpHandler): Http4kServer = http.asServer(Undertow(port))
}

fun Filter.then(filters: List<Filter>): Filter = filters.fold(this) { acc, filter -> acc.then(filter) }

fun healthCheckHandler(healthMonitors: List<HealthMonitor>): HttpHandler = { _ ->
    val healthCheckResponses = healthCheckAll(healthMonitors)
    val status = if (healthCheckResponses.any { !it.isHealthy }) Status.INTERNAL_SERVER_ERROR else Status.OK
    Response(status).body(healthCheckResponses.toJsonString())
}

val CatchAllFailure = Filter { next ->
    {
        try {
            next(it)
        } catch (throwable: Throwable) {
            Response(Status.INTERNAL_SERVER_ERROR)
                .header("Content-Type", ContentType.APPLICATION_JSON.toHeaderValue())
                .body(mapper.writeValueAsString(mapOf("error" to throwable.message)))
        }
    }
}
