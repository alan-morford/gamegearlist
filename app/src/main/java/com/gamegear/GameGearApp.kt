package com.gamegear

import android.app.Application
import com.gamegear.data.GameDatabase
import com.gamegear.data.GameRepository
import com.gamegear.network.IgdbService
import com.gamegear.network.TgdbService
import com.gamegear.network.TokenManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class GameGearApp : Application() {

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
        )
    }
}
