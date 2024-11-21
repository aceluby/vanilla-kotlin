package vanillakotlin.api.favoriteitems

import vanillakotlin.http.clients.item.GetItemDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory

/** alias for [FavoriteItemsService.saveFavoriteTcin] */
typealias SaveFavoriteTcin = (userFavoriteTcin: UserFavoriteTcin) -> SaveResult

/** alias for [FavoriteItemsService.deleteFavoriteTcin] */
typealias DeleteFavoriteTcin = (username: UserName, itemIdentifier: ItemIdentifier) -> DeleteResult

/** alias for [FavoriteItemsService.getFavoriteTcins] */
typealias GetFavoriteTcins = (username: UserName) -> List<ItemIdentifier>

/** alias for [FavoriteItemsService.getFavoriteItems] */
typealias GetFavoriteItems = (username: UserName) -> List<Item>

class FavoriteItemsService(
    val userFavoriteTcinRepository: UserFavoriteTcinRepository,
    val getItemDetails: GetItemDetails,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun saveFavoriteTcin(userFavoriteTcin: UserFavoriteTcin): SaveResult {
        return try {
            userFavoriteTcinRepository.upsert(userFavoriteTcin)
            SaveResult.Success
        } catch (ex: Exception) {
            log.atError().withUserName(userFavoriteTcin.userName).withTgtTcin(userFavoriteTcin.itemIdentifier).setCause(ex).log("Save Failed")
            SaveResult.Error(SaveErrorType.SQL_FAILURE)
        }
    }

    fun deleteFavoriteTcin(
        userName: UserName,
        itemIdentifier: ItemIdentifier,
    ): DeleteResult {
        return try {
            val rowCount = userFavoriteTcinRepository.deleteByUserNameAndTcin(userName, itemIdentifier)
            if (rowCount == 0) {
                log.atWarn().withUserName(userName).withTgtTcin(itemIdentifier).log("Favorite not found during delete.")
                DeleteResult.NotFound
            } else {
                DeleteResult.Success
            }
        } catch (ex: Exception) {
            log.atWarn()
                .withUserName(userName)
                .withTgtTcin(itemIdentifier)
                .setCause(ex)
                .log("Failed to delete record")
            return DeleteResult.Error(DeleteErrorType.SQL_FAILURE)
        }
    }

    fun getFavoriteTcins(username: UserName): List<ItemIdentifier> = userFavoriteTcinRepository.findByUserName(username).map { it.tcin }

    fun getFavoriteItems(username: UserName): List<Item> {
        val tcins = getFavoriteTcins(username)

        // map over the tcins in an asynchronous coroutine block, collecting the items that were found.
        val items =
            runBlocking(Dispatchers.IO) {
                tcins.map { tcin ->
                    async { getItemDetails(tcin) }
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
