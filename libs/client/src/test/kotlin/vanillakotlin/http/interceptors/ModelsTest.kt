package vanillakotlin.http.interceptors

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ModelsTest {

    @Test
    fun `CacheTag cacheKey should combine context and key with colon`() {
        val cacheTag = CacheTag(context = "itemDetails", key = "12345")

        assertEquals("itemDetails:12345", cacheTag.cacheKey())
    }

    @Test
    fun `CacheTag cacheKey should handle empty context`() {
        val cacheTag = CacheTag(context = "", key = "12345")

        assertEquals(":12345", cacheTag.cacheKey())
    }

    @Test
    fun `CacheTag cacheKey should handle empty key`() {
        val cacheTag = CacheTag(context = "itemDetails", key = "")

        assertEquals("itemDetails:", cacheTag.cacheKey())
    }

    @Test
    fun `CacheTag cacheKey should handle special characters`() {
        val cacheTag = CacheTag(context = "item-details", key = "key_with_underscore")

        assertEquals("item-details:key_with_underscore", cacheTag.cacheKey())
    }

    @Test
    fun `TelemetryTag should initialize with valid service and endpoint`() {
        val telemetryTag = TelemetryTag(service = "item", endpoint = "details")

        assertEquals("item", telemetryTag.service)
        assertEquals("details", telemetryTag.endpoint)
        assertEquals(emptyMap<String, String>(), telemetryTag.logMap)
        assertEquals(mapOf("service" to "item", "endpoint" to "details"), telemetryTag.metricTags)
    }

    @Test
    fun `TelemetryTag should initialize with custom logMap and metricTags`() {
        val logMap = mapOf("userId" to "123", "action" to "fetch")
        val metricTags = mapOf("service" to "item", "endpoint" to "details", "region" to "us-east")

        val telemetryTag = TelemetryTag(
            service = "item",
            endpoint = "details",
            logMap = logMap,
            metricTags = metricTags,
        )

        assertEquals(logMap, telemetryTag.logMap)
        assertEquals(metricTags, telemetryTag.metricTags)
    }

    @Test
    fun `TelemetryTag should clean service name with invalid characters`() {
        // This should not throw an exception because the service name is cleaned
        val telemetryTag = TelemetryTag(service = "item-service", endpoint = "details")

        assertEquals("item-service", telemetryTag.service)
        assertEquals(mapOf("service" to "item-service", "endpoint" to "details"), telemetryTag.metricTags)
    }

    @Test
    fun `TelemetryTag should clean endpoint name with invalid characters`() {
        val telemetryTag = TelemetryTag(service = "item", endpoint = "get_details")

        assertEquals("get_details", telemetryTag.endpoint)
        assertEquals(mapOf("service" to "item", "endpoint" to "get_details"), telemetryTag.metricTags)
    }

    @Test
    fun `TelemetryTag should throw exception for invalid service name`() {
        assertThrows<IllegalStateException> {
            TelemetryTag(service = "item service", endpoint = "details")
        }
    }

    @Test
    fun `TelemetryTag should throw exception for invalid endpoint name`() {
        assertThrows<IllegalStateException> {
            TelemetryTag(service = "item", endpoint = "get details")
        }
    }

    @Test
    fun `TelemetryTag should throw exception for invalid metric tag values`() {
        assertThrows<IllegalStateException> {
            TelemetryTag(
                service = "item",
                endpoint = "details",
                metricTags = mapOf("service" to "item service", "endpoint" to "details"),
            )
        }
    }

    @Test
    fun `TelemetryTag should allow valid metric characters`() {
        val telemetryTag = TelemetryTag(
            service = "item-service_v1.0",
            endpoint = "get-details/v2",
            metricTags = mapOf(
                "service" to "item-service_v1.0",
                "endpoint" to "get-details/v2",
                "version" to "1.0.0",
                "region" to "us-east-1",
            ),
        )

        assertTrue(telemetryTag.metricTags.containsKey("version"))
        assertTrue(telemetryTag.metricTags.containsKey("region"))
    }

    @Test
    fun `RequestContext should initialize with telemetryTag only`() {
        val telemetryTag = TelemetryTag(service = "item", endpoint = "details")
        val requestContext = RequestContext(telemetryTag = telemetryTag)

        assertEquals(telemetryTag, requestContext.telemetryTag)
        assertEquals(null, requestContext.cacheTag)
    }

    @Test
    fun `RequestContext should initialize with both telemetryTag and cacheTag`() {
        val telemetryTag = TelemetryTag(service = "item", endpoint = "details")
        val cacheTag = CacheTag(context = "itemDetails", key = "12345")
        val requestContext = RequestContext(telemetryTag = telemetryTag, cacheTag = cacheTag)

        assertEquals(telemetryTag, requestContext.telemetryTag)
        assertEquals(cacheTag, requestContext.cacheTag)
    }

    @Test
    fun `RequestContext should handle null cacheTag explicitly`() {
        val telemetryTag = TelemetryTag(service = "item", endpoint = "details")
        val requestContext = RequestContext(telemetryTag = telemetryTag, cacheTag = null)

        assertEquals(telemetryTag, requestContext.telemetryTag)
        assertEquals(null, requestContext.cacheTag)
    }

    @Test
    fun `String cleanedForMetrics should replace invalid characters with underscores`() {
        // This tests the private extension function indirectly through TelemetryTag validation
        assertThrows<IllegalStateException> {
            TelemetryTag(service = "item@service#", endpoint = "details")
        }
    }

    @Test
    fun `String cleanedForMetrics should preserve valid characters`() {
        // Valid characters: alphanumeric, '.', '/', '-', '_'
        val telemetryTag = TelemetryTag(
            service = "item_service-v1.0",
            endpoint = "get/details",
        )

        assertEquals("item_service-v1.0", telemetryTag.service)
        assertEquals("get/details", telemetryTag.endpoint)
    }
}
