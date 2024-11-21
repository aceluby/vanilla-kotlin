package vanillakotlin.api


// Utility to build a server handler for testing.
// This builds the same HTTP server used in the real app, using a simpler
// interface for testing by allowing an optional list of healthMonitors and an
// optional single route parameter.
fun buildTestHandler(
    healthMonitors: List<HealthMonitor> = emptyList(),
    contractRoute: ContractRoute? = null,
): HttpHandler {
    modifyHttp4kMapperToTargetDefault()

    return buildHandler(
        healthMonitors = healthMonitors,
        contractRoutes = if (contractRoute != null) listOf(contractRoute) else emptyList(),
        meterRegistry = SimpleMeterRegistry(),
        corsMode = CorsMode.ALLOW_ALL,
        apiMetadata = apiMetadata,
    )
}

// Convenience function for tests to append authentication headers to requests
fun Request.addAuth(username: String = randomUsername()): Request {
    return this
        .header(LAN_ID_HEADER_NAME, username)
        .header(EMAIL_HEADER_NAME, "$username@host.com")
        .header(MEMBER_OF_HEADER_NAME, STANDARD_USER_ROLE.toString())
}

fun Request.addAdminAuth(username: String = randomUsername()): Request {
    return this
        .header(LAN_ID_HEADER_NAME, username)
        .header(EMAIL_HEADER_NAME, "$username@host.com")
        .header(MEMBER_OF_HEADER_NAME, ADMIN_USER_ROLE.toString())
}
