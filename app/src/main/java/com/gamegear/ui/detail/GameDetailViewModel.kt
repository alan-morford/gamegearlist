package com.gamegear.ui.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gamegear.data.GameEntity
import com.gamegear.data.GameRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GameDetailViewModel(
    private val gameId: Int,
    private val repository: GameRepository,
) : ViewModel() {

    val game: StateFlow<GameEntity?> = repository.getGame(gameId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    // Pending notes text — debounced to avoid a DB write on every keystroke
    private val _pendingNotes = MutableStateFlow<String?>(null)

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _pendingNotes
                .filterNotNull()
                .debounce(300)
                .collect { notes ->
                    val current = game.value ?: return@collect
                    repository.updateGame(current.copy(notes = notes.ifBlank { null }))
                }
        }
    }

    fun setJapanOwned(owned: Boolean) = saveField { game ->
        if (game.japanOwned == null) game
        else {
            val newGame = game.copy(japanOwned = owned)
            newGame.copy(owned = newGame.anyRegionOwned())
        }
    }

    fun setUsaOwned(owned: Boolean) = saveField { game ->
        if (game.usaOwned == null) game
        else {
            val newGame = game.copy(usaOwned = owned)
            newGame.copy(owned = newGame.anyRegionOwned())
        }
    }

    fun setEuropeOwned(owned: Boolean) = saveField { game ->
        if (game.europeOwned == null) game
        else {
            val newGame = game.copy(europeOwned = owned)
            newGame.copy(owned = newGame.anyRegionOwned())
        }
    }

    private fun GameEntity.anyRegionOwned() =
        japanOwned == true || usaOwned == true || europeOwned == true

    fun updateNotes(text: String) {
        _pendingNotes.value = text
    }

    private fun saveField(transform: (GameEntity) -> GameEntity) {
        viewModelScope.launch {
            val current = game.filterNotNull().first()
            repository.updateGame(transform(current))
        }
    }

    class Factory(
        private val gameId: Int,
        private val repository: GameRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            GameDetailViewModel(gameId, repository) as T
    }
}
