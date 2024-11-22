package vanillakotlin.kafka.transformer

import vanillakotlin.kafka.models.KafkaMessage
import vanillakotlin.kafka.models.KafkaOutputMessage

fun interface TransformerEventHandler<V> {
    fun transform(kafkaMessage: KafkaMessage): TransformerMessages<V>
}

data class TransformerMessage<V>(
    val kafkaOutputMessage: KafkaOutputMessage<V>,
    val metricTags: Map<String, String> = emptyMap(),
)

sealed class TransformerMessages<V>(val messages: List<TransformerMessage<V>>) {
    class Single<V>(
        kafkaOutputMessage: KafkaOutputMessage<V>,
        metricTags: Map<String, String> = emptyMap(),
    ) : TransformerMessages<V>(listOf(TransformerMessage(kafkaOutputMessage, metricTags)))

    class Multiple<V>(messages: List<TransformerMessage<V>>) : TransformerMessages<V>(messages)

    class Dropped<V>(metricTags: Map<String, String> = emptyMap()) :
        TransformerMessages<V>(listOf(TransformerMessage(KafkaOutputMessage(null, null), metricTags)))
}
