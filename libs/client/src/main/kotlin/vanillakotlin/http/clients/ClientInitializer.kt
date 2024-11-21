package vanillakotlin.http.clients

import okhttp3.ConnectionPool
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import vanillakotlin.metrics.PublishGaugeMetric
import java.util.concurrent.TimeUnit

data class ConnectionConfig(
    val connectTimeoutMillis: Long,
    val readTimeoutMillis: Long,
    val maxIdleConnections: Int = 1,
    val maxConnections: Int,
    val keepAliveDurationMinutes: Long,
    val clientName: String = "default",
)

/**
 * Initialize an OkHttpClient with the given configuration and interceptors.
 * The client will be configured with the given connection pool settings and interceptors.
 * The client will also publish metrics for the connection pool and dispatcher.
 */
fun initializeHttpClient(
    config: ConnectionConfig,
    publishGaugeMetric: PublishGaugeMetric,
    vararg interceptors: Interceptor,
): OkHttpClient = OkHttpClient.Builder()
    .readTimeout(config.readTimeoutMillis, TimeUnit.MILLISECONDS)
    .connectTimeout(config.connectTimeoutMillis, TimeUnit.MILLISECONDS)
    .connectionPool(
        ConnectionPool(
            maxIdleConnections = config.maxIdleConnections,
            keepAliveDuration = config.keepAliveDurationMinutes,
            timeUnit = TimeUnit.MINUTES,
        ),
    )
    .apply { interceptors.forEach { addInterceptor(it) } }
    .build()
    .apply {
        dispatcher.maxRequestsPerHost = config.maxConnections
        dispatcher.maxRequests = config.maxConnections
        publishGaugeMetric("http.client.connections.total", mapOf("client" to config.clientName)) {
            connectionPool.connectionCount()
        }
        publishGaugeMetric("http.client.connections.active", mapOf("client" to config.clientName)) {
            connectionPool.connectionCount() - connectionPool.idleConnectionCount()
        }
        publishGaugeMetric("http.client.connections.idle", mapOf("client" to config.clientName)) {
            connectionPool.idleConnectionCount()
        }
        publishGaugeMetric("http.client.calls.running", mapOf("client" to config.clientName)) {
            dispatcher.runningCallsCount()
        }
        publishGaugeMetric("http.client.calls.queued", mapOf("client" to config.clientName)) {
            dispatcher.queuedCallsCount()
        }
    }
