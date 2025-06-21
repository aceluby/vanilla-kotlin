package vanillakotlin.bulkinserter

import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import vanillakotlin.db.repository.AddFavoriteThingToBatch
import vanillakotlin.db.repository.RunFavoriteThingBatch
import vanillakotlin.kafka.models.KafkaConsumerSequenceHandler
import vanillakotlin.kafka.models.KafkaMessage
import vanillakotlin.models.FavoriteThing
import vanillakotlin.models.UserFavoriteThing
import vanillakotlin.serde.mapper

/**
 * This handler expects to receive a KafkaMessage whose body is a UserFavoriteThing.
 * It then converts it to a FavoriteThing and adds it to a batch, running the batch when the end of the batch is reached.
 *
 * This handler will fail fast on any errors - malformed messages, null keys, or JSON parsing errors will cause the app to shut down.
 */
class BulkInserterHandler(
    private val addToBatch: AddFavoriteThingToBatch,
    private val runBatch: RunFavoriteThingBatch,
) : KafkaConsumerSequenceHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun processSequence(messages: Sequence<KafkaMessage>) = messages.forEach { message ->
        // Note that this function and its input/output are not directly tied to kafka.
        // The major benefit of separating processing logic from kafka is that it is much easier to thoroughly test.
        // e.g. We can write unit tests that populate a KafkaMessage data class with the intended test values and directly call this
        // function without needing to spin up a kafka consumer and deal with its baggage.
        // We still want integration/functional tests, but we don't need as many.

        // See the `Concurrency Considerations` doc for more information on design

        processMessage(message)
    }.also {
        // Once the end of the consumer batch has been reached, run the batch.  There is no concurrency here, so be sure that if your
        // application does have concurrency, you handle it properly.
        runBatch().also { batchSize -> log.atDebug().log { "batch successfully run, processed $batchSize records" } }
    }

    private fun processMessage(message: KafkaMessage) {
        // Require valid key - app will fail if key is null or blank
        val key = requireNotNull(message.key) { "Message key is required but was null" }
        require(key.isNotBlank()) { "Message key is required but was blank" }

        // Extract thingIdentifier from key format "username:thingIdentifier"
        // App will fail if key doesn't contain a colon
        require(key.contains(":")) { "Message key '$key' must be in format 'username:thingIdentifier'" }

        val thingIdentifier = key.substringAfterLast(":") // Use substringAfterLast to handle multiple colons
        require(thingIdentifier.isNotBlank()) { "Thing identifier cannot be blank in key '$key'" }

        // A null body means the user favorite was deleted, so we'll create a new FavoriteThing with isDeleted = true to indicate to the
        // batch processor that it should delete that record
        val favoriteThing = message.body?.let { body ->
            // Parse JSON - will throw exception if malformed, causing app to fail
            val userFavoriteThing = mapper.readValue<UserFavoriteThing>(body)
            FavoriteThing(
                thingIdentifier = userFavoriteThing.thingIdentifier,
                isDeleted = userFavoriteThing.isDeleted,
            )
        } ?: FavoriteThing(
            thingIdentifier = thingIdentifier,
            isDeleted = true,
        )

        // Add the FavoriteThing to the current batch
        addToBatch(favoriteThing).also { log.atDebug().log { "adding $favoriteThing to batch" } }
    }
}
