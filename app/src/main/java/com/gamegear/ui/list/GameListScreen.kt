package com.gamegear.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.graphics.Color
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp

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
    scrollToGameId: Int? = null,
    onScrollToGameHandled: () -> Unit = {},
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
    var dragPositionY by remember { mutableFloatStateOf(0f) }
    var isScrollbarDragging by remember { mutableStateOf(false) }
    val isDragging = draggedIndex >= 0
    val dragEnabled = query.isBlank() && filter == GameFilter.ALL && regionFilter == RegionFilter.ALL

    val scrollbarAlpha by animateFloatAsState(
        targetValue = if (scrollState.isScrollInProgress || isScrollbarDragging) 1f else 0.45f,
        animationSpec = tween(durationMillis = if (scrollState.isScrollInProgress || isScrollbarDragging) 0 else 600),
        label = "scrollbar",
    )
    var showLetterPopup by remember { mutableStateOf(false) }
    val currentLetter by remember {
        derivedStateOf {
            val title = draggableGames.getOrNull(scrollState.firstVisibleItemIndex)?.title ?: ""
            val effective = if (title.startsWith("the ", ignoreCase = true)) title.drop(4) else title
            effective.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: ""
        }
    }
    LaunchedEffect(scrollState) {
        val scope = this
        var prevIndex = scrollState.firstVisibleItemIndex
        var prevTime = System.currentTimeMillis()
        var hideJob: Job? = null
        snapshotFlow { scrollState.firstVisibleItemIndex to scrollState.isScrollInProgress }
            .collect { (index, isScrolling) ->
                val now = System.currentTimeMillis()
                val dt = now - prevTime
                val delta = abs(index - prevIndex).toFloat()
                prevIndex = index
                prevTime = now
                if (isScrolling && dt in 1L..500L) {
                    val itemsPerSecond = delta / dt * 1000f
                    if (itemsPerSecond >= 15f) {
                        hideJob?.cancel()
                        showLetterPopup = true
                    }
                }
                if (!isScrolling) {
                    hideJob?.cancel()
                    hideJob = scope.launch {
                        delay(600)
                        showLetterPopup = false
                    }
                }
            }
    }

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
            return@LaunchedEffect
        }
        val returnId = scrollToGameId
        if (returnId != null) {
            val index = draggableGames.indexOfFirst { it.id == returnId }
            if (index >= 0) {
                scrollState.scrollToItem(index)
            }
            onScrollToGameHandled()
        }
    }

    LaunchedEffect(dragEnabled) {
        if (!dragEnabled) {
            draggedIndex = -1
            dragOffsetY = 0f
        }
    }

    LaunchedEffect(isDragging) {
        if (!isDragging) return@LaunchedEffect
        while (draggedIndex >= 0) {
            val info = scrollState.layoutInfo
            val viewportH = (info.viewportEndOffset - info.viewportStartOffset).toFloat()

            if (dragPositionY < 0f || dragPositionY > viewportH) {
                vm.persistReorder(draggableGames.map { it.id })
                draggedIndex = -1
                dragOffsetY = 0f
                break
            }

            // f: 0 at center, +1 at top edge, -1 at bottom edge
            val center = viewportH / 2f
            val f = ((center - dragPositionY) / center).coerceIn(-1f, 1f)
            val absFraction = abs(f)
            // exponential ramp: nearly zero near center, very fast near top/bottom visible row
            val speed = 60f * (exp(4f * absFraction) - 1f) / (exp(4f) - 1f)
            val scrollAmount = -f * speed   // positive f (near top) → scroll up (negative)

            if (scrollAmount != 0f) {
                dragOffsetY += scrollState.scrollBy(scrollAmount)
                // Swap the dragged item with any neighbor it crossed during this scroll frame.
                // Stale layoutInfo offsets are fine: both items shift by the same scroll delta,
                // so the relative comparison and the offset correction are both still correct.
                val visibleItems = scrollState.layoutInfo.visibleItemsInfo
                var swapped = true
                while (swapped) {
                    swapped = false
                    val draggedItem = visibleItems.firstOrNull { it.index == draggedIndex } ?: break
                    val draggedCenter = draggedItem.offset + draggedItem.size / 2 + dragOffsetY.toInt()
                    if (scrollAmount < 0 && draggedIndex > 0) {
                        val prevItem = visibleItems.firstOrNull { it.index == draggedIndex - 1 }
                        if (prevItem != null && draggedCenter < prevItem.offset + prevItem.size / 2) {
                            draggableGames.add(draggedIndex - 1, draggableGames.removeAt(draggedIndex))
                            dragOffsetY += (draggedItem.offset - prevItem.offset).toFloat()
                            draggedIndex--
                            swapped = true
                        }
                    } else if (scrollAmount > 0 && draggedIndex < draggableGames.size - 1) {
                        val nextItem = visibleItems.firstOrNull { it.index == draggedIndex + 1 }
                        if (nextItem != null && draggedCenter > nextItem.offset + nextItem.size / 2) {
                            draggableGames.add(draggedIndex + 1, draggableGames.removeAt(draggedIndex))
                            dragOffsetY -= (nextItem.offset - draggedItem.offset).toFloat()
                            draggedIndex++
                            swapped = true
                        }
                    }
                }
            }
            delay(16L)
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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
            LazyColumn(
                state = scrollState,
                modifier = Modifier
                    .fillMaxSize()
                    .drawWithContent {
                        drawContent()
                        val info = scrollState.layoutInfo
                        val total = info.totalItemsCount
                        val visible = info.visibleItemsInfo
                        if (total > 0 && visible.isNotEmpty()) {
                            val viewH = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                            val avgItem = visible.sumOf { it.size }.toFloat() / visible.size
                            val estimatedTotal = total * avgItem
                            val thumbH = (viewH / estimatedTotal * viewH).coerceIn(40f, viewH * 0.4f)
                            val scrolled = scrollState.firstVisibleItemIndex * avgItem + scrollState.firstVisibleItemScrollOffset
                            val maxScroll = (estimatedTotal - viewH).coerceAtLeast(1f)
                            val fraction = (scrolled / maxScroll).coerceIn(0f, 1f)
                            val thumbTop = fraction * (viewH - thumbH)
                            val trackW = if (isScrollbarDragging) 14.dp.toPx() else 10.dp.toPx()
                            val trackX = size.width - trackW - 4.dp.toPx()
                            drawRect(
                                color = Color(0xFF888888).copy(alpha = 0.15f * scrollbarAlpha),
                                topLeft = Offset(trackX, 0f),
                                size = Size(trackW, viewH),
                            )
                            drawRoundRect(
                                color = Color(0xFF888888).copy(alpha = 0.6f * scrollbarAlpha),
                                topLeft = Offset(trackX, thumbTop),
                                size = Size(trackW, thumbH),
                                cornerRadius = CornerRadius(trackW / 2),
                            )
                        }
                    }
                    .pointerInput(Unit) {
                        val tapZone = 48.dp.toPx()
                        coroutineScope {
                            val scrollChannel = Channel<Float>(Channel.CONFLATED)
                            // Unrestricted consumer: can call suspend scrollBy
                            launch {
                                for (delta in scrollChannel) scrollState.scrollBy(delta)
                            }
                            // Gesture detector: restricted AwaitPointerEventScope, uses channel
                            launch {
                                awaitEachGesture {
                                    // Wait for a fresh finger-down event
                                    var downEvent = awaitPointerEvent()
                                    while (downEvent.changes.none { it.pressed && !it.previousPressed }) {
                                        downEvent = awaitPointerEvent()
                                    }
                                    val down = downEvent.changes.first { it.pressed && !it.previousPressed }
                                    if (down.position.x < size.width - tapZone) return@awaitEachGesture
                                    isScrollbarDragging = true
                                    down.consume()
                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) break
                                            change.consume()
                                            val dy = change.position.y - change.previousPosition.y
                                            if (dy != 0f) {
                                                val info = scrollState.layoutInfo
                                                val viewH = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
                                                val visible = info.visibleItemsInfo
                                                if (visible.isNotEmpty() && info.totalItemsCount > 0) {
                                                    val avgItem = visible.sumOf { it.size }.toFloat() / visible.size
                                                    scrollChannel.trySend(dy * info.totalItemsCount * avgItem / viewH)
                                                }
                                            }
                                        }
                                    } finally {
                                        isScrollbarDragging = false
                                    }
                                }
                            }
                        }
                    }
                    .pointerInput(dragEnabled) {
                        val scrollbarTapZone = 48.dp.toPx()
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                if (dragEnabled && offset.x < size.width - scrollbarTapZone) {
                                    val item = scrollState.layoutInfo.visibleItemsInfo
                                        .firstOrNull { offset.y.toInt() in it.offset..(it.offset + it.size) }
                                    item?.let {
                                        draggedIndex = it.index
                                        dragOffsetY = 0f
                                        dragPositionY = offset.y
                                    }
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragPositionY = change.position.y
                                if (draggedIndex >= 0) {
                                    dragOffsetY += dragAmount.y
                                    val visibleItems = scrollState.layoutInfo.visibleItemsInfo
                                    val draggedItem = visibleItems.firstOrNull { it.index == draggedIndex }
                                        ?: return@detectDragGesturesAfterLongPress
                                    val viewportH = (scrollState.layoutInfo.viewportEndOffset - scrollState.layoutInfo.viewportStartOffset).toFloat()
                                    dragOffsetY = dragOffsetY.coerceIn(
                                        -draggedItem.offset.toFloat(),
                                        viewportH - draggedItem.offset - draggedItem.size,
                                    )
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
                    },
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
            AnimatedVisibility(
                visible = showLetterPopup && currentLetter.isNotEmpty(),
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 32.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shadowElevation = 2.dp,
                ) {
                    Text(
                        text = currentLetter,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
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
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
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
                Box(contentAlignment = Alignment.Center) {
                    // Invisible anchor sized to the widest label so the chip never resizes
                    Text(
                        text = "Exclusives",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.Transparent,
                    )
                    Text(
                        text = when (regionFilter) {
                            RegionFilter.ALL    -> "Exclusives"
                            RegionFilter.JAPAN  -> "JP Only"
                            RegionFilter.USA    -> "US Only"
                            RegionFilter.EUROPE -> "EU Only"
                        },
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
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
