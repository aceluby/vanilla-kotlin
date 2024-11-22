package vanillakotlin.kafka.models

import org.apache.kafka.clients.consumer.OffsetAndMetadata
import org.apache.kafka.common.TopicPartition
import vanillakotlin.kafka.provenance.Provenance
import vanillakotlin.kafka.provenance.SPAN_ID_HEADER_NAME
import java.time.Instant

typealias Partition = Int
typealias Offset = Long

data class TopicPartitionOffset(val topic: String, val partition: Int, val offset: Long) {
    fun getTopicPartition() = TopicPartition(topic, partition)

    fun getOffsetAndMetadata() = OffsetAndMetadata(offset)
}

fun interface SequenceHandler {
    fun processSequence(messages: Sequence<KafkaMessage>)
}

data class KafkaMessage(
    val broker: String,
    val topic: String,
    val key: String?,
    val partition: Partition,
    val offset: Offset,
    val headers: Map<String, ByteArray>,
    val timestamp: Long,
    val body: ByteArray?,
    val endOfBatch: Boolean = false,
) {
    fun buildProvenance(): Provenance = Provenance(
        spanId = headers[SPAN_ID_HEADER_NAME]?.let { String(it) },
        timestamp = Instant.ofEpochMilli(timestamp),
        entity = "kafka://${broker.replace(Regex("(:9092|:9093)"), "")}/$topic/$partition/$offset",
    )
}

data class KafkaOutputMessage<V>(
    val key: String?,
    val value: V?,
    // You can provide a list of provenances to be attached to the message as headers instead of manually populating them in the headers
    // the provenances are mutable to allow adding more provenances after the message is created (used in the transformer)
    val provenances: MutableList<Provenance> = mutableListOf(),
    val headers: Map<String, ByteArray> = emptyMap(),
    // if no partition or partitionKey is provided, the default partition will be used based on the message key
    val partition: Int? = null,
    val partitionKey: String? = null,
)

