package vanillakotlin.db.repository

import org.intellij.lang.annotations.Language
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.mapper.RowMapper
import org.jdbi.v3.core.statement.StatementContext
import vanillakotlin.models.FavoriteThing
import vanillakotlin.models.ThingIdentifier
import vanillakotlin.models.buildDeletedOutbox
import java.sql.ResultSet

typealias AddToBatch = (FavoriteThing) -> Unit
typealias RunBatch = () -> Int

/** SAM interface for [FavoriteThingRepository.upsert] */
fun interface UpsertFavoriteThing {
    operator fun invoke(favorite: FavoriteThing): FavoriteThing
}

/** SAM interface for [FavoriteThingRepository.deleteItem] */
fun interface DeleteFavoriteThing {
    operator fun invoke(thingIdentifier: ThingIdentifier): Int
}

/** SAM interface for [FavoriteThingRepository.findAll] */
fun interface FindAllFavoriteThings {
    operator fun invoke(): List<FavoriteThing>
}

/** SAM interface for [FavoriteThingRepository.addToBatch] */
fun interface AddFavoriteThingToBatch {
    operator fun invoke(favoriteThing: FavoriteThing): Unit
}

/** SAM interface for [FavoriteThingRepository.runBatch] */
fun interface RunFavoriteThingBatch {
    operator fun invoke(): Int
}

// This class encapsulates all the SQL operations for the FavoriteThing entity.
class FavoriteThingRepository(private val jdbi: Jdbi) {
    fun upsert(favorite: FavoriteThing): FavoriteThing = jdbi.inTransaction<FavoriteThing, Exception> { handle ->
        val favoriteThing = handle.createQuery(
            """
            INSERT INTO favorite_thing (thing)
            VALUES (:thing)
            ON CONFLICT (thing) DO UPDATE SET
                updated_ts = CURRENT_TIMESTAMP
            RETURNING *
            """,
        )
            .bind("thing", favorite.thingIdentifier)
            .mapTo(FavoriteThing::class.java)
            .one()

        insertOutbox(handle, favoriteThing.buildOutbox())

        favoriteThing
    }

    fun deleteItem(thingIdentifier: ThingIdentifier): Int = jdbi.withHandle<Int, Exception> { handle ->
        val rowCount = handle.createUpdate("DELETE FROM favorite_thing WHERE thing = :thing")
            .bind("thing", thingIdentifier)
            .execute()

        if (rowCount > 0) {
            insertOutbox(handle, buildDeletedOutbox(thingIdentifier))
        }

        rowCount
    }

    fun findAll(): List<FavoriteThing> = jdbi.withHandle<List<FavoriteThing>, Exception> { handle ->
        handle.createQuery("SELECT * FROM favorite_thing")
            .mapTo(FavoriteThing::class.java)
            .list()
    }

    // The batch here is just a list because we don't need to worry about concurrency in this case - once the consumer has consumed an
    // entire batch of records, the thread will wait until the running of the batch has been completed. If we were to run this in an
    // environment where concurrency was an issue, we would need to use a thread-safe data structure or synchronize access to the batch.
    private val batch = mutableListOf<FavoriteThing>()

    fun addToBatch(favoriteThing: FavoriteThing) = batch.add(favoriteThing)

    // This function will run two batch statements. The first will batch update the table with the updated timestamp, then it will see if
    // the entire batch was updates, and if not, will run a batch insert, doing nothing when there are conflicts. The reason this pattern is
    // necessary is that if we run an upsert for the batch, the postgresql driver will not actually run them as a batch, and will instead
    // turn the batch into a series of individual upserts. This is a workaround to that issue.
    fun runBatch(): Int {
        val batchSize = batch.size
        if (batch.isEmpty()) return 0

        @Language("SQL")
        val updateQuery =
            """
            UPDATE favorite_thing 
            SET updated_ts = CURRENT_TIMESTAMP 
            WHERE thing = ?
            """.trimIndent()

        @Language("SQL")
        val insertQuery =
            """
            INSERT INTO favorite_thing (thing) 
            VALUES (?) 
            ON CONFLICT ON CONSTRAINT unique_thing DO NOTHING
            """.trimIndent()

        @Language("SQL")
        val deleteQuery =
            """
            DELETE FROM favorite_thing 
            WHERE thing = ?
            """.trimIndent()

        jdbi.useTransaction<Exception> { handle ->
            val updateBatch = handle.prepareBatch(updateQuery)
            val insertBatch = handle.prepareBatch(insertQuery)
            val deleteBatch = handle.prepareBatch(deleteQuery)

            batch.forEach { favoriteThing ->
                if (favoriteThing.isDeleted) {
                    deleteBatch.bind(0, favoriteThing.thingIdentifier).add()
                } else {
                    updateBatch.bind(0, favoriteThing.thingIdentifier).add()
                    insertBatch.bind(0, favoriteThing.thingIdentifier).add()
                }
            }

            val updateResults = updateBatch.execute()

            // Run delete batch for deleted items
            deleteBatch.execute()

            // Run insert batch only if there are rows that were not updated
            if (updateResults.sum() < batch.size) {
                insertBatch.execute()
            }

            batch.clear()
        }

        return batchSize
    }
}

// Custom row mapper for FavoriteThing
class FavoriteThingMapper : RowMapper<FavoriteThing> {
    override fun map(
        rs: ResultSet,
        ctx: StatementContext,
    ): FavoriteThing {
        return FavoriteThing(
            id = rs.getLong("id"),
            thingIdentifier = rs.getString("thing"),
            createdTs = rs.getTimestamp("created_ts").toLocalDateTime(),
            updatedTs = rs.getTimestamp("updated_ts").toLocalDateTime(),
        )
    }
}
