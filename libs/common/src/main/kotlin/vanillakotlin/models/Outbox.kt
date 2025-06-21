package vanillakotlin.models

import java.time.Instant

data class Outbox(
    val id: Long? = null,
    val messageKey: String,
    val headers: Map<String, ByteArray> = emptyMap(),
    val body: ByteArray? = null,
    val createdTs: Instant? = null,
) {
    // `==` and `!=` only performs referential equality checks for ByteArray rather than structural.
    // This is a consequence of the fact that the JVM implements `.equals()` for arrays differently than other collections.
    // Custom equals and hashcode implementations are required here to ensure duplicates are handled correctly.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Outbox

        if (id != other.id) return false
        if (messageKey != other.messageKey) return false
        if (!headers.contentEquals(other.headers)) return false
        if (body != null) {
            if (other.body == null) return false
            if (!body.contentEquals(other.body)) return false
        } else if (other.body != null) {
            return false
        }
        if (createdTs != other.createdTs) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id?.hashCode() ?: 0
        result = 31 * result + messageKey.hashCode()
        result = 31 * result + (headers.contentHashCode() ?: 0)
        result = 31 * result + (body?.contentHashCode() ?: 0)
        result = 31 * result + (createdTs?.hashCode() ?: 0)
        return result
    }

    // these perform structural equals and hashcode for maps containing ByteArray values
    private fun <T> Map<T, ByteArray>.contentEquals(other: Map<T, ByteArray>): Boolean = size == other.size && entries.all { (k, v) -> other[k].contentEquals(v) }

    private fun <T> Map<T, ByteArray>.contentHashCode(): Int = entries.fold(0) { sum, (k, v) -> 31 * (sum + k.hashCode() + v.contentHashCode()) }
}
