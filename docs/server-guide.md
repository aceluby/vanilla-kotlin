# Server Guide

This document explains the server architecture used in the vanilla Kotlin project, covering HTTP4K integration, custom
library wrappers, and the flexibility to swap in alternative frameworks like Ktor.

## Overview

The project uses a layered server architecture that prioritizes flexibility and maintainability:

- **HTTP4K**: Current HTTP server framework with functional approach
- **Custom Wrappers**: Abstraction layer that encapsulates HTTP4K specifics
- **Framework Agnostic**: Applications are not locked into HTTP4K
- **Swap-Friendly**: Easy to replace with Ktor, Spring WebFlux, or other frameworks

## HTTP4K Foundation

[HTTP4K](https://www.http4k.org/) is a lightweight, functional HTTP toolkit for Kotlin that treats HTTP as a simple
function: `(Request) -> Response`.

### Core Philosophy

```kotlin
// HTTP4K treats everything as functions
typealias HttpHandler = (Request) -> Response

// Simple example
val handler: HttpHandler = { request ->
    Response(Status.OK).body("Hello from ${request.uri.path}")
}
```

### Benefits of HTTP4K

**1. Functional Approach**

- Immutable request/response objects
- Composable filters and handlers
- No magic annotations or reflection

**2. Lightweight**

- Minimal dependencies
- Fast startup times
- Small memory footprint

**3. Testing-Friendly**

- Pure functions are easy to test
- No need for test containers for unit testing
- Simple mocking and stubbing

## Custom HTTP4K Wrappers

The project creates abstraction layers around HTTP4K to make framework swapping easier and provide opinionated defaults.

### Server Builder DSL

The `ServerBuilderDsl` provides a clean, declarative way to configure servers:

```kotlin
// libs/http4k/src/main/kotlin/vanillakotlin/http4k/ServerBuilderDsl.kt
@Http4kServerMarker
fun buildServer(
    host: String = DEFAULT_HOST,
    port: Int = DEFAULT_PORT,
    action: ServerBuilder.() -> Unit,
) = ServerBuilder(host, port).apply(action).build()

// Usage in applications
val httpServer = buildServer(
    host = config.http.server.host,
    port = config.http.server.port,
) {
    routeHandlers {
        +postFavoriteThingsRoute(favoriteThingsService::saveFavoriteThing)
        +getFavoriteThingsRoute(favoriteThingsService::getFavoriteThingsDetails)
    }

    healthMonitors {
        +HealthMonitor("database") { databaseHealthCheck() }
    }

    filters {
        +customAuthFilter()
        +rateLimitingFilter()
    }
}
```

### Key Abstraction Features

**1. Opinionated Defaults**

```kotlin
// Sensible defaults are provided
val DEFAULT_HEALTH_CHECKS: List<HealthMonitor> = emptyList()
val DEFAULT_CORS_MODE = CorsMode.NO_OP
val DEFAULT_LOGGING_FILTER = simpleLoggingFilter()
val DEFAULT_EXCEPTION_FILTER = simpleExceptionFilter()

// Jackson configuration with Target standards
Jackson.mapper
    .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
    .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
    .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
```

**2. Built-in Health Checks**

```kotlin
// Automatic health check endpoint
fun healthCheckHandler(healthMonitors: List<HealthMonitor>): HttpHandler = { _ ->
    val healthCheckResponses = healthCheckAll(healthMonitors)
    val status = if (healthCheckResponses.any { !it.isHealthy })
        Status.INTERNAL_SERVER_ERROR else Status.OK
    Response(status).body(healthCheckResponses.toJsonString())
}
```

**3. CORS Configuration**

```kotlin
enum class CorsMode(val filter: Filter) {
    ALLOW_ALL(
        ServerFilters.Cors(
            CorsPolicy(
                originPolicy = OriginPolicy.AllowAll(),
                headers = listOf("*"),
                methods = Method.entries,
                credentials = true,
            ),
        ),
    ),
    NO_OP(Filter.NoOp),
}
```

### Route Definition Pattern

Routes are defined as pure functions that return `RoutingHttpHandler`:

```kotlin
fun postFavoriteThingsRoute(saveFavoriteThing: SaveFavoriteThing): RoutingHttpHandler {
    return org.http4k.routing.routes(
        "/api/v1/favorite_things/{thingIdentifier}" bind Method.POST to { request ->
            if (!request.hasAuth()) return@to Response(Status.UNAUTHORIZED)

            val thingIdentifier = request.uri.path.substringAfterLast("/")
            when (val result = saveFavoriteThing(FavoriteThing(thingIdentifier = thingIdentifier))) {
                is SaveResult.Success -> Response(Status.OK)
                is SaveResult.Error -> Response(Status.INTERNAL_SERVER_ERROR)
                    .body(result.errorType.toString())
            }
        },
    )
}
```

**Benefits of This Pattern:**

- Routes are testable functions
- Business logic is separated from HTTP concerns
- Easy to compose and reuse
- Framework-agnostic business logic

## Framework Agnostic Architecture

The application architecture is designed to make framework swapping straightforward.

### VanillaApp Interface

All applications implement a simple interface:

```kotlin
// libs/common/src/main/kotlin/vanillakotlin/app/AppInterfaces.kt
interface VanillaApp : AutoCloseable {
    fun start()
}

// Helper function for lifecycle management
fun runApplication(block: () -> VanillaApp) = block().use { app ->
    Runtime.getRuntime().addShutdownHook(Thread { app.close() })
    app.start()
    Thread.sleep(Long.MAX_VALUE)
}
```

### Application Structure

Applications follow a consistent structure that isolates framework-specific code:

```kotlin
class App : VanillaApp {
    private val config = loadConfig<Config>()
    private val favoriteThingsService = createFavoriteThingsService()

    // Framework-specific server creation
    private val httpServer = buildServer(config.http.server) {
        routeHandlers {
            +postFavoriteThingsRoute(favoriteThingsService::saveFavoriteThing)
            +getFavoriteThingsRoute(favoriteThingsService::getFavoriteThingsDetails)
        }
    }

    override fun start() {
        httpServer.start()
    }

    override fun close() {
        httpServer.stop()
    }
}
```

**Key Separation Points:**

1. **Business Logic**: Lives in service classes, framework-agnostic
2. **Route Handlers**: Pure functions that can be adapted to any framework
3. **Server Configuration**: Isolated in framework-specific builders
4. **Application Lifecycle**: Standardized through `VanillaApp` interface

## Swapping to Alternative Frameworks

The architecture makes it relatively simple to swap HTTP4K for other frameworks like Ktor.

### Example: Ktor Integration

Here's how you could create a Ktor equivalent of the HTTP4K wrapper:

```kotlin
// libs/ktor/src/main/kotlin/vanillakotlin/ktor/KtorServer.kt
@KtorServerMarker
class KtorServerBuilder internal constructor(
    private val host: String,
    private val port: Int,
) {
    private var healthMonitors: List<HealthMonitor> = emptyList()
    private var routeHandlers: List<Route.() -> Unit> = emptyList()

    @KtorServerMarker
    fun healthMonitors(action: ListBuilder<HealthMonitor>.() -> Unit) {
        healthMonitors = ListBuilder<HealthMonitor>().apply(action)
    }

    @KtorServerMarker
    fun routeHandlers(action: ListBuilder<Route.() -> Unit>.() -> Unit) {
        routeHandlers = ListBuilder<Route.() -> Unit>().apply(action)
    }

    fun build(): NettyApplicationEngine {
        return embeddedServer(Netty, port = port, host = host) {
            install(ContentNegotiation) {
                jackson {
                    propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
                    setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                }
            }

            routing {
                // Built-in health check
                get("/health") {
                    val healthCheckResponses = healthCheckAll(healthMonitors)
                    val isHealthy = healthCheckResponses.all { it.isHealthy }
                    call.respond(
                        if (isHealthy) HttpStatusCode.OK else HttpStatusCode.InternalServerError,
                        healthCheckResponses
                    )
                }

                // Apply custom route handlers
                routeHandlers.forEach { it() }
            }
        }
    }
}

@KtorServerMarker
fun buildKtorServer(
    host: String = "127.0.0.1",
    port: Int = 8080,
    action: KtorServerBuilder.() -> Unit,
) = KtorServerBuilder(host, port).apply(action).build()
```

### Route Adaptation

Routes would need minimal adaptation for Ktor:

```kotlin
// HTTP4K version
fun postFavoriteThingsRoute(saveFavoriteThing: SaveFavoriteThing): RoutingHttpHandler

// Ktor version  
fun Route.postFavoriteThingsRoute(saveFavoriteThing: SaveFavoriteThing) {
    post("/api/v1/favorite_things/{thingIdentifier}") {
        if (!call.request.hasAuth()) {
            call.respond(HttpStatusCode.Unauthorized)
            return@post
        }

        val thingIdentifier = call.parameters["thingIdentifier"]!!
        when (val result = saveFavoriteThing(FavoriteThing(thingIdentifier = thingIdentifier))) {
            is SaveResult.Success -> call.respond(HttpStatusCode.OK)
            is SaveResult.Error -> call.respond(
                HttpStatusCode.InternalServerError,
                result.errorType.toString()
            )
        }
    }
}
```

### Application Migration

The application class would require minimal changes:

```kotlin
class App : VanillaApp {
    private val config = loadConfig<Config>()
    private val favoriteThingsService = createFavoriteThingsService()

    // Swap HTTP4K for Ktor
    private val httpServer = buildKtorServer(
        host = config.http.server.host,
        port = config.http.server.port,
    ) {
        routeHandlers {
            +{ postFavoriteThingsRoute(favoriteThingsService::saveFavoriteThing) }
            +{ getFavoriteThingsRoute(favoriteThingsService::getFavoriteThingsDetails) }
        }
    }

    override fun start() {
        httpServer.start(wait = false)
    }

    override fun close() {
        httpServer.stop()
    }
}
```

## HTTP4K Specific Features

While the architecture supports framework swapping, HTTP4K provides some unique benefits worth highlighting.

### Contract Routes & OpenAPI

HTTP4K supports contract-first API development with automatic OpenAPI generation:

```kotlin
// Contract-based route definition
val contractRoute = "/api/v1/items" meta {
    summary = "Create a new item"
    operationId = "createItem"
    receiving(Body.auto<CreateItemRequest>().toLens())
    returning(Status.CREATED, Body.auto<ItemResponse>().toLens())
} bindContract Method.POST to { request ->
    val createRequest = Body.auto<CreateItemRequest>().toLens()(request)
    // Handle request...
    Response(Status.CREATED).with(Body.auto<ItemResponse>().toLens() of response)
}

// Automatic OpenAPI spec generation
fun contract(action: ContractBuilder.() -> Unit) {
    contractRoutes = ContractBuilder().apply(action).build()
}
```

### Functional Filters

HTTP4K's functional approach makes middleware composition elegant:

```kotlin
val authFilter = Filter { next ->
    { request ->
        if (request.hasValidAuth()) {
            next(request)
        } else {
            Response(Status.UNAUTHORIZED)
        }
    }
}

val loggingFilter = Filter { next ->
    { request ->
        val start = System.currentTimeMillis()
        val response = next(request)
        val duration = System.currentTimeMillis() - start
        log.info("${request.method} ${request.uri} -> ${response.status} (${duration}ms)")
        response
    }
}

// Compose filters functionally
val handler = authFilter
    .then(loggingFilter)
    .then(routingHandler)
```

### Testing Benefits

HTTP4K's functional nature makes testing extremely simple:

```kotlin
@Test fun `should return unauthorized for missing auth`() {
    val handler = postFavoriteThingsRoute { error("Should not be called") }
    val request = Request(Method.POST, "/api/v1/favorite_things/test")

    val response = handler(request)

    response.status shouldBe Status.UNAUTHORIZED
}

@Test fun `should save favorite thing successfully`() {
    val mockSave: SaveFavoriteThing = { SaveResult.Success }
    val handler = postFavoriteThingsRoute(mockSave)
    val request = Request(Method.POST, "/api/v1/favorite_things/test")
        .header("X-ID", "user123")
        .header("X-EMAIL", "user@example.com")
        .header("X-MEMBER-OF", "user")

    val response = handler(request)

    response.status shouldBe Status.OK
}
```

## Performance Considerations

### HTTP4K Performance

**Strengths:**

- Minimal overhead due to functional approach
- No reflection or annotation processing at runtime
- Efficient request/response handling
- Small memory footprint

**Benchmarks:**

- Fast startup times (typically < 1 second)
- Low latency for simple operations
- Efficient under load due to immutable objects

### Framework Comparison

| Feature        | HTTP4K | Ktor    | Spring WebFlux |
|----------------|--------|---------|----------------|
| Startup Time   | Fast   | Fast    | Slow           |
| Memory Usage   | Low    | Medium  | High           |
| Learning Curve | Medium | Easy    | Hard           |
| Ecosystem      | Small  | Growing | Large          |
| Coroutines     | Manual | Native  | Reactive       |

## Best Practices

### 1. Separation of Concerns

```kotlin
// ✅ Good: Business logic separated from HTTP concerns
fun postFavoriteThingsRoute(saveFavoriteThing: SaveFavoriteThing): RoutingHttpHandler {
    return routes(
        "/api/v1/favorite_things/{id}" bind Method.POST to { request ->
            val id = request.path("id") ?: return@to Response(Status.BAD_REQUEST)
            when (val result = saveFavoriteThing(FavoriteThing(thingIdentifier = id))) {
                is SaveResult.Success -> Response(Status.OK)
                is SaveResult.Error -> Response(Status.INTERNAL_SERVER_ERROR)
            }
        }
    )
}

// ❌ Bad: Business logic mixed with HTTP handling
fun postFavoriteThingsRoute(repository: FavoriteThingRepository): RoutingHttpHandler {
    return routes(
        "/api/v1/favorite_things/{id}" bind Method.POST to { request ->
            try {
                val id = request.path("id") ?: return@to Response(Status.BAD_REQUEST)
                val result = repository.save(FavoriteThing(thingIdentifier = id))
                // ... database logic mixed with HTTP logic
                Response(Status.OK)
            } catch (e: SQLException) {
                Response(Status.INTERNAL_SERVER_ERROR)
            }
        }
    )
}
```

### 2. Consistent Error Handling

```kotlin
val errorHandlingFilter = Filter { next ->
    { request ->
        try {
            next(request)
        } catch (e: ValidationException) {
            Response(Status.BAD_REQUEST).body(e.message ?: "Validation failed")
        } catch (e: NotFoundException) {
            Response(Status.NOT_FOUND).body(e.message ?: "Resource not found")
        } catch (e: Exception) {
            log.error("Unexpected error processing request", e)
            Response(Status.INTERNAL_SERVER_ERROR).body("Internal server error")
        }
    }
}
```

### 3. Configuration Management

```kotlin
// Use configuration objects instead of hardcoded values
data class ServerConfig(
    val host: String = "127.0.0.1",
    val port: Int = 8080,
    val cors: CorsMode = CorsMode.NO_OP,
    val enableMetrics: Boolean = true,
)

fun buildConfiguredServer(config: ServerConfig, routes: List<RoutingHttpHandler>) =
    buildServer(config.host, config.port) {
        corsMode = config.cors
        routeHandlers { +routes }
        if (config.enableMetrics) {
            filters { +metricsFilter() }
        }
    }
```

## Migration Strategy

If you decide to migrate from HTTP4K to another framework:

### 1. Create New Library Module

```bash
# Create new framework library
mkdir libs/ktor
# Copy and adapt HTTP4K patterns
cp -r libs/http4k/* libs/ktor/
# Update imports and adapt to new framework
```

### 2. Maintain Interface Compatibility

```kotlin
// Keep the same builder pattern
fun buildServer(host: String, port: Int, action: ServerBuilder.() -> Unit): Server

// Keep the same route function signatures  
fun postFavoriteThingsRoute(saveFavoriteThing: SaveFavoriteThing): RouteHandler
```

### 3. Gradual Migration

```kotlin
// Phase 1: Create adapter layer
class Http4kToKtorAdapter(private val ktorServer: KtorServer) : Http4kServer {
    override fun start() = ktorServer.start()
    override fun stop() = ktorServer.stop()
}

// Phase 2: Update applications one by one
// Phase 3: Remove HTTP4K dependency
```

## Conclusion

The vanilla Kotlin project's server architecture provides:

- **Flexibility**: Easy to swap frameworks without major application changes
- **Testability**: Pure functions and dependency injection make testing simple
- **Performance**: HTTP4K provides excellent performance characteristics
- **Maintainability**: Clear separation of concerns and consistent patterns

While HTTP4K is the current choice, the abstraction layers ensure that applications are not locked into this decision,
allowing for future framework migrations as needs evolve.
