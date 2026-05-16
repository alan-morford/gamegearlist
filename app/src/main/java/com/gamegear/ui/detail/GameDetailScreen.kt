package com.gamegear.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.gamegear.data.GameRepository
import com.gamegear.network.GameImageUrl
import kotlinx.coroutines.flow.drop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameDetailScreen(
    gameId: Int,
    gameIds: List<Int> = emptyList(),
    repository: GameRepository,
    onBack: () -> Unit,
    onOpenGallery: () -> Unit,
    onFindImages: () -> Unit,
    onGameChange: (Int) -> Unit = {},
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val effectiveIds = gameIds.ifEmpty { listOf(gameId) }
    val initialPage = effectiveIds.indexOf(gameId).coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = initialPage) { effectiveIds.size }

    // Jump to the correct page when gameId changes externally (e.g. tap in list)
    LaunchedEffect(gameId) {
        val targetPage = effectiveIds.indexOf(gameId).coerceAtLeast(0)
        if (pagerState.currentPage != targetPage) {
            pagerState.scrollToPage(targetPage)
        }
    }

    val currentPageId = effectiveIds.getOrElse(pagerState.currentPage) { gameId }
    val topBarVm: GameDetailViewModel = viewModel(
        key = "detail_$currentPageId",
        factory = GameDetailViewModel.Factory(currentPageId, repository),
    )
    val game by topBarVm.game.collectAsState()

    val currentOnGameChange by rememberUpdatedState(onGameChange)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .drop(1)
            .collect { page ->
                currentOnGameChange(effectiveIds.getOrElse(page) { gameId })
            }
    }

    var showEditTitleDialog by remember { mutableStateOf(false) }
    var editTitleText by remember { mutableStateOf("") }

    if (showEditTitleDialog) {
        AlertDialog(
            onDismissRequest = { showEditTitleDialog = false },
            title = { Text("Edit Title") },
            text = {
                OutlinedTextField(
                    value = editTitleText,
                    onValueChange = { editTitleText = it },
                    singleLine = true,
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        topBarVm.updateTitle(editTitleText.trim())
                        showEditTitleDialog = false
                    },
                    enabled = editTitleText.isNotBlank(),
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditTitleDialog = false }) { Text("Cancel") }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .windowInsetsPadding(TopAppBarDefaults.windowInsets)
                        .heightIn(min = 64.dp)
                        .padding(end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    } else {
                        Spacer(Modifier.width(16.dp))
                    }
                    Text(
                        text = game?.title ?: "",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 16.dp),
                    )
                    IconButton(
                        onClick = {
                            editTitleText = game?.title ?: ""
                            showEditTitleDialog = true
                        },
                    ) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Edit title",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) { page ->
            GameDetailPage(
                gameId = effectiveIds.getOrElse(page) { gameId },
                repository = repository,
                onOpenGallery = onOpenGallery,
                onFindImages = onFindImages,
            )
        }
    }
}

@Composable
private fun GameDetailPage(
    gameId: Int,
    repository: GameRepository,
    onOpenGallery: () -> Unit,
    onFindImages: () -> Unit,
) {
    val vm: GameDetailViewModel = viewModel(
        key = "detail_$gameId",
        factory = GameDetailViewModel.Factory(gameId, repository),
    )
    val game by vm.game.collectAsState()
    val g = game

    if (g == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var notesText by remember { mutableStateOf("") }
    LaunchedEffect(g.id) {
        notesText = g.notes.orEmpty()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        if (g.coverImageId != null) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .aspectRatio(3f / 4f),
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(onClick = onOpenGallery),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        AsyncImage(
                            model = GameImageUrl.thumbnail(g.coverImageId),
                            contentDescription = g.title,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    IconButton(
                        onClick = onFindImages,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp),
                    ) {
                        Icon(
                            Icons.Default.ImageSearch,
                            contentDescription = "Find images",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Regions",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            RegionRow(label = "Japan", state = g.japanOwned, onToggle = vm::setJapanOwned)
            RegionRow(label = "USA", state = g.usaOwned, onToggle = vm::setUsaOwned)
            RegionRow(label = "Europe", state = g.europeOwned, onToggle = vm::setEuropeOwned)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Notes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = notesText,
                onValueChange = { text ->
                    notesText = text
                    vm.updateNotes(text)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Add a note…") },
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

@Composable
private fun RegionRow(label: String, state: Boolean?, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(width = 56.dp, height = 28.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        when (state) {
            null -> Text(
                text = "N/A",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.weight(1f),
            )
            else -> {
                Text(
                    text = if (state) "Owned" else "Not owned",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state,
                    onCheckedChange = onToggle,
                )
            }
        }
    }
}
