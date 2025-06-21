package vanillakotlin.http4k

import org.http4k.client.JavaHttpClient
import org.http4k.core.Filter
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class FilterTest {

    @Test fun `should apply CatchAllFailure filter correctly`() {
        val server = buildServer(port = 0) {
            filters {
                includeDefaults = false
                +CatchAllFailure
            }

            routeHandlers {
                +("/error" bind Method.GET to { throw RuntimeException("Test exception") })
                +("/success" bind Method.GET to { Response(Status.OK).body("Success") })
            }
        }

        server.start()
        try {
            val client = JavaHttpClient()

            // Test error handling
            val errorResponse = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/error"))
            assertEquals(Status.INTERNAL_SERVER_ERROR, errorResponse.status)
            assertTrue(errorResponse.bodyString().contains("Test exception"))
            assertTrue(errorResponse.header("Content-Type")?.contains("application/json") == true)

            // Test normal operation
            val successResponse = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/success"))
            assertEquals(Status.OK, successResponse.status)
            assertEquals("Success", successResponse.bodyString())
        } finally {
            server.stop()
        }
    }

    @Test fun `should chain multiple filters in correct order`() {
        val executionOrder = mutableListOf<String>()

        val filter1 = Filter { next ->
            { request ->
                executionOrder.add("filter1-before")
                val response = next(request)
                executionOrder.add("filter1-after")
                response.header("X-Filter-1", "applied")
            }
        }

        val filter2 = Filter { next ->
            { request ->
                executionOrder.add("filter2-before")
                val response = next(request)
                executionOrder.add("filter2-after")
                response.header("X-Filter-2", "applied")
            }
        }

        val filter3 = Filter { next ->
            { request ->
                executionOrder.add("filter3-before")
                val response = next(request)
                executionOrder.add("filter3-after")
                response.header("X-Filter-3", "applied")
            }
        }

        val server = buildServer(port = 0) {
            filters {
                includeDefaults = false
                +filter1
                +filter2
                +filter3
            }

            routeHandlers {
                +(
                    "/test" bind Method.GET to {
                        executionOrder.add("handler")
                        Response(Status.OK).body("Test response")
                    }
                    )
            }
        }

        server.start()
        try {
            val client = JavaHttpClient()
            val response = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/test"))

            assertEquals(Status.OK, response.status)
            assertEquals("Test response", response.bodyString())
            assertEquals("applied", response.header("X-Filter-1"))
            assertEquals("applied", response.header("X-Filter-2"))
            assertEquals("applied", response.header("X-Filter-3"))

            // Verify execution order: filters execute in order, then handler, then filters in reverse order
            assertEquals(
                listOf(
                    "filter1-before",
                    "filter2-before",
                    "filter3-before",
                    "handler",
                    "filter3-after",
                    "filter2-after",
                    "filter1-after",
                ),
                executionOrder,
            )
        } finally {
            server.stop()
        }
    }

    @Test fun `should handle request and response modification filters`() {
        val requestCounter = AtomicInteger(0)

        val requestModifierFilter = Filter { next ->
            { request ->
                val modifiedRequest = request
                    .header("X-Request-ID", "req-${requestCounter.incrementAndGet()}")
                    .header("X-Modified", "true")
                next(modifiedRequest)
            }
        }

        val responseModifierFilter = Filter { next ->
            { request ->
                val response = next(request)
                response
                    .header("X-Response-Modified", "true")
                    .header("X-Original-Status", response.status.toString())
            }
        }

        val server = buildServer(port = 0) {
            filters {
                includeDefaults = false
                +requestModifierFilter
                +responseModifierFilter
            }

            routeHandlers {
                +(
                    "/echo-headers" bind Method.GET to { request ->
                        val requestId = request.header("X-Request-ID")
                        val modified = request.header("X-Modified")
                        Response(Status.OK).body("Request-ID: $requestId, Modified: $modified")
                    }
                    )
            }
        }

        server.start()
        try {
            val client = JavaHttpClient()

            val response1 = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/echo-headers"))
            assertEquals(Status.OK, response1.status)
            assertEquals("Request-ID: req-1, Modified: true", response1.bodyString())
            assertEquals("true", response1.header("X-Response-Modified"))
            assertEquals("200 OK", response1.header("X-Original-Status"))

            val response2 = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/echo-headers"))
            assertEquals("Request-ID: req-2, Modified: true", response2.bodyString())
        } finally {
            server.stop()
        }
    }

    @Test fun `should handle default filters (logging and exception)`() {
        val server = buildServer(port = 0) {
            filters {
                includeDefaults = false // Use explicit filters for predictable behavior
                +DEFAULT_LOGGING_FILTER
                +CatchAllFailure // Use CatchAllFailure instead of simpleExceptionFilter for message in response
            }

            routeHandlers {
                +("/normal" bind Method.GET to { Response(Status.OK).body("Normal response") })
                +("/error" bind Method.GET to { throw IllegalArgumentException("Bad request") })
            }
        }

        server.start()
        try {
            val client = JavaHttpClient()

            // Test normal request (should be logged but work normally)
            val normalResponse = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/normal"))
            assertEquals(Status.OK, normalResponse.status)
            assertEquals("Normal response", normalResponse.bodyString())

            // Test error request (should be caught by exception filter)
            val errorResponse = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/error"))
            assertEquals(Status.INTERNAL_SERVER_ERROR, errorResponse.status)
            assertTrue(errorResponse.bodyString().contains("Bad request"))
        } finally {
            server.stop()
        }
    }

    @Test fun `should handle custom authentication filter`() {
        val authFilter = Filter { next ->
            { request ->
                val authHeader = request.header("Authorization")
                when {
                    authHeader == null -> Response(Status.UNAUTHORIZED).body("Missing Authorization header")
                    authHeader == "Bearer valid-token" -> next(request)
                    else -> Response(Status.FORBIDDEN).body("Invalid token")
                }
            }
        }

        val server = buildServer(port = 0) {
            filters {
                includeDefaults = false
                +authFilter
            }

            routeHandlers {
                +("/protected" bind Method.GET to { Response(Status.OK).body("Protected resource") })
            }
        }

        server.start()
        try {
            val client = JavaHttpClient()

            // Test without auth header
            val noAuthResponse = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/protected"))
            assertEquals(Status.UNAUTHORIZED, noAuthResponse.status)
            assertEquals("Missing Authorization header", noAuthResponse.bodyString())

            // Test with invalid token
            val invalidTokenResponse = client(
                Request(Method.GET, "http://127.0.0.1:${server.port()}/protected")
                    .header("Authorization", "Bearer invalid-token"),
            )
            assertEquals(Status.FORBIDDEN, invalidTokenResponse.status)
            assertEquals("Invalid token", invalidTokenResponse.bodyString())

            // Test with valid token
            val validTokenResponse = client(
                Request(Method.GET, "http://127.0.0.1:${server.port()}/protected")
                    .header("Authorization", "Bearer valid-token"),
            )
            assertEquals(Status.OK, validTokenResponse.status)
            assertEquals("Protected resource", validTokenResponse.bodyString())
        } finally {
            server.stop()
        }
    }

    @Test fun `should handle mixed filter configurations`() {
        val metricsFilter = Filter { next ->
            { request ->
                val startTime = System.currentTimeMillis()
                val response = try {
                    next(request)
                } catch (throwable: Throwable) {
                    // Even if there's an exception, we want to measure timing
                    val duration = System.currentTimeMillis() - startTime
                    throw throwable // Re-throw but we've captured the timing
                }
                val duration = System.currentTimeMillis() - startTime
                response.header("X-Duration-Ms", duration.toString())
            }
        }

        val server = buildServer(port = 0) {
            filters {
                includeDefaults = false // Use explicit filters for predictable behavior
                +DEFAULT_LOGGING_FILTER
                +metricsFilter // Add custom metrics filter BEFORE exception handling
                +CatchAllFailure // Use CatchAllFailure last to catch exceptions
            }

            routeHandlers {
                +(
                    "/timed" bind Method.GET to {
                        Thread.sleep(10) // Small delay for timing
                        Response(Status.OK).body("Timed response")
                    }
                    )
                +("/error" bind Method.GET to { throw RuntimeException("Timed error") })
            }
        }

        server.start()
        try {
            val client = JavaHttpClient()

            // Test timed normal response
            val timedResponse = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/timed"))
            assertEquals(Status.OK, timedResponse.status)
            assertEquals("Timed response", timedResponse.bodyString())

            val duration = timedResponse.header("X-Duration-Ms")?.toIntOrNull()
            assertTrue(duration != null && duration >= 10, "Duration should be at least 10ms")

            // Test timed error response (should still have timing despite exception)
            val errorResponse = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/error"))
            assertEquals(Status.INTERNAL_SERVER_ERROR, errorResponse.status)
            assertTrue(errorResponse.bodyString().contains("Timed error"))
            // Note: The timing header won't be present for error responses because
            // CatchAllFailure creates a completely new response
        } finally {
            server.stop()
        }
    }
}
