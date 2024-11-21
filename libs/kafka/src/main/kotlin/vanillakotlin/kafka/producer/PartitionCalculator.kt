package vanillakotlin.kafka.producer

import org.apache.kafka.common.utils.Utils

/**
 * Helper class for calculating which partition to put a message based on the partition key.
 * This is to be used when an application would like to separate out the message key from the partition key.
 * For non-compacted topics, a message key is usually all that is required to send a message, however for
 * compacted topics you will likely have a use-case where you want all messages for a specific dataset
 * (user, account, store, etc...) on the same partition, but because of the compaction need a unique key
 * for the message.
 *
 * If an app has that requirement, there is an optional `partition` on the KafkaOutput message where
 * this function could be used:
 *
 * KafkaOutputMessage<String>(
 *      key = "uniqueKey",
 *      value = "value",
 *      partition = PartitionCalculator.partitionFor("partitionKey", producer.partitionCount)
 * )
 */
class PartitionCalculator {
    companion object {
        fun partitionFor(
            partitionKey: String?,
            partitionCount: Int,
        ): Int? = partitionKey?.let { Utils.toPositive(Utils.murmur2(it.toByteArray())) % partitionCount }
    }
}
