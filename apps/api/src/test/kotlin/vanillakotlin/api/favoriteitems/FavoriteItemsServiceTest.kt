package vanillakotlin.api.favoriteitems

import vanillakotlin.api.buildTestItem
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyAll
import org.junit.jupiter.api.Test
import java.sql.SQLException
import java.time.Instant

private const val TEST_USER = "user"
private val testTcins: List<String> = listOf(randomTcin(), randomTcin())
private val testTimestamp = Instant.ofEpochMilli(1000)
private val testFavorites: List<UserFavoriteTcin> =
    listOf(
        UserFavoriteTcin(id = 1, userName = TEST_USER, itemIdentifier = testTcins[0], createdTs = testTimestamp, updatedTs = testTimestamp),
        UserFavoriteTcin(id = 2, userName = TEST_USER, itemIdentifier = testTcins[1], createdTs = testTimestamp, updatedTs = testTimestamp),
    )

class FavoriteItemsServiceTest {

    private val mockUserFavoriteTcinRepository = mockk<UserFavoriteTcinRepository>()

    @Test fun `saveFavoriteTcin success`() {
        val getItemDetails = { _: ItemIdentifier -> throw Exception("Unexpected call to getItemDetails") }

        val service =
            FavoriteItemsService(
                userFavoriteTcinRepository = mockUserFavoriteTcinRepository,
                getItemDetails = getItemDetails,
            )

        every {
            mockUserFavoriteTcinRepository.upsert(
                testFavorites[0].copy(id = null, createdTs = null, updatedTs = null),
            )
        }.answers { testFavorites[0] }

        service.saveFavoriteTcin(UserFavoriteTcin(userName = TEST_USER, itemIdentifier = testTcins[0]))

        verifyAll {
            mockUserFavoriteTcinRepository.upsert(
                testFavorites[0].copy(id = null, createdTs = null, updatedTs = null),
            )
        }
    }

    @Test fun `deleteFavoriteTcin success`() {
        val getItemDetails = { _: ItemIdentifier -> null }

        val service =
            FavoriteItemsService(
                userFavoriteTcinRepository = mockUserFavoriteTcinRepository,
                getItemDetails = getItemDetails,
            )

        every {
            mockUserFavoriteTcinRepository.deleteByUserNameAndTcin(
                userName = TEST_USER,
                tcin = testFavorites[0].itemIdentifier,
            )
        }.answers { 1 }

        val deleteFavoriteTcin = service.deleteFavoriteTcin(TEST_USER, testFavorites[0].itemIdentifier)

        assertSoftly(deleteFavoriteTcin) {
            this shouldBe DeleteResult.Success
        }
        verifyAll {
            mockUserFavoriteTcinRepository.deleteByUserNameAndTcin(
                userName = TEST_USER,
                tcin = testFavorites[0].itemIdentifier,
            )
        }
    }

    @Test fun `deleteFavoriteTcin record doesn't exist`() {
        val getItemDetails = { _: ItemIdentifier -> null }

        val service =
            FavoriteItemsService(
                userFavoriteTcinRepository = mockUserFavoriteTcinRepository,
                getItemDetails = getItemDetails,
            )

        every { mockUserFavoriteTcinRepository.deleteByUserNameAndTcin(TEST_USER, testFavorites[0].itemIdentifier) }.answers { 0 }

        val result = service.deleteFavoriteTcin(TEST_USER, testFavorites[0].itemIdentifier)

        result shouldBe DeleteResult.NotFound
    }

    @Test fun `deleteFavoriteTcin SQL Exception`() {
        val getItemDetails = { _: ItemIdentifier -> null }

        val service =
            FavoriteItemsService(
                userFavoriteTcinRepository = mockUserFavoriteTcinRepository,
                getItemDetails = getItemDetails,
            )

        every {
            mockUserFavoriteTcinRepository.deleteByUserNameAndTcin(
                TEST_USER,
                testFavorites[0].itemIdentifier,
            )
        }.answers { throw SQLException("Failure") }

        val result = service.deleteFavoriteTcin(TEST_USER, testFavorites[0].itemIdentifier)

        result shouldBe DeleteResult.Error(DeleteErrorType.SQL_FAILURE)
    }

    @Test fun `getFavoriteTcins success`() {
        val getItemDetails = { _: ItemIdentifier -> throw Exception("Unexpected call to getItemDetails") }

        val service =
            FavoriteItemsService(
                userFavoriteTcinRepository = mockUserFavoriteTcinRepository,
                getItemDetails = getItemDetails,
            )

        every { mockUserFavoriteTcinRepository.findByUserName(TEST_USER) }.answers { testFavorites }

        val items = service.getFavoriteTcins(TEST_USER)

        items.size shouldBe testTcins.size
        items shouldContainAll testTcins
    }

    @Test fun `getFavoriteItems success`() {
        val getItemDetails = { itemIdentifier: ItemIdentifier -> buildTestItem(itemIdentifier) }

        val service =
            FavoriteItemsService(
                userFavoriteTcinRepository = mockUserFavoriteTcinRepository,
                getItemDetails = getItemDetails,
            )

        every { mockUserFavoriteTcinRepository.findByUserName(TEST_USER) }.answers { testFavorites }

        val items = service.getFavoriteItems(TEST_USER)

        items.size shouldBe testFavorites.size
    }
}
