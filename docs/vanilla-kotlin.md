# Vanilla Kotlin: A Frameworkless Approach to Microservices

## What is Vanilla Kotlin?

Vanilla Kotlin represents a **frameworkless approach** to building microservices and applications using pure Kotlin and
carefully selected, lightweight libraries. Instead of relying on heavy frameworks like Spring Boot or Micronaut, this
approach emphasizes:

- **Explicit over implicit** - Every dependency and configuration is visible and intentional
- **Simplicity over complexity** - Using the minimum necessary components to solve problems
- **Control over convention** - Developers maintain full control over application behavior
- **Performance over convenience** - Optimizing for runtime efficiency and resource usage

## Why Not Spring?

While Spring Boot is popular, it introduces significant complexity that often makes development harder rather than
easier:

### The Problem with Framework Magic

Spring's "magic" comes at a cost:

- **Hidden complexity**: Annotations trigger complex initialization chains that are difficult to debug
- **Implicit dependencies**: Components are wired together through reflection and proxies
- **Startup overhead**: Extensive classpath scanning and bean initialization slow down development cycles
- **Memory footprint**: Heavy framework overhead consumes resources that could be used for business logic
- **Debugging difficulties**: Stack traces become convoluted with framework internals

### Real-World Impact

As discussed in Dan Tanner's excellent article ["Spring Rites"](https://dantanner.com/post/spring-rites/), frameworks
like Spring often:

1. **Obscure the actual work being done** - Simple HTTP handlers become buried under layers of annotations
2. **Create coupling to framework internals** - Business logic becomes tightly bound to Spring concepts
3. **Increase cognitive load** - Developers must understand both the business domain AND the framework's mental model
4. **Slow down development** - Complex initialization and testing setups increase feedback cycles

## The Vanilla Kotlin Alternative

This project demonstrates how to build production-ready microservices without framework overhead:

### Core Principles

1. **Direct Library Usage**: Use libraries like HTTP4K, JDBI, and Kafka clients directly
2. **Explicit Configuration**: All wiring and setup is visible in code
3. **Minimal Abstraction**: Only abstract when it provides clear value
4. **Fast Feedback**: Quick startup times and simple testing

### Architecture Overview

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   HTTP Layer    │    │  Business Logic  │    │   Data Layer    │
│   (HTTP4K)      │───▶│   (Pure Kotlin)  │───▶│    (JDBI)       │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                        │                        │
         ▼                        ▼                        ▼
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Monitoring    │    │   Event Stream   │    │   Database      │
│   (OpenTel)     │    │    (Kafka)       │    │  (PostgreSQL)   │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

### Key Benefits

#### 🚀 **Performance**

- **Fast startup**: Applications start in milliseconds, not seconds
- **Low memory**: Minimal framework overhead means more resources for business logic
- **Efficient runtime**: No reflection-heavy proxy layers

#### 🔧 **Maintainability**

- **Explicit dependencies**: Easy to understand what each component does
- **Simple debugging**: Clean stack traces without framework noise
- **Testable**: Pure functions and explicit dependencies make testing straightforward

#### 📈 **Scalability**

- **Resource efficiency**: Lower memory and CPU usage per instance
- **Container-friendly**: Smaller images and faster cold starts
- **Kubernetes-native**: Designed for cloud-native deployment patterns

## Project Structure

This codebase demonstrates the vanilla Kotlin approach across multiple applications:

### Applications (`apps/`)

- **API Service**: RESTful HTTP service using HTTP4K
- **Kafka Transformer**: Event processing service
- **Bulk Inserter**: High-throughput data processing
- **Outbox Processor**: Reliable event publishing

### Libraries (`libs/`)

- **Common**: Shared models and utilities
- **HTTP4K**: HTTP server and client abstractions
- **Database**: JDBI-based data access
- **Kafka**: Event streaming utilities
- **Metrics**: OpenTelemetry integration

### Key Technologies

| Component     | Library          | Why Not Spring Alternative              |
|---------------|------------------|-----------------------------------------|
| HTTP Server   | HTTP4K           | Lightweight, functional, no annotations |
| Database      | JDBI             | SQL-first, no ORM complexity            |
| JSON          | Jackson          | Direct usage, no auto-configuration     |
| Testing       | JUnit 5 + Kotest | Simple, fast test execution             |
| Configuration | Hoplite          | Type-safe, explicit config loading      |
| Metrics       | OpenTelemetry    | Industry standard, no vendor lock-in    |

## Getting Started

### Prerequisites

- JDK 21+
- Gradle 8.5+
- Docker (for local development)

### Running the Application

```bash
# Start dependencies
docker-compose up -d

# Run database migrations
./gradlew :db-migration:flywayMigrate

# Start the API service
./gradlew :apps:api:run

# Run tests
./gradlew test
```

### Development Workflow

1. **Make changes** - Edit Kotlin source files
2. **Fast feedback** - Applications restart in milliseconds
3. **Test locally** - Simple unit and integration tests
4. **Deploy** - Lightweight containers with fast startup

## Comparison: Spring vs Vanilla Kotlin

| Aspect         | Spring Boot            | Vanilla Kotlin                          |
|----------------|------------------------|-----------------------------------------|
| Startup Time   | 10-30 seconds          | 100-500 milliseconds                    |
| Memory Usage   | 200-500MB              | 50-150MB                                |
| JAR Size       | 50-100MB               | 15-30MB                                 |
| Configuration  | Annotations + YAML     | Explicit Kotlin code leveraging hoplite |
| Testing        | Complex test slices    | Simple unit & integration tests         |
| Debugging      | Framework stack traces | Clean application traces                |
| Learning Curve | Framework + domain     | Just the domain                         |

## Best Practices

### 1. Keep It Simple

- Use the minimum necessary libraries
- Avoid premature abstraction
- Prefer composition over inheritance

### 2. Make Dependencies Explicit

- Constructor injection over field injection
- Explicit configuration over auto-configuration
- Clear separation of concerns

### 3. Optimize for Change

- Pure functions where possible
- Immutable data structures
- Explicit error handling

### 4. Test Effectively

- Fast unit tests for business logic
- Integration tests for external dependencies
- End-to-end tests for critical paths

## Conclusion

Vanilla Kotlin demonstrates that you don't need heavy frameworks to build production-ready microservices. By choosing
simplicity over complexity, explicitness over magic, and performance over convenience, we can create systems that are:

- **Easier to understand** and maintain
- **Faster to develop** and deploy
- **More efficient** in production
- **Less prone to framework-specific issues**

The result is code that focuses on solving business problems rather than wrestling with framework complexity.

---

*"The best code is no code at all. The second best code is simple, explicit code that solves the problem at hand."* 
