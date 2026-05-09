package com.gamegear

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.gamegear.data.GameRepository
import com.gamegear.ui.detail.GameDetailScreen
import com.gamegear.ui.gallery.ImageGalleryScreen
import com.gamegear.ui.imagepicker.ImagePickerScreen
import com.gamegear.ui.list.GameListScreen
import kotlinx.coroutines.delay

@Composable
fun NavGraph(repository: GameRepository) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isWideLayout = screenWidthDp >= 600

    val listScrollState = rememberLazyListState()
    var selectedGameId by rememberSaveable { mutableStateOf<Int?>(null) }
    var galleryGameId by rememberSaveable { mutableStateOf<Int?>(null) }
    var imagePickerGameId by rememberSaveable { mutableStateOf<Int?>(null) }

    // Full-screen overlays — shown on top of whatever layout is active
    if (imagePickerGameId != null) {
        ImagePickerScreen(
            gameId = imagePickerGameId!!,
            repository = repository,
            onSelect = { imagePickerGameId = null },
            onDismiss = { imagePickerGameId = null },
        )
        return
    }
    if (galleryGameId != null) {
        ImageGalleryScreen(
            gameId = galleryGameId!!,
            repository = repository,
            onDismiss = { galleryGameId = null },
        )
        return
    }

    val context = LocalContext.current
    var exitWarningShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(exitWarningShown) {
        if (exitWarningShown) {
            delay(2_000)
            exitWarningShown = false
        }
    }

    if (isWideLayout) {
        // Wide (unfolded inner screen): always show list + detail side by side.
        // System back exits the app (with toast warning) since there's no pane to pop.
        BackHandler {
            if (exitWarningShown) {
                (context as Activity).finish()
            } else {
                exitWarningShown = true
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // List pane — fixed width
            GameListScreen(
                repository = repository,
                onGameClick = { selectedGameId = it },
                scrollState = listScrollState,
                modifier = Modifier
                    .width(320.dp)
                    .fillMaxHeight(),
            )

            // Pane divider
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )

            // Detail pane — fills remaining space
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                val gameId = selectedGameId
                if (gameId != null) {
                    GameDetailScreen(
                        gameId = gameId,
                        repository = repository,
                        onBack = { selectedGameId = null },
                        onOpenGallery = { galleryGameId = gameId },
                        onFindImages = { imagePickerGameId = gameId },
                        showBackButton = false,
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "Select a game",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    } else {
        // Narrow (folded / outer screen): single pane, navigate between list and detail.
        if (selectedGameId != null) {
            // Detail is showing — back returns to list
            BackHandler { selectedGameId = null }
            GameDetailScreen(
                gameId = selectedGameId!!,
                repository = repository,
                onBack = { selectedGameId = null },
                onOpenGallery = { galleryGameId = selectedGameId },
                onFindImages = { imagePickerGameId = selectedGameId },
            )
        } else {
            // List is showing — back exits with warning
            BackHandler {
                if (exitWarningShown) {
                    (context as Activity).finish()
                } else {
                    exitWarningShown = true
                    Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                }
            }
            GameListScreen(
                repository = repository,
                onGameClick = { selectedGameId = it },
                scrollState = listScrollState,
            )
        }
    }
}
