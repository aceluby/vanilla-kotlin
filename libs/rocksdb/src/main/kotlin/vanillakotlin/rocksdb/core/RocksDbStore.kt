package vanillakotlin.rocksdb.core

import org.rocksdb.ColumnFamilyOptions
import org.rocksdb.DBOptions
import org.rocksdb.Options
import org.rocksdb.RocksDB
import org.rocksdb.Statistics
import org.rocksdb.WriteBatch
import org.rocksdb.WriteOptions

data class KeyValuePair(
    val key: ByteArray,
    val value: ByteArray?,
)

class RocksDbStore(
    private val dataDirectory: String,
    private val configureOptions: (options: Options) -> Unit = {},
    private val defaultWriteOptions: WriteOptions? = null,
) : AutoCloseable {
    val db: RocksDB
    val options: Options
    private var writeOptions: WriteOptions

    init {
        RocksDB.loadLibrary()

        ColumnFamilyOptions().use { cfOpts ->
            options = Options(DBOptions(), cfOpts).apply {
                setCreateIfMissing(true)
                setCreateMissingColumnFamilies(true)
                setStatistics(Statistics())
            }
            configureOptions(options)

            writeOptions = defaultWriteOptions ?: defaultWriteOptions()

            db = RocksDB.open(options, dataDirectory)
        }
    }

    private fun defaultWriteOptions(): WriteOptions = WriteOptions().apply { setDisableWAL(true) }

    fun write(writeBatch: WriteBatch) = db.write(writeOptions, writeBatch)

    fun put(
        key: ByteArray,
        value: ByteArray,
    ) = db.put(writeOptions, key, value)

    fun get(key: ByteArray): ByteArray? = db.get(key)

    fun delete(key: ByteArray) = db.delete(key)

    fun withRange(
        start: ByteArray,
        end: ByteArray,
        block: (sequence: Sequence<KeyValuePair>) -> Unit,
    ) = db.newIterator().getRange(start, end).use { disposable ->
        block(disposable.value())
    }

    fun withPrefixed(
        start: ByteArray,
        block: (sequence: Sequence<KeyValuePair>) -> Unit,
    ) = db.newIterator().getPrefixed(start).use { disposable ->
        block(disposable.value())
    }

    fun withSequence(block: (sequence: Sequence<KeyValuePair>) -> Unit) {
        db.newIterator().sequence().use { disposable ->
            block(disposable.value())
        }
    }

    fun enableAutoCompaction() {
        db.enableAutoCompaction(listOf(db.defaultColumnFamily))
    }

    fun compact() {
        db.compactRange()
    }

    override fun close() {
        db.close()
    }
}
