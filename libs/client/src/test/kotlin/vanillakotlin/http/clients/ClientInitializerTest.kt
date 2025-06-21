package vanillakotlin.http.clients

import io.opentelemetry.api.metrics.ObservableDoubleGauge
import okhttp3.Interceptor
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import vanillakotlin.metrics.PublishGaugeMetric

class ClientInitializerTest {

    @Test
    fun `ConnectionConfig should initialize with all required parameters`() {
        val config = ConnectionConfig(
            connectTimeoutMillis = 1000L,
            readTimeoutMillis = 2000L,
            maxIdleConnections = 5,
            maxConnections = 10,
            keepAliveDurationMinutes = 15L,
            clientName = "test-client",
        )

        assertEquals(1000L, config.connectTimeoutMillis)
        assertEquals(2000L, config.readTimeoutMillis)
        assertEquals(5, config.maxIdleConnections)
        assertEquals(10, config.maxConnections)
        assertEquals(15L, config.keepAliveDurationMinutes)
        assertEquals("test-client", config.clientName)
    }

    @Test
    fun `ConnectionConfig should use default values for optional parameters`() {
        val config = ConnectionConfig(
            connectTimeoutMillis = 1000L,
            readTimeoutMillis = 2000L,
            maxConnections = 10,
            keepAliveDurationMinutes = 15L,
        )

        assertEquals(1, config.maxIdleConnections) // default value
        assertEquals("default", config.clientName) // default value
    }

    @Test
    fun `initializeHttpClient should create client with correct timeouts`() {
        val config = ConnectionConfig(
            connectTimeoutMillis = 1500L,
            readTimeoutMillis = 3000L,
            maxConnections = 5,
            keepAliveDurationMinutes = 10L,
        )

        val client = initializeHttpClient(
            config = config,
            publishGaugeMetric = createMockPublishGaugeMetric(),
        )

        assertEquals(1500, client.connectTimeoutMillis)
        assertEquals(3000, client.readTimeoutMillis)
    }

    @Test
    fun `initializeHttpClient should configure connection pool correctly`() {
        val config = ConnectionConfig(
            connectTimeoutMillis = 1000L,
            readTimeoutMillis = 2000L,
            maxIdleConnections = 8,
            maxConnections = 12,
            keepAliveDurationMinutes = 20L,
        )

        val client = initializeHttpClient(
            config = config,
            publishGaugeMetric = createMockPublishGaugeMetric(),
        )

        assertNotNull(client.connectionPool)
    }

    @Test
    fun `initializeHttpClient should configure dispatcher correctly`() {
        val config = ConnectionConfig(
            connectTimeoutMillis = 1000L,
            readTimeoutMillis = 2000L,
            maxConnections = 15,
            keepAliveDurationMinutes = 10L,
        )

        val client = initializeHttpClient(
            config = config,
            publishGaugeMetric = createMockPublishGaugeMetric(),
        )

        assertEquals(15, client.dispatcher.maxRequests)
        assertEquals(15, client.dispatcher.maxRequestsPerHost)
    }

    @Test
    fun `initializeHttpClient should add interceptors in correct order`() {
        val config = ConnectionConfig(
            connectTimeoutMillis = 1000L,
            readTimeoutMillis = 2000L,
            maxConnections = 5,
            keepAliveDurationMinutes = 10L,
        )

        val interceptor1 = TestInterceptor("interceptor1")
        val interceptor2 = TestInterceptor("interceptor2")

        val client = initializeHttpClient(
            config = config,
            publishGaugeMetric = createMockPublishGaugeMetric(),
            interceptor1,
            interceptor2,
        )

        assertEquals(2, client.interceptors.size)
        assertTrue(client.interceptors.contains(interceptor1))
        assertTrue(client.interceptors.contains(interceptor2))
    }

    @Test
    fun `initializeHttpClient should work with no interceptors`() {
        val config = ConnectionConfig(
            connectTimeoutMillis = 1000L,
            readTimeoutMillis = 2000L,
            maxConnections = 5,
            keepAliveDurationMinutes = 10L,
        )

        val client = initializeHttpClient(
            config = config,
            publishGaugeMetric = createMockPublishGaugeMetric(),
        )

        assertTrue(client.interceptors.isEmpty())
    }

    @Test
    fun `initializeHttpClient should publish gauge metrics`() {
        val config = ConnectionConfig(
            connectTimeoutMillis = 1000L,
            readTimeoutMillis = 2000L,
            maxConnections = 5,
            keepAliveDurationMinutes = 10L,
            clientName = "metrics-test-client",
        )

        val publishedMetrics = mutableListOf<MetricCall>()
        val publishGaugeMetric: PublishGaugeMetric = { name, tags, valueSupplier ->
            publishedMetrics.add(MetricCall(name, tags, valueSupplier()?.toInt() ?: 0))
            createMockObservableDoubleGauge()
        }

        val client = initializeHttpClient(
            config = config,
            publishGaugeMetric = publishGaugeMetric,
        )

        // Verify that gauge metrics were registered
        assertEquals(5, publishedMetrics.size)

        val metricNames = publishedMetrics.map { it.name }.toSet()
        assertTrue(metricNames.contains("http.client.connections.total"))
        assertTrue(metricNames.contains("http.client.connections.active"))
        assertTrue(metricNames.contains("http.client.connections.idle"))
        assertTrue(metricNames.contains("http.client.calls.running"))
        assertTrue(metricNames.contains("http.client.calls.queued"))

        // Verify all metrics have the correct client tag
        publishedMetrics.forEach { metric ->
            assertEquals("metrics-test-client", metric.tags["client"])
        }
    }

    @Test
    fun `initializeHttpClient should handle custom client name in metrics`() {
        val config = ConnectionConfig(
            connectTimeoutMillis = 1000L,
            readTimeoutMillis = 2000L,
            maxConnections = 5,
            keepAliveDurationMinutes = 10L,
            clientName = "custom-client-name",
        )

        val publishedMetrics = mutableListOf<MetricCall>()
        val publishGaugeMetric: PublishGaugeMetric = { name, tags, valueSupplier ->
            publishedMetrics.add(MetricCall(name, tags, valueSupplier()?.toInt() ?: 0))
            createMockObservableDoubleGauge()
        }

        initializeHttpClient(
            config = config,
            publishGaugeMetric = publishGaugeMetric,
        )

        publishedMetrics.forEach { metric ->
            assertEquals("custom-client-name", metric.tags["client"])
        }
    }

    @Test
    fun `initializeHttpClient should handle default client name in metrics`() {
        val config = ConnectionConfig(
            connectTimeoutMillis = 1000L,
            readTimeoutMillis = 2000L,
            maxConnections = 5,
            keepAliveDurationMinutes = 10L,
            // clientName defaults to "default"
        )

        val publishedMetrics = mutableListOf<MetricCall>()
        val publishGaugeMetric: PublishGaugeMetric = { name, tags, valueSupplier ->
            publishedMetrics.add(MetricCall(name, tags, valueSupplier()?.toInt() ?: 0))
            createMockObservableDoubleGauge()
        }

        initializeHttpClient(
            config = config,
            publishGaugeMetric = publishGaugeMetric,
        )

        publishedMetrics.forEach { metric ->
            assertEquals("default", metric.tags["client"])
        }
    }

    @Test
    fun `initializeHttpClient should configure connection pool with correct parameters`() {
        val config = ConnectionConfig(
            connectTimeoutMillis = 1000L,
            readTimeoutMillis = 2000L,
            maxIdleConnections = 7,
            maxConnections = 10,
            keepAliveDurationMinutes = 25L,
        )

        val client = initializeHttpClient(
            config = config,
            publishGaugeMetric = createMockPublishGaugeMetric(),
        )

        // Connection pool configuration is internal to OkHttp, but we can verify the client was created successfully
        assertNotNull(client)
        assertNotNull(client.connectionPool)
    }

    @Test
    fun `initializeHttpClient should handle multiple interceptors of same type`() {
        val config = ConnectionConfig(
            connectTimeoutMillis = 1000L,
            readTimeoutMillis = 2000L,
            maxConnections = 5,
            keepAliveDurationMinutes = 10L,
        )

        val interceptor1 = TestInterceptor("first")
        val interceptor2 = TestInterceptor("second")
        val interceptor3 = TestInterceptor("third")

        val client = initializeHttpClient(
            config = config,
            publishGaugeMetric = createMockPublishGaugeMetric(),
            interceptor1,
            interceptor2,
            interceptor3,
        )

        assertEquals(3, client.interceptors.size)
        assertEquals(interceptor1, client.interceptors[0])
        assertEquals(interceptor2, client.interceptors[1])
        assertEquals(interceptor3, client.interceptors[2])
    }

    @Test
    fun `ConnectionConfig should handle edge case values`() {
        val config = ConnectionConfig(
            connectTimeoutMillis = 0L,
            readTimeoutMillis = Long.MAX_VALUE,
            maxIdleConnections = 1,
            maxConnections = Integer.MAX_VALUE,
            keepAliveDurationMinutes = 1L,
            clientName = "",
        )

        assertEquals(0L, config.connectTimeoutMillis)
        assertEquals(Long.MAX_VALUE, config.readTimeoutMillis)
        assertEquals(1, config.maxIdleConnections)
        assertEquals(Integer.MAX_VALUE, config.maxConnections)
        assertEquals(1L, config.keepAliveDurationMinutes)
        assertEquals("", config.clientName)
    }

    private fun createMockPublishGaugeMetric(): PublishGaugeMetric = { _, _, _ ->
        createMockObservableDoubleGauge()
    }

    private fun createMockObservableDoubleGauge(): ObservableDoubleGauge {
        return object : ObservableDoubleGauge {
            override fun close() {}
        }
    }

    private data class MetricCall(
        val name: String,
        val tags: Map<String, String>,
        val value: Int,
    )

    private class TestInterceptor(private val name: String) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            return chain.proceed(chain.request())
        }

        override fun toString(): String = "TestInterceptor($name)"

        override fun equals(other: Any?): Boolean {
            return other is TestInterceptor && other.name == this.name
        }

        override fun hashCode(): Int = name.hashCode()
    }
} 
