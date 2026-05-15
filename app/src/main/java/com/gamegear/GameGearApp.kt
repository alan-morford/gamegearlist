package com.gamegear

import android.app.Application
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import com.gamegear.data.GameDatabase
import com.gamegear.data.GameRepository
import com.gamegear.network.IgdbService
import com.gamegear.network.TgdbService
import com.gamegear.network.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GameGearApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Coil.setImageLoader {
            ImageLoader.Builder(this)
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizePercent(0.02)
                        .build()
                }
                .build()
        }
        applicationScope.launch {
            repository.migrateLegacyCoverImages(contentResolver)
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
