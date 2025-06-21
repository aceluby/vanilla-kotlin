package vanillakotlin.kafka.provenance

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant
import java.util.UUID

const val SPAN_ID_HEADER_NAME = "span_id"
const val PROVENANCES_HEADER_NAME = "provenances"

/**
 * Represents the provenance of a message.
 * @property spanId Unique identifier for the provenance if provided. e.g. 68B20473-0CE5-4650-A0EE-EB4477EAAE67
 * @property timestamp Source timestamp, ideally the business event timestamp if it exists, or the message creation timestamp if no
 * business timestamp exists. e.g. 1622464871000
 * @property entity the entity that this provenance is associated with. e.g. a Kafka message coordinate, or a DB row coordinate
 */
data class Provenance @JsonCreator constructor(
    @JsonProperty("spanId") val spanId: String? = null,
    @JsonProperty("timestamp") val timestamp: Instant,
    @JsonProperty("entity") val entity: String,
)

fun generateSpanId(): String = UUID.randomUUID().toString()
