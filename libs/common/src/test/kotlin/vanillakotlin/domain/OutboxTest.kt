package vanillakotlin.domain

import io.kotest.matchers.equals.shouldBeEqual
import org.junit.jupiter.api.Test

class OutboxTest {

    @Test fun equals() {
        val outbox = randomOutbox()

        val other =
            outbox.copy(
                headers = outbox.headers.mapValues { (_, v) -> v.clone() },
                body = outbox.body?.clone(),
            )

        outbox shouldBeEqual other
    }

    @Test fun hashcode() {
        val outbox = randomOutbox()

        val other =
            outbox.copy(
                headers = outbox.headers.mapValues { (_, v) -> v.clone() },
                body = outbox.body?.clone(),
            )

        outbox.hashCode() shouldBeEqual other.hashCode()
    }
}
