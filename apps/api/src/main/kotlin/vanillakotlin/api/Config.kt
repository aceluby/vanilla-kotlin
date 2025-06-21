package vanillakotlin.api

import vanillakotlin.db.DbConfig
import vanillakotlin.http.clients.ConnectionConfig
import vanillakotlin.http.clients.thing.ThingGateway
import vanillakotlin.http.interceptors.RetryInterceptor
import vanillakotlin.metrics.OtelMetrics

// This file includes the data classes needed to define the configuration for the app.

data class Config(
    val db: DbConfig,
    val http: HttpConfig,
    val metrics: OtelMetrics.Config,
) {
    data class HttpConfig(
        val client: ClientConfig,
        val server: ServerConfig,
    ) {

        data class ClientConfig(
            val thing: ThingGateway.Config,
            val connectionConfig: ConnectionConfig,
            val retryConfig: RetryInterceptor.Config,
        )

        data class ServerConfig(
            val port: Int,
            val host: String,
        )
    }
}
