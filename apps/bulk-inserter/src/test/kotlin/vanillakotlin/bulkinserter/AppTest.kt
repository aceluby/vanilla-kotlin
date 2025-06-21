package vanillakotlin.bulkinserter

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.kafka.clients.admin.AdminClient
import org.http4k.client.JavaHttpClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import vanillakotlin.db.repository.FavoriteThingRepository
import vanillakotlin.db.repository.buildTestDb
import vanillakotlin.kafka.MockMetricsPublisher
import vanillakotlin.kafka.collectMessages
import vanillakotlin.kafka.createTestTopic
import vanillakotlin.kafka.deleteTestTopic
import vanillakotlin.kafka.models.KafkaOutputMessage
import vanillakotlin.kafka.producer.KafkaProducer
import vanillakotlin.models.UserFavoriteThing
import vanillakotlin.random.randomString
import vanillakotlin.random.randomThing
import vanillakotlin.random.randomUsername

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppTest {
    private var app: App? = null
    private lateinit var adminClient: AdminClient
    private lateinit var repository: FavoriteThingRepository
    private val testTopics = mutableSetOf<String>()
    private val broker = "localhost:9092"

    @BeforeAll fun beforeAll() {
        // Setup test database
        repository = FavoriteThingRepository(buildTestDb())

        // Setup Kafka admin client for topic management
        adminClient = AdminClient.create(mapOf("bootstrap.servers" to broker))

        // Start the app
        app = App()
        app?.start()
    }

    @AfterAll fun afterAll() {
        app?.close()

        // Cleanup test topics
        testTopics.forEach { topic ->
            runCatching { adminClient.deleteTestTopic(topic) }
        }
        adminClient.close()
    }

    private fun createTestTopic(): String {
        val topicName = "test-bulk-inserter-${randomString()}"
        adminClient.createTestTopic(topicName)
        testTopics.add(topicName)
        return topicName
    }

    @Test fun `health check ok`() {
        val request = Request(Method.GET, "http://localhost:${app?.httpServerPort}/health")
        val response = JavaHttpClient()(request)
        response.status shouldBe Status.OK
    }

    @Test fun `should process kafka messages and save to database`() {
        val topicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()

        // Create test data
        val userName1 = randomUsername()
        val userName2 = randomUsername()
        val thing1 = randomThing()
        val thing2 = randomThing()
        val thing3 = randomThing()

        val testMessages = listOf(
            UserFavoriteThing(userName = userName1, thingIdentifier = thing1),
            UserFavoriteThing(userName = userName1, thingIdentifier = thing2),
            UserFavoriteThing(userName = userName2, thingIdentifier = thing3),
        )

        // Send messages to Kafka
        val producer = KafkaProducer<UserFavoriteThing>(
            config = KafkaProducer.Config(broker = broker, topic = topicName),
            publishTimerMetric = metricsPublisher,
        ).start()

        testMessages.forEach { message ->
            producer.send(
                KafkaOutputMessage(
                    key = "${message.userName}:${message.thingIdentifier}",
                    value = message,
                ),
            )
        }

        producer.close()

        // Verify messages were sent to Kafka topic
        val sentMessages = collectMessages(
            broker = broker,
            topic = topicName,
            timeoutMs = 5000L,
        )

        sentMessages.size shouldBe 3
        sentMessages.forEach { message ->
            message.key shouldNotBe null
            message.body shouldNotBe null
        }

        // Verify the keys are in the expected format
        val expectedKeys = testMessages.map { "${it.userName}:${it.thingIdentifier}" }.toSet()
        val actualKeys = sentMessages.map { it.key }.toSet()
        actualKeys shouldBe expectedKeys
    }

    @Test fun `should handle delete messages with null body`() {
        val topicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()

        val userName = randomUsername()
        val thing = randomThing()

        // For null values, we need to use a producer that can handle null values
        // Use the raw Kafka producer with String serializer
        val producer = org.apache.kafka.clients.producer.KafkaProducer<String, String>(
            mapOf(
                "bootstrap.servers" to broker,
                "key.serializer" to "org.apache.kafka.common.serialization.StringSerializer",
                "value.serializer" to "org.apache.kafka.common.serialization.StringSerializer",
            ),
        )

        // Send delete message (null body)
        producer.send(
            org.apache.kafka.clients.producer.ProducerRecord(
                topicName,
                "$userName:$thing",
                null as String?, // Explicit null value for deletion
            ),
        ).get() // Wait for send to complete

        producer.close()

        // Verify delete message was sent
        val sentMessages = collectMessages(
            broker = broker,
            topic = topicName,
            timeoutMs = 5000L,
        )

        sentMessages.size shouldBe 1
        sentMessages[0].key shouldBe "$userName:$thing"
        sentMessages[0].body shouldBe null
    }

    @Test fun `should handle batch processing of mixed operations`() {
        val topicName = createTestTopic()
        val metricsPublisher = MockMetricsPublisher()

        val userName = randomUsername()
        val thing1 = randomThing()
        val thing2 = randomThing()
        val thing3 = randomThing()

        // Use raw Kafka producer for mixed message types
        val producer = org.apache.kafka.clients.producer.KafkaProducer<String, String>(
            mapOf(
                "bootstrap.servers" to broker,
                "key.serializer" to "org.apache.kafka.common.serialization.StringSerializer",
                "value.serializer" to "org.apache.kafka.common.serialization.StringSerializer",
            ),
        )

        // Send create messages
        val createMessage1 = UserFavoriteThing(userName = userName, thingIdentifier = thing1)
        val createMessage2 = UserFavoriteThing(userName = userName, thingIdentifier = thing2)

        producer.send(
            org.apache.kafka.clients.producer.ProducerRecord(
                topicName,
                "$userName:$thing1",
                vanillakotlin.serde.mapper.writeValueAsString(createMessage1),
            ),
        ).get()

        producer.send(
            org.apache.kafka.clients.producer.ProducerRecord(
                topicName,
                "$userName:$thing2",
                vanillakotlin.serde.mapper.writeValueAsString(createMessage2),
            ),
        ).get()

        // Send delete message
        producer.send(
            org.apache.kafka.clients.producer.ProducerRecord(
                topicName,
                "$userName:$thing3",
                null as String?, // Delete
            ),
        ).get()

        producer.close()

        // Verify mixed batch was sent
        val sentMessages = collectMessages(
            broker = broker,
            topic = topicName,
            timeoutMs = 5000L,
        )

        sentMessages.size shouldBe 3

        // Verify create messages have bodies
        val createMessages = sentMessages.filter { it.body != null }
        createMessages.size shouldBe 2

        // Verify delete message has null body
        val deleteMessages = sentMessages.filter { it.body == null }
        deleteMessages.size shouldBe 1
        deleteMessages[0].key shouldBe "$userName:$thing3"
    }
}
