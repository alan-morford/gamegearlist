package com.gamegear.network

import com.gamegear.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

private val tokenJson = Json { ignoreUnknownKeys = true }

@Serializable
private data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("expires_in") val expiresIn: Long,
)

class TokenManager {
    private val client = OkHttpClient()
    private var cachedToken: String? = null
    private var expiresAt: Long = 0L

    suspend fun getToken(): String? {
        if (BuildConfig.IGDB_CLIENT_ID.isBlank() || BuildConfig.IGDB_CLIENT_SECRET.isBlank()) return null
        val now = System.currentTimeMillis()
        if (cachedToken != null && now < expiresAt - 60_000) return cachedToken

        return withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("client_id", BuildConfig.IGDB_CLIENT_ID)
                    .add("client_secret", BuildConfig.IGDB_CLIENT_SECRET)
                    .add("grant_type", "client_credentials")
                    .build()
                val request = Request.Builder()
                    .url("https://id.twitch.tv/oauth2/token")
                    .post(body)
                    .build()
                val response = client.newCall(request).execute()
                val bodyStr = response.body?.string() ?: return@withContext null
                val tokenResponse = tokenJson.decodeFromString<TokenResponse>(bodyStr)
                cachedToken = tokenResponse.accessToken
                expiresAt = now + tokenResponse.expiresIn * 1000
                cachedToken
            } catch (_: Exception) {
                null
            }
        }
    }
}
