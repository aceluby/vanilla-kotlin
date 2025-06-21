package vanillakotlin.bulkinserter

import vanillakotlin.db.DbConfig
import vanillakotlin.kafka.consumer.KafkaConsumer
import vanillakotlin.metrics.OtelMetrics

data class Config(
    val http: HttpConfig,
    val metrics: OtelMetrics.Config,
    val kafka: KafkaConfig,
    val db: DbConfig,
) {
    data class HttpConfig(
        val server: HttpServerConfig,
    ) {
        data class HttpServerConfig(
            val port: Int,
        )
    }

    data class KafkaConfig(
        val consumer: KafkaConsumer.Config,
    )
}
