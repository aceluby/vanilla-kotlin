package vanillakotlin.api.favoritethings

import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyAll
import org.junit.jupiter.api.Test
import vanillakotlin.api.buildTestThing
import vanillakotlin.db.repository.FindAllFavoriteThings
import vanillakotlin.db.repository.UpsertFavoriteThing
import vanillakotlin.http.clients.thing.GetThingDetails
import vanillakotlin.models.FavoriteThing
import vanillakotlin.random.randomThing
import java.sql.SQLException
import java.time.LocalDateTime
import vanillakotlin.db.repository.DeleteFavoriteThing as DeleteFavoriteThingRepository

private val testThings: List<String> = listOf(randomThing(), randomThing())
private val testTimestamp = LocalDateTime.of(2023, 1, 1, 12, 0, 0)
private val testFavorites: List<FavoriteThing> =
    listOf(
        FavoriteThing(id = 1, thingIdentifier = testThings[0], createdTs = testTimestamp, updatedTs = testTimestamp),
        FavoriteThing(id = 2, thingIdentifier = testThings[1], createdTs = testTimestamp, updatedTs = testTimestamp),
    )

class FavoriteThingsServiceTest {

    private val mockUpsertFavoriteThing = mockk<UpsertFavoriteThing>()
    private val mockDeleteFavoriteThingRepository = mockk<DeleteFavoriteThingRepository>()
    private val mockFindAllFavoriteThings = mockk<FindAllFavoriteThings>()
    private val mockGetThingDetails = mockk<GetThingDetails>()

    @Test fun `saveFavoriteThing success`() {
        val service =
            FavoriteThingsService(
                upsertFavoriteThing = mockUpsertFavoriteThing,
                deleteFavoriteThingRepository = mockDeleteFavoriteThingRepository,
                findAllFavoriteThings = mockFindAllFavoriteThings,
                getThingDetails = mockGetThingDetails,
            )

        every {
            mockUpsertFavoriteThing(any())
        }.answers { testFavorites[0] }

        val result = service.saveFavoriteThing(FavoriteThing(thingIdentifier = testThings[0]))

        assertSoftly(result) {
            this shouldBe SaveResult.Success
        }

        verifyAll {
            mockUpsertFavoriteThing(any())
        }
    }

    @Test fun `deleteFavoriteThing success`() {
        val service =
            FavoriteThingsService(
                upsertFavoriteThing = mockUpsertFavoriteThing,
                deleteFavoriteThingRepository = mockDeleteFavoriteThingRepository,
                findAllFavoriteThings = mockFindAllFavoriteThings,
                getThingDetails = mockGetThingDetails,
            )

        every {
            mockDeleteFavoriteThingRepository(testFavorites[0].thingIdentifier)
        }.answers { 1 }

        val result = service.deleteFavoriteThing(testFavorites[0].thingIdentifier)

        assertSoftly(result) {
            this shouldBe DeleteResult.Success
        }
        verifyAll {
            mockDeleteFavoriteThingRepository(testFavorites[0].thingIdentifier)
        }
    }

    @Test fun `deleteFavoriteThing record doesn't exist`() {
        val service =
            FavoriteThingsService(
                upsertFavoriteThing = mockUpsertFavoriteThing,
                deleteFavoriteThingRepository = mockDeleteFavoriteThingRepository,
                findAllFavoriteThings = mockFindAllFavoriteThings,
                getThingDetails = mockGetThingDetails,
            )

        every { mockDeleteFavoriteThingRepository(testFavorites[0].thingIdentifier) }.answers { 0 }

        val result = service.deleteFavoriteThing(testFavorites[0].thingIdentifier)

        result shouldBe DeleteResult.NotFound
    }

    @Test fun `deleteFavoriteThing SQL Exception`() {
        val service =
            FavoriteThingsService(
                upsertFavoriteThing = mockUpsertFavoriteThing,
                deleteFavoriteThingRepository = mockDeleteFavoriteThingRepository,
                findAllFavoriteThings = mockFindAllFavoriteThings,
                getThingDetails = mockGetThingDetails,
            )

        every {
            mockDeleteFavoriteThingRepository(testFavorites[0].thingIdentifier)
        }.answers { throw SQLException("Failure") }

        val result = service.deleteFavoriteThing(testFavorites[0].thingIdentifier)

        result shouldBe DeleteResult.Error(DeleteErrorType.DATABASE_ERROR)
    }

    @Test fun `getFavoriteThings success`() {
        val service =
            FavoriteThingsService(
                upsertFavoriteThing = mockUpsertFavoriteThing,
                deleteFavoriteThingRepository = mockDeleteFavoriteThingRepository,
                findAllFavoriteThings = mockFindAllFavoriteThings,
                getThingDetails = mockGetThingDetails,
            )

        every { mockFindAllFavoriteThings() }.answers { testFavorites }

        val things = service.getFavoriteThings()

        things.size shouldBe testThings.size
        things shouldContainAll testThings
    }

    @Test fun `getFavoriteThingsDetails success`() {
        val service =
            FavoriteThingsService(
                upsertFavoriteThing = mockUpsertFavoriteThing,
                deleteFavoriteThingRepository = mockDeleteFavoriteThingRepository,
                findAllFavoriteThings = mockFindAllFavoriteThings,
                getThingDetails = mockGetThingDetails,
            )

        every { mockFindAllFavoriteThings() }.answers { testFavorites }
        every { mockGetThingDetails(testThings[0]) }.answers { buildTestThing(testThings[0]) }
        every { mockGetThingDetails(testThings[1]) }.answers { buildTestThing(testThings[1]) }

        val things = service.getFavoriteThingsDetails()

        things.size shouldBe testFavorites.size
    }
}
