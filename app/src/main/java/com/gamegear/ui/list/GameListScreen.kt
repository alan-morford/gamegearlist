package com.gamegear.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.gamegear.R
import com.gamegear.data.GameEntity
import com.gamegear.data.GameRepository
import com.gamegear.network.GameImageUrl
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameListScreen(
    repository: GameRepository,
    onGameClick: (gameId: Int, orderedIds: List<Int>) -> Unit,
    onOpenSettings: () -> Unit,
    onFindImages: (Int) -> Unit,
    modifier: Modifier = Modifier,
    scrollState: LazyListState = rememberLazyListState(),
    topAppBarState: TopAppBarState = rememberTopAppBarState(),
) {
    val vm: GameListViewModel = viewModel(factory = GameListViewModel.Factory(repository))
    val dbGames by vm.games.collectAsState()
    val query by vm.searchQuery.collectAsState()
    val filter by vm.filterMode.collectAsState()
    val regionFilter by vm.regionFilter.collectAsState()
    val appTitle by vm.appTitle.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior(topAppBarState)
    var shouldScrollToTop by remember { mutableStateOf(false) }

    val draggableGames = remember { mutableStateListOf<GameEntity>() }
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val isDragging = draggedIndex >= 0
    val dragEnabled = query.isBlank() && filter == GameFilter.ALL && regionFilter == RegionFilter.ALL

    BackHandler(enabled = query.isNotEmpty()) {
        vm.searchQuery.value = ""
        shouldScrollToTop = true
    }

    LaunchedEffect(dbGames) {
        if (!isDragging) {
            draggableGames.clear()
            draggableGames.addAll(dbGames)
        }
        if (shouldScrollToTop) {
            shouldScrollToTop = false
            scrollState.scrollToItem(0)
        }
    }

    LaunchedEffect(dragEnabled) {
        if (!dragEnabled) {
            draggedIndex = -1
            dragOffsetY = 0f
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(appTitle) },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Image(
                            painter = painterResource(id = R.drawable.app_icon),
                            contentDescription = null,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(32.dp)
                                .clip(RoundedCornerShape(6.dp)),
                        )
                    },
                    scrollBehavior = scrollBehavior,
                )
                SearchField(
                    query = query,
                    hint = "Search ${dbGames.size} games…",
                    onQueryChange = { vm.searchQuery.value = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
                FilterRow(
                    selected = filter,
                    onSelect = {
                        if (it == GameFilter.ALL) shouldScrollToTop = true
                        vm.filterMode.value = it
                    },
                    regionFilter = regionFilter,
                    onRegionCycle = {
                        vm.cycleRegionFilter()
                        if (vm.regionFilter.value == RegionFilter.ALL) shouldScrollToTop = true
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        },
    ) { innerPadding ->
        if (draggableGames.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (query.isBlank() && filter == GameFilter.ALL && regionFilter == RegionFilter.ALL) "Loading…" else "No results",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .padding(innerPadding)
                    .then(
                        if (dragEnabled) {
                            Modifier.pointerInput(dragEnabled) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { offset ->
                                        val item = scrollState.layoutInfo.visibleItemsInfo
                                            .firstOrNull { offset.y.toInt() in it.offset..(it.offset + it.size) }
                                        item?.let {
                                            draggedIndex = it.index
                                            dragOffsetY = 0f
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        if (draggedIndex >= 0) {
                                            dragOffsetY += dragAmount.y
                                            val visibleItems = scrollState.layoutInfo.visibleItemsInfo
                                            val draggedItem = visibleItems.firstOrNull { it.index == draggedIndex }
                                                ?: return@detectDragGesturesAfterLongPress
                                            val draggedCenter = draggedItem.offset + draggedItem.size / 2 + dragOffsetY.toInt()
                                            if (dragOffsetY > 0 && draggedIndex < draggableGames.size - 1) {
                                                val nextItem = visibleItems.firstOrNull { it.index == draggedIndex + 1 }
                                                if (nextItem != null && draggedCenter > nextItem.offset + nextItem.size / 2) {
                                                    draggableGames.add(draggedIndex + 1, draggableGames.removeAt(draggedIndex))
                                                    dragOffsetY -= (nextItem.offset - draggedItem.offset).toFloat()
                                                    draggedIndex++
                                                }
                                            } else if (dragOffsetY < 0 && draggedIndex > 0) {
                                                val prevItem = visibleItems.firstOrNull { it.index == draggedIndex - 1 }
                                                if (prevItem != null && draggedCenter < prevItem.offset + prevItem.size / 2) {
                                                    draggableGames.add(draggedIndex - 1, draggableGames.removeAt(draggedIndex))
                                                    dragOffsetY += (draggedItem.offset - prevItem.offset).toFloat()
                                                    draggedIndex--
                                                }
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        if (draggedIndex >= 0) {
                                            vm.persistReorder(draggableGames.map { it.id })
                                        }
                                        draggedIndex = -1
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggableGames.clear()
                                        draggableGames.addAll(dbGames)
                                        draggedIndex = -1
                                        dragOffsetY = 0f
                                    },
                                )
                            }
                        } else Modifier
                    ),
            ) {
                itemsIndexed(draggableGames, key = { _, g -> g.id }) { index, game ->
                    val isDragged = dragEnabled && index == draggedIndex
                    GameRow(
                        game = game,
                        onClick = { if (!isDragging) onGameClick(game.id, draggableGames.map { it.id }) },
                        onFindImages = { onFindImages(game.id) },
                        showDragHandle = dragEnabled,
                        modifier = if (isDragged) {
                            Modifier
                                .zIndex(1f)
                                .offset { IntOffset(0, dragOffsetY.roundToInt()) }
                        } else {
                            Modifier.animateItem()
                        },
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        thickness = 0.5.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    selected: GameFilter,
    onSelect: (GameFilter) -> Unit,
    regionFilter: RegionFilter,
    onRegionCycle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GameFilter.entries.forEach { f ->
            FilterChip(
                selected = selected == f,
                onClick = { onSelect(f) },
                label = {
                    Text(
                        text = when (f) {
                            GameFilter.ALL -> "All"
                            GameFilter.OWNED -> "Owned"
                            GameFilter.MISSING -> "Missing"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
            )
        }
        FilterChip(
            selected = regionFilter != RegionFilter.ALL,
            onClick = onRegionCycle,
            label = {
                Text(
                    text = when (regionFilter) {
                        RegionFilter.ALL    -> "Exclusives"
                        RegionFilter.JAPAN  -> "JP Only"
                        RegionFilter.USA    -> "US Only"
                        RegionFilter.EUROPE -> "EU Only"
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            },
        )
    }
}

@Composable
private fun SearchField(
    query: String,
    hint: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = { onQueryChange("") },
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.Cancel,
                    contentDescription = "Clear search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (query.isEmpty()) 0.3f else 1f,
                    ),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun GameRow(
    game: GameEntity,
    onClick: () -> Unit,
    onFindImages: () -> Unit,
    showDragHandle: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val isOwned = game.japanOwned == true || game.usaOwned == true || game.europeOwned == true
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 76.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onFindImages),
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                if (game.coverImageId != null) {
                    AsyncImage(
                        model = GameImageUrl.thumbnail(game.coverImageId),
                        contentDescription = game.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Icon(
                Icons.Default.ImageSearch,
                contentDescription = "Find image",
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f),
                modifier = Modifier
                    .size(16.dp)
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 3.dp, end = 3.dp),
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = game.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            RegionChips(game)
        }

        if (isOwned) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Owned",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
        }

        if (showDragHandle) {
            Spacer(Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.DragHandle,
                contentDescription = "Drag to reorder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun RegionChips(game: GameEntity) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(
            "JP" to game.japanOwned,
            "US" to game.usaOwned,
            "EU" to game.europeOwned,
        ).forEach { (label, state) ->
            if (state != null) {
                RegionChip(label = label, owned = state)
            }
        }
    }
}

@Composable
private fun RegionChip(label: String, owned: Boolean) {
    val containerColor = if (owned)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (owned)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = containerColor,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}
