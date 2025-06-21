package vanillakotlin.kafkatransformer

import io.kotest.assertions.assertSoftly
import io.kotest.extensions.system.withSystemProperties
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.NewTopic
import org.http4k.client.JavaHttpClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import vanillakotlin.kafka.MockMetricsPublisher
import vanillakotlin.kafka.collectMessages
import vanillakotlin.kafka.models.KafkaOutputMessage
import vanillakotlin.kafka.producer.AGENT_HEADER_NAME
import vanillakotlin.kafka.producer.KafkaProducer
import vanillakotlin.kafka.provenance.PROVENANCES_HEADER_NAME
import vanillakotlin.kafka.provenance.SPAN_ID_HEADER_NAME
import vanillakotlin.models.UserFavoriteThing
import vanillakotlin.random.randomString
import vanillakotlin.random.randomThing
import vanillakotlin.random.randomUsername
import vanillakotlin.serde.mapper

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppTest {
    private var app: App? = null
    private val mockThingServer = MockWebServer()

    // Use dynamic topic names for test isolation
    private val sourceTopicName = randomString()
    private val sinkTopicName = randomString()
    private val broker = if (System.getenv().containsKey("CI")) "kafka:9092" else "localhost:9092"

    private val thingIdentifier = randomThing()
    private val testThing = buildTestThing(thingIdentifier = thingIdentifier)

    @Language("JSON")
    private val sampleThingGraphQlResponse =
        """
        {
          "data": {
            "thing": {
              "id": "${testThing.id}",
              "product_name": "${testThing.productName}",
              "selling_price": ${testThing.sellingPrice}
            }
          }
        }
        """.trimIndent()

    @BeforeAll fun beforeAll() {
        // mock the thing endpoint to return the same sample responses based on the request path
        val dispatcher: Dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.path?.substringBefore("?")) {
                        "/things/v4/graphql/compact/thing" -> return MockResponse().setResponseCode(200).setBody(sampleThingGraphQlResponse)
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
        mockThingServer.dispatcher = dispatcher
        mockThingServer.start()

        // create dynamic source and sink topics
        val adminClient = AdminClient.create(mapOf("bootstrap.servers" to broker))
        adminClient.createTopics(listOf(NewTopic(sourceTopicName, 1, 1)))
        adminClient.createTopics(listOf(NewTopic(sinkTopicName, 1, 1)))

        // Override configuration with dynamic topic names
        val overriddenConfiguration = mapOf(
            "config.override.http.client.thing.gateway.baseUrl" to "http://localhost:${mockThingServer.port}",
            "config.override.kafka.consumer.topics" to sourceTopicName,
            "config.override.kafka.producer.topic" to sinkTopicName,
        )

        withSystemProperties(overriddenConfiguration) {
            app = App()
            app?.start()
        }
    }

    @AfterAll fun afterAll() {
        app?.close()
        mockThingServer.close()
    }

    @Test fun `health check ok`() {
        val request = Request(Method.GET, "http://localhost:${app?.httpServerPort}/health")

        val response = JavaHttpClient()(request)

        response.status shouldBe Status.OK
    }

    @Test fun `the kafkatransformer should consume, process, and forward a valid message successfully`() {
        // The beforeAll test setup function created a new source and sink topic, and configured the application to use those.
        // So at this point, the kafkatransformer's event handler should be running and waiting for a message.

        val metricsPublisher = MockMetricsPublisher()

        // produce a message on source topic
        val producer =
            KafkaProducer<String>(
                config =
                KafkaProducer.Config(
                    broker = broker,
                    topic = sourceTopicName,
                ),
                publishTimerMetric = metricsPublisher,
            )
        producer.start()

        val userName = randomUsername()

        producer.send(
            KafkaOutputMessage(
                key = "$userName:$thingIdentifier",
                value = thingIdentifier,
            ),
        )

        val expectedUserFavoriteThing =
            UserFavoriteThing(
                userName = userName,
                thingIdentifier = testThing.id,
            )

        val receivedMessages =
            collectMessages(
                broker = broker,
                topic = sinkTopicName,
                metricsPublisher = metricsPublisher,
            )

        receivedMessages.size shouldBe 1
        assertSoftly(receivedMessages.first()) {
            this.key shouldBe "$userName:$thingIdentifier"
            this.body shouldBe mapper.writeValueAsBytes(expectedUserFavoriteThing)
            this.timestamp shouldNotBe null
            this.offset shouldBe 0
            this.partition shouldBe 0
            this.headers.size shouldBe 3
            this.headers[SPAN_ID_HEADER_NAME] shouldNotBe null
            this.headers[AGENT_HEADER_NAME] shouldNotBe null
            this.headers[PROVENANCES_HEADER_NAME] shouldNotBe null
        }
    }
}
