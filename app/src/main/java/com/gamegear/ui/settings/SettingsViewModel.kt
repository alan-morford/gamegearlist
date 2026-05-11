package com.gamegear.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gamegear.data.GameRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: GameRepository) : ViewModel() {

    sealed class ScanState {
        object Idle : ScanState()
        object Connecting : ScanState()
        data class Scanning(val current: Int, val total: Int) : ScanState()
        data class Done(val message: String) : ScanState()
    }

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState

    fun resetAll() {
        viewModelScope.launch { repository.resetOwnershipAndNotes() }
    }

    fun loadFromContent(content: String) {
        viewModelScope.launch {
            try {
                repository.loadFromContent(content)
            } catch (_: Exception) { }
        }
    }

    suspend fun buildSaveContent(): String = repository.buildSaveContent()

    fun scanMissingImages() {
        if (_scanState.value is ScanState.Scanning || _scanState.value is ScanState.Connecting) return
        viewModelScope.launch {
            _scanState.value = ScanState.Connecting
            val found = repository.scanMissingImages { current, total ->
                _scanState.value = if (total == -1) ScanState.Connecting
                else ScanState.Scanning(current, total)
            }
            _scanState.value = ScanState.Done(
                if (found == 0) "No new images found"
                else "$found image${if (found != 1) "s" else ""} added"
            )
        }
    }

    class Factory(private val repository: GameRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(repository) as T
    }
}
