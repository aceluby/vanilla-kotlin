package vanillakotlin.kafkatransformer

import com.fasterxml.jackson.core.JsonParseException
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import java.time.Instant

class FavoriteItemsEventHandlerTest {

    @Test fun `a populated message should be enriched and forwarded`() {
        val userName = randomUsername()
        val item = randomTcin()
        val item = buildTestItem(itemIdentifier = item)
        val kafkaMessage = buildTestMessage(username = userName, itemIdentifier = item)
        val kafkaMesageKey = "$userName:$item"

        val handler =
            FavoriteItemsEventHandler(
                getItemDetails = { item },
            )

        assertSoftly(handler.transform(kafkaMessage).messages) {
            size shouldBe 1
            val outputMessage = first().kafkaOutputMessage
            outputMessage.key shouldBe kafkaMesageKey
            outputMessage.value shouldBe UserFavoriteItem(userName = userName, item = item)
            outputMessage.headers shouldNotBe null
        }
    }

    @Test fun `a message with a null body should forward a message with the same key and null body`() {
        // A message with a null body would correlate with the system deleting a user favorite.
        // The message key and any headers would still be populated, but the body would be null.

        val userName = randomUsername()
        val item = randomTcin()
        val kafkaMessage = buildTestMessage(username = userName, itemIdentifier = item).copy(body = null)
        val kafkaMesageKey = "$userName:$item"

        val handler =
            FavoriteItemsEventHandler(
                getItemDetails = { fail("unexpected method call") },
            )

        assertSoftly(handler.transform(kafkaMessage).messages) {
            size shouldBe 1
            val outputMessage = first().kafkaOutputMessage
            outputMessage.key shouldBe kafkaMesageKey
            outputMessage.value shouldBe null
            outputMessage.headers shouldNotBe null
        }
    }

    @Test fun `invalid message body should throw an exception`() {
        // This test shows one way of handling exceptions, but what you should do depends on the context.
        // You might want to catch message body parsing issues and do something else with them, like drop the message and
        // emit an error metric. It depends on your usage scenario, like whether you can afford to stop processing and fix the issue,
        // or if you need to keep processing and use a dead letter queue or something else.
        val kafkaMessage = buildTestMessage().copy(body = "oh no".toByteArray())

        val handler =
            FavoriteItemsEventHandler(
                getItemDetails = { fail("unexpected method call") },
            )

        shouldThrow<JsonParseException> {
            handler.transform(kafkaMessage)
        }
    }

    private fun buildTestMessage(
        username: String = randomUsername(),
        itemIdentifier: ItemIdentifier = randomTcin(),
    ): KafkaMessage {
        return KafkaMessage(
            broker = randomString(),
            topic = randomString(),
            key = "$username:$itemIdentifier",
            partition = randomInt(1..3),
            offset = randomLong(),
            headers = emptyMap(),
            timestamp = Instant.now().toEpochMilli(),
            body = mapper.writeValueAsBytes(UserFavoriteTcin(userName = username, itemIdentifier = itemIdentifier)),
        )
    }
}
