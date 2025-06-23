# Database Guide

This document explains the database setup and patterns used in the vanilla Kotlin project, covering Docker Compose for
local development, Flyway for schema migrations, and JDBI for clean, efficient data access.

## Overview

The project uses a modern database stack that prioritizes simplicity and maintainability:

- **Docker Compose**: Local PostgreSQL database with health checks
- **Flyway**: Version-controlled database migrations
- **JDBI**: Lightweight SQL-first data access layer
- **Plain SQL**: No ORM complexity, just readable SQL queries

## Docker Compose Database Setup

The project uses Docker Compose to provide a consistent local development database environment.

### Configuration

```yaml
# docker-compose.yml
services:
  db:
    image: postgres:15.8-alpine
    container_name: vanilla-kotlin-db
    environment:
      - POSTGRES_USER=postgres
      - POSTGRES_PASSWORD=password
    ports:
      - '5432:5432'
    networks:
      - app
    volumes:
      - ./db-migration/initdb.d/:/docker-entrypoint-initdb.d
    healthcheck:
      test: [ "CMD", "sh", "-c", "pg_isready -U $$POSTGRES_USER -h $$(hostname -i)" ]
      interval: 5s
      timeout: 5s
      retries: 5
```

### Database Initialization

The database is automatically initialized with the required users and database:

```sql
-- db-migration/initdb.d/01_docker-bootstrap.sql
create database vanilla_kotlin;

-- create the administrative user
create user vanilla_kotlin with password 'vanilla_kotlin' superuser;

-- the application user (privileges handled by Flyway afterMigrate.sql)
create user vanilla_kotlin_app with password 'vanilla_kotlin_app';
```

### Benefits

**1. Consistent Environment**

- Same database version across all developers
- Automatic initialization with required users and database
- Health checks ensure database is ready before applications start

**2. Zero Configuration**

- No manual database setup required
- Works identically in development and CI environments
- Isolated from host system PostgreSQL installations

**3. Easy Management**

```bash
# Start database
docker-compose up -d db

# View logs
docker-compose logs db

# Stop and clean up
docker-compose down
```

## Flyway Database Migrations

[Flyway](https://flywaydb.org/) handles all database schema changes through version-controlled migration scripts.

### Configuration

```kotlin
// db-migration/db-migration.gradle.kts
plugins {
    alias(libs.plugins.flyway)
}

flyway {
    val dbhost = when {
        System.getenv().containsKey("DATABASE_HOST") -> System.getenv("DATABASE_HOST")
        System.getenv().containsKey("CI") -> "postgres"
        else -> "localhost"
    }
    baselineOnMigrate = true
    baselineVersion = "0"
    url = "jdbc:postgresql://$dbhost:5432/vanilla_kotlin"
    user = System.getenv("DATABASE_USERNAME") ?: "vanilla_kotlin"
    password = System.getenv("DATABASE_PASSWORD") ?: "vanilla_kotlin"
}
```

### Migration Structure

```
db-migration/src/main/resources/db/migration/
├── V01__add_favorite_item.sql    # Initial table creation
├── V02__add_outbox.sql          # Add outbox pattern table
└── afterMigrate.sql             # Post-migration tasks
```

### Migration Examples

**Initial Table Creation (V01__add_favorite_item.sql)**:

```sql
CREATE TABLE favorite_thing (
    id         INTEGER GENERATED ALWAYS AS IDENTITY,
    thing      VARCHAR(511) NOT NULL,
    created_ts TIMESTAMP    NOT NULL DEFAULT (CURRENT_TIMESTAMP),
    updated_ts TIMESTAMP    NOT NULL DEFAULT (CURRENT_TIMESTAMP),
    PRIMARY KEY (id)
);

CREATE UNIQUE INDEX uidx_thing ON favorite_thing (thing);

ALTER TABLE favorite_thing
    ADD CONSTRAINT unique_thing UNIQUE USING INDEX uidx_thing;
```

**Adding New Tables (V02__add_outbox.sql)**:

```sql
CREATE TABLE outbox (
    id          INTEGER GENERATED ALWAYS AS IDENTITY,
    message_key TEXT      NOT NULL,
    headers     BYTEA,
    body        BYTEA,
    created_ts  TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP),
    PRIMARY KEY (id)
);

CREATE INDEX idx_outbox_created ON outbox (created_ts);
```

**Post-Migration Tasks (afterMigrate.sql)**:

```sql
-- Run after every migration
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO vanilla_kotlin_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO vanilla_kotlin_app;

-- Secure Flyway metadata
REVOKE ALL ON TABLE flyway_schema_history FROM vanilla_kotlin_app;
```

### Migration Best Practices

**1. Naming Convention**

- `V{version}__{description}.sql` for versioned migrations
- Use sequential version numbers (V01, V02, V03...)
- Descriptive names that explain the change

**2. Idempotent Operations**

- Migrations should be safe to run multiple times
- Use `IF NOT EXISTS` clauses where appropriate
- Always test migrations on a copy of production data

**3. Incremental Changes**

- Small, focused migrations are easier to review and rollback
- Separate data changes from schema changes
- Document breaking changes clearly

### Usage

```bash
# Run pending migrations
./gradlew :db-migration:flywayMigrate

# Check migration status
./gradlew :db-migration:flywayInfo

# Validate migration checksums
./gradlew :db-migration:flywayValidate

# Clean database (development only!)
./gradlew :db-migration:flywayClean
```

## JDBI Data Access Layer

[JDBI](https://jdbi.org/) provides a clean, SQL-first approach to database access without the complexity of full ORMs.

### Configuration

```kotlin
// libs/db/src/main/kotlin/vanillakotlin/db/JdbiConfig.kt
data class DbConfig(
    val username: String,
    val password: String,
    val databaseName: String,
    val host: String = "localhost",
    val port: Int = 5432,
) {
    val jdbcUrl: String
        get() = "jdbc:postgresql://$host:$port/$databaseName"
}

fun createJdbi(config: DbConfig, objectMapper: ObjectMapper): Jdbi {
    val jdbi = Jdbi.create(config.jdbcUrl, config.username, config.password)
        .installPlugin(PostgresPlugin())

    // Register row mappers for automatic result mapping
    jdbi.registerRowMapper(FavoriteThing::class.java, FavoriteThingMapper())
    jdbi.registerRowMapper(Outbox::class.java, OutboxMapper())

    return jdbi
}
```

### Repository Pattern

The project uses repository classes that encapsulate all SQL operations for an entity:

```kotlin
class FavoriteThingRepository(private val jdbi: Jdbi) {

    fun upsert(favorite: FavoriteThing): FavoriteThing =
        jdbi.inTransaction<FavoriteThing, Exception> { handle ->
            val favoriteThing = handle.createQuery(
                """
                INSERT INTO favorite_thing (thing)
                VALUES (:thing)
                ON CONFLICT (thing) DO UPDATE SET
                    updated_ts = CURRENT_TIMESTAMP
                RETURNING *
                """
            )
                .bind("thing", favorite.thingIdentifier)
                .mapTo(FavoriteThing::class.java)
                .one()

            // Side effect: add to outbox for event publishing
            insertOutbox(handle, favoriteThing.buildOutbox())

            favoriteThing
        }

    fun findAll(): List<FavoriteThing> =
        jdbi.withHandle<List<FavoriteThing>, Exception> { handle ->
            handle.createQuery("SELECT * FROM favorite_thing")
                .mapTo(FavoriteThing::class.java)
                .list()
        }
}
```

### Custom Row Mappers

JDBI uses row mappers to convert SQL results to Kotlin objects:

```kotlin
class FavoriteThingMapper : RowMapper<FavoriteThing> {
    override fun map(rs: ResultSet, ctx: StatementContext): FavoriteThing =
        FavoriteThing(
            id = rs.getLong("id"),
            thingIdentifier = rs.getString("thing"),
            createdTs = rs.getTimestamp("created_ts").toLocalDateTime(),
            updatedTs = rs.getTimestamp("updated_ts").toLocalDateTime(),
        )
}
```

### Transaction Management

JDBI provides clean transaction handling:

```kotlin
// Simple transaction
jdbi.inTransaction<ReturnType, Exception> { handle ->
    // All operations use same connection/transaction
    handle.createUpdate("INSERT INTO ...").execute()
    handle.createQuery("SELECT ...").mapTo(MyClass::class.java).list()
}

// Handle-based operations (auto-commit)
jdbi.withHandle<ReturnType, Exception> { handle ->
    handle.createQuery("SELECT ...").mapTo(MyClass::class.java).one()
}
```

### Batch Operations

For high-performance bulk operations, JDBI supports batching:

```kotlin
fun runBatch(): Int {
    val batchSize = batch.size
    if (batch.isEmpty()) return 0

    jdbi.useTransaction<Exception> { handle ->
        val updateBatch = handle.prepareBatch(
            "UPDATE favorite_thing SET updated_ts = CURRENT_TIMESTAMP WHERE thing = ?"
        )
        val insertBatch = handle.prepareBatch(
            "INSERT INTO favorite_thing (thing) VALUES (?) ON CONFLICT DO NOTHING"
        )

        batch.forEach { favoriteThing ->
            updateBatch.bind(0, favoriteThing.thingIdentifier).add()
            insertBatch.bind(0, favoriteThing.thingIdentifier).add()
        }

        val updateResults = updateBatch.execute()

        // Only run inserts for rows that weren't updated
        if (updateResults.sum() < batch.size) {
            insertBatch.execute()
        }

        batch.clear()
    }

    return batchSize
}
```

## Functional Interfaces for Testing

The repository uses SAM (Single Abstract Method) interfaces for easy testing:

```kotlin
/** SAM interface for FavoriteThingRepository.upsert */
fun interface UpsertFavoriteThing {
    operator fun invoke(favorite: FavoriteThing): FavoriteThing
}

/** SAM interface for FavoriteThingRepository.findAll */
fun interface FindAllFavoriteThings {
    operator fun invoke(): List<FavoriteThing>
}

// Usage in service
class FavoriteThingsService(
    private val upsertFavoriteThing: UpsertFavoriteThing,
    private val findAllFavoriteThings: FindAllFavoriteThings,
    // ... other dependencies
) {
    fun saveFavoriteThing(favorite: FavoriteThing): SaveResult = try {
        upsertFavoriteThing(favorite)
        SaveResult.Success
    } catch (e: Exception) {
        SaveResult.Error(SaveErrorType.DATABASE_ERROR)
    }
}
```

### Testing Benefits

This approach makes testing much cleaner than traditional mocking:

```kotlin
@Test fun `saveFavoriteThing success`() {
    val service = FavoriteThingsService(
        upsertFavoriteThing = { testFavorites[0] },  // Simple lambda
        findAllFavoriteThings = { testFavorites },
        // ... other simple functions
    )

    val result = service.saveFavoriteThing(FavoriteThing(thingIdentifier = "test"))

    result shouldBe SaveResult.Success
}
```

## Database Patterns

### 1. Outbox Pattern

The project implements the transactional outbox pattern for reliable event publishing:

```kotlin
// Within a transaction, both business logic and event are persisted
fun upsert(favorite: FavoriteThing): FavoriteThing =
    jdbi.inTransaction<FavoriteThing, Exception> { handle ->
        val favoriteThing = handle.createQuery(upsertSql)
            .bind("thing", favorite.thingIdentifier)
            .mapTo(FavoriteThing::class.java)
            .one()

        // Atomically add event to outbox
        insertOutbox(handle, favoriteThing.buildOutbox())

        favoriteThing
    }
```

### 2. Optimistic Batch Processing

For high-throughput scenarios, the project uses an optimistic batch approach:

1. Try to update existing records
2. Insert only records that weren't updated
3. This avoids individual upsert operations that can't be batched effectively

### 3. Generated Identity Columns

Uses PostgreSQL's `GENERATED ALWAYS AS IDENTITY` for primary keys:

```sql
CREATE TABLE favorite_thing (
    id INTEGER GENERATED ALWAYS AS IDENTITY,
    -- other columns
    PRIMARY KEY (id)
);
```

Benefits:

- More portable than `SERIAL`
- Explicit about auto-generation
- Better performance than UUIDs for primary keys

## Development Workflow

### 1. Local Development Setup

```bash
# Start database
docker-compose up -d db

# Run migrations
./gradlew :db-migration:flywayMigrate

# Verify setup
docker-compose exec db psql -U vanilla_kotlin -d vanilla_kotlin -c "\dt"
```

### 2. Adding New Tables

1. **Create migration file**:
   ```bash
   # Create V03__add_new_table.sql
   touch db-migration/src/main/resources/db/migration/V03__add_new_table.sql
   ```

2. **Write SQL**:
   ```sql
   CREATE TABLE new_table (
       id INTEGER GENERATED ALWAYS AS IDENTITY,
       name VARCHAR(255) NOT NULL,
       created_ts TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
       PRIMARY KEY (id)
   );
   ```

3. **Run migration**:
   ```bash
   ./gradlew :db-migration:flywayMigrate
   ```

4. **Create repository and mapper**:
   ```kotlin
   class NewTableRepository(private val jdbi: Jdbi) {
       fun findAll(): List<NewTable> = jdbi.withHandle { handle ->
           handle.createQuery("SELECT * FROM new_table")
               .mapTo(NewTable::class.java)
               .list()
       }
   }
   ```

### 3. Testing Database Code

```kotlin
class FavoriteThingRepositoryTest {
    private val jdbi by lazy { buildTestDb() }
    private val repository by lazy { FavoriteThingRepository(jdbi) }

    @Test fun `upsert creates new record`() {
        val favorite = FavoriteThing(thingIdentifier = randomThing())

        val result = repository.upsert(favorite)

        assertSoftly {
            result.id shouldNotBe null
            result.thingIdentifier shouldBe favorite.thingIdentifier
            result.createdTs shouldNotBe null
        }
    }
}
```

## Performance Considerations

### 1. Connection Pooling

JDBI automatically handles connection pooling through the underlying JDBC driver.

### 2. Batch Operations

Use batch operations for bulk inserts/updates:

- Significant performance improvement over individual operations
- Reduced network round trips
- Better transaction isolation

### 3. Indexing Strategy

```sql
-- Unique constraints with explicit indexes
CREATE UNIQUE INDEX uidx_thing ON favorite_thing (thing);
ALTER TABLE favorite_thing
    ADD CONSTRAINT unique_thing UNIQUE USING INDEX uidx_thing;

-- Query optimization indexes
CREATE INDEX idx_outbox_created ON outbox (created_ts);
```

### 4. Query Optimization

- Use explicit column lists instead of `SELECT *` in production code
- Leverage PostgreSQL's `RETURNING` clause to avoid separate queries
- Use appropriate data types (VARCHAR with limits, not TEXT everywhere)

## Security Best Practices

### 1. Principle of Least Privilege

```sql
-- Application user has only necessary permissions
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO vanilla_kotlin_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO vanilla_kotlin_app;

-- Deny access to migration metadata
REVOKE ALL ON TABLE flyway_schema_history FROM vanilla_kotlin_app;
```

### 2. SQL Injection Prevention

JDBI's parameter binding prevents SQL injection:

```kotlin
// Safe - uses parameter binding
handle.createQuery("SELECT * FROM users WHERE id = :id")
    .bind("id", userId)
    .mapTo(User::class.java)
    .one()

// NEVER do this - vulnerable to SQL injection
handle.createQuery("SELECT * FROM users WHERE id = $userId")
```

### 3. Environment-Specific Configuration

```kotlin
val dbConfig = DbConfig(
    username = System.getenv("DATABASE_USERNAME") ?: "vanilla_kotlin_app",
    password = System.getenv("DATABASE_PASSWORD") ?: "vanilla_kotlin_app",
    databaseName = System.getenv("DATABASE_NAME") ?: "vanilla_kotlin",
    host = System.getenv("DATABASE_HOST") ?: "localhost"
)
```

## Troubleshooting

### Common Issues

**1. Migration Checksum Mismatch**

```bash
# Fix checksum issues (development only)
./gradlew :db-migration:flywayRepair
```

**2. Connection Issues**

```bash
# Check if database is running
docker-compose ps db

# View database logs
docker-compose logs db

# Test connection
docker-compose exec db psql -U postgres
```

**3. Permission Issues**

```bash
# Rerun afterMigrate.sql manually if needed
./gradlew :db-migration:flywayMigrate
```

This database setup provides a solid foundation for data persistence with minimal complexity while maintaining
professional standards for schema management, data access, and testing.
