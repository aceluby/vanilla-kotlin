package vanillakotlin.http4k

import org.http4k.client.JavaHttpClient
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CorsTest {

    @Test fun `should handle ALLOW_ALL CORS mode`() {
        val server = buildServer(port = 0) {
            corsMode = CorsMode.ALLOW_ALL

            routeHandlers {
                +("/api/test" bind Method.GET to { Response(Status.OK).body("test data") })
                +(
                    "/api/test" bind Method.POST to { request ->
                        Response(Status.CREATED).body("created: ${request.bodyString()}")
                    }
                    )
            }
        }

        server.start()
        try {
            val client = JavaHttpClient()

            // Test preflight OPTIONS request
            val preflightResponse = client(
                Request(Method.OPTIONS, "http://127.0.0.1:${server.port()}/api/test")
                    .header("Origin", "https://example.com")
                    .header("Access-Control-Request-Method", "POST")
                    .header("Access-Control-Request-Headers", "Content-Type, Authorization"),
            )

            assertEquals("*", preflightResponse.header("Access-Control-Allow-Origin"))
            assertNotNull(preflightResponse.header("Access-Control-Allow-Methods"))
            assertNotNull(preflightResponse.header("Access-Control-Allow-Headers"))
            assertEquals("true", preflightResponse.header("Access-Control-Allow-Credentials"))

            // Test actual GET request with CORS headers
            val getResponse = client(
                Request(Method.GET, "http://127.0.0.1:${server.port()}/api/test")
                    .header("Origin", "https://example.com"),
            )

            assertEquals(Status.OK, getResponse.status)
            assertEquals("test data", getResponse.bodyString())
            assertEquals("*", getResponse.header("Access-Control-Allow-Origin"))

            // Test actual POST request with CORS headers
            val postResponse = client(
                Request(Method.POST, "http://127.0.0.1:${server.port()}/api/test")
                    .header("Origin", "https://example.com")
                    .header("Content-Type", "application/json")
                    .body("test payload"),
            )

            assertEquals(Status.CREATED, postResponse.status)
            assertEquals("created: test payload", postResponse.bodyString())
            assertEquals("*", postResponse.header("Access-Control-Allow-Origin"))
        } finally {
            server.stop()
        }
    }

    @Test fun `should handle NO_OP CORS mode`() {
        val server = buildServer(port = 0) {
            corsMode = CorsMode.NO_OP

            routeHandlers {
                +("/api/test" bind Method.GET to { Response(Status.OK).body("test data") })
            }
        }

        server.start()
        try {
            val client = JavaHttpClient()

            // Test request without CORS processing
            val response = client(
                Request(Method.GET, "http://127.0.0.1:${server.port()}/api/test")
                    .header("Origin", "https://example.com"),
            )

            assertEquals(Status.OK, response.status)
            assertEquals("test data", response.bodyString())
            // Should not have CORS headers with NO_OP mode
            assertNull(response.header("Access-Control-Allow-Origin"))
        } finally {
            server.stop()
        }
    }

    @Test fun `should handle complex CORS scenarios`() {
        val server = buildServer(port = 0) {
            corsMode = CorsMode.ALLOW_ALL

            routeHandlers {
                +("/api/users" bind Method.GET to { Response(Status.OK).body("users list") })
                +("/api/users" bind Method.POST to { Response(Status.CREATED).body("user created") })
                +("/api/users/{id}" bind Method.PUT to { Response(Status.OK).body("user updated") })
                +("/api/users/{id}" bind Method.DELETE to { Response(Status.NO_CONTENT) })
            }
        }

        server.start()
        try {
            val client = JavaHttpClient()

            // Test preflight for PUT request
            val putPreflightResponse = client(
                Request(Method.OPTIONS, "http://127.0.0.1:${server.port()}/api/users/123")
                    .header("Origin", "https://frontend.example.com")
                    .header("Access-Control-Request-Method", "PUT")
                    .header("Access-Control-Request-Headers", "Content-Type, X-Custom-Header"),
            )

            assertEquals("*", putPreflightResponse.header("Access-Control-Allow-Origin"))
            assertEquals("true", putPreflightResponse.header("Access-Control-Allow-Credentials"))

            // Test preflight for DELETE request
            val deletePreflightResponse = client(
                Request(Method.OPTIONS, "http://127.0.0.1:${server.port()}/api/users/123")
                    .header("Origin", "https://admin.example.com")
                    .header("Access-Control-Request-Method", "DELETE"),
            )

            assertEquals("*", deletePreflightResponse.header("Access-Control-Allow-Origin"))

            // Test actual PUT request
            val putResponse = client(
                Request(Method.PUT, "http://127.0.0.1:${server.port()}/api/users/123")
                    .header("Origin", "https://frontend.example.com")
                    .header("Content-Type", "application/json")
                    .body("""{"name":"Updated User"}"""),
            )

            assertEquals(Status.OK, putResponse.status)
            assertEquals("user updated", putResponse.bodyString())
            assertEquals("*", putResponse.header("Access-Control-Allow-Origin"))

            // Test actual DELETE request
            val deleteResponse = client(
                Request(Method.DELETE, "http://127.0.0.1:${server.port()}/api/users/123")
                    .header("Origin", "https://admin.example.com"),
            )

            assertEquals(Status.NO_CONTENT, deleteResponse.status)
            assertEquals("*", deleteResponse.header("Access-Control-Allow-Origin"))
        } finally {
            server.stop()
        }
    }
}
