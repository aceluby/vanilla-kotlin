package vanillakotlin.outboxprocessor

import com.target.liteforjdbc.DbConfig
import vanillakotlin.kafka.producer.KafkaProducer
import vanillakotlin.metrics.OtelMetrics

// This file includes the data classes needed to define the configuration for the app.
// see /docs/configuration.md for more details

data class Config(
    val http: HttpConfig,
    val metrics: OtelMetrics.Config,
    val db: DbConfig,
    val kafka: KafkaConfig,
    val outbox: OutboxProcessor.Config,
) {
    data class HttpConfig(
        val server: HttpServerConfig,
    ) {
        data class HttpServerConfig(
            val port: Int,
        )
    }

    data class KafkaConfig(
        val producer: KafkaProducer.Config,
    )
}


