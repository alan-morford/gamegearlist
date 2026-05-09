package com.gamegear.data

import android.content.SharedPreferences
import android.content.res.AssetManager
import com.gamegear.network.IgdbGameResult
import com.gamegear.network.IgdbImageUrl
import com.gamegear.network.IgdbService
import com.gamegear.network.TgdbService
import com.gamegear.network.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

class GameRepository(
    private val dao: GameDao,
    private val igdbService: IgdbService,
    private val tgdbService: TgdbService,
    private val tokenManager: TokenManager,
    private val assets: AssetManager,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
) {
    fun getAllGames(): Flow<List<GameEntity>> = dao.getAllGames()
    fun searchGames(query: String): Flow<List<GameEntity>> = dao.searchGames(query)
    fun getGame(id: Int): Flow<GameEntity?> = dao.getGame(id)

    suspend fun updateGame(game: GameEntity) = dao.update(game)

    suspend fun setCoverImage(gameId: Int, imageUrl: String) =
        dao.updateCoverImageId(gameId, imageUrl)

    /** Returns full image URLs from both IGDB and TheGamesDB combined. */
    suspend fun fetchCandidateImages(gameId: Int): List<String> {
        val game = dao.getAllGamesList().firstOrNull { it.id == gameId } ?: return emptyList()
        return coroutineScope {
            val igdbDeferred = async { fetchIgdbCandidates(game.title) }
            val tgdbDeferred = async { fetchTgdbCandidates(game.title) }
            val combined = mutableListOf<String>()
            igdbDeferred.await().forEach { if (!combined.contains(it)) combined.add(it) }
            tgdbDeferred.await().forEach { if (!combined.contains(it)) combined.add(it) }
            combined
        }
    }

    private suspend fun fetchIgdbCandidates(title: String): List<String> {
        return try {
            val token = tokenManager.getToken() ?: return emptyList()
            val escapedTitle = title.replace("\"", "\\\"")
            val queryBody = "search \"$escapedTitle\"; fields name,cover.image_id,screenshots.image_id,artworks.image_id; where platforms=(35); limit 10;"
                .toRequestBody("text/plain".toMediaType())
            val results = igdbService.searchGameImages(
                authorization = "Bearer $token",
                body = queryBody,
            )
            buildList {
                results.forEach { result ->
                    result.cover?.imageId?.let { add(IgdbImageUrl.coverBig(it)) }
                    result.artworks?.forEach { add(IgdbImageUrl.coverBig(it.imageId)) }
                    result.screenshots?.forEach { add(IgdbImageUrl.screenshot(it.imageId)) }
                }
            }.distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchTgdbCandidates(title: String): List<String> {
        return try {
            val apiKey = com.gamegear.BuildConfig.TGDB_API_KEY
            val searchResponse = tgdbService.searchByName(apiKey = apiKey, name = title)
            val games = searchResponse.data?.games ?: return emptyList()
            if (games.isEmpty()) return emptyList()

            val urls = mutableListOf<String>()
            // Fetch images for up to 3 matching games to maximise results
            for (game in games.take(3)) {
                val imagesResponse = tgdbService.getImages(apiKey = apiKey, gameId = game.id)
                val data = imagesResponse.data ?: continue
                val baseUrl = data.baseUrl?.large ?: data.baseUrl?.original ?: continue
                val imageList = data.images?.get(game.id.toString()) ?: continue

                // Order: box front, box back, screenshots — skip fanart/clearlogo
                val ordered = imageList.sortedWith(compareBy {
                    when {
                        it.type == "boxart" && it.side == "front" -> 0
                        it.type == "boxart" && it.side == "back" -> 1
                        it.type == "screenshot" -> 2
                        else -> 3
                    }
                })
                ordered.forEach { image ->
                    if (image.type in listOf("boxart", "screenshot")) {
                        val url = baseUrl + image.filename
                        if (!urls.contains(url)) urls.add(url)
                    }
                }
            }
            urls
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun initializeIfNeeded() {
        if (!prefs.getBoolean("db_seeded", false)) {
            scope.launch(Dispatchers.IO) {
                seedDatabase()
                prefs.edit().putBoolean("db_seeded", true).apply()
                fetchIgdbImages()
            }
        } else if (!prefs.getBoolean("images_fetched", false)) {
            scope.launch(Dispatchers.IO) { fetchIgdbImages() }
        }
    }

    private suspend fun seedDatabase() {
        val text = assets.open("games.json").bufferedReader().readText()
        val array = json.parseToJsonElement(text).jsonArray
        val entities = array.mapIndexed { index, element ->
            val obj = element.jsonObject
            GameEntity(
                id = index + 1,
                title = obj["title"]!!.jsonPrimitive.content,
                owned = obj["owned"]!!.jsonPrimitive.boolean,
                japanOwned = obj["japanOwned"].takeIf { it != null && it !is JsonNull }?.jsonPrimitive?.booleanOrNull,
                usaOwned = obj["usaOwned"].takeIf { it != null && it !is JsonNull }?.jsonPrimitive?.booleanOrNull,
                europeOwned = obj["europeOwned"].takeIf { it != null && it !is JsonNull }?.jsonPrimitive?.booleanOrNull,
                notes = obj["notes"].takeIf { it != null && it !is JsonNull }?.jsonPrimitive?.contentOrNull,
            )
        }
        dao.insertAll(entities)
    }

    private suspend fun fetchIgdbImages() {
        try {
            val token = tokenManager.getToken() ?: return
            val queryBody = "fields name,cover.image_id,screenshots.image_id,artworks.image_id; where platforms=(35); limit 500;"
                .toRequestBody("text/plain".toMediaType())
            val results = igdbService.getGameGearGames(
                authorization = "Bearer $token",
                body = queryBody,
            )
            val allGames = dao.getAllGamesList()
            matchAndStoreImages(allGames, results)
            prefs.edit().putBoolean("images_fetched", true).apply()
        } catch (_: Exception) {
            // Images are optional — app works without them
        }
    }

    private suspend fun matchAndStoreImages(
        games: List<GameEntity>,
        igdbResults: List<IgdbGameResult>,
    ) {
        val igdbByNorm = igdbResults.associateBy { it.name.normalize() }

        for (game in games) {
            val norm = game.title.normalize()
            val match = igdbByNorm[norm]
                ?: igdbByNorm.entries.firstOrNull { (k, _) -> k.contains(norm) || norm.contains(k) }?.value
                ?: continue

            val screenshotJson = match.screenshots
                ?.map { "\"${it.imageId}\"" }
                ?.joinToString(",", "[", "]")

            dao.updateImages(
                id = game.id,
                igdbId = match.id,
                coverImageId = match.cover?.imageId,
                screenshotIds = screenshotJson,
            )
        }
    }
}

private fun String.normalize(): String =
    lowercase()
        .replace(Regex("[^a-z0-9 ]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
