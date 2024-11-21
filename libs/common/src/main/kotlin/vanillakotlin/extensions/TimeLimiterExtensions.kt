package vanillakotlin.extensions

import io.github.resilience4j.timelimiter.TimeLimiter
import java.lang.Thread.sleep
import java.time.Duration
import java.util.concurrent.CompletableFuture

inline fun <R> attempt(
    duration: Duration =
        Duration.ofSeconds(
            10L,
        ),
    crossinline block: () -> R,
): R = TimeLimiter.of(duration).attempt(block)

inline fun <R> TimeLimiter.attempt(crossinline block: () -> R): R = executeFutureSupplier { CompletableFuture.supplyAsync { block() } }

// mostly useful in tests, the thing is expected to fail sometimes (like grabbing a port) but we want to retry
inline fun <R> eventually(
    duration: Duration = Duration.ofSeconds(10),
    delay: Long = 1,
    crossinline block: () -> R,
): R = attempt(duration) { untilErrorFree(block, delay) }

inline fun <R> untilErrorFree(
    crossinline block: () -> R,
    delay: Long,
): R {
    while (true) {
        try {
            return block()
        } catch (ex: Throwable) {
            // errors are "expected" just sleep a bit
            sleep(delay)
        }
    }
}
