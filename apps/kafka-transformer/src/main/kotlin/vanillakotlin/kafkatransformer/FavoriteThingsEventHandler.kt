package vanillakotlin.kafkatransformer

import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import vanillakotlin.http.clients.thing.GetThingDetails
import vanillakotlin.kafka.models.KafkaMessage
import vanillakotlin.kafka.models.KafkaOutputMessage
import vanillakotlin.kafka.transformer.TransformerEventHandler
import vanillakotlin.kafka.transformer.TransformerMessages
import vanillakotlin.models.UserFavoriteThing
import vanillakotlin.serde.mapper

/**
 * This handler expects to receive a KafkaMessage whose body is a ThingIdentifier string.
 * It then gets additional thing information and publishes a message to another topic with the augmented data.
 * Follows fail-fast architecture - any errors will cause the app to shut down.
 */
class FavoriteThingsEventHandler(
    private val getThingDetails: GetThingDetails,
) : TransformerEventHandler<UserFavoriteThing> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun transform(kafkaMessage: KafkaMessage): TransformerMessages<UserFavoriteThing> {
        // Note that this function and its input/output are not directly tied to kafka.
        // The major benefit of separating processing logic from kafka is that it is much easier to thoroughly test.
        // e.g. We can write unit tests that populate a KafkaMessage data class with the intended test values and directly call this
        // function without needing to spin up a kafka consumer and deal with its baggage.
        // We still want integration/functional tests, but we don't need as many.

        // See the `Concurrency Considerations` doc for more information on design

        // Always validate key format first - fail fast if invalid format
        val key = requireNotNull(kafkaMessage.key) { "Message key is required but was null" }
        require(key.contains(":")) { "Message key '$key' must be in format 'username:thingIdentifier'" }
        val userName = key.substringBeforeLast(":")
        require(userName.isNotBlank()) { "Username cannot be blank in key '$key'" }

        // A null body means the user favorite was deleted. Send a message downstream with the same pattern.
        val outputBody = kafkaMessage.body?.let { body ->
            // Parse the thingIdentifier from the message body - will fail fast if invalid JSON
            val thingIdentifier = mapper.readValue<String>(body)

            // Get thing details - will fail fast if service call fails
            val thing = getThingDetails(thingIdentifier)

            // Require that we got thing details - fail fast if thing not found
            requireNotNull(thing) { "Thing details not found for identifier: $thingIdentifier" }

            UserFavoriteThing(
                userName = userName,
                thingIdentifier = thing.id,
            )
        }

        // Here, we are returning a single message to be published to the next topic. If we wanted
        // to publish multiple messages, we would return a TransformerMessages.Multiple object, and if
        // we wanted to drop the message we would return a TransformerMessages.Dropped object. Once returned,
        // the KafkaTransformer will handle sending the message downstream in the order it was consumed.
        // Committing the offset is also handled by the KafkaTransformer and done periodically every 2 seconds.
        return TransformerMessages.Single(
            kafkaOutputMessage = KafkaOutputMessage(
                key = kafkaMessage.key,
                value = outputBody,
            ),
        ).also { log.atDebug().log("Processed event") }
    }
}
