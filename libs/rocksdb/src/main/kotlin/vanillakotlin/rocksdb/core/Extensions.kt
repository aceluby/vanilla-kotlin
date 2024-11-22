package vanillakotlin.rocksdb.core

import org.rocksdb.RocksIterator
import java.nio.ByteBuffer
import vanillakotlin.rocksdb.core.map as disposableMap

internal fun RocksIterator.getPrefixed(prefix: ByteArray): Disposable<Sequence<KeyValuePair>> =
    Disposable.from(this).disposableMap { iterator ->
        sequence {
            iterator.seek(prefix)

            while (iterator.isValid) {
                val key = iterator.key()
                if (!key.startsWith(prefix)) {
                    break
                }

                yield(KeyValuePair(key, iterator.value()))
                iterator.next()
            }
        }
    }

internal fun RocksIterator.getRange(
    startKey: ByteArray,
    endKey: ByteArray,
): Disposable<Sequence<KeyValuePair>> =
    Disposable.from(this).disposableMap { iterator ->
        sequence {
            iterator.seek(startKey)
            var key = iterator.key()

            while (iterator.isValid && key <= endKey) {
                yield(KeyValuePair(key, iterator.value()))
                iterator.next()
                key = iterator.key()
            }
        }
    }

internal fun RocksIterator.sequence(): Disposable<Sequence<KeyValuePair>> =
    Disposable.from(this).disposableMap { iterator ->
        sequence {
            iterator.seekToFirst()

            while (iterator.isValid) {
                val key = iterator.key()
                yield(KeyValuePair(key, iterator.value()))
                iterator.next()
            }
        }
    }

fun ByteBuffer.size() = this.remaining()

fun ByteArray.startsWith(other: ByteArray): Boolean {
    if (other.size > this.size) {
        return false
    }

    for (i in other.indices) {
        if (this[i] != other[i]) {
            return false
        }
    }

    return true
}

operator fun ByteArray?.compareTo(other: ByteArray?): Int {
    @Suppress("ReplaceCallWithBinaryOperator")
    if ((this == null && other == null) || (this != null && this.equals(other))) {
        return 0
    }

    if (this == null) {
        return -1
    } else if (other == null) {
        return 1
    }

    val last = kotlin.math.min(this.size, other.size)
    for (i in 0 until last) {
        val v1 = this[i]
        val v2 = other[i]

        if (v1 == v2) {
            continue
        }

        val comp = v1.compareTo(v2)
        if (comp != 0) {
            return comp
        }
    }

    if (this.size < other.size) {
        return -1
    } else if (this.size > other.size) {
        return 1
    }

    return 0
}

fun Long.toByteArray(): ByteArray = ByteBuffer.allocate(Long.SIZE_BYTES).putLong(this).array()

fun ByteArray.toLong(): Long = ByteBuffer.wrap(this).long
