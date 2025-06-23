package vanillakotlin.kafkatransformer

import vanillakotlin.http.clients.ConnectionConfig
import vanillakotlin.http.clients.thing.ThingGateway
import vanillakotlin.http.interceptors.RetryInterceptor
import vanillakotlin.kafka.consumer.KafkaConsumer
import vanillakotlin.kafka.producer.KafkaProducer
import vanillakotlin.metrics.OtelMetrics

// see docs/configuration.md for more details

data class Config(
    val http: HttpConfig,
    val metrics: OtelMetrics.Config,
    val kafka: KafkaConfig,
) {
    data class HttpConfig(
        val server: HttpServerConfig,
        val client: ClientConfig,
    ) {
        data class HttpServerConfig(val port: Int)

        data class ClientConfig(val thing: ThingConfig) {
            data class ThingConfig(
                val gateway: ThingGateway.Config,
                val connection: ConnectionConfig,
                val retry: RetryInterceptor.Config,
            )
        }
    }

    data class KafkaConfig(
        val consumer: KafkaConsumer.Config,
        val producer: KafkaProducer.Config,
    )
}
