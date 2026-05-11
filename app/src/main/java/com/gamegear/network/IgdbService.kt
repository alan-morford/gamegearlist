package com.gamegear.network

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

@Serializable
data class IgdbCover(
    @SerialName("image_id") val imageId: String,
)

@Serializable
data class IgdbArtwork(
    @SerialName("image_id") val imageId: String,
)

@Serializable
data class IgdbGameResult(
    val id: Int,
    val name: String,
    val cover: IgdbCover? = null,
    val artworks: List<IgdbArtwork>? = null,
)

interface IgdbService {

    @POST("games")
    suspend fun getGameGearGames(
        @Header("Authorization") authorization: String,
        @Body body: RequestBody,
    ): List<IgdbGameResult>

    @POST("games")
    suspend fun searchGameImages(
        @Header("Authorization") authorization: String,
        @Body body: RequestBody,
    ): List<IgdbGameResult>

    companion object {
        private val jsonConverter = Json { ignoreUnknownKeys = true; coerceInputValues = true }

        fun create(): IgdbService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .addInterceptor { chain ->
                    val request = chain.request().newBuilder()
                        .header("Client-ID", com.gamegear.BuildConfig.IGDB_CLIENT_ID)
                        .header("Accept", "application/json")
                        .build()
                    chain.proceed(request)
                }
                .build()

            return Retrofit.Builder()
                .baseUrl("https://api.igdb.com/v4/")
                .client(client)
                .addConverterFactory(
                    jsonConverter.asConverterFactory("application/json".toMediaType())
                )
                .build()
                .create(IgdbService::class.java)
        }
    }
}

object IgdbImageUrl {
    fun thumbnail(imageId: String) =
        "https://images.igdb.com/igdb/image/upload/t_thumb/$imageId.jpg"

    fun coverBig(imageId: String) =
        "https://images.igdb.com/igdb/image/upload/t_cover_big/$imageId.jpg"
}
