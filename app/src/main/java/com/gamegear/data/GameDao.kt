package com.gamegear.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {

    @Query("SELECT * FROM games ORDER BY title ASC")
    fun getAllGames(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE id = :id")
    fun getGame(id: Int): Flow<GameEntity?>

    @Query("SELECT * FROM games WHERE title LIKE '%' || :query || '%' ORDER BY title ASC")
    fun searchGames(query: String): Flow<List<GameEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(games: List<GameEntity>)

    @Update
    suspend fun update(game: GameEntity)

    @Query("SELECT COUNT(*) FROM games")
    suspend fun count(): Int

    @Query("SELECT * FROM games ORDER BY title ASC")
    suspend fun getAllGamesList(): List<GameEntity>

    @Query("UPDATE games SET igdbId = :igdbId, coverImageId = :coverImageId, screenshotIds = :screenshotIds WHERE id = :id")
    suspend fun updateImages(id: Int, igdbId: Int?, coverImageId: String?, screenshotIds: String?)

    @Query("UPDATE games SET coverImageId = :imageId WHERE id = :id")
    suspend fun updateCoverImageId(id: Int, imageId: String)
}
