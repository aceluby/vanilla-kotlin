package vanillakotlin.kafkatransformer

import com.fasterxml.jackson.module.kotlin.readValue
import vanillakotlin.http.clients.item.GetItemDetails
import org.slf4j.LoggerFactory

/**
 * This handler expects to receive a KafkaMessage whose body is a UserFavoriteTcin.
 * It then gets additional item information and publishes a message to another topic with the augmented data
 */
class FavoriteItemsEventHandler(
    private val getItemDetails: GetItemDetails,
) : TransformerEventHandler<UserFavoriteItem> {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun transform(kafkaMessage: KafkaMessage): TransformerMessages<UserFavoriteItem> {
        // Note that this function and its input/output are not directly tied to kafka.
        // The major benefit of separating processing logic from kafka is that it is much easier to thoroughly test.
        // e.g. We can write unit tests that populate a KafkaMessage data class with the intended test values and directly call this
        // function without needing to spin up a kafka consumer and deal with its baggage.
        // We still want integration/functional tests, but we don't need as many.

        // See the `Concurrency Considerations` doc for more information on design

        // A null body means the user favorite was deleted. Send a message downstream with the same pattern.
        val outputBody =
            kafkaMessage.body?.let { body ->
                val userFavoriteTcin = mapper.readValue<UserFavoriteTcin>(body)
                getItemDetails(userFavoriteTcin.tcin)?.let { item: Item ->
                    UserFavoriteItem(userName = userFavoriteTcin.userName, item = item)
                }
            }

        // Here, we are returning a single message to be published to the next topic. If we wanted
        // to publish multiple messages, we would return a TransformerMessages.Multiple object, and if
        // we wanted to drop the message we would return a TransformerMessages.Dropped object. Once returned,
        // the KafkaTransformer will handle sending the message downstream in the order it was consumed.
        // Committing the offset is also handled by the KafkaTransformer and done periodically every 2 seconds.
        return TransformerMessages.Single(
            kafkaOutputMessage =
                KafkaOutputMessage(
                    key = kafkaMessage.key,
                    value = outputBody,
                ),
        ).also { log.atDebug().withEventId(kafkaMessage.key ?: "null").log("Processed event") }
    }
}
