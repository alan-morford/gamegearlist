package com.gamegear.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {

    @Query("SELECT * FROM games ORDER BY sortOrder ASC, title ASC")
    fun getAllGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    fun getGame(id: Int): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE title LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%' ORDER BY sortOrder ASC, title ASC")
    fun searchGames(query: String): Flow<List<GameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(games: List<GameEntity>)

    @Update
    suspend fun update(game: GameEntity)

    @Query("SELECT COUNT(*) FROM games")
    suspend fun count(): Int

    @Query("SELECT * FROM games ORDER BY sortOrder ASC, title ASC")
    suspend fun getAllGamesList(): List<GameEntity>

    @Query("SELECT * FROM games WHERE coverImageId IS NULL ORDER BY sortOrder ASC, title ASC")
    suspend fun getGamesWithoutImages(): List<GameEntity>

    @Query("""
        UPDATE games
        SET japanOwned  = CASE WHEN japanOwned  IS NOT NULL THEN 0 ELSE NULL END,
            usaOwned    = CASE WHEN usaOwned    IS NOT NULL THEN 0 ELSE NULL END,
            europeOwned = CASE WHEN europeOwned IS NOT NULL THEN 0 ELSE NULL END,
            owned = 0,
            notes = NULL
    """)
    suspend fun resetOwnershipAndNotes()

    @Query("UPDATE games SET igdbId = :igdbId, coverImageId = :coverImageId, screenshotIds = :screenshotIds WHERE id = :id")
    suspend fun updateImages(id: Int, igdbId: Int?, coverImageId: String?, screenshotIds: String?)

    @Query("UPDATE games SET coverImageId = :imageId WHERE id = :id")
    suspend fun updateCoverImageId(id: Int, imageId: String)

    @Query("UPDATE games SET coverImageId = NULL")
    suspend fun clearAllCoverImageIds()

    @Query("UPDATE games SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Int, title: String)

    @Query("UPDATE games SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun updateSortOrder(id: Int, sortOrder: Int)

    @Transaction
    suspend fun updateSortOrders(ids: List<Int>) {
        ids.forEachIndexed { index, id -> updateSortOrder(id, index) }
    }
}
