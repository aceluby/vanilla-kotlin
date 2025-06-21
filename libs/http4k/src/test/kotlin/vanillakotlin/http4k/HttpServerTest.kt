package vanillakotlin.http4k

import org.http4k.client.JavaHttpClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.routing.bind
import org.http4k.routing.routes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import vanillakotlin.models.HealthCheckResponse
import vanillakotlin.models.HealthMonitor
import java.util.concurrent.atomic.AtomicInteger

class HttpServerTest {

    @Test fun `should create server with default configuration`() {
        val server = Server(port = 0, host = "127.0.0.1")
        val handler = { _: Request -> Response(Status.OK).body("Hello World") }

        val http4kServer = server.toServer(handler)
        http4kServer.start()

        try {
            val client = JavaHttpClient()
            val response = client(Request(Method.GET, "http://127.0.0.1:${http4kServer.port()}/"))

            assertEquals(Status.OK, response.status)
            assertEquals("Hello World", response.bodyString())
        } finally {
            http4kServer.stop()
        }
    }

    @Test fun `should handle health check endpoint`() {
        val healthMonitors = listOf(
            object : HealthMonitor {
                override val name = "test-monitor"
                override fun check() = HealthCheckResponse(
                    name = name,
                    isHealthy = true,
                    details = "All good",
                )
            },
        )

        val handler = healthCheckHandler(healthMonitors)
        val server = Server(port = 0, host = "127.0.0.1").toServer(handler)

        server.start()
        try {
            val client = JavaHttpClient()
            val response = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/"))

            assertEquals(Status.OK, response.status)
            assertTrue(response.bodyString().contains("test-monitor"))
            assertTrue(response.bodyString().contains("All good"))
        } finally {
            server.stop()
        }
    }

    @Test fun `should handle unhealthy health checks`() {
        val healthMonitors = listOf(
            object : HealthMonitor {
                override val name = "failing-monitor"
                override fun check() = HealthCheckResponse(
                    name = name,
                    isHealthy = false,
                    details = "Something is wrong",
                )
            },
        )

        val handler = healthCheckHandler(healthMonitors)
        val server = Server(port = 0, host = "127.0.0.1").toServer(handler)

        server.start()
        try {
            val client = JavaHttpClient()
            val response = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/"))

            assertEquals(Status.INTERNAL_SERVER_ERROR, response.status)
            assertTrue(response.bodyString().contains("failing-monitor"))
            assertTrue(response.bodyString().contains("Something is wrong"))
        } finally {
            server.stop()
        }
    }

    @Test fun `should apply CatchAllFailure filter`() {
        val handler = CatchAllFailure.then { _: Request ->
            throw RuntimeException("Test exception")
        }

        val server = Server(port = 0, host = "127.0.0.1").toServer(handler)

        server.start()
        try {
            val client = JavaHttpClient()
            val response = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/"))

            assertEquals(Status.INTERNAL_SERVER_ERROR, response.status)
            assertTrue(response.bodyString().contains("Test exception"))
        } finally {
            server.stop()
        }
    }

    @Test fun `should chain filters correctly`() {
        val requestCounter = AtomicInteger(0)
        val counterFilter = org.http4k.core.Filter { next ->
            { request ->
                requestCounter.incrementAndGet()
                next(request)
            }
        }

        val loggingFilter = org.http4k.core.Filter { next ->
            { request ->
                val response = next(request)
                response.header("X-Logged", "true")
            }
        }

        val handler = counterFilter.then(loggingFilter).then { _: Request ->
            Response(Status.OK).body("Filtered response")
        }

        val server = Server(port = 0, host = "127.0.0.1").toServer(handler)

        server.start()
        try {
            val client = JavaHttpClient()
            val response = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/"))

            assertEquals(Status.OK, response.status)
            assertEquals("Filtered response", response.bodyString())
            assertEquals("true", response.header("X-Logged"))
            assertEquals(1, requestCounter.get())
        } finally {
            server.stop()
        }
    }

    @Test fun `should handle multiple routes`() {
        val handler = routes(
            "/hello" bind Method.GET to { Response(Status.OK).body("Hello") },
            "/world" bind Method.GET to { Response(Status.OK).body("World") },
            "/echo" bind Method.POST to { request -> Response(Status.OK).body("Echo: ${request.bodyString()}") },
        )

        val server = Server(port = 0, host = "127.0.0.1").toServer(handler)

        server.start()
        try {
            val client = JavaHttpClient()

            // Test GET /hello
            val helloResponse = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/hello"))
            assertEquals(Status.OK, helloResponse.status)
            assertEquals("Hello", helloResponse.bodyString())

            // Test GET /world
            val worldResponse = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/world"))
            assertEquals(Status.OK, worldResponse.status)
            assertEquals("World", worldResponse.bodyString())

            // Test POST /echo
            val echoResponse = client(Request(Method.POST, "http://127.0.0.1:${server.port()}/echo").body("test message"))
            assertEquals(Status.OK, echoResponse.status)
            assertEquals("Echo: test message", echoResponse.bodyString())

            // Test 404 for unknown route
            val notFoundResponse = client(Request(Method.GET, "http://127.0.0.1:${server.port()}/unknown"))
            assertEquals(Status.NOT_FOUND, notFoundResponse.status)
        } finally {
            server.stop()
        }
    }

    @Test fun `should handle CORS configuration`() {
        val corsFilter = CorsMode.ALLOW_ALL.filter
        val handler = corsFilter.then { _: Request ->
            Response(Status.OK).body("CORS enabled")
        }

        val server = Server(port = 0, host = "127.0.0.1").toServer(handler)

        server.start()
        try {
            val client = JavaHttpClient()
            val response = client(
                Request(Method.OPTIONS, "http://127.0.0.1:${server.port()}/")
                    .header("Origin", "http://example.com")
                    .header("Access-Control-Request-Method", "GET"),
            )

            assertEquals("*", response.header("Access-Control-Allow-Origin"))
        } finally {
            server.stop()
        }
    }
}
