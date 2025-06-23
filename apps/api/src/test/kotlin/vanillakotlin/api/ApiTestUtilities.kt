package vanillakotlin.api

import org.http4k.contract.ContractRoute
import org.http4k.contract.openapi.ApiInfo
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import vanillakotlin.http4k.CorsMode
import vanillakotlin.http4k.buildServerHandler
import vanillakotlin.models.HealthMonitor
import vanillakotlin.random.randomUsername

// Utility to build a server handler for testing.
// This builds the same HTTP server used in the real app, using a simpler
// interface for testing by allowing an optional list of healthMonitors and an
// optional single route parameter.
fun buildTestHandler(
    healthMonitors: List<HealthMonitor> = emptyList(),
    contractRoute: ContractRoute? = null,
): HttpHandler = buildServerHandler(port = 0) {
    healthMonitors { +healthMonitors }
    corsMode = CorsMode.ALLOW_ALL

    if (contractRoute != null) {
        contract {
            apiInfo = ApiInfo("Test API", "1.0")
            routes {
                +contractRoute
            }
        }
    }
}

// Convenience function for tests to append authentication headers to requests
fun Request.addAuth(username: String = randomUsername()): Request = this
    .header("X-ID", username)
    .header("X-EMAIL", "$username@host.com")
    .header("X-MEMBER-OF", "standard-user")

fun Request.addAdminAuth(username: String = randomUsername()): Request = this
    .header("X-ID", username)
    .header("X-EMAIL", "$username@host.com")
    .header("X-MEMBER-OF", "admin-user")
