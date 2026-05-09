package com.gamegear.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gamegear.data.GameEntity
import com.gamegear.data.GameRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

enum class GameFilter { ALL, OWNED, MISSING }

class GameListViewModel(private val repository: GameRepository) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val filterMode = MutableStateFlow(GameFilter.ALL)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val games: StateFlow<List<GameEntity>> = combine(
        searchQuery.debounce(200),
        filterMode,
    ) { query, filter -> query to filter }
        .flatMapLatest { (query, filter) ->
            val source = if (query.isBlank()) repository.getAllGames()
                         else repository.searchGames(query)
            source.map { list ->
                when (filter) {
                    GameFilter.ALL -> list
                    GameFilter.OWNED -> list.filter {
                        it.japanOwned == true || it.usaOwned == true || it.europeOwned == true
                    }
                    GameFilter.MISSING -> list.filter {
                        it.japanOwned != true && it.usaOwned != true && it.europeOwned != true
                    }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        repository.initializeIfNeeded()
    }

    class Factory(private val repository: GameRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GameListViewModel(repository) as T
    }
}
