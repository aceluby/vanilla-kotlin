# Testing Strategy

This document outlines the testing approach used in the vanilla Kotlin project, explaining the different types of tests
and their purposes.

## Test Types

### Functional Tests

**Status: Not Implemented** - Since this application is not deployed to any environment, there are no
functional/end-to-end tests. All testing is done at the integration and unit test levels.

### Integration Tests

Integration tests verify that multiple components work together correctly. In this project, integration tests are
located in `AppTest` classes and test the full application stack including:

- HTTP endpoints
- Database connections
- Kafka producers/consumers
- External service integrations
- Docker Compose services

### Unit Tests

Unit tests focus on testing individual components in isolation, typically business logic within services, event
handlers, and utility classes. These tests use functional injection to avoid complex mocking frameworks.

## Integration Test Examples

Integration tests in `AppTest` classes start real applications and external dependencies using Docker Compose.

### API Integration Test (`apps/api/src/test/kotlin/vanillakotlin/api/AppTest.kt`)

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppTest {
    private val mockThingServer = MockThingServer()
    private var app: App? = null

    @BeforeAll fun beforeAll() {
        // Override external service URLs to point to mock servers
        withSystemProperties(
            mapOf(
                "config.override.http.client.thing.gateway.baseUrl" to "http://localhost:${mockThingServer.port}",
            ),
        ) {
            app = App()
            app?.start()
        }
    }

    @Test fun `health check ok`() {
        val request = Request(Method.GET, "http://localhost:${app?.httpServerPort}/health")
        val response = JavaHttpClient()(request)
        response.status shouldBe Status.OK
    }
}
```

**Key Features:**

- Starts the full application
- Uses real HTTP server and database connections
- Mocks external dependencies (ThingServer)
- Tests actual HTTP endpoints
- Cleans up database state between tests

### Kafka Transformer Integration Test (
`apps/kafka-transformer/src/test/kotlin/vanillakotlin/kafkatransformer/AppTest.kt`)

```kotlin
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AppTest {
    private var app: App? = null
    private val mockThingServer = MockWebServer()
    private val broker = if (System.getenv().containsKey("CI")) "kafka:9092" else "localhost:9092"

    @BeforeAll fun beforeAll() {
        // Create dynamic Kafka topics for test isolation
        val adminClient = AdminClient.create(mapOf("bootstrap.servers" to broker))
        adminClient.createTopics(listOf(NewTopic(sourceTopicName, 1, 1)))
        adminClient.createTopics(listOf(NewTopic(sinkTopicName, 1, 1)))

        // Start app with test configuration
        withSystemProperties(overriddenConfiguration) {
            app = App()
            app?.start()
        }
    }

    @Test fun `the kafkatransformer should consume, process, and forward a valid message successfully`() {
        // Send message to source topic
        producer.send(KafkaOutputMessage(key = "$userName:$thingIdentifier", value = thingIdentifier))

        // Verify message was transformed and sent to sink topic
        val receivedMessages = collectMessages(broker = broker, topic = sinkTopicName)

        receivedMessages.size shouldBe 1
        // Verify message content, headers, and metadata
    }
}
```

**Key Features:**

- Creates dynamic Kafka topics for test isolation
- Tests end-to-end message processing
- Verifies message transformation logic
- Uses real Kafka brokers (Docker Compose)
- Mocks external HTTP services

### Outbox Processor Integration Test (`apps/outbox-processor/src/test/kotlin/vanillakotlin/outboxprocessor/AppTest.kt`)

```kotlin
@Test fun `the outbox processor should pop and send successfully`() {
    val messageKey = randomString()
    val bodyString = randomString()

    // Insert message into outbox table
    jdbi.inTransaction<Unit, Exception> { handle ->
        insertOutbox(handle, buildOutbox(messageKey = messageKey).copy(body = bodyString.toByteArray()))
    }

    // Verify message appears in Kafka topic
    val receivedMessages = collectMessages(
        broker = broker,
        topic = sinkTopicName,
        filter = { kafkaMessage -> kafkaMessage.key == messageKey },
        stopWhen = { messages -> messages.size == 1 },
    )

    receivedMessages.size shouldBe 1
    String(receivedMessages.first().body!!) shouldBe bodyString

    // Verify message was removed from database
    getRowCount(jdbi, messageKey) shouldBe 0
}
```

**Key Features:**

- Tests transactional outbox pattern
- Verifies database-to-Kafka message flow
- Uses real database transactions
- Confirms cleanup of processed messages

## Unit Test Examples

Unit tests focus on business logic and use functional injection for clean, readable tests.

### Service Unit Test (`apps/api/src/test/kotlin/vanillakotlin/api/favoritethings/FavoriteThingsServiceTest.kt`)

```kotlin
@Test fun `saveFavoriteThing success`() {
    val service = FavoriteThingsService(
        upsertFavoriteThing = { testFavorites[0] },  // Simple lambda - no mocking framework
        deleteFavoriteThingRepository = { 1 },
        findAllFavoriteThings = { testFavorites },
        getThingDetails = { thingId -> buildTestThing(thingId) },
    )

    val result = service.saveFavoriteThing(FavoriteThing(thingIdentifier = testThings[0]))

    result shouldBe SaveResult.Success
}

@Test fun `deleteFavoriteThing SQL Exception`() {
    val service = FavoriteThingsService(
        upsertFavoriteThing = { testFavorites[0] },
        deleteFavoriteThingRepository = { throw SQLException("Failure") }, // Direct exception testing
        findAllFavoriteThings = { testFavorites },
        getThingDetails = { thingId -> buildTestThing(thingId) },
    )

    val result = service.deleteFavoriteThing(testFavorites[0].thingIdentifier)

    result shouldBe DeleteResult.Error(DeleteErrorType.DATABASE_ERROR)
}
```

**Key Features:**

- No mocking frameworks - uses functional injection
- Tests business logic in isolation
- Simple lambda functions replace complex mock setups
- Easy to read and understand test scenarios

### Event Handler Unit Test (
`apps/bulk-inserter/src/test/kotlin/vanillakotlin/bulkinserter/BulkInserterEventHandlerTest.kt`)

```kotlin
@Test fun `a populated message should be added to the batch`() {
    val batchCount = AtomicInteger(0)
    val addedItems = mutableListOf<FavoriteThing>()
    val runBatchCount = AtomicInteger(0)

    val handler = BulkInserterHandler(
        addToBatch = { item ->
            addedItems.add(item)
            batchCount.incrementAndGet()
        },
        runBatch = {
            runBatchCount.incrementAndGet()
            1
        },
    )

    handler.processSequence(sequenceOf(testKafkaMessage))

    assertSoftly {
        batchCount.get() shouldBe 1
        runBatchCount.get() shouldBe 1
        addedItems.size shouldBe 1
        addedItems[0].thingIdentifier shouldBe userFavoriteThing.thingIdentifier
    }
}
```

**Key Features:**

- Tests message processing logic
- Uses simple counters and collections instead of mocks
- Verifies batch processing behavior
- Clear assertions on expected outcomes

### Repository Integration Test (`libs/db/src/test/kotlin/vanillakotlin/db/repository/FavoriteThingRepositoryTest.kt`)

```kotlin
class FavoriteThingRepositoryTest {
    private val jdbi by lazy { buildTestDb() }
    private val repository by lazy { FavoriteThingRepository(jdbi) }

    @Test fun `upsert favorite thing - insert new thing`() {
        val favoriteThing = FavoriteThing(id = null, thingIdentifier = randomThing())

        val result = repository.upsert(favoriteThing)

        assertSoftly {
            result.id shouldNotBe null
            result.thingIdentifier shouldBe favoriteThing.thingIdentifier
            result.createdTs shouldNotBe null
            result.updatedTs shouldNotBe null
        }
    }
}
```

**Key Features:**

- Tests database operations with real database
- Uses test database configuration
- Verifies CRUD operations
- Tests batch operations

## Docker Compose Integration

The project uses Docker Compose to provide real external dependencies for integration tests:

```yaml
# docker-compose.yml
services:
  kafka:
    image: apache/kafka:3.8.1
    ports:
      - "9092:9092"

  db:
    image: postgres:15.8-alpine
    environment:
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=password
    ports:
      - '5432:5432'
    volumes:
      - ./db-migration/initdb.d/:/docker-entrypoint-initdb.d
```

**Benefits:**

- Real Kafka and PostgreSQL instances
- Consistent test environment
- Database migrations applied automatically
- Network isolation between tests

## Test Configuration

### Environment-Specific Configuration

Tests use different brokers based on environment:

```kotlin
private val broker = if (System.getenv().containsKey("CI")) "kafka:9092" else "localhost:9092"
```

### Dynamic Resource Creation

Tests create isolated resources to prevent interference:

```kotlin
// Dynamic Kafka topics
private val sourceTopicName = randomString()
private val sinkTopicName = randomString()

// Dynamic test data
private val testThing = buildTestThing(thingIdentifier = randomThing())
```

### Configuration Overrides

Tests override application configuration using system properties:

```kotlin
withSystemProperties(
    mapOf(
        "config.override.kafka.consumer.topics" to sourceTopicName,
        "config.override.kafka.producer.topic" to sinkTopicName,
    )
) {
    app = App()
    app?.start()
}
```

## Testing Best Practices

### 1. Test Isolation

- Each test creates its own Kafka topics
- Database state is cleaned between tests
- Random test data prevents conflicts

### 2. Functional Injection Over Mocking

- Use lambda functions instead of mock frameworks
- Easier to read and maintain
- Better compile-time safety

### 3. Real Dependencies in Integration Tests

- Use Docker Compose for external services
- Test against real databases and message brokers
- Catch integration issues early

### 4. Clear Test Structure

- Arrange-Act-Assert pattern
- Descriptive test names
- Comprehensive assertions with `assertSoftly`

### 5. Proper Cleanup

- `@AfterAll` methods clean up resources
- Try-catch blocks for cleanup operations
- Proper connection and client closing

## Running Tests

### All Tests

```bash
./gradlew test
```

### Specific Module

```bash
./gradlew :apps:api:test
./gradlew :libs:kafka:test
```

### Integration Tests Only

```bash
./gradlew test --tests "*AppTest"
```

### Unit Tests Only

```bash
./gradlew test --tests "*ServiceTest" --tests "*HandlerTest"
```

## Test Coverage

The project maintains comprehensive test coverage across:

- **Integration Tests**: Full application workflows
- **Unit Tests**: Business logic and edge cases
- **Repository Tests**: Database operations
- **HTTP Client Tests**: External service integration
- **Kafka Tests**: Message processing and transformation

This multi-layered approach ensures both individual components and their interactions are thoroughly tested. 
