package com.gamegear

import android.app.Application
import coil.Coil
import coil.ImageLoader
import com.gamegear.data.GameDatabase
import com.gamegear.data.GameRepository
import com.gamegear.network.IgdbService
import com.gamegear.network.TgdbService
import com.gamegear.network.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.OkHttpClient

class GameGearApp : Application() {

    val imageCache by lazy {
        Cache(
            directory = filesDir.resolve("img_http_cache"),
            maxSize = 100L * 1024 * 1024,
        )
    }

    override fun onCreate() {
        super.onCreate()
        Coil.setImageLoader {
            // Use OkHttp's HTTP cache with a network interceptor that overrides whatever
            // Cache-Control the CDN returns, so images are always stored locally after
            // first download and served from disk on every subsequent request.
            val imageClient = OkHttpClient.Builder()
                .cache(imageCache)
                .addNetworkInterceptor { chain ->
                    chain.proceed(chain.request()).newBuilder()
                        .header("Cache-Control", "public, max-age=31536000, immutable")
                        .removeHeader("Pragma")
                        .build()
                }
                .build()
            ImageLoader.Builder(this)
                .okHttpClient(imageClient)
                .build()
        }
        applicationScope.launch {
            repository.migrateLegacyCoverImages(contentResolver)
        }
        applicationScope.launch {
            repository.saveRemoteImagesLocally { _, _ -> }
        }
    }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database by lazy { GameDatabase.getInstance(this) }
    val tokenManager by lazy { TokenManager() }
    val igdbService by lazy { IgdbService.create() }
    val tgdbService by lazy { TgdbService.create() }
    val repository by lazy {
        GameRepository(
            dao = database.gameDao(),
            igdbService = igdbService,
            tgdbService = tgdbService,
            tokenManager = tokenManager,
            assets = assets,
            prefs = getSharedPreferences("gamegear_prefs", MODE_PRIVATE),
            scope = applicationScope,
            saveDir = getExternalFilesDir(null) ?: filesDir,
        )
    }
}
