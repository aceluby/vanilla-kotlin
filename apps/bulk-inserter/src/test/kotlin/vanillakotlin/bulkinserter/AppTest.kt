package vanillakotlin.bulkinserter

import io.kotest.assertions.assertSoftly
import io.kotest.extensions.system.withSystemProperties
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppTest {
    private var app: App? = null
    private val sourceTopicName = randomString()
    private val broker = if (System.getenv().containsKey("CI")) "kafka:9092" else "localhost:9092"
    private val tcin = randomTcin()

    @BeforeAll fun beforeAll() {
        // create random source and sink topic
        val adminClient = AdminClient.create(mapOf("bootstrap.servers" to broker))
        adminClient.createTestTopic(sourceTopicName, 1)

        // override some of the app configuration
        val overriddenConfiguration =
            mapOf(
                "config.override.kafka.consumer.topic" to sourceTopicName,
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

    @Test fun `the bulk inserter should consume, process, and insert the message successfully`() {
        // The beforeAll test setup function created a new source and sink topic, and configured the application to use those.
        // So at this point, the kafkatransformer's event handler should be running and waiting for a message.

        val metricsPublisher = MockMetricsPublisher()

        // produce a message on source topic
        val producer =
            KafkaProducer<UserFavoriteTcin>(
                config =
                    KafkaProducer.Config(
                        broker = broker,
                        topic = sourceTopicName,
                    ),
                publishTimerMetric = metricsPublisher::publishTimerMetric,
            )
        producer.start()

        val userName = randomUsername()
        val userFavoriteTcin = UserFavoriteTcin(userName = userName, itemIdentifier = tcin)

        producer.send(
            KafkaOutputMessage(
                key = "$userName:$tcin",
                value = userFavoriteTcin,
            ),
        )

        // use the database connection in the app itself to query the db to ensure the record was inserted
        eventually {
            assertSoftly(requireNotNull(app?.repository?.findByUserName(userName))) {
                size shouldBe 1
                first().userName shouldBe userName
                first().tcin shouldBe tcin
            }
        }
    }
}
