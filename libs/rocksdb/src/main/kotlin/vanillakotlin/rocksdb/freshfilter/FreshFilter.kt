package vanillakotlin.rocksdb.freshfilter

import vanillakotlin.rocksdb.core.RocksDbStore
import vanillakotlin.rocksdb.core.smallValueReadHeavyConfigArchetype
import vanillakotlin.rocksdb.core.toByteArray
import vanillakotlin.rocksdb.core.toLong
import java.io.File

private val nullHash = "null".toByteArray()

interface FreshFilterable {
    val key: ByteArray
    val valueHash: ByteArray
    val timestamp: Long

    companion object {
        fun of(
            key: String,
            valueHash: ByteArray?,
            timestamp: Long,
        ): FreshFilterable = of(key.toByteArray(), valueHash, timestamp)

        fun of(
            key: ByteArray,
            valueHash: ByteArray?,
            timestamp: Long,
        ): FreshFilterable = DefaultFreshFilterable(
            key = key,
            valueHash = valueHash ?: nullHash,
            timestamp = timestamp,
        )
    }
}

private class DefaultFreshFilterable(
    override val key: ByteArray,
    override val valueHash: ByteArray,
    override val timestamp: Long,
) : FreshFilterable

enum class FilterStatus {
    NEW,
    STALE,
    REDUNDANT,
}

class FreshFilter(dataDirectory: String = "/tmp/rocks_db") : AutoCloseable {
    init {
        File(dataDirectory).deleteRecursively()
        File(dataDirectory).mkdirs()
    }

    private val rocksDbStore = RocksDbStore(
        dataDirectory = dataDirectory,
        configureOptions = smallValueReadHeavyConfigArchetype::configureOptions,
    )

    fun filter(message: FreshFilterable): FilterStatus {
        val savedBytes = rocksDbStore.get(message.key)
        if (savedBytes == null) {
            updateStorage(message)
            return FilterStatus.NEW
        }
        val savedTimestamp = savedBytes.copyOfRange(0, Long.SIZE_BYTES).toLong()

        if (savedTimestamp > message.timestamp) {
            return FilterStatus.STALE
        }

        val savedValueHash = savedBytes.takeLast(savedBytes.size - Long.SIZE_BYTES).toByteArray()

        // Check if this is the same message (same timestamp and hash)
        if (savedTimestamp == message.timestamp && message.valueHash.contentEquals(savedValueHash)) {
            return FilterStatus.REDUNDANT
        }

        // This is a new message (newer timestamp or different hash)
        updateStorage(message)
        return FilterStatus.NEW
    }

    private fun updateStorage(message: FreshFilterable) {
        val timestampBytes = message.timestamp.toByteArray()
        val bytes = timestampBytes + message.valueHash
        rocksDbStore.put(message.key, bytes)
    }

    override fun close() {
        rocksDbStore.close()
    }
}
