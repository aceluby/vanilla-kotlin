package vanillakotlin.api

import com.target.liteforjdbc.DbConfig
import vanillakotlin.http.clients.ConnectionConfig
import vanillakotlin.http.clients.item.ItemGateway
import vanillakotlin.http.interceptors.RetryInterceptor
import vanillakotlin.http4k.CorsMode
import vanillakotlin.metrics.OtelMetrics

// This file includes the data classes needed to define the configuration for the app.

data class Config(
    val http: HttpConfig,
    val metrics: OtelMetrics.Config,
    val db: DbConfig,
) {
    data class HttpConfig(
        val server: HttpServerConfig,
        val client: ClientConfig,
    ) {
        data class HttpServerConfig(
            val port: Int,
            val corsMode: CorsMode,
        )

        data class ClientConfig(
            val item: ItemConfig,
        ) {
            data class ItemConfig(
                val gateway: ItemGateway.Config,
                val connection: ConnectionConfig,
                val retry: RetryInterceptor.Config,
            )
        }
    }
}
