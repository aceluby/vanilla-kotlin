package vanillakotlin.bulkinserter

import com.fasterxml.jackson.module.kotlin.readValue
import org.slf4j.LoggerFactory
import vanillakotlin.db.repository.AddToBatch
import vanillakotlin.db.repository.RunBatch
import vanillakotlin.kafka.models.KafkaConsumerSequenceHandler
import vanillakotlin.kafka.models.KafkaMessage
import vanillakotlin.models.FavoriteItem
import vanillakotlin.serde.mapper

/**
 * This handler expects to receive a KafkaMessage whose body is a FavoriteItem.
 * It then adds the FavoriteItem to a batch and runs the batch when the end of the batch is reached.
 */
class BulkInserterHandler(
    private val addToBatch: AddToBatch,
    private val runBatch: RunBatch,
) : KafkaConsumerSequenceHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    override fun processSequence(messages: Sequence<KafkaMessage>) = messages.forEach { message ->
        // Note that this function and its input/output are not directly tied to kafka.
        // The major benefit of separating processing logic from kafka is that it is much easier to thoroughly test.
        // e.g. We can write unit tests that populate a KafkaMessage data class with the intended test values and directly call this
        // function without needing to spin up a kafka consumer and deal with its baggage.
        // We still want integration/functional tests, but we don't need as many.

        // See the `Concurrency Considerations` doc for more information on design

        // A null body means the user favorite was deleted, so we'll create a new UserFavoriteTcin with isDeleted = true to indicate to the
        // batch processor that it should delete that record
        val item = requireNotNull(message.key) { "key is required" }
        val outputBody = message.body?.let { body ->
            mapper.readValue<FavoriteItem>(body)
        } ?: FavoriteItem(
            itemIdentifier = item,
            isDeleted = true,
        )

        // Add the UserFavoriteTcin to the current batch
        addToBatch(outputBody).also { log.atDebug().log { "adding $outputBody to batch" } }
    }.also {
        // Once the end of the consumer batch has been reached, run the batch.  There is no concurrency here, so be sure that if your
        // application does have concurrency, you handle it properly.
        runBatch().also { batchSize -> log.atDebug().log { "batch successfully run, processed $batchSize records" } }
    }
}
