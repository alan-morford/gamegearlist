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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
private val downloadClient = OkHttpClient()

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

    private val _appTitle = MutableStateFlow(
        prefs.getString("app_title", "Alan's Game Gear List") ?: "Alan's Game Gear List"
    )
    val appTitle: StateFlow<String> = _appTitle

    fun setAppTitle(title: String) {
        _appTitle.value = title
        prefs.edit().putString("app_title", title).apply()
    }

    fun getAllGames(): Flow<List<GameEntity>> = dao.getAllGames()
    fun searchGames(query: String): Flow<List<GameEntity>> = dao.searchGames(query)
    fun getGame(id: Int): Flow<GameEntity?> = dao.getGame(id)

    suspend fun updateGame(game: GameEntity) {
        dao.update(game)
        triggerAutoSave()
    }

    suspend fun updateGameTitle(gameId: Int, title: String) {
        dao.updateTitle(gameId, title)
    }

    suspend fun setCoverImage(gameId: Int, imageUrl: String) {
        if (imageUrl.startsWith("http")) {
            val localUri = downloadImageToFile(gameId, imageUrl)
            dao.updateCoverImageId(gameId, localUri ?: imageUrl)
        } else {
            dao.updateCoverImageId(gameId, imageUrl)
        }
    }

    suspend fun migrateLegacyCoverImages(contentResolver: ContentResolver) {
        dao.getAllGamesList()
            .filter { it.coverImageId?.startsWith("content://") == true }
            .forEach { game ->
                try {
                    copyAndSetCoverImage(game.id, contentResolver, Uri.parse(game.coverImageId!!))
                } catch (_: Exception) { /* URI no longer valid — leave as-is */ }
            }
    }

    suspend fun copyAndSetCoverImage(gameId: Int, contentResolver: ContentResolver, uri: Uri): String {
        val coversDir = File(saveDir, "covers").also { it.mkdirs() }
        val destFile = File(coversDir, "cover_$gameId.jpg")
        withContext(Dispatchers.IO) {
            contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val fileUri = Uri.fromFile(destFile).toString()
        dao.updateCoverImageId(gameId, fileUri)
        return fileUri
    }

    suspend fun deleteAllImages() {
        withContext(Dispatchers.IO) {
            File(saveDir, "covers").deleteRecursively()
        }
        dao.clearAllCoverImageIds()
    }

    private suspend fun downloadImageToFile(gameId: Int, url: String): String? {
        return try {
            withContext(Dispatchers.IO) {
                val coversDir = File(saveDir, "covers").also { it.mkdirs() }
                val destFile = File(coversDir, "cover_$gameId.jpg")
                val response = downloadClient.newCall(Request.Builder().url(url).build()).execute()
                if (!response.isSuccessful) return@withContext null
                response.body?.byteStream()?.use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext null
                Uri.fromFile(destFile).toString()
            }
        } catch (_: Exception) { null }
    }

    suspend fun saveRemoteImagesLocally(onProgress: (current: Int, total: Int) -> Unit): Int {
        val games = dao.getAllGamesList()
            .filter { it.coverImageId?.startsWith("http") == true }
        var saved = 0
        games.forEachIndexed { idx, game ->
            onProgress(idx + 1, games.size)
            val localUri = downloadImageToFile(game.id, game.coverImageId!!) ?: return@forEachIndexed
            dao.updateCoverImageId(game.id, localUri)
            saved++
        }
        return saved
    }

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

            // IGDB pass
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
                val localUri = downloadImageToFile(game.id, IgdbImageUrl.coverBig(imageId))
                dao.updateCoverImageId(game.id, localUri ?: imageId)
                if (localUri != null) found++
            }

            // TGDB pass for games still missing images after IGDB
            val stillMissing = dao.getAllGamesList().filter { it.coverImageId == null }
            val combinedTotal = allGames.size + stillMissing.size
            val apiKey = com.gamegear.BuildConfig.TGDB_API_KEY
            stillMissing.forEachIndexed { idx, game ->
                onProgress(allGames.size + idx + 1, combinedTotal)
                try {
                    val searchResponse = tgdbService.searchByName(apiKey = apiKey, name = game.title.stripRegionCodes())
                    val tgdbGame = searchResponse.data?.games?.firstOrNull() ?: return@forEachIndexed
                    val imagesResponse = tgdbService.getImages(apiKey = apiKey, gameId = tgdbGame.id, type = "boxart")
                    val data = imagesResponse.data ?: return@forEachIndexed
                    val baseUrl = data.baseUrl?.large ?: data.baseUrl?.original ?: return@forEachIndexed
                    val imageList = data.images?.get(tgdbGame.id.toString()) ?: return@forEachIndexed
                    val boxart = imageList.firstOrNull { it.type == "boxart" && it.side == "front" }
                        ?: imageList.firstOrNull { it.type == "boxart" }
                        ?: return@forEachIndexed
                    val tgdbUrl = baseUrl + boxart.filename
                    val localUri = downloadImageToFile(game.id, tgdbUrl)
                    dao.updateCoverImageId(game.id, localUri ?: tgdbUrl)
                    if (localUri != null) found++
                } catch (_: Exception) { }
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

    suspend fun loadFromZip(inputStream: java.io.InputStream) {
        withContext(Dispatchers.IO) {
            val coversDir = File(saveDir, "covers").also { it.mkdirs() }
            var jsonContent: String? = null

            java.util.zip.ZipInputStream(inputStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "gamegear_save.json" -> {
                            jsonContent = zip.readBytes().toString(Charsets.UTF_8)
                        }
                        entry.name.startsWith("covers/") && !entry.isDirectory -> {
                            val fileName = entry.name.substringAfterLast("/")
                            File(coversDir, fileName).outputStream().use { out -> zip.copyTo(out) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }

            jsonContent?.let { content ->
                val saveData = json.decodeFromString<SaveFile>(content)
                val allGames = dao.getAllGamesList().associateBy { it.id }
                saveData.games.forEach { entry ->
                    val game = allGames[entry.id] ?: return@forEach
                    dao.update(game.copy(
                        japanOwned = entry.japanOwned,
                        usaOwned = entry.usaOwned,
                        europeOwned = entry.europeOwned,
                        owned = entry.japanOwned == true || entry.usaOwned == true || entry.europeOwned == true,
                        notes = entry.notes,
                    ))
                }
            }

            coversDir.listFiles()?.forEach { imageFile ->
                val gameId = imageFile.nameWithoutExtension.removePrefix("cover_").toIntOrNull()
                    ?: return@forEach
                dao.updateCoverImageId(gameId, Uri.fromFile(imageFile).toString())
            }
        }
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

    fun getCoversDir(): File = File(saveDir, "covers")

    suspend fun updateSortOrders(ids: List<Int>) {
        dao.updateSortOrders(ids)
    }
}

private fun String.stripRegionCodes(): String =
    replace(Regex("\\s*\\([JEUWjeuw]\\)\\s*$"), "").trim()

private fun String.normalize(): String =
    lowercase()
        .replace(Regex("[^a-z0-9 ]"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
