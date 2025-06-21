package vanillakotlin.kafka.models

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import vanillakotlin.kafka.provenance.Provenance
import vanillakotlin.kafka.provenance.SPAN_ID_HEADER_NAME
import java.time.Instant

class ModelsTest {

    @Test fun `should create TopicPartitionOffset with correct values`() {
        val topic = "test-topic"
        val partition = 2
        val offset = 100L

        val tpo = TopicPartitionOffset(topic, partition, offset)

        assertEquals(topic, tpo.topic)
        assertEquals(partition, tpo.partition)
        assertEquals(offset, tpo.offset)
    }

    @Test fun `should convert TopicPartitionOffset to TopicPartition`() {
        val tpo = TopicPartitionOffset("test-topic", 1, 50L)

        val topicPartition = tpo.getTopicPartition()

        assertEquals("test-topic", topicPartition.topic())
        assertEquals(1, topicPartition.partition())
    }

    @Test fun `should convert TopicPartitionOffset to OffsetAndMetadata`() {
        val tpo = TopicPartitionOffset("test-topic", 1, 75L)

        val offsetAndMetadata = tpo.getOffsetAndMetadata()

        assertEquals(75L, offsetAndMetadata.offset())
    }

    @Test fun `should create KafkaMessage with all properties`() {
        val broker = "localhost:9092"
        val topic = "test-topic"
        val key = "test-key"
        val partition = 0
        val offset = 123L
        val headers = mapOf("header1" to "value1".toByteArray())
        val timestamp = System.currentTimeMillis()
        val body = "test-body".toByteArray()
        val endOfBatch = true

        val kafkaMessage = KafkaMessage(
            broker = broker,
            topic = topic,
            key = key,
            partition = partition,
            offset = offset,
            headers = headers,
            timestamp = timestamp,
            body = body,
            endOfBatch = endOfBatch,
        )

        assertEquals(broker, kafkaMessage.broker)
        assertEquals(topic, kafkaMessage.topic)
        assertEquals(key, kafkaMessage.key)
        assertEquals(partition, kafkaMessage.partition)
        assertEquals(offset, kafkaMessage.offset)
        assertEquals(headers, kafkaMessage.headers)
        assertEquals(timestamp, kafkaMessage.timestamp)
        assertEquals(body, kafkaMessage.body)
        assertEquals(endOfBatch, kafkaMessage.endOfBatch)
    }

    @Test fun `should build provenance from KafkaMessage`() {
        val spanId = "test-span-id"
        val timestamp = System.currentTimeMillis()
        val headers = mapOf(SPAN_ID_HEADER_NAME to spanId.toByteArray())

        val kafkaMessage = KafkaMessage(
            broker = "localhost:9092",
            topic = "test-topic",
            key = "test-key",
            partition = 1,
            offset = 456L,
            headers = headers,
            timestamp = timestamp,
            body = "test-body".toByteArray(),
        )

        val provenance = kafkaMessage.buildProvenance()

        assertEquals(spanId, provenance.spanId)
        assertEquals(Instant.ofEpochMilli(timestamp), provenance.timestamp)
        assertEquals("kafka://localhost/test-topic/1/456", provenance.entity)
    }

    @Test fun `should build provenance without span ID header`() {
        val timestamp = System.currentTimeMillis()

        val kafkaMessage = KafkaMessage(
            broker = "kafka-cluster:9093",
            topic = "another-topic",
            key = "another-key",
            partition = 2,
            offset = 789L,
            headers = emptyMap(),
            timestamp = timestamp,
            body = "another-body".toByteArray(),
        )

        val provenance = kafkaMessage.buildProvenance()

        assertEquals(null, provenance.spanId)
        assertEquals(Instant.ofEpochMilli(timestamp), provenance.timestamp)
        assertEquals("kafka://kafka-cluster/another-topic/2/789", provenance.entity)
    }

    @Test fun `should normalize broker URL in provenance entity`() {
        val kafkaMessage = KafkaMessage(
            broker = "broker.example.com:9092",
            topic = "normalized-topic",
            key = "normalized-key",
            partition = 0,
            offset = 100L,
            headers = emptyMap(),
            timestamp = System.currentTimeMillis(),
            body = "normalized-body".toByteArray(),
        )

        val provenance = kafkaMessage.buildProvenance()

        assertEquals("kafka://broker.example.com/normalized-topic/0/100", provenance.entity)
    }

    @Test fun `should create KafkaOutputMessage with minimal properties`() {
        val key = "output-key"
        val value = "output-value"

        val outputMessage = KafkaOutputMessage(
            key = key,
            value = value,
        )

        assertEquals(key, outputMessage.key)
        assertEquals(value, outputMessage.value)
        assertTrue(outputMessage.provenances.isEmpty())
        assertTrue(outputMessage.headers.isEmpty())
        assertEquals(null, outputMessage.partition)
        assertEquals(null, outputMessage.partitionKey)
    }

    @Test fun `should create KafkaOutputMessage with all properties`() {
        val key = "full-key"
        val value = "full-value"
        val provenance = Provenance(
            spanId = "test-span",
            timestamp = Instant.now(),
            entity = "test-entity",
        )
        val headers = mapOf("custom-header" to "custom-value".toByteArray())
        val partition = 3
        val partitionKey = "partition-key"

        val outputMessage = KafkaOutputMessage(
            key = key,
            value = value,
            provenances = mutableListOf(provenance),
            headers = headers,
            partition = partition,
            partitionKey = partitionKey,
        )

        assertEquals(key, outputMessage.key)
        assertEquals(value, outputMessage.value)
        assertEquals(1, outputMessage.provenances.size)
        assertEquals(provenance, outputMessage.provenances[0])
        assertEquals(headers, outputMessage.headers)
        assertEquals(partition, outputMessage.partition)
        assertEquals(partitionKey, outputMessage.partitionKey)
    }

    @Test fun `should handle null values in KafkaOutputMessage`() {
        val outputMessage = KafkaOutputMessage<String?>(
            key = null,
            value = null,
        )

        assertEquals(null, outputMessage.key)
        assertEquals(null, outputMessage.value)
        assertTrue(outputMessage.provenances.isEmpty())
        assertTrue(outputMessage.headers.isEmpty())
    }

    @Test fun `should allow mutable provenances list`() {
        val outputMessage = KafkaOutputMessage(
            key = "mutable-key",
            value = "mutable-value",
        )

        assertTrue(outputMessage.provenances.isEmpty())

        val provenance1 = Provenance(
            spanId = "span-1",
            timestamp = Instant.now(),
            entity = "entity-1",
        )
        val provenance2 = Provenance(
            spanId = "span-2",
            timestamp = Instant.now(),
            entity = "entity-2",
        )

        outputMessage.provenances.add(provenance1)
        outputMessage.provenances.add(provenance2)

        assertEquals(2, outputMessage.provenances.size)
        assertEquals(provenance1, outputMessage.provenances[0])
        assertEquals(provenance2, outputMessage.provenances[1])
    }

    @Test fun `should handle KafkaMessage with null body`() {
        val kafkaMessage = KafkaMessage(
            broker = "localhost:9092",
            topic = "null-body-topic",
            key = "null-body-key",
            partition = 0,
            offset = 1L,
            headers = emptyMap(),
            timestamp = System.currentTimeMillis(),
            body = null,
        )

        assertEquals(null, kafkaMessage.body)

        // Should still be able to build provenance
        val provenance = kafkaMessage.buildProvenance()
        assertNotNull(provenance)
        assertEquals("kafka://localhost/null-body-topic/0/1", provenance.entity)
    }

    @Test fun `should handle KafkaMessage with null key`() {
        val kafkaMessage = KafkaMessage(
            broker = "localhost:9092",
            topic = "null-key-topic",
            key = null,
            partition = 1,
            offset = 2L,
            headers = emptyMap(),
            timestamp = System.currentTimeMillis(),
            body = "body-content".toByteArray(),
        )

        assertEquals(null, kafkaMessage.key)
        assertEquals("body-content", String(kafkaMessage.body ?: fail { "kafkaMessage.body should not be null" }))
    }

    @Test fun `should handle default endOfBatch value`() {
        val kafkaMessage = KafkaMessage(
            broker = "localhost:9092",
            topic = "default-batch-topic",
            key = "batch-key",
            partition = 0,
            offset = 1L,
            headers = emptyMap(),
            timestamp = System.currentTimeMillis(),
            body = "batch-body".toByteArray(),
        )

        assertEquals(false, kafkaMessage.endOfBatch, "Default endOfBatch should be false")
    }
}
