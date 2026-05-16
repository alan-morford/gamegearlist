package com.gamegear

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberTopAppBarState
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
import androidx.compose.runtime.remember
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
import com.gamegear.ui.settings.SettingsScreen
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavGraph(repository: GameRepository) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val isWideLayout = screenWidthDp >= 600

    val listScrollState = rememberLazyListState()
    val listTopAppBarState = rememberTopAppBarState()
    var selectedGameId by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedGameIds by remember { mutableStateOf<List<Int>>(emptyList()) }
    var galleryGameId by rememberSaveable { mutableStateOf<Int?>(null) }
    var imagePickerGameId by rememberSaveable { mutableStateOf<Int?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var returnScrollToId by rememberSaveable { mutableStateOf<Int?>(null) }

    // In wide layout, scroll the list to keep the selected game visible when swiping
    LaunchedEffect(selectedGameId) {
        if (!isWideLayout) return@LaunchedEffect
        val id = selectedGameId ?: return@LaunchedEffect
        val index = selectedGameIds.indexOf(id)
        if (index < 0) return@LaunchedEffect
        if (listScrollState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            listScrollState.animateScrollToItem(index)
        }
    }

    // Full-screen overlays shown on top of whatever layout is active
    if (settingsOpen) {
        SettingsScreen(
            repository = repository,
            onDismiss = { settingsOpen = false },
        )
        return
    }
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
            onFindImages = {
                imagePickerGameId = galleryGameId
                galleryGameId = null
            },
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
        BackHandler {
            if (exitWarningShown) {
                (context as Activity).finish()
            } else {
                exitWarningShown = true
                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            GameListScreen(
                repository = repository,
                onGameClick = { id, orderedIds ->
                    selectedGameId = id
                    selectedGameIds = orderedIds
                },
                onOpenSettings = { settingsOpen = true },
                onFindImages = { imagePickerGameId = it },
                scrollState = listScrollState,
                topAppBarState = listTopAppBarState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                val gameId = selectedGameId
                if (gameId != null) {
                    GameDetailScreen(
                        gameId = gameId,
                        gameIds = selectedGameIds,
                        repository = repository,
                        onBack = { selectedGameId = null },
                        onOpenGallery = { galleryGameId = selectedGameId },
                        onFindImages = { imagePickerGameId = selectedGameId },
                        onGameChange = { selectedGameId = it },
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
        if (selectedGameId != null) {
            BackHandler {
                returnScrollToId = selectedGameId
                selectedGameId = null
            }
            GameDetailScreen(
                gameId = selectedGameId!!,
                gameIds = selectedGameIds,
                repository = repository,
                onBack = { selectedGameId = null },
                onOpenGallery = { galleryGameId = selectedGameId },
                onFindImages = { imagePickerGameId = selectedGameId },
                onGameChange = { selectedGameId = it },
            )
        } else {
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
                onGameClick = { id, orderedIds ->
                    selectedGameId = id
                    selectedGameIds = orderedIds
                },
                onOpenSettings = { settingsOpen = true },
                onFindImages = { imagePickerGameId = it },
                scrollState = listScrollState,
                topAppBarState = listTopAppBarState,
                scrollToGameId = returnScrollToId,
                onScrollToGameHandled = { returnScrollToId = null },
            )
        }
    }
}
