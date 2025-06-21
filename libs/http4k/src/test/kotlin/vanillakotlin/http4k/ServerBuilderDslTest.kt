package vanillakotlin.http4k

import org.http4k.client.JavaHttpClient
import org.http4k.contract.div
import org.http4k.core.Filter
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import vanillakotlin.models.HealthCheckResponse
import vanillakotlin.models.HealthMonitor
import java.util.concurrent.atomic.AtomicInteger

class ServerBuilderDslTest {

    @Test fun `should build server with DSL using minimal configuration`() {
        val server = buildServer(port = 0) {
            // Minimal configuration - should use defaults
        }

        server.start()
        try {
            val client = JavaHttpClient()
            val response = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/health"))
            assertEquals(Status.OK, response.status)

            // Should contain empty health check response
            val healthChecks = response.bodyString()
            assertTrue(healthChecks.contains("[]") || healthChecks.contains("isHealthy"))
        } finally {
            server.stop()
        }
    }

    @Test fun `should build server with custom route handlers`() {
        val server = buildServer(port = 0) {
            routeHandlers {
                +("/users" bind Method.GET to { Response(Status.OK).body("User list") })
                +("/users/{id}" bind Method.GET to { Response(Status.OK).body("User details") })
                +("/users" bind Method.POST to { Response(Status.CREATED).body("User created") })
            }
        }

        server.start()
        try {
            val client = JavaHttpClient()

            val getUsersResponse = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/users"))
            assertEquals(Status.OK, getUsersResponse.status)
            assertEquals("User list", getUsersResponse.bodyString())

            val getUserResponse = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/users/123"))
            assertEquals(Status.OK, getUserResponse.status)
            assertEquals("User details", getUserResponse.bodyString())

            val createUserResponse = client(Request(Method.POST, "http://127.0.0.1:${server.port()}/users"))
            assertEquals(Status.CREATED, createUserResponse.status)
            assertEquals("User created", createUserResponse.bodyString())
        } finally {
            server.stop()
        }
    }

    @Test fun `should build server with custom filters`() {
        val requestCounter = AtomicInteger(0)

        val customFilter = Filter { next ->
            { request ->
                requestCounter.incrementAndGet()
                next(request).header("X-Custom-Filter", "applied")
            }
        }

        val server = buildServer(port = 0) {
            filters {
                includeDefaults = false
                +customFilter
            }

            routeHandlers {
                +("/test" bind Method.GET to { Response(Status.OK).body("Test response") })
            }
        }

        server.start()
        try {
            val client = JavaHttpClient()
            val response = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/test"))

            assertEquals(Status.OK, response.status)
            assertEquals("Test response", response.bodyString())
            assertEquals("applied", response.header("X-Custom-Filter"))
            assertEquals(1, requestCounter.get())
        } finally {
            server.stop()
        }
    }

    @Test fun `should build server with custom health monitors`() {
        val healthyMonitor = object : HealthMonitor {
            override val name = "test-service"
            override fun check() = HealthCheckResponse("test-service", true, "All good")
        }
        val unhealthyMonitor = object : HealthMonitor {
            override val name = "failing-service"
            override fun check() = HealthCheckResponse("failing-service", false, "Something wrong")
        }

        val server = buildServer(port = 0) {
            healthMonitors {
                +healthyMonitor
                +unhealthyMonitor
            }
        }

        server.start()
        try {
            val client = JavaHttpClient()
            val response = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/health"))

            assertEquals(Status.INTERNAL_SERVER_ERROR, response.status) // Should be unhealthy due to failing service
            val healthChecks = response.bodyString()
            assertTrue(healthChecks.contains("test-service"))
            assertTrue(healthChecks.contains("failing-service"))
        } finally {
            server.stop()
        }
    }

    @Test fun `should build server with CORS configuration`() {
        val server = buildServer(port = 0) {
            corsMode = CorsMode.ALLOW_ALL

            routeHandlers {
                +("/api/data" bind Method.GET to { Response(Status.OK).body("API data") })
            }
        }

        server.start()
        try {
            val client = JavaHttpClient()

            // Test preflight request
            val preflightResponse = client(
                Request(Method.OPTIONS, "http://127.0.0.1:${server.port()}/api/data")
                    .header("Origin", "http://localhost:3000")
                    .header("Access-Control-Request-Method", "GET"),
            )
            assertEquals(Status.OK, preflightResponse.status)

            // Test actual request
            val dataResponse = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/api/data"))
            assertEquals(Status.OK, dataResponse.status)
            assertEquals("API data", dataResponse.bodyString())
        } finally {
            server.stop()
        }
    }

    @Test fun `should handle complex DSL configuration`() {
        val customFilter = Filter { next ->
            { request -> next(request).header("X-Complex", "true") }
        }

        val healthMonitor = object : HealthMonitor {
            override val name = "complex-service"
            override fun check() = HealthCheckResponse("complex-service", true, "Complex service OK")
        }

        val server = buildServer(port = 0) {
            corsMode = CorsMode.ALLOW_ALL

            healthMonitors {
                +healthMonitor
            }

            filters {
                includeDefaults = true
                +customFilter
            }

            routeHandlers {
                +("/complex" bind Method.GET to { Response(Status.OK).body("Complex response") })
            }
        }

        server.start()
        try {
            val client = JavaHttpClient()

            val healthResponse = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/health"))
            assertEquals(Status.OK, healthResponse.status)

            val complexResponse = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/complex"))
            assertEquals(Status.OK, complexResponse.status)
            assertEquals("Complex response", complexResponse.bodyString())
            assertEquals("true", complexResponse.header("X-Complex"))
        } finally {
            server.stop()
        }
    }

    // TODO: Add contract route tests once the contract functionality is properly implemented
    // The contract routes require proper integration with http4k's OpenAPI contract system
}
