package vanillakotlin.db.repository

import org.intellij.lang.annotations.Language
import java.sql.PreparedStatement
import java.sql.ResultSet

typealias AddToBatch = (UserFavoriteTcin) -> Unit
typealias RunBatch = () -> Int

class UserFavoriteTcinRepository(private val db: Db) {
    fun upsert(favorite: UserFavoriteTcin): UserFavoriteTcin {
        return db.withTransaction { tx ->
            val userFavoriteTcin =
                checkNotNull(
                    tx.executeQuery(
                        sql =
                        """
                            INSERT INTO user_favorite_tcin (user_name, tcin)
                              VALUES (:userName, :tcin)
                            ON CONFLICT ON CONSTRAINT unique_user_tcin DO UPDATE 
                              SET updated_ts = CURRENT_TIMESTAMP
                            RETURNING *
                            """.trimIndent(),
                        args = favorite.propertiesToMap(),
                        rowMapper = ::mapToUserFavoriteTcin,
                    ),
                ) { "Unexpected state: Insert didn't return a result." }

            insertOutbox(tx, userFavoriteTcin.buildOutbox())

            userFavoriteTcin
        }
    }

    fun deleteByUserNameAndTcin(
        userName: UserName,
        tcin: Tcin,
    ): Int {
        return db.withTransaction { tx ->
            val rowCount =
                tx.executeUpdate(
                    sql = "DELETE FROM user_favorite_tcin WHERE user_name = :userName AND tcin = :tcin",
                    args = mapOf("userName" to userName, "tcin" to tcin),
                )

            if (rowCount > 0) {
                insertOutbox(tx, buildDeletedOutbox(userName, tcin))
            }

            rowCount
        }
    }

    fun findByUserName(userName: UserName): List<UserFavoriteTcin> =
        db.findAll(
            sql = "SELECT * FROM user_favorite_tcin WHERE user_name = :userName",
            args = mapOf("userName" to userName),
            rowMapper = ::mapToUserFavoriteTcin,
        )

    // The batch here is just a list because we don't need to worry about concurrency in this case - once the consumer has consumed an
    // entire batch of records, the thread will wait until the running of the batch has been completed. If we were to run this in an
    // environment where concurrency was an issue, we would need to use a thread-safe data structure or synchronize access to the batch.
    private val batch = mutableListOf<UserFavoriteTcin>()

    fun addToBatch(userFavoriteTcin: UserFavoriteTcin) = batch.add(userFavoriteTcin)

    // This function will run two batch statements. The first will batch update the table with the updated timestamp, then it will see if
    // the entire batch was updates, and if not, will run a batch insert, doing nothing when there are conflicts. The reason this pattern is
    // necessary is that if we run an upsert for the batch, the postgresql driver will not actually run them as a batch, and will instead
    // turn the batch into a series of individual upserts. This is a workaround to that issue.
    //
    // Notice that the queries are not using name parameters here, this is due to the way the function setParameters works
    // in lite-for-jdbc. The library uses indexing to set the parameters, so named parameters can't be used
    fun runBatch(): Int {
        val batchSize = batch.size
        takeIf { batch.isNotEmpty() }?.run {
            @Language("SQL") val updateQuery =
                """
                UPDATE user_favorite_tcin 
                SET updated_ts = CURRENT_TIMESTAMP 
                WHERE user_name = ? AND tcin = ?
                """.trimIndent()

            @Language("SQL") val insertQuery =
                """
                INSERT INTO user_favorite_tcin (user_name, tcin) 
                VALUES (?, ?) 
                ON CONFLICT ON CONSTRAINT unique_user_tcin DO NOTHING
                """.trimIndent()

            @Language("SQL") val deleteQuery =
                """
                DELETE FROM user_favorite_tcin 
                WHERE user_name = ? AND tcin = ?
                """.trimIndent()

            // We're going to create 3 prepared statements here, one for each query. We're going to use the same connection for all of them.
            // We're going to batch the updates, inserts, and deletes together, and then run all the updates first as a batch first. If all
            // the batch was updated, we're done. If not, we'll run the deletes and inserts as two other batches.  This will run at most
            // three round trips per batch, where if this was an upsert would require a round trip per record. If there are failures during
            // the batch, the entire batch will be rolled back before the app restarts.
            db.useConnection { conn ->
                val (update, insert, delete) = listOf(updateQuery, insertQuery, deleteQuery).map(conn::prepareStatement)
                update.useWithRollback { updateStatement ->
                    insert.useWithRollback { insertStatement ->
                        delete.useWithRollback { deleteStatement ->
                            batch.forEach { userFavoriteTcin ->
                                with(userFavoriteTcin) {
                                    if (isDeleted) {
                                        with(deleteStatement) { setParameters(userName, tcin).also { addBatch() } }
                                    } else {
                                        with(updateStatement) { setParameters(userName, tcin).also { addBatch() } }
                                        with(insertStatement) { setParameters(userName, tcin).also { addBatch() } }
                                    }
                                }
                                val batchUpdateResults = updateStatement.executeBatch()
                                // runs the batch insert only if there are rows that were not updated
                                if (batchUpdateResults.sum() < batch.size) {
                                    deleteStatement.executeBatch()
                                    insertStatement.executeBatch()
                                }
                            }
                        }
                    }
                }
                conn.commit().also { batch.clear() }
            }
        }
        return batchSize
    }

    private inline fun <T> PreparedStatement.useWithRollback(block: (PreparedStatement) -> T): T =
        use {
            connection.autoCommit = false
            runCatching { block(this) }.onFailure { connection.rollback() }.getOrThrow()
        }
}

private fun mapToUserFavoriteTcin(resultSet: ResultSet) =
    with(resultSet) {
        UserFavoriteTcin(
            id = getLong("id"),
            userName = getString("user_name"),
            tcin = getString("tcin"),
            createdTs = getInstant("created_ts"),
            updatedTs = getInstant("updated_ts"),
        )
    }
