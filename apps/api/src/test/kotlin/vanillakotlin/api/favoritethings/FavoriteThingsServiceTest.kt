package vanillakotlin.api.favoritethings

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import vanillakotlin.api.buildTestThing
import vanillakotlin.models.FavoriteThing
import vanillakotlin.random.randomThing
import java.sql.SQLException
import java.time.LocalDateTime

private val testThings: List<String> = listOf(randomThing(), randomThing())
private val testTimestamp = LocalDateTime.of(2023, 1, 1, 12, 0, 0)
private val testFavorites: List<FavoriteThing> =
    listOf(
        FavoriteThing(id = 1, thingIdentifier = testThings[0], createdTs = testTimestamp, updatedTs = testTimestamp),
        FavoriteThing(id = 2, thingIdentifier = testThings[1], createdTs = testTimestamp, updatedTs = testTimestamp),
    )

class FavoriteThingsServiceTest {

    @Test fun `saveFavoriteThing success`() {
        val service =
            FavoriteThingsService(
                upsertFavoriteThing = { testFavorites[0] },
                deleteFavoriteThingRepository = { 1 },
                findAllFavoriteThings = { testFavorites },
                getThingDetails = { thingId -> buildTestThing(thingId) },
            )

        val result = service.saveFavoriteThing(FavoriteThing(thingIdentifier = testThings[0]))

        assertSoftly(result) {
            this shouldBe SaveResult.Success
        }
    }

    @Test fun `deleteFavoriteThing success`() {
        val service =
            FavoriteThingsService(
                upsertFavoriteThing = { testFavorites[0] },
                deleteFavoriteThingRepository = { thingIdentifier ->
                    if (thingIdentifier == testFavorites[0].thingIdentifier) 1 else 0
                },
                findAllFavoriteThings = { testFavorites },
                getThingDetails = { thingId -> buildTestThing(thingId) },
            )

        val result = service.deleteFavoriteThing(testFavorites[0].thingIdentifier)

        assertSoftly(result) {
            this shouldBe DeleteResult.Success
        }
    }

    @Test fun `deleteFavoriteThing record doesn't exist`() {
        val service =
            FavoriteThingsService(
                upsertFavoriteThing = { testFavorites[0] },
                deleteFavoriteThingRepository = { 0 },
                findAllFavoriteThings = { testFavorites },
                getThingDetails = { thingId -> buildTestThing(thingId) },
            )

        val result = service.deleteFavoriteThing(testFavorites[0].thingIdentifier)

        result shouldBe DeleteResult.NotFound
    }

    @Test fun `deleteFavoriteThing SQL Exception`() {
        val service =
            FavoriteThingsService(
                upsertFavoriteThing = { testFavorites[0] },
                deleteFavoriteThingRepository = { throw SQLException("Failure") },
                findAllFavoriteThings = { testFavorites },
                getThingDetails = { thingId -> buildTestThing(thingId) },
            )

        val result = service.deleteFavoriteThing(testFavorites[0].thingIdentifier)

        result shouldBe DeleteResult.Error(DeleteErrorType.DATABASE_ERROR)
    }

    @Test fun `getFavoriteThings success`() {
        val service =
            FavoriteThingsService(
                upsertFavoriteThing = { testFavorites[0] },
                deleteFavoriteThingRepository = { 1 },
                findAllFavoriteThings = { testFavorites },
                getThingDetails = { thingId -> buildTestThing(thingId) },
            )

        val things = service.getFavoriteThings()

        things.size shouldBe testThings.size
        things shouldContainAll testThings
    }

    @Test fun `getFavoriteThingsDetails success`() {
        val service =
            FavoriteThingsService(
                upsertFavoriteThing = { testFavorites[0] },
                deleteFavoriteThingRepository = { 1 },
                findAllFavoriteThings = { testFavorites },
                getThingDetails = { thingId -> buildTestThing(thingId) },
            )

        val things = service.getFavoriteThingsDetails()

        things.size shouldBe testFavorites.size
    }
}
