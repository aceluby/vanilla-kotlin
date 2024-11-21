package vanillakotlin.kafkatransformer

import vanillakotlin.http.clients.item.ItemGateway

// see docs/configuration.md for more details

data class Config(
    val http: HttpConfig,
    val metrics: OtelMetricsPublisher.Config,
    val kafka: KafkaConfig,
) {
    data class HttpConfig(
        val server: HttpServerConfig,
        val client: ClientConfig,
    ) {
        data class HttpServerConfig(
            val port: Int,
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

    data class KafkaConfig(
        val consumer: KafkaConsumer.Config,
        val producer: KafkaProducer.Config,
    )
}
