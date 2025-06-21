# Vanilla Kotlin

A modern, production-ready Kotlin microservices framework built with best practices and enterprise-grade patterns. This project provides a comprehensive set of libraries and applications for building scalable, maintainable services with Kotlin.

## 🚀 Features

### Core Libraries

- **Common**: Shared utilities, models, and application interfaces
- **Database**: JDBI-based database access with connection pooling and migrations
- **HTTP4K**: HTTP server and client utilities with OpenAPI support
- **Kafka**: Production-ready Kafka producer and consumer implementations
- **Metrics**: OpenTelemetry-based metrics publishing
- **RocksDB**: High-performance embedded key-value store integration
- **Client**: HTTP client utilities for external service integration

### Sample Applications

- **API**: RESTful web service with health checks and OpenAPI documentation
- **Bulk Inserter**: High-throughput data ingestion service
- **Kafka Transformer**: Stream processing application for message transformation
- **Outbox Processor**: Transactional outbox pattern implementation

## 🏗️ Architecture

This project follows a modular architecture with:

- **Multi-module Gradle build** with consistent configuration
- **Clean separation** between libraries and applications
- **Production-ready patterns** including health checks, metrics, and error handling
- **Modern Kotlin practices** with coroutines and type-safe builders
- **Comprehensive testing** with JUnit 5 and test containers

## 🛠️ Technology Stack

- **Kotlin** 1.9+ with JVM toolchain 21
- **Gradle** with Kotlin DSL
- **HTTP4K** for web services
- **Apache Kafka** for messaging
- **PostgreSQL** for persistence
- **RocksDB** for embedded storage
- **JDBI** for database access
- **OpenTelemetry** for observability
- **Docker Compose** for local development

## 🚀 Quick Start

### Prerequisites

- Java 21+
- Docker and Docker Compose

### Local Development

1. **Clone the repository**
   ```bash
   git clone https://github.com/your-org/vanilla-kotlin-public.git
   cd vanilla-kotlin-public
   ```

2. **Start infrastructure services**
   ```bash
   docker-compose up -d
   ```

3. **Run database migrations**
   ```bash
   ./gradlew :db-migration:flywayMigrate
   ```

4. **Build the project**
   ```bash
   ./gradlew build
   ```

5. **Run the API application**
   ```bash
   ./gradlew :apps:api:run
   ```

The API will be available at `http://localhost:8080` with health checks at `/health`.

## 📚 Module Overview

### Libraries (`libs/`)

#### Common (`libs/common`)
Core utilities and shared interfaces:
- Application lifecycle interfaces (`VanillaApp`)
- JSON serialization with Jackson
- Extension functions and utilities
- Common data models

#### Database (`libs/db`)
Database access layer:
- JDBI configuration and setup
- Repository patterns
- Connection pooling
- Transaction management

#### HTTP4K (`libs/http4k`)
Web service utilities:
- Server builder DSL
- Contract-based routing
- OpenAPI documentation
- CORS and security filters

#### Kafka (`libs/kafka`)
Messaging infrastructure:
- Type-safe producer and consumer APIs
- Automatic serialization/deserialization
- Error handling and retry logic
- Metrics integration

#### Metrics (`libs/metrics`)
Observability support:
- OpenTelemetry integration
- Counter, timer, and gauge metrics
- Standardized metric publishing

### Applications (`apps/`)

#### API (`apps/api`)
RESTful web service providing:
- Item management endpoints
- Health check endpoints
- OpenAPI documentation
- Metrics publishing

#### Bulk Inserter (`apps/bulk-inserter`)
High-throughput data ingestion service for batch processing.

#### Kafka Transformer (`apps/kafka-transformer`)
Stream processing application for transforming messages between Kafka topics.

#### Outbox Processor (`apps/outbox-processor`)
Implementation of the transactional outbox pattern for reliable message publishing.

## 🧪 Testing

Run all tests:
```bash
./gradlew test
```

Run tests for a specific module:
```bash
./gradlew :libs:kafka:test
```

The project includes:
- Unit tests with mocking
- Integration tests with test containers
- Comprehensive test coverage

## 🏗️ Building and Deployment

### Build

```bash
./gradlew build
```

### Create distribution

```bash
./gradlew :apps:api:installDist
```

### Docker

Each application can be containerized. Example Dockerfile:

```dockerfile
FROM openjdk:21-jre-slim
COPY apps/api/build/install/api /app
WORKDIR /app
CMD ["./bin/api"]
```

## 📖 Usage Examples

### Creating a Kafka Producer

```kotlin
val producer = KafkaProducer<String>(
    config = KafkaProducer.Config(
        broker = "localhost:9092",
        topic = "my-topic"
    ),
    publishTimerMetric = metricsPublisher
).start()

val message = KafkaOutputMessage(
    key = "message-key",
    value = "Hello, Kafka!"
)

producer.send(message)
```

### Building an HTTP Service

```kotlin
val server = httpServer(port = 8080) {
    routeHandlers {
        add("/api/items" bind Method.GET to { request ->
            Response(Status.OK).json(itemService.getAllItems())
        })
    }
    
    healthMonitors {
        add(HealthMonitor("database") { databaseHealthCheck() })
    }
}

server.start()
```

### Database Access

```kotlin
class ItemRepository(private val jdbi: Jdbi) {
    fun findById(id: String): Item? = jdbi.withHandle { handle ->
        handle.createQuery("SELECT * FROM items WHERE id = :id")
            .bind("id", id)
            .mapTo<Item>()
            .findOne()
            .orElse(null)
    }
}
```

## 🔧 Configuration

Applications use environment-based configuration. Example:

```kotlin
data class AppConfig(
    val database: DatabaseConfig,
    val kafka: KafkaConfig,
    val server: ServerConfig
)

data class DatabaseConfig(
    val url: String = System.getenv("DATABASE_URL") ?: "jdbc:postgresql://localhost:5432/app",
    val username: String = System.getenv("DATABASE_USER") ?: "postgres",
    val password: String = System.getenv("DATABASE_PASSWORD") ?: "password"
)
```

## 🤝 Contributing

We welcome contributions! Please see our contributing guidelines:

1. Fork the repository
2. Create a feature branch
3. Make your changes with tests
4. Ensure all tests pass: `./gradlew test`
5. Run code formatting: `./gradlew spotlessApply`
6. Submit a pull request

### Code Style

This project uses:
- **Spotless** with **ktlint** for code formatting
- **Warnings as errors** to maintain code quality
- **Comprehensive testing** requirements

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](#license) file for details.

### MIT License

```
MIT License

Copyright (c) 2024 Vanilla Kotlin Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

## 🆘 Support

- **Documentation**: Check the inline documentation and tests for usage examples
- **Issues**: Report bugs and request features via GitHub Issues
- **Discussions**: Join our GitHub Discussions for questions and community support

## 🙏 Acknowledgments

This project builds upon excellent open-source libraries:
- [HTTP4K](https://http4k.org/) for HTTP handling
- [Apache Kafka](https://kafka.apache.org/) for messaging
- [JDBI](https://jdbi.org/) for database access
- [RocksDB](https://rocksdb.org/) for embedded storage
- [OpenTelemetry](https://opentelemetry.io/) for observability

---

**Built with ❤️ in Kotlin**

