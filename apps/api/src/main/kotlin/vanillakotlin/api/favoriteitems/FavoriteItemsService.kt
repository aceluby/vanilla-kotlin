package vanillakotlin.api.favoriteitems

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import vanillakotlin.db.repository.FavoriteItemRepository
import vanillakotlin.http.clients.item.GetItemDetails
import vanillakotlin.models.FavoriteItem
import vanillakotlin.models.Item
import vanillakotlin.models.ItemIdentifier
import vanillakotlin.models.UserName

/** alias for [FavoriteItemsService.saveFavoriteTcin] */
typealias SaveFavoriteItem = (userFavoriteTcin: FavoriteItem) -> SaveResult

/** alias for [FavoriteItemsService.deleteFavoriteTcin] */
typealias DeleteFavoriteItem = (itemIdentifier: ItemIdentifier) -> DeleteResult

/** alias for [FavoriteItemsService.getFavoriteItems] */
typealias GetFavoriteItemIds = () -> List<ItemIdentifier>

/** alias for [FavoriteItemsService.getFavoriteItems] */
typealias GetFavoriteItems = () -> List<Item>

class FavoriteItemsService(
    val userFavoriteTcinRepository: FavoriteItemRepository,
    val getItemDetails: GetItemDetails,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun saveFavoriteTcin(userFavoriteTcin: FavoriteItem): SaveResult {
        return try {
            userFavoriteTcinRepository.upsert(userFavoriteTcin)
            SaveResult.Success
        } catch (ex: Exception) {
            log.atError().setCause(ex).log("Save Failed")
            SaveResult.Error(SaveErrorType.SQL_FAILURE)
        }
    }

    fun deleteFavoriteTcin(itemIdentifier: ItemIdentifier): DeleteResult = try {
        val rowCount = userFavoriteTcinRepository.deleteItem(itemIdentifier)
        if (rowCount == 0) {
            log.atWarn().log("Favorite not found during delete.")
            DeleteResult.NotFound
        } else {
            DeleteResult.Success
        }
    } catch (ex: Exception) {
        log.atWarn().setCause(ex).log("Failed to delete record")
        DeleteResult.Error(DeleteErrorType.SQL_FAILURE)
    }

    fun getFavoriteItems(): List<ItemIdentifier> = userFavoriteTcinRepository.findAll().map { it.itemIdentifier }

    fun getFavoriteItemsDetails(): List<Item> {
        val ids = getFavoriteItems()
        // map over the items in an asynchronous coroutine block, collecting the items that were found.
        val items = runBlocking(Dispatchers.IO) {
            ids.map { id ->
                async { getItemDetails(id) }
            }.awaitAll().filterNotNull()
        }

        return items
    }
}

sealed class DeleteResult {
    data object Success : DeleteResult()

    data object NotFound : DeleteResult()

    data class Error(val errorType: DeleteErrorType) : DeleteResult()
}

enum class DeleteErrorType { SQL_FAILURE }

sealed class SaveResult {
    data object Success : SaveResult()

    data class Error(val errorType: SaveErrorType) : SaveResult()
}

enum class SaveErrorType { SQL_FAILURE }
