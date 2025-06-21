package vanillakotlin.kafkatransformer

import com.fasterxml.jackson.core.JsonParseException
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import vanillakotlin.kafka.models.KafkaMessage
import vanillakotlin.models.UserFavoriteThing
import vanillakotlin.random.randomInt
import vanillakotlin.random.randomLong
import vanillakotlin.random.randomString
import vanillakotlin.random.randomThing
import vanillakotlin.random.randomUsername
import vanillakotlin.serde.mapper
import java.time.Instant

class FavoriteThingsEventHandlerTest {

    @Test fun `a populated message should be enriched and forwarded`() {
        val userName = randomUsername()
        val thingIdentifier = randomThing()
        val thing = buildTestThing(thingIdentifier = thingIdentifier)
        val kafkaMessage = buildTestMessage(username = userName, thingIdentifier = thingIdentifier)
        val kafkaMessageKey = "$userName:$thingIdentifier"

        val handler =
            FavoriteThingsEventHandler(
                getThingDetails = { thing },
            )

        val result = handler.transform(kafkaMessage)
        assertSoftly(result.messages) {
            size shouldBe 1
            val outputMessage = first().kafkaOutputMessage
            outputMessage.key shouldBe kafkaMessageKey
            outputMessage.value shouldBe UserFavoriteThing(userName = userName, thingIdentifier = thingIdentifier)
            outputMessage.headers shouldNotBe null
        }
    }

    @Test fun `a message with a null body should forward a message with the same key and null body`() {
        // A message with a null body would correlate with the system deleting a user favorite.
        // The message key and any headers would still be populated, but the body would be null.

        val userName = randomUsername()
        val thingIdentifier = randomThing()
        val kafkaMessage = buildTestMessage(username = userName, thingIdentifier = thingIdentifier).copy(body = null)
        val kafkaMessageKey = "$userName:$thingIdentifier"

        val handler =
            FavoriteThingsEventHandler(
                getThingDetails = { fail("unexpected method call") },
            )

        val result = handler.transform(kafkaMessage)
        assertSoftly(result.messages) {
            size shouldBe 1
            val outputMessage = first().kafkaOutputMessage
            outputMessage.key shouldBe kafkaMessageKey
            outputMessage.value shouldBe null
            outputMessage.headers shouldNotBe null
        }
    }

    @Test fun `invalid message body should throw an exception`() {
        // This test shows fail-fast behavior - malformed JSON will cause the app to shut down
        val kafkaMessage = buildTestMessage().copy(body = "oh no".toByteArray())

        val handler =
            FavoriteThingsEventHandler(
                getThingDetails = { fail("unexpected method call") },
            )

        shouldThrow<JsonParseException> {
            handler.transform(kafkaMessage)
        }
    }

    @Test fun `null key should throw exception`() {
        val kafkaMessage = buildTestMessage().copy(key = null)

        val handler =
            FavoriteThingsEventHandler(
                getThingDetails = { fail("unexpected method call") },
            )

        shouldThrow<IllegalArgumentException> {
            handler.transform(kafkaMessage)
        }
    }

    @Test fun `malformed key should throw exception`() {
        val kafkaMessage = buildTestMessage().copy(key = "malformed-key-without-colon")

        val handler =
            FavoriteThingsEventHandler(
                getThingDetails = { fail("unexpected method call") },
            )

        shouldThrow<IllegalArgumentException> {
            handler.transform(kafkaMessage)
        }
    }

    @Test fun `empty username in key should throw exception`() {
        val kafkaMessage = buildTestMessage().copy(key = ":${randomThing()}")

        val handler =
            FavoriteThingsEventHandler(
                getThingDetails = { fail("unexpected method call") },
            )

        shouldThrow<IllegalArgumentException> {
            handler.transform(kafkaMessage)
        }
    }

    @Test fun `thing not found should throw exception`() {
        val kafkaMessage = buildTestMessage()

        val handler =
            FavoriteThingsEventHandler(
                getThingDetails = { null }, // Return null to simulate thing not found
            )

        shouldThrow<IllegalArgumentException> {
            handler.transform(kafkaMessage)
        }
    }

    private fun buildTestMessage(
        username: String = randomUsername(),
        thingIdentifier: String = randomThing(),
    ): KafkaMessage {
        return KafkaMessage(
            broker = randomString(),
            topic = randomString(),
            key = "$username:$thingIdentifier",
            partition = randomInt(1..3),
            offset = randomLong(),
            headers = emptyMap(),
            timestamp = Instant.now().toEpochMilli(),
            body = mapper.writeValueAsBytes(thingIdentifier),
        )
    }
}
