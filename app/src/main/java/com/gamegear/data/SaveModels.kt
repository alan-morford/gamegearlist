package com.gamegear.data

import kotlinx.serialization.Serializable

@Serializable
data class GameSaveEntry(
    val id: Int,
    val japanOwned: Boolean? = null,
    val usaOwned: Boolean? = null,
    val europeOwned: Boolean? = null,
    val notes: String? = null,
)

@Serializable
data class SaveFile(
    val version: Int = 1,
    val games: List<GameSaveEntry>,
)
