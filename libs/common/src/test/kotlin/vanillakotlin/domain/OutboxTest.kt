package vanillakotlin.domain

import io.kotest.matchers.equals.shouldBeEqual
import org.junit.jupiter.api.Test
import vanillakotlin.models.Outbox
import vanillakotlin.random.randomByteArray
import vanillakotlin.random.randomString

fun randomOutbox(): Outbox = Outbox(
    messageKey = randomString(),
    headers = mapOf("header1" to randomByteArray(), "header2" to randomByteArray()),
    body = randomByteArray(),
)

class OutboxTest {

    @Test fun equals() {
        val outbox = randomOutbox()

        val other =
            outbox.copy(
                headers = outbox.headers.mapValues { (_, v) -> v.copyOf() },
                body = outbox.body?.copyOf(),
            )

        outbox shouldBeEqual other
    }

    @Test fun hashcode() {
        val outbox = randomOutbox()

        val other =
            outbox.copy(
                headers = outbox.headers.mapValues { (_, v) -> v.copyOf() },
                body = outbox.body?.copyOf(),
            )

        outbox.hashCode() shouldBeEqual other.hashCode()
    }
}
