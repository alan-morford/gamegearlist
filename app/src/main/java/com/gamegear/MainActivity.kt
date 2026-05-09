package com.gamegear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.gamegear.ui.theme.GameGearTheme
import com.gamegear.NavGraph

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as GameGearApp
        setContent {
            GameGearTheme {
                NavGraph(repository = app.repository)
            }
        }
    }
}
