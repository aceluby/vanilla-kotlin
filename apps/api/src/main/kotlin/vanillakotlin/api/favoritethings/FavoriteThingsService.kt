package vanillakotlin.api.favoritethings

import org.slf4j.LoggerFactory
import vanillakotlin.db.repository.FindAllFavoriteThings
import vanillakotlin.db.repository.UpsertFavoriteThing
import vanillakotlin.http.clients.thing.GetThingDetails
import vanillakotlin.models.FavoriteThing
import vanillakotlin.models.Thing
import vanillakotlin.models.ThingIdentifier
import vanillakotlin.db.repository.DeleteFavoriteThing as DeleteFavoriteThingRepository

/** SAM interface for [FavoriteThingsService.saveFavoriteThing] */
fun interface SaveFavoriteThing {
    operator fun invoke(userFavoriteThing: FavoriteThing): SaveResult
}

/** SAM interface for [FavoriteThingsService.deleteFavoriteThing] */
fun interface DeleteFavoriteThing {
    operator fun invoke(thingIdentifier: ThingIdentifier): DeleteResult
}

/** SAM interface for [FavoriteThingsService.getFavoriteThings] */
fun interface GetFavoriteThingIds {
    operator fun invoke(): List<ThingIdentifier>
}

/** SAM interface for [FavoriteThingsService.getFavoriteThingsDetails] */
fun interface GetFavoriteThings {
    operator fun invoke(): List<Thing>
}

class FavoriteThingsService(
    private val upsertFavoriteThing: UpsertFavoriteThing,
    private val deleteFavoriteThingRepository: DeleteFavoriteThingRepository,
    private val findAllFavoriteThings: FindAllFavoriteThings,
    private val getThingDetails: GetThingDetails,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun saveFavoriteThing(userFavoriteThing: FavoriteThing): SaveResult {
        return try {
            upsertFavoriteThing(userFavoriteThing)
            SaveResult.Success
        } catch (e: Exception) {
            logger.warn("Failed to save record", e)
            SaveResult.Error(SaveErrorType.DATABASE_ERROR)
        }
    }

    fun deleteFavoriteThing(thingIdentifier: ThingIdentifier): DeleteResult = try {
        val rowCount = deleteFavoriteThingRepository(thingIdentifier)
        if (rowCount > 0) {
            DeleteResult.Success
        } else {
            logger.warn("Favorite not found during delete.")
            DeleteResult.NotFound
        }
    } catch (e: Exception) {
        logger.warn("Failed to delete record", e)
        DeleteResult.Error(DeleteErrorType.DATABASE_ERROR)
    }

    fun getFavoriteThings(): List<ThingIdentifier> = findAllFavoriteThings().map { it.thingIdentifier }

    fun getFavoriteThingsDetails(): List<Thing> {
        val ids = getFavoriteThings()

        return ids.mapNotNull { id ->
            try {
                getThingDetails(id)
            } catch (e: Exception) {
                logger.warn("Failed to get thing details for $id", e)
                null
            }
        }
    }
}

sealed class SaveResult {
    object Success : SaveResult()
    data class Error(val errorType: SaveErrorType) : SaveResult()
}

sealed class DeleteResult {
    object Success : DeleteResult()
    object NotFound : DeleteResult()
    data class Error(val errorType: DeleteErrorType) : DeleteResult()
}

enum class SaveErrorType {
    DATABASE_ERROR,
}

enum class DeleteErrorType {
    DATABASE_ERROR,
}
