package vanillakotlin.api

import vanillakotlin.http.clients.item.ItemGateway

// This file includes the data classes needed to define the configuration for the app.
// see docs/configuration.md for more details

data class Config(
    val http: HttpConfig,
    val metrics: OtelMetricsPublisher.Config,
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
                val cache: CachingInterceptor.Config,
            )
        }
    }
}
