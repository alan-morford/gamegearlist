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
import kotlinx.serialization.encodeToString
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
import java.io.File

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

class GameRepository(
    private val dao: GameDao,
    private val igdbService: IgdbService,
    private val tgdbService: TgdbService,
    private val tokenManager: TokenManager,
    private val assets: AssetManager,
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope,
    private val saveDir: File,
) {
    private val saveFile get() = File(saveDir, "gamegear_save.json")

    fun getAllGames(): Flow<List<GameEntity>> = dao.getAllGames()
    fun searchGames(query: String): Flow<List<GameEntity>> = dao.searchGames(query)
    fun getGame(id: Int): Flow<GameEntity?> = dao.getGame(id)

    suspend fun updateGame(game: GameEntity) {
        dao.update(game)
        triggerAutoSave()
    }

    suspend fun setCoverImage(gameId: Int, imageUrl: String) =
        dao.updateCoverImageId(gameId, imageUrl)

    // ── Image picking ────────────────────────────────────────────────────────

    suspend fun fetchCandidateImages(gameId: Int): List<String> {
        val game = dao.getAllGamesList().firstOrNull { it.id == gameId } ?: return emptyList()
        val searchTitle = game.title.stripRegionCodes()
        return coroutineScope {
            val igdbDeferred = async { fetchIgdbCandidates(searchTitle) }
            val tgdbDeferred = async { fetchTgdbCandidates(searchTitle) }
            val combined = mutableListOf<String>()
            igdbDeferred.await().forEach { if (!combined.contains(it)) combined.add(it) }
            tgdbDeferred.await().forEach { if (!combined.contains(it)) combined.add(it) }
            combined
        }
    }

    private suspend fun fetchIgdbCandidates(title: String): List<String> {
        return try {
            val token = tokenManager.getToken() ?: return emptyList()
            val escapedTitle = title.stripRegionCodes().replace("\"", "\\\"")
            val queryBody = "search \"$escapedTitle\"; fields name,cover.image_id,artworks.image_id; where platforms=(35); limit 10;"
                .toRequestBody("text/plain".toMediaType())
            val results = igdbService.searchGameImages(
                authorization = "Bearer $token",
                body = queryBody,
            )
            buildList {
                results.forEach { result ->
                    result.cover?.imageId?.let { add(IgdbImageUrl.coverBig(it)) }
                    result.artworks?.forEach { add(IgdbImageUrl.coverBig(it.imageId)) }
                }
            }.distinct()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun fetchTgdbCandidates(title: String): List<String> {
        return try {
            val apiKey = com.gamegear.BuildConfig.TGDB_API_KEY
            val searchResponse = tgdbService.searchByName(apiKey = apiKey, name = title.stripRegionCodes())
            val games = searchResponse.data?.games ?: return emptyList()
            if (games.isEmpty()) return emptyList()

            val urls = mutableListOf<String>()
            for (game in games.take(3)) {
                val imagesResponse = tgdbService.getImages(apiKey = apiKey, gameId = game.id)
                val data = imagesResponse.data ?: continue
                val baseUrl = data.baseUrl?.large ?: data.baseUrl?.original ?: continue
                val imageList = data.images?.get(game.id.toString()) ?: continue

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

    // ── Batch image scan ──────────────────────────────────────────────────────

    suspend fun scanMissingImages(onProgress: (current: Int, total: Int) -> Unit): Int {
        return try {
            onProgress(0, -1)
            val token = tokenManager.getToken() ?: return 0
            val queryBody = "fields name,cover.image_id,artworks.image_id; where platforms=(35); limit 500;"
                .toRequestBody("text/plain".toMediaType())
            val igdbResults = igdbService.getGameGearGames(
                authorization = "Bearer $token",
                body = queryBody,
            )
            val allGames = dao.getAllGamesList()
            val igdbByNorm = igdbResults.associateBy { it.name.normalize() }
            var found = 0

            allGames.forEachIndexed { idx, game ->
                onProgress(idx + 1, allGames.size)
                if (game.coverImageId != null) return@forEachIndexed
                val norm = game.title.stripRegionCodes().normalize()
                val match = igdbByNorm[norm]
                    ?: igdbByNorm.entries.firstOrNull { (k, _) ->
                        k.contains(norm) || norm.contains(k)
                    }?.value ?: return@forEachIndexed
                val imageId = match.cover?.imageId ?: match.artworks?.firstOrNull()?.imageId
                    ?: return@forEachIndexed
                dao.updateCoverImageId(game.id, imageId)
                found++
            }
            found
        } catch (_: Exception) {
            0
        }
    }

    // ── Initialization ────────────────────────────────────────────────────────

    fun initializeIfNeeded() {
        if (!prefs.getBoolean("db_seeded", false)) {
            scope.launch(Dispatchers.IO) {
                seedDatabase()
                prefs.edit().putBoolean("db_seeded", true).apply()
            }
        }
    }

    private fun cleanTitle(raw: String): Pair<String, String?> {
        val idx = raw.indexOf('/')
        return if (idx < 0) {
            raw.replace("€", "(E)") to null
        } else {
            val primary = raw.substring(0, idx).trim().replace("€", "(E)")
            val secondary = raw.substring(idx + 1).trim().replace("€", "(E)")
            primary to secondary
        }
    }

    private suspend fun seedDatabase() {
        val text = assets.open("games.json").bufferedReader().readText()
        val array = json.parseToJsonElement(text).jsonArray
        val entities = array.mapIndexed { index, element ->
            val obj = element.jsonObject
            val rawTitle = obj["title"]!!.jsonPrimitive.content
            val (cleanedTitle, titleSuffix) = cleanTitle(rawTitle)
            val existingNotes = obj["notes"].takeIf { it != null && it !is JsonNull }
                ?.jsonPrimitive?.contentOrNull?.replace("€", "(E)")
            val finalNotes = when {
                titleSuffix != null && existingNotes != null -> "$titleSuffix\n$existingNotes"
                titleSuffix != null -> titleSuffix
                else -> existingNotes
            }
            GameEntity(
                id = index + 1,
                title = cleanedTitle,
                owned = obj["owned"]!!.jsonPrimitive.boolean,
                japanOwned = obj["japanOwned"].takeIf { it != null && it !is JsonNull }?.jsonPrimitive?.booleanOrNull,
                usaOwned = obj["usaOwned"].takeIf { it != null && it !is JsonNull }?.jsonPrimitive?.booleanOrNull,
                europeOwned = obj["europeOwned"].takeIf { it != null && it !is JsonNull }?.jsonPrimitive?.booleanOrNull,
                notes = finalNotes,
            )
        }
        dao.insertAll(entities)
    }

    // ── Save / load / reset ───────────────────────────────────────────────────

    private fun triggerAutoSave() {
        scope.launch(Dispatchers.IO) { autoSave() }
    }

    private suspend fun autoSave() {
        try {
            val games = dao.getAllGamesList()
            val data = SaveFile(
                games = games.map { g ->
                    GameSaveEntry(
                        id = g.id,
                        japanOwned = g.japanOwned,
                        usaOwned = g.usaOwned,
                        europeOwned = g.europeOwned,
                        notes = g.notes,
                    )
                }
            )
            saveDir.mkdirs()
            saveFile.writeText(json.encodeToString(data))
        } catch (_: Exception) { }
    }

    suspend fun buildSaveContent(): String {
        val games = dao.getAllGamesList()
        val data = SaveFile(
            games = games.map { g ->
                GameSaveEntry(
                    id = g.id,
                    japanOwned = g.japanOwned,
                    usaOwned = g.usaOwned,
                    europeOwned = g.europeOwned,
                    notes = g.notes,
                )
            }
        )
        return json.encodeToString(data)
    }

    suspend fun loadFromContent(content: String) {
        val saveData = json.decodeFromString<SaveFile>(content)
        val allGames = dao.getAllGamesList().associateBy { it.id }
        saveData.games.forEach { entry ->
            val game = allGames[entry.id] ?: return@forEach
            val updated = game.copy(
                japanOwned = entry.japanOwned,
                usaOwned = entry.usaOwned,
                europeOwned = entry.europeOwned,
                owned = entry.japanOwned == true || entry.usaOwned == true || entry.europeOwned == true,
                notes = entry.notes,
            )
            dao.update(updated)
        }
    }

    suspend fun resetOwnershipAndNotes() {
        dao.resetOwnershipAndNotes()
        triggerAutoSave()
    }

    fun getSaveFilePath(): String = saveFile.absolutePath
}

private fun String.stripRegionCodes(): String =
    replace(Regex("\\s*\\([JEUWjeuw]\\)\\s*$"), "").trim()

private fun String.normalize(): String =
    lowercase()
        .replace(Regex("[^a-z0-9 ]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
