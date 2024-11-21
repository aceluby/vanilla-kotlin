package vanillakotlin.outboxprocessor

import io.kotest.extensions.system.withSystemProperties
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppTest {
    private var app: App? = null
    private val db by lazy { buildTestDb() }

    private val sinkTopicName = randomString()
    private val broker = if (System.getenv().containsKey("CI")) "kafka:9092" else "localhost:9092"

    @BeforeAll fun beforeAll() {
        // create random sink topic
        val adminClient = AdminClient.create(mapOf("bootstrap.servers" to broker))
        adminClient.createTopics(listOf(NewTopic(sinkTopicName, 1, 1)))

        // override some of the app configuration
        val overriddenConfiguration =
            mapOf(
                "config.override.kafka.producer.topic" to sinkTopicName,
                "config.override.outbox.pollEvery" to "1s",
            )
        withSystemProperties(overriddenConfiguration) {
            app = App()
            app?.start()
        }
    }

    @AfterAll fun afterAll() {
        app?.close()
    }

    @Test fun `health check ok`() {
        val request = Request(Method.GET, "http://localhost:${app?.httpServerPort}/health")

        val response = JavaHttpClient()(request)

        response.status shouldBe Status.OK
    }

    @Test fun `the outbox processor should pop and send successfully`() {
        // the processor should be running already because the app has been started

        val messageKey = randomString()
        val bodyString = randomString()

        // insert an outbox item
        db.withTransaction { tx -> insertOutbox(tx, buildOutbox(messageKey = messageKey).copy(body = bodyString.toByteArray())) }

        // validate the message is in the topic
        val receivedMessages =
            collectMessages(
                broker = broker,
                topic = sinkTopicName,
                filter = { kafkaMessage: KafkaMessage -> kafkaMessage.key == messageKey },
                stopWhen = { messages: MutableList<KafkaMessage> -> messages.size == 1 },
            )

        // we should have received a message
        receivedMessages.size shouldBe 1
        String(receivedMessages.first().body!!) shouldBe bodyString

        // the db row should be gone
        getRowCount(db, messageKey) shouldBe 0
    }
}
