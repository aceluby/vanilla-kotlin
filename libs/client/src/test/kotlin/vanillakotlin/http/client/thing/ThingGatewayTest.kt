package vanillakotlin.http.client.thing

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import vanillakotlin.http.clients.thing.ThingGateway
import vanillakotlin.http.interceptors.RetryInterceptor
import vanillakotlin.http.interceptors.TelemetryInterceptor
import vanillakotlin.random.randomString
import vanillakotlin.random.randomThing
import java.time.Duration

class ThingGatewayTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var client: OkHttpClient

    private val testThingId = randomThing()

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        // Create a basic HTTP client for testing
        client = OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(1))
            .readTimeout(Duration.ofSeconds(1))
            .build()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `getThingDetails should return thing when response is successful`() {
        // Arrange
        val thingResponse = """
            {
                "data": {
                    "thing": {
                        "id": "$testThingId",
                        "product_name": "Test Product",
                        "selling_price": 19.99
                    }
                }
            }
        """.trimIndent()

        mockWebServer.enqueue(MockResponse().setBody(thingResponse).setResponseCode(200))

        val thingGateway = buildThingGateway(
            client = client,
            baseUrl = mockWebServer.url("/").toString().removeSuffix("/"),
        )

        // Act
        val result = thingGateway.getThingDetails(testThingId)

        // Assert
        result shouldNotBe null
        result?.let {
            it.id shouldBe testThingId
            it.productName shouldBe "Test Product"
            it.sellingPrice shouldBe 19.99
        }
    }

    @Test
    fun `getThingDetails should return null when response is not successful`() {
        // Arrange
        mockWebServer.enqueue(MockResponse().setResponseCode(404))

        val thingGateway = buildThingGateway(
            client = client,
            baseUrl = mockWebServer.url("/").toString().removeSuffix("/"),
        )

        // Act
        val result = thingGateway.getThingDetails(testThingId)

        // Assert
        result shouldBe null
    }

    @Test
    fun `getThingDetails should return null when response body is empty`() {
        // Arrange
        mockWebServer.enqueue(MockResponse().setResponseCode(200))

        val thingGateway = buildThingGateway(
            client = client,
            baseUrl = mockWebServer.url("/").toString().removeSuffix("/"),
        )

        // Act
        val result = thingGateway.getThingDetails(testThingId)

        // Assert
        result shouldBe null
    }

    @Test
    fun `getThingDetails should retry on failure and return result`() {
        // Arrange
        val thingResponse = """
            {
                "data": {
                    "thing": {
                        "id": "$testThingId",
                        "product_name": "Test Product",
                        "selling_price": 19.99
                    }
                }
            }
        """.trimIndent()

        // First call fails, second succeeds
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setBody(thingResponse).setResponseCode(200))

        val thingGateway = buildThingGateway(
            client = client,
            baseUrl = mockWebServer.url("/").toString().removeSuffix("/"),
        )

        // Act
        val result = thingGateway.getThingDetails(testThingId)

        // Assert - we've configured the RetryInterceptor in the test setup to have a maxAttempts of 2 in buildThingGateway
        result shouldNotBe null
        result?.let {
            it.id shouldBe testThingId
        }

        // Verify that 2 requests were made (1 failure + 1 retry)
        mockWebServer.requestCount shouldBe 2
    }

    @Test
    fun `getThingDetails should fail after max retries exceeded`() {
        // Arrange
        // All calls fail
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(500))
        mockWebServer.enqueue(MockResponse().setResponseCode(500))

        val thingGateway = buildThingGateway(
            client = client,
            baseUrl = mockWebServer.url("/").toString().removeSuffix("/"),
        )

        // Act
        val result = thingGateway.getThingDetails(testThingId)

        // Assert
        result shouldBe null

        // Verify that the expected number of requests were made (initial + retries)
        mockWebServer.requestCount shouldBe 2 // maxAttempts = 2
    }

    private fun buildThingGateway(
        client: OkHttpClient = this.client,
        apiKey: String = randomString(),
        baseUrl: String,
    ): ThingGateway {
        val clientWithInterceptors = client.newBuilder()
            .addInterceptor(
                RetryInterceptor(
                    config = RetryInterceptor.Config(
                        maxAttempts = 2,
                        initialRetryDelayMs = 10,
                        maxRetryDelayMs = 100,
                    ),
                    // Mock function for publishCounterMetric
                    publishCounterMetric = { _, _ -> },
                ),
            )
            .addInterceptor(
                TelemetryInterceptor(
                    // Mock function for publishTimerMetric
                    publishTimerMetric = { _, _, _ -> },
                ),
            )
            .build()

        return ThingGateway(
            httpClient = clientWithInterceptors,
            config = ThingGateway.Config(apiKey = apiKey, baseUrl = baseUrl),
        )
    }
}
