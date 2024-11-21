package vanillakotlin.bulkinserter


// see docs/configuration.md for more details

data class Config(
    val http: HttpConfig,
    val metrics: OtelMetricsPublisher.Config,
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
