package vanillakotlin.rocksdb.core

import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean

// This allows for cleanup (ie. close) logic to be implemented
// separately from a related value
interface Disposable<out T> : Closeable {
    companion object {
        fun <T> from(value: T): Disposable<T> = NoOpDisposable(value)

        fun <T> from(
            value: T,
            onClose: () -> Unit,
        ): Disposable<T> = WrappedDisposable(value, onClose)

        fun <T : Closeable> from(value: T) = from(value) { value.close() }

        fun <T : AutoCloseable> from(value: T) = from(value) { value.close() }
    }

    fun value(): T
}

private sealed class SafeDisposable<T> : Disposable<T> {
    private val hasClosed = AtomicBoolean(false)

    protected fun isClosed() = hasClosed.get()

    protected fun safeClose(block: () -> Unit) {
        val shouldClose = hasClosed.compareAndSet(false, true)

        if (shouldClose) {
            block()
        }
    }
}

private class WrappedDisposable<T>(private val wrapped: T, private val onClose: () -> Unit) : SafeDisposable<T>() {
    override fun value() = wrapped

    override fun close() = safeClose { onClose() }
}

private class NoOpDisposable<T>(private val wrapped: T) : Disposable<T> {
    override fun value() = wrapped

    override fun close() = Unit
}

inline fun <T, U> Disposable<T>.map(selector: (T) -> U) = Disposable.from(selector(value())) { this.close() }
