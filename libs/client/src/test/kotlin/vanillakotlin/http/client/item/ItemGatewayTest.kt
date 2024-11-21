package vanillakotlin.http.client.item

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Test
import vanillakotlin.http.clients.item.ItemGateway
import java.io.IOException
import java.time.Duration

class ItemGatewayTest {

    @Test fun `getItemDetails found success`() {
        val metricsPublisher = MockMetricsPublisher()

        val expectedTcin = randomTcin()
        val expectedLifecyeState = "READY_FOR_LAUNCH"
        val expectedDepartmentId = 62
        val expectedClassId = 6

        val mockResponseString =
            """
             {
              "data": {
                "item": {
                  "tcin": "$expectedTcin",
                  "lifecycleState": "$expectedLifecyeState",
                  "classification": {
                    "merchandise": {
                      "departmentId": $expectedDepartmentId,
                      "classId": $expectedClassId
                    }
                  }
                }
              }
            }
            """.trimIndent()

        val mockResponses =
            listOf(
                MockResponse().setBody(mockResponseString),
            )

        val itemGateway =
            buildItemGateway(
                publishCounterMetric = metricsPublisher::publishCounterMetric,
                publishTimerMetric = metricsPublisher::publishTimerMetric,
                mockResponses = mockResponses,
            )

        assertSoftly(itemGateway.getItemDetails(expectedTcin)) { item ->
            checkNotNull(item)
            item.apply {
                id shouldBe expectedTcin
                description shouldBe expectedLifecyeState
                price.merchandise?.departmentId shouldBe expectedDepartmentId
                price.merchandise?.classId shouldBe expectedClassId
            }
        }

        assertSoftly(metricsPublisher.timers) { timers ->
            size shouldBe 1
            val timer = timers.first()
            timer.name shouldBe "http.request"
            timer.tags shouldBe
                mapOf(
                    "status" to "200",
                    "source" to "remote",
                    "service" to "item",
                    "endpoint" to "details",
                )
        }

        // a second call to the service should be cached
        itemGateway.getItemDetails(expectedTcin)
        assertSoftly(metricsPublisher.timers) { timers ->
            size shouldBe 2
            val timer = timers[1]
            timer.name shouldBe "http.request"
            timer.tags shouldBe
                mapOf(
                    "status" to "200",
                    "source" to "cache",
                    "service" to "item",
                    "endpoint" to "details",
                )
        }
    }

    @Test fun `getItemDetails with null merchandise`() {
        val metricsPublisher = MockMetricsPublisher()

        val mockResponseString =
            """
             {
              "data": {
                "item": {
                  "tcin": "1",
                  "lifecycleState": "DELETED",
                  "classification": {
                    "merchandise": null
                  }
                }
              }
            }
            """.trimIndent()

        val mockResponses =
            listOf(
                MockResponse().setBody(mockResponseString),
            )

        val itemGateway =
            buildItemGateway(
                publishCounterMetric = metricsPublisher::publishCounterMetric,
                publishTimerMetric = metricsPublisher::publishTimerMetric,
                mockResponses = mockResponses,
            )

        assertSoftly(itemGateway.getItemDetails("1")) { item ->
            checkNotNull(item)
            item.apply {
                id shouldNotBe null
                price.merchandise shouldBe null
            }
        }
    }

    @Test fun `getItemDetails none found should return null`() {
        val metricsPublisher = MockMetricsPublisher()

        val mockResponses =
            listOf(
                MockResponse().setBody(
                    """{
                  "data": null,
                  "errors": [
                    {
                      "error_message": "Requested item(s) could not be found",
                      "type": "DataFetchingException",
                      "message": "Requested item(s) could not be found",
                      "extensions": {
                        "statusName": "Not Found"
                      },
                      "locations": [],
                      "error_type": "DataFetchingException",
                      "path": null
                    }
                  ]
                }""",
                ),
            )

        val itemGateway =
            buildItemGateway(
                publishCounterMetric = metricsPublisher::publishCounterMetric,
                publishTimerMetric = metricsPublisher::publishTimerMetric,
                mockResponses = mockResponses,
            )

        val item = itemGateway.getItemDetails(randomTcin())
        item shouldBe null
    }

    @Test fun `getItemDetails unsuccessful response`() {
        val metricsPublisher = MockMetricsPublisher()

        val mockResponses =
            listOf(
                MockResponse().setResponseCode(503),
                MockResponse().setResponseCode(503),
            )

        val itemGateway =
            buildItemGateway(
                publishCounterMetric = metricsPublisher::publishCounterMetric,
                publishTimerMetric = metricsPublisher::publishTimerMetric,
                mockResponses = mockResponses,
            )

        shouldThrow<IOException> {
            itemGateway.getItemDetails(randomTcin())
        }

        // we've configured the RetryInterceptor in the test setup to have a maxAttempts of 2 in buildItemGateway
        assertSoftly(metricsPublisher.timers) { timers ->
            size shouldBe 2
            val timer1 = timers[0]
            timer1.name shouldBe "http.request"
            timer1.tags shouldBe
                mapOf(
                    "status" to "503",
                    "source" to "remote",
                    "service" to "item",
                    "endpoint" to "details",
                )

            val timer2 = timers[0]
            timer2.name shouldBe "http.request"
            timer2.tags shouldBe
                mapOf(
                    "status" to "503",
                    "source" to "remote",
                    "service" to "item",
                    "endpoint" to "details",
                )
        }
    }

    private fun buildItemGateway(
        publishCounterMetric: PublishCounterMetric,
        publishTimerMetric: PublishTimerMetric,
        mockResponses: List<MockResponse>,
    ): ItemGateway {
        val server = MockWebServer()
        mockResponses.forEach { server.enqueue(it) }
        server.start()

        val baseUrl = server.url("/").toString()

        val httpClient =
            initializeHttpClient(
                config =
                    ConnectionConfig(
                        connectTimeoutMillis = 1000L,
                        readTimeoutMillis = 1000L,
                        maxConnections = 1,
                        keepAliveDurationMinutes = 1L,
                    ),
                CachingInterceptor(
                    CachingInterceptor.Config(
                        cacheTimeout = Duration.ofHours(1),
                        cacheSize = 1000,
                    ),
                    publishTimerMetric,
                ),
                RetryInterceptor(RetryInterceptor.Config(maxAttempts = 2, initialRetryDelayMs = 1), publishCounterMetric),
                TelemetryInterceptor(publishTimerMetric),
            )

        return ItemGateway(
            httpClient = httpClient,
            config = ItemGateway.Config(apiKey = randomString(), baseUrl = baseUrl),
        )
    }
}
