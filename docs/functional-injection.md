# Functional Injection: A Lightweight Alternative to Traditional DI

## Introduction

Functional injection represents a paradigm shift from traditional object-oriented dependency injection patterns. Instead
of injecting complex objects with multiple methods, we inject simple functions that do exactly what we need. This
approach leverages Kotlin's powerful functional programming features to create more testable, maintainable, and explicit
code.

## Traditional Object-Oriented Injection vs Functional Injection

### The Old Way: Class-Based Injection

In traditional dependency injection (like Spring), we typically inject entire objects:

```kotlin
// Traditional approach - inject entire repositories
class FavoriteThingsService(
    private val favoriteThingRepository: FavoriteThingRepository,
    private val thingGateway: ThingGateway
) {
    fun saveFavoriteThing(favorite: FavoriteThing): SaveResult {
        return try {
            favoriteThingRepository.upsert(favorite)
            SaveResult.Success
        } catch (e: Exception) {
            SaveResult.Error(SaveErrorType.DATABASE_ERROR)
        }
    }

    fun getFavoriteThings(): List<ThingIdentifier> {
        return favoriteThingRepository.findAll().map { it.thingIdentifier }
    }
}
```

**Problems with this approach:**

- **Tight coupling**: Service depends on entire repository classes
- **Testing complexity**: Must mock entire objects with multiple methods
- **Hidden dependencies**: Not clear which specific methods are actually used
- **Over-injection**: Often only 1-2 methods from a large class are needed

### The Functional Way: Inject What You Actually Need

With functional injection, we inject only the specific functions we need:

```kotlin
// Functional approach - inject specific functions
class FavoriteThingsService(
    private val upsertFavoriteThing: UpsertFavoriteThing,
    private val deleteFavoriteThingRepository: DeleteFavoriteThing,
    private val findAllFavoriteThings: FindAllFavoriteThings,
    private val getThingDetails: GetThingDetails,
) {
    fun saveFavoriteThing(favorite: FavoriteThing): SaveResult {
        return try {
            upsertFavoriteThing(favorite)
            SaveResult.Success
        } catch (e: Exception) {
            SaveResult.Error(SaveErrorType.DATABASE_ERROR)
        }
    }

    fun getFavoriteThings(): List<ThingIdentifier> {
        return findAllFavoriteThings().map { it.thingIdentifier }
    }
}
```

**Benefits of this approach:**

- **Explicit dependencies**: Clear exactly which operations are needed
- **Minimal coupling**: Only coupled to specific function signatures
- **Easy testing**: Simple lambda functions instead of complex mocks
- **Lightweight**: No heavy object creation or framework magic

## SAM Interfaces: The Bridge Between OO and Functional

### What are SAM Interfaces?

SAM (Single Abstract Method) interfaces are Kotlin's way of bridging object-oriented and functional programming. They
allow us to define functional contracts that can be implemented as simple lambda expressions.

```kotlin
// SAM interface definition
fun interface UpsertFavoriteThing {
    operator fun invoke(favorite: FavoriteThing): FavoriteThing
}

// SAM interface definition
fun interface GetThingDetails {
    operator fun invoke(thingIdentifier: ThingIdentifier): Thing?
}
```

### SAM vs Traditional Interfaces

#### Traditional Java-Style Interface

```kotlin
// Traditional interface (multiple methods)
interface FavoriteThingRepository {
    fun upsert(favorite: FavoriteThing): FavoriteThing
    fun delete(thingIdentifier: ThingIdentifier): Int
    fun findAll(): List<FavoriteThing>
    fun addToBatch(favorite: FavoriteThing)
    fun runBatch(): Int
}
```

#### SAM Interface Approach

```kotlin
// Multiple focused SAM interfaces
fun interface UpsertFavoriteThing {
    operator fun invoke(favorite: FavoriteThing): FavoriteThing
}

fun interface DeleteFavoriteThing {
    operator fun invoke(thingIdentifier: ThingIdentifier): Int
}

fun interface FindAllFavoriteThings {
    operator fun invoke(): List<FavoriteThing>
}
```

### Why SAM Interfaces Are Superior

1. **Single Responsibility**: Each interface has exactly one purpose
2. **Lambda Compatibility**: Can be implemented as simple lambda expressions
3. **Type Safety**: Full compile-time type checking
4. **Composability**: Easy to combine and substitute
5. **Testing Simplicity**: No need for complex mocking frameworks

## Testing: The Real Game Changer

### Before: Complex Mock-Based Testing

With traditional object injection, testing requires heavy mocking frameworks:

```kotlin
class FavoriteThingsServiceTest {
    private val mockRepository = mockk<FavoriteThingRepository>()
    private val mockGateway = mockk<ThingGateway>()

    @Test fun `saveFavoriteThing success`() {
        val service = FavoriteThingsService(mockRepository, mockGateway)

        // Complex mock setup
        every { mockRepository.upsert(any()) } returns testFavorite
        every { mockRepository.findAll() } returns testFavorites
        every { mockGateway.getThingDetails(any()) } returns testThing

        val result = service.saveFavoriteThing(testFavorite)

        result shouldBe SaveResult.Success

        // Verification overhead
        verify { mockRepository.upsert(any()) }
        verify(exactly = 0) { mockRepository.findAll() }
    }
}
```

**Problems:**

- **Framework dependency**: Requires mockk or similar
- **Complex setup**: Multiple `every` blocks for different scenarios
- **Verification overhead**: Manual verification of mock interactions
- **Brittle tests**: Changes to mock behavior break tests
- **Hard to read**: Mock syntax obscures test intent

### After: Simple Functional Testing

With functional injection, testing becomes incredibly simple:

```kotlin
class FavoriteThingsServiceTest {

    @Test fun `saveFavoriteThing success`() {
        val service = FavoriteThingsService(
            upsertFavoriteThing = { testFavorites[0] },
            deleteFavoriteThingRepository = { 1 },
            findAllFavoriteThings = { testFavorites },
            getThingDetails = { thingId -> buildTestThing(thingId) },
        )

        val result = service.saveFavoriteThing(FavoriteThing(thingIdentifier = testThings[0]))

        result shouldBe SaveResult.Success
    }

    @Test fun `deleteFavoriteThing record doesn't exist`() {
        val service = FavoriteThingsService(
            upsertFavoriteThing = { testFavorites[0] },
            deleteFavoriteThingRepository = { 0 }, // Returns 0 = not found
            findAllFavoriteThings = { testFavorites },
            getThingDetails = { thingId -> buildTestThing(thingId) },
        )

        val result = service.deleteFavoriteThing(testFavorites[0].thingIdentifier)

        result shouldBe DeleteResult.NotFound
    }

    @Test fun `deleteFavoriteThing SQL Exception`() {
        val service = FavoriteThingsService(
            upsertFavoriteThing = { testFavorites[0] },
            deleteFavoriteThingRepository = { throw SQLException("Failure") },
            findAllFavoriteThings = { testFavorites },
            getThingDetails = { thingId -> buildTestThing(thingId) },
        )

        val result = service.deleteFavoriteThing(testFavorites[0].thingIdentifier)

        result shouldBe DeleteResult.Error(DeleteErrorType.DATABASE_ERROR)
    }
}
```

**Benefits:**

- **Zero framework dependencies**: No mockk, no Spring Test, nothing
- **Crystal clear intent**: Exactly what each function returns is obvious
- **Minimal setup**: One line per dependency
- **Easy edge cases**: Simple to test error conditions
- **Fast execution**: No mock framework overhead
- **Readable**: Business logic is immediately apparent

## Real-World Examples from FavoriteThingsServiceTest

### Example 1: Testing Success Scenarios

```kotlin
// Simple success case - just return what we expect
val service = FavoriteThingsService(
    upsertFavoriteThing = { testFavorites[0] },  // Always succeed
    deleteFavoriteThingRepository = { 1 },       // Always delete 1 row
    findAllFavoriteThings = { testFavorites },   // Return test data
    getThingDetails = { thingId -> buildTestThing(thingId) }, // Build valid thing
)
```

### Example 2: Testing Conditional Logic

```kotlin
// Test conditional behavior with inline logic
val service = FavoriteThingsService(
    upsertFavoriteThing = { testFavorites[0] },
    deleteFavoriteThingRepository = { thingIdentifier ->
        // Inline conditional logic - much clearer than mock setup
        if (thingIdentifier == testFavorites[0].thingIdentifier) 1 else 0
    },
    findAllFavoriteThings = { testFavorites },
    getThingDetails = { thingId -> buildTestThing(thingId) },
)
```

### Example 3: Testing Error Conditions

```kotlin
// Test exception handling - just throw what you need to test
val service = FavoriteThingsService(
    upsertFavoriteThing = { testFavorites[0] },
    deleteFavoriteThingRepository = { throw SQLException("Failure") }, // Direct exception
    findAllFavoriteThings = { testFavorites },
    getThingDetails = { thingId -> buildTestThing(thingId) },
)
```

## Implementation Patterns

### 1. Creating SAM Interfaces

```kotlin
// Define the contract
fun interface UpsertFavoriteThing {
    operator fun invoke(favorite: FavoriteThing): FavoriteThing
}

// Implement with a class method reference
val repository = FavoriteThingRepository(jdbi)
val upsertFunction: UpsertFavoriteThing = repository::upsert

// Or implement with a lambda
val upsertFunction: UpsertFavoriteThing = { favorite ->
    // Custom implementation
    repository.upsert(favorite)
}
```

### 2. Dependency Wiring in Production

```kotlin
// Production wiring - use real implementations
class FavoriteThingsServiceFactory(
    private val repository: FavoriteThingRepository,
    private val gateway: ThingGateway
) {
    fun create(): FavoriteThingsService = FavoriteThingsService(
        upsertFavoriteThing = repository::upsert,
        deleteFavoriteThingRepository = repository::deleteItem,
        findAllFavoriteThings = repository::findAll,
        getThingDetails = gateway::getThingDetails,
    )
}
```

### 3. Partial Application for Complex Dependencies

```kotlin
// When you need to configure behavior
fun createThingDetailsGetter(config: Config): GetThingDetails = { thingId ->
    val gateway = ThingGateway(httpClient, config)
    gateway.getThingDetails(thingId)
}

val service = FavoriteThingsService(
    // ... other dependencies
    getThingDetails = createThingDetailsGetter(appConfig.thingGateway)
)
```

## Advanced Patterns

### Composing Functions

```kotlin
// Compose multiple operations
fun createCachedThingDetailsGetter(
    cache: Cache<String, Thing>,
    gateway: GetThingDetails
): GetThingDetails = { thingId ->
    cache.get(thingId) ?: gateway(thingId)?.also { cache.put(thingId, it) }
}
```

### Error Handling Wrappers

```kotlin
// Add error handling to any function
fun <T, R> withErrorHandling(
    fn: (T) -> R,
    onError: (Exception) -> R
): (T) -> R = { input ->
    try {
        fn(input)
    } catch (e: Exception) {
        onError(e)
    }
}

val safeUpsert = withErrorHandling(
    fn = repository::upsert,
    onError = { throw DatabaseException("Upsert failed", it) }
)
```

## Performance Benefits

### Memory Usage

- **SAM interfaces**: Minimal memory overhead (single function reference)
- **Traditional objects**: Full object allocation + method table overhead

### Startup Time

- **Functional injection**: Instant - just function references
- **Framework injection**: Reflection, classpath scanning, proxy creation

### Runtime Performance

- **Direct function calls**: No proxy overhead
- **Framework calls**: Multiple layers of indirection

## Migration Strategy

### Step 1: Identify Single-Use Dependencies

Look for services that only use 1-2 methods from injected dependencies.

### Step 2: Create SAM Interfaces

```kotlin
// Extract the specific operations you need
fun interface SaveUser {
    operator fun invoke(user: User): User
}
```

### Step 3: Update Service Constructor

```kotlin
// Replace object injection with functional injection
class UserService(
    private val saveUser: SaveUser,  // Instead of entire UserRepository
    private val sendEmail: SendEmail // Instead of entire EmailService
)
```

### Step 4: Update Tests

Replace mocks with simple lambdas as shown in the examples above.

### Step 5: Update Production Wiring

```kotlin
// Wire with method references
UserService(
    saveUser = userRepository::save,
    sendEmail = emailService::send
)
```

## Conclusion

Functional injection represents a significant improvement over traditional object-oriented dependency injection:

- **Simpler testing**: No mocking frameworks required
- **Clearer intent**: Explicit about what each service actually needs
- **Better performance**: Minimal overhead, fast startup
- **Easier maintenance**: Smaller, focused interfaces
- **Functional style**: Leverages Kotlin's strengths

The `FavoriteThingsServiceTest` demonstrates how this approach transforms complex, framework-heavy tests into simple,
readable, and maintainable code. By embracing functional injection, we align with the vanilla Kotlin philosophy of
choosing simplicity over complexity and explicitness over magic.

---

*"The best dependency injection framework is no dependency injection framework at all. Just use functions."* 
