package vanillakotlin.extensions

import io.github.resilience4j.timelimiter.TimeLimiter
import io.kotest.assertions.throwables.shouldThrowExactly
import io.kotest.matchers.comparables.shouldBeLessThan
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeoutException
import kotlin.system.measureTimeMillis

private const val SUCCESS: Int = 1
const val TEN_MILLIS = 10L
const val TEN_SECONDS = 10_000L

fun Long.sleep() = Thread.sleep(this)

class TimeLimiterExtensionsTest {
    private fun takesTenSeconds(): Int {
        TEN_SECONDS.sleep()
        return SUCCESS
    }

    private fun takesTenMillis(): Int {
        TEN_MILLIS.sleep()
        return SUCCESS
    }

    @Test fun `time limiter should timeout if it takes too long`() {
        measureTimeMillis {
            shouldThrowExactly<TimeoutException> {
                val timeLimiter: TimeLimiter = TimeLimiter.of(10.milliseconds)
                timeLimiter.attempt { takesTenSeconds() }
            }
        } shouldBeLessThan TEN_SECONDS
    }

    @Test fun `time limiter should return value if quick enough`() {
        measureTimeMillis {
            val timeLimiter: TimeLimiter = TimeLimiter.of(10.seconds)
            timeLimiter.attempt { takesTenMillis() } shouldBe SUCCESS
        } shouldBeLessThan TEN_SECONDS
    }

    @Test fun `attempt with defaults succeeds`() {
        measureTimeMillis {
            attempt { takesTenMillis() } shouldBe SUCCESS
        } shouldBeLessThan TEN_SECONDS
    }
}
