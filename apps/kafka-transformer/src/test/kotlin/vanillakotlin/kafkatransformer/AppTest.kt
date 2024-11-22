package vanillakotlin.kafkatransformer

import io.kotest.assertions.assertSoftly
import io.kotest.extensions.system.withSystemProperties
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppTest {
    private var app: App? = null
    private val mockItemServer = MockWebServer()
    private val sourceTopicName = randomString()
    private val sinkTopicName = randomString()
    private val broker = if (System.getenv().containsKey("CI")) "kafka:9092" else "localhost:9092"

    private val item = randomTcin()
    private val testItem = buildTestItem(itemIdentifier = item)

    @Language("JSON") private val sampleItemGraphQlResponse =
        """
        {
          "data": {
            "item": {
              "lifecycleState": "${testItem.description}",
              "item": "$item",
              "classification": {
                "merchandise": {
                  "classId": ${testItem.price.merchandise?.classId},
                  "departmentId": ${testItem.price.merchandise?.departmentId}
                }
              }
            }
          }
        }
        """.trimIndent()

    @BeforeAll fun beforeAll() {
        // mock the item endpoint to return the same sample responses based on the request path
        val dispatcher: Dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    return when (request.path?.substringBefore("?")) {
                        "/items/v4/graphql/compact/item" -> return MockResponse().setResponseCode(200).setBody(sampleItemGraphQlResponse)
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
        mockItemServer.dispatcher = dispatcher

        // create random source and sink topic
        val adminClient = AdminClient.create(mapOf("bootstrap.servers" to broker))
        adminClient.createTopics(listOf(NewTopic(sourceTopicName, 1, 1)))
        adminClient.createTopics(listOf(NewTopic(sinkTopicName, 1, 1)))

        // override some of the app configuration
        val overriddenConfiguration =
            mapOf(
                "config.override.http.client.item.gateway.baseUrl" to "http://localhost:${mockItemServer.port}",
                "config.override.kafka.consumer.topic" to sourceTopicName,
                "config.override.kafka.producer.topic" to sinkTopicName,
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

    @Test fun `the kafkatransformer should consume, process, and forward a valid message successfully`() {
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
        val userFavoriteTcin = UserFavoriteTcin(userName = userName, itemIdentifier = item)

        producer.send(
            KafkaOutputMessage(
                key = "$userName:$item",
                value = userFavoriteTcin,
            ),
        )

        val expectedUserFavoriteItem =
            UserFavoriteItem(
                userName = userName,
                item =
                    Item(
                        id = item,
                        description = testItem.description,
                        price =
                        Item.Classification(
                            merchandise =
                            Item.Classification.Merchandise(
                                departmentId = testItem.price.merchandise!!.departmentId,
                                classId = testItem.price.merchandise!!.classId,
                            ),
                        ),
                    ),
            )

        val receivedMessages =
            collectMessages(
                broker = broker,
                topic = sinkTopicName,
                metricsPublisher = metricsPublisher,
            )

        receivedMessages.size shouldBe 1
        assertSoftly(receivedMessages.first()) {
            this.key shouldBe key
            this.body shouldBe mapper.writeValueAsBytes(expectedUserFavoriteItem)
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
