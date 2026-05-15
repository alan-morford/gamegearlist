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
enum class RegionFilter { ALL, JAPAN, USA, EUROPE }

class GameListViewModel(private val repository: GameRepository) : ViewModel() {

    val searchQuery = MutableStateFlow("")
    val filterMode = MutableStateFlow(GameFilter.ALL)
    val regionFilter = MutableStateFlow(RegionFilter.ALL)

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val games: StateFlow<List<GameEntity>> = combine(
        searchQuery.debounce(200),
        filterMode,
        regionFilter,
    ) { query, filter, region -> Triple(query, filter, region) }
        .flatMapLatest { (query, filter, region) ->
            val source = if (query.isBlank()) repository.getAllGames()
                         else repository.searchGames(query)
            source.map { list ->
                val ownershipFiltered = when (filter) {
                    GameFilter.ALL -> list
                    GameFilter.OWNED -> list.filter {
                        it.japanOwned == true || it.usaOwned == true || it.europeOwned == true
                    }
                    GameFilter.MISSING -> list.filter {
                        it.japanOwned != true && it.usaOwned != true && it.europeOwned != true
                    }
                }
                when (region) {
                    RegionFilter.ALL -> ownershipFiltered
                    RegionFilter.JAPAN  -> ownershipFiltered.filter { it.japanOwned != null && it.usaOwned == null && it.europeOwned == null }
                    RegionFilter.USA    -> ownershipFiltered.filter { it.usaOwned != null && it.japanOwned == null && it.europeOwned == null }
                    RegionFilter.EUROPE -> ownershipFiltered.filter { it.europeOwned != null && it.japanOwned == null && it.usaOwned == null }
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cycleRegionFilter() {
        regionFilter.value = when (regionFilter.value) {
            RegionFilter.ALL    -> RegionFilter.JAPAN
            RegionFilter.JAPAN  -> RegionFilter.USA
            RegionFilter.USA    -> RegionFilter.EUROPE
            RegionFilter.EUROPE -> RegionFilter.ALL
        }
    }

    init {
        repository.initializeIfNeeded()
    }

    class Factory(private val repository: GameRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GameListViewModel(repository) as T
    }
}
