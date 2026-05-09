package com.gamegear.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "games")
data class GameEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val owned: Boolean,
    /** null = never released in this region; false = released, not owned; true = owned */
    val japanOwned: Boolean?,
    val usaOwned: Boolean?,
    val europeOwned: Boolean?,
    val notes: String?,
    val igdbId: Int? = null,
    val coverImageId: String? = null,
    /** JSON array string of IGDB screenshot image IDs */
    val screenshotIds: String? = null,
)
