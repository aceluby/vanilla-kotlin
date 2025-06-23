package vanillakotlin.http.interceptors

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import kotlin.time.Duration

class TelemetryInterceptorTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var metricsPublisher: MockMetricsPublisher

    @BeforeEach
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        metricsPublisher = MockMetricsPublisher()
    }

    @AfterEach
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `should publish timer metric for successful request`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("success"))

        val client = createClientWithTelemetryInterceptor()
        val request = createRequestWithTelemetryTag()

        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(1, metricsPublisher.timers.size)

        val timer = metricsPublisher.timers[0]
        assertEquals("http.request", timer.name)
        assertEquals(
            mapOf(
                "status" to "200",
                "source" to "remote",
                "service" to "test-service",
                "endpoint" to "test-endpoint",
            ),
            timer.tags,
        )
        assertTrue(timer.duration.inWholeMilliseconds >= 0)
    }

    @Test
    fun `should publish timer metric for client error`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(404).setBody("not found"))

        val client = createClientWithTelemetryInterceptor()
        val request = createRequestWithTelemetryTag()

        val response = client.newCall(request).execute()

        assertEquals(404, response.code)
        assertEquals(1, metricsPublisher.timers.size)

        val timer = metricsPublisher.timers[0]
        assertEquals("http.request", timer.name)
        assertEquals(
            mapOf(
                "status" to "404",
                "source" to "remote",
                "service" to "test-service",
                "endpoint" to "test-endpoint",
            ),
            timer.tags,
        )
    }

    @Test
    fun `should publish timer metric for server error`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("internal error"))

        val client = createClientWithTelemetryInterceptor()
        val request = createRequestWithTelemetryTag()

        val response = client.newCall(request).execute()

        assertEquals(500, response.code)
        assertEquals(1, metricsPublisher.timers.size)

        val timer = metricsPublisher.timers[0]
        assertEquals("http.request", timer.name)
        assertEquals(
            mapOf(
                "status" to "500",
                "source" to "remote",
                "service" to "test-service",
                "endpoint" to "test-endpoint",
            ),
            timer.tags,
        )
    }

    @Test
    fun `should publish timer metric for exception with exception class name`() {
        // Close the server to cause a connection exception
        mockWebServer.shutdown()

        val client = createClientWithTelemetryInterceptor()
        val request = createRequestWithTelemetryTag()

        assertThrows<IOException> {
            client.newCall(request).execute()
        }

        assertEquals(1, metricsPublisher.timers.size)

        val timer = metricsPublisher.timers[0]
        assertEquals("http.request", timer.name)
        assertEquals("remote", timer.tags["source"])
        assertEquals("test-service", timer.tags["service"])
        assertEquals("test-endpoint", timer.tags["endpoint"])
        // The status should be the exception class name
        assertTrue(timer.tags["status"]?.contains("Exception") == true)
    }

    @Test
    fun `should handle request without telemetry tag`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("success"))

        val client = createClientWithTelemetryInterceptor()
        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .build()

        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(1, metricsPublisher.timers.size)

        val timer = metricsPublisher.timers[0]
        assertEquals("http.request", timer.name)
        assertEquals(
            mapOf(
                "status" to "200",
                "source" to "remote",
            ),
            timer.tags,
        )
    }

    @Test
    fun `should handle request with custom telemetry tags`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("success"))

        val client = createClientWithTelemetryInterceptor()
        val telemetryTag = TelemetryTag(
            service = "custom-service",
            endpoint = "custom-endpoint",
            metricTags = mapOf(
                "service" to "custom-service",
                "endpoint" to "custom-endpoint",
                "region" to "us-east-1",
                "version" to "v1.0",
            ),
        )
        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .tag(TelemetryTag::class.java, telemetryTag)
            .build()

        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(1, metricsPublisher.timers.size)

        val timer = metricsPublisher.timers[0]
        assertEquals("http.request", timer.name)
        assertEquals(
            mapOf(
                "status" to "200",
                "source" to "remote",
                "service" to "custom-service",
                "endpoint" to "custom-endpoint",
                "region" to "us-east-1",
                "version" to "v1.0",
            ),
            timer.tags,
        )
    }

    @Test
    fun `should measure request duration accurately`() {
        // Add a delay to the response to ensure measurable duration
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("success")
                .setBodyDelay(50, java.util.concurrent.TimeUnit.MILLISECONDS),
        )

        val client = createClientWithTelemetryInterceptor()
        val request = createRequestWithTelemetryTag()

        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(1, metricsPublisher.timers.size)

        val timer = metricsPublisher.timers[0]
        val measuredDuration = timer.duration.inWholeMilliseconds

        // Just verify that some duration was measured (should be > 0)
        assertTrue(measuredDuration > 0, "Duration should be greater than 0, but was ${measuredDuration}ms")
        // Verify it's reasonable (not too large, indicating a bug)
        assertTrue(measuredDuration < 5000, "Duration should be less than 5 seconds, but was ${measuredDuration}ms")
    }

    @Test
    fun `should handle multiple requests with different responses`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("success"))
        mockWebServer.enqueue(MockResponse().setResponseCode(404).setBody("not found"))
        mockWebServer.enqueue(MockResponse().setResponseCode(500).setBody("error"))

        val client = createClientWithTelemetryInterceptor()

        // Make three requests
        val request1 = createRequestWithTelemetryTag("service1", "endpoint1")
        val request2 = createRequestWithTelemetryTag("service2", "endpoint2")
        val request3 = createRequestWithTelemetryTag("service3", "endpoint3")

        client.newCall(request1).execute()
        client.newCall(request2).execute()
        client.newCall(request3).execute()

        assertEquals(3, metricsPublisher.timers.size)

        // Check first request
        val timer1 = metricsPublisher.timers[0]
        assertEquals("200", timer1.tags["status"])
        assertEquals("service1", timer1.tags["service"])
        assertEquals("endpoint1", timer1.tags["endpoint"])

        // Check second request
        val timer2 = metricsPublisher.timers[1]
        assertEquals("404", timer2.tags["status"])
        assertEquals("service2", timer2.tags["service"])
        assertEquals("endpoint2", timer2.tags["endpoint"])

        // Check third request
        val timer3 = metricsPublisher.timers[2]
        assertEquals("500", timer3.tags["status"])
        assertEquals("service3", timer3.tags["service"])
        assertEquals("endpoint3", timer3.tags["endpoint"])
    }

    @Test
    fun `should handle request with empty metric tags`() {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("success"))

        val client = createClientWithTelemetryInterceptor()
        val telemetryTag = TelemetryTag(
            service = "test-service",
            endpoint = "test-endpoint",
            metricTags = emptyMap(),
        )
        val request = Request.Builder()
            .url(mockWebServer.url("/test"))
            .tag(TelemetryTag::class.java, telemetryTag)
            .build()

        val response = client.newCall(request).execute()

        assertEquals(200, response.code)
        assertEquals(1, metricsPublisher.timers.size)

        val timer = metricsPublisher.timers[0]
        assertEquals(
            mapOf(
                "status" to "200",
                "source" to "remote",
            ),
            timer.tags,
        )
    }

    private fun createClientWithTelemetryInterceptor(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(TelemetryInterceptor(metricsPublisher::publishTimerMetric))
        .build()

    private fun createRequestWithTelemetryTag(
        service: String = "test-service",
        endpoint: String = "test-endpoint",
    ): Request {
        val telemetryTag = TelemetryTag(service = service, endpoint = endpoint)
        return Request.Builder()
            .url(mockWebServer.url("/test"))
            .tag(TelemetryTag::class.java, telemetryTag)
            .build()
    }

    private data class TimerMetric(
        val name: String,
        val tags: Map<String, String>,
        val duration: Duration,
    )

    private class MockMetricsPublisher {
        val timers = mutableListOf<TimerMetric>()

        fun publishTimerMetric(
            name: String,
            tags: Map<String, String>,
            duration: Duration,
        ) {
            timers.add(TimerMetric(name, tags, duration))
        }
    }
}
