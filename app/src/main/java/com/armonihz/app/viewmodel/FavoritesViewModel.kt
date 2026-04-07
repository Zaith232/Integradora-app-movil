package com.armonihz.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.model.MusicianProfileDetailResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class FavoritesUiState {
    object Loading : FavoritesUiState()
    object Empty : FavoritesUiState()
    data class Success(val favoritos: List<MusicianProfileDetailResponse>) : FavoritesUiState()
    data class Error(val message: String) : FavoritesUiState()
}

class FavoritesViewModel(
    private val api: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow<FavoritesUiState>(FavoritesUiState.Loading)
    val uiState: StateFlow<FavoritesUiState> = _uiState

    // Lista mutable para eliminación optimista
    private val _listaActual = MutableStateFlow<List<MusicianProfileDetailResponse>>(emptyList())
    val listaActual: StateFlow<List<MusicianProfileDetailResponse>> = _listaActual

    init {
        cargarFavoritos()
    }

    private fun cargarFavoritos() {
        _uiState.value = FavoritesUiState.Loading

        viewModelScope.launch {
            try {
                val response = api.getMyFavorites()

                if (response.isSuccessful && response.body() != null) {
                    val favoritos = response.body()!!.data
                    _listaActual.value = favoritos

                    if (favoritos.isEmpty()) {
                        _uiState.value = FavoritesUiState.Empty
                    } else {
                        _uiState.value = FavoritesUiState.Success(favoritos)
                    }
                } else {
                    _uiState.value = FavoritesUiState.Error("Error al cargar favoritos")
                }
            } catch (e: Exception) {
                _uiState.value = FavoritesUiState.Error("Error de conexión")
            }
        }
    }

    fun eliminarFavorito(musician: MusicianProfileDetailResponse, position: Int) {
        // Eliminación optimista: actualizar UI antes de llamar al servidor
        val listaActualizada = _listaActual.value.toMutableList()
        listaActualizada.removeAt(position)
        _listaActual.value = listaActualizada

        viewModelScope.launch {
            try {
                val response = api.removeFavorite(musician.id)
                if (!response.isSuccessful) {
                    // Si el servidor falla, revertir y recargar
                    _uiState.value = FavoritesUiState.Error("Error al sincronizar, intenta de nuevo")
                    cargarFavoritos()
                }
            } catch (e: Exception) {
                _uiState.value = FavoritesUiState.Error("Revisa tu conexión a internet")
                cargarFavoritos()
            }
        }
    }
}

class FavoritesViewModelFactory(
    private val api: ApiService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FavoritesViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FavoritesViewModel(api) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}