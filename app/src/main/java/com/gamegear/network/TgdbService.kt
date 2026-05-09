package com.gamegear.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import okhttp3.MediaType.Companion.toMediaType

@Serializable
data class TgdbGameResult(
    val id: Int,
    @SerialName("game_title") val gameTitle: String,
)

@Serializable
data class TgdbGamesData(
    val games: List<TgdbGameResult>? = null,
)

@Serializable
data class TgdbGamesResponse(
    val data: TgdbGamesData? = null,
)

@Serializable
data class TgdbImage(
    val id: Int,
    val type: String,
    val side: String? = null,
    val filename: String,
)

@Serializable
data class TgdbImagesData(
    val images: Map<String, List<TgdbImage>>? = null,
    @SerialName("base_url") val baseUrl: TgdbBaseUrl? = null,
)

@Serializable
data class TgdbBaseUrl(
    val original: String,
    val large: String? = null,
    val medium: String? = null,
    val thumb: String? = null,
)

@Serializable
data class TgdbImagesResponse(
    val data: TgdbImagesData? = null,
)

interface TgdbService {

    @GET("Games/ByGameName")
    suspend fun searchByName(
        @Query("apikey") apiKey: String,
        @Query("name") name: String,
        @Query("filter[platform][]") platformId: Int = GAME_GEAR_PLATFORM_ID,
        @Query("fields") fields: String = "game_title",
    ): TgdbGamesResponse

    @GET("Games/Images")
    suspend fun getImages(
        @Query("apikey") apiKey: String,
        @Query("games_id") gameId: Int,
        @Query("filter[type][]") type: String? = null,
    ): TgdbImagesResponse

    companion object {
        const val GAME_GEAR_PLATFORM_ID = 20

        private val jsonConverter = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        fun create(): TgdbService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .build()

            return Retrofit.Builder()
                .baseUrl("https://api.thegamesdb.net/v1/")
                .client(client)
                .addConverterFactory(
                    jsonConverter.asConverterFactory("application/json".toMediaType())
                )
                .build()
                .create(TgdbService::class.java)
        }
    }
}
