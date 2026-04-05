package com.armonihz.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.model.BusyDate
import com.armonihz.app.network.model.MultimediaItem
import com.armonihz.app.network.model.MusicianProfileDetailResponse
import com.armonihz.app.network.model.ReviewItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class MusicianProfileUiState(
    val isLoading: Boolean = true,
    val musician: MusicianProfileDetailResponse? = null,
    val photos: List<MultimediaItem> = emptyList(),
    val videos: List<MultimediaItem> = emptyList(),
    val reviews: List<ReviewItem> = emptyList(),
    val averageRating: Double? = null,
    val isFavorite: Boolean = false,
    val error: String? = null
)

sealed class MusicianProfileEvent {
    object FavoriteError : MusicianProfileEvent()
    data class Error(val message: String) : MusicianProfileEvent()
}

class MusicianProfileViewModel(
    private val api: ApiService,
    private val musicianId: Int
) : ViewModel() {

    private val _uiState = MutableStateFlow(MusicianProfileUiState())
    val uiState: StateFlow<MusicianProfileUiState> = _uiState

    private val _busyDates = MutableStateFlow<List<BusyDate>>(emptyList())
    val busyDates: StateFlow<List<BusyDate>> = _busyDates

    private val _calendarLoading = MutableStateFlow(false)
    val calendarLoading: StateFlow<Boolean> = _calendarLoading

    private val _events = MutableStateFlow<MusicianProfileEvent?>(null)
    val events: StateFlow<MusicianProfileEvent?> = _events

    init {
        cargarTodo()
    }

    fun cargarTodo() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)
        viewModelScope.launch {
            cargarPerfil()
            cargarReviews()
        }
    }

    private suspend fun cargarPerfil() {
        try {
            val response = api.getMusicianProfile(musicianId)
            if (response.isSuccessful && response.body() != null) {
                val musician = response.body()!!.data

                val photos = musician.media?.photos?.map {
                    MultimediaItem(id = it.id, type = "image", file_path = it.url)
                } ?: emptyList()

                val videos = musician.media?.videos?.map {
                    MultimediaItem(id = it.id, type = "video", file_path = it.url)
                } ?: emptyList()

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    musician = musician,
                    photos = photos,
                    videos = videos,
                    isFavorite = musician.is_favorite == true
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error al cargar datos del músico"
                )
            }
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = "Error de conexión"
            )
        }
    }

    private suspend fun cargarReviews() {
        try {
            val response = api.getMusicianReviews(musicianId)
            if (response.isSuccessful && response.body() != null) {
                val reviews = response.body()!!.data
                val average = if (reviews.isNotEmpty())
                    reviews.map { it.rating }.average()
                else null

                _uiState.value = _uiState.value.copy(
                    reviews = reviews,
                    averageRating = average
                )
            }
        } catch (e: Exception) {
            // Reviews son secundarias, fallo silencioso
        }
    }

    fun toggleFavorite() {
        val current = _uiState.value.isFavorite
        // Cambio optimista
        _uiState.value = _uiState.value.copy(isFavorite = !current)

        viewModelScope.launch {
            try {
                val response = if (!current) {
                    api.addFavorite(musicianId)
                } else {
                    api.removeFavorite(musicianId)
                }
                if (!response.isSuccessful) {
                    // Revertir si falla
                    _uiState.value = _uiState.value.copy(isFavorite = current)
                    _events.value = MusicianProfileEvent.FavoriteError
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isFavorite = current)
                _events.value = MusicianProfileEvent.FavoriteError
            }
        }
    }

    fun cargarDisponibilidad() {
        _calendarLoading.value = true
        viewModelScope.launch {
            try {
                val response = api.getMusicianAvailability(musicianId)
                if (response.isSuccessful && response.body() != null) {
                    _busyDates.value = response.body()!!.data
                } else {
                    _events.value = MusicianProfileEvent.Error("Error al cargar disponibilidad")
                }
            } catch (e: Exception) {
                _events.value = MusicianProfileEvent.Error("Sin conexión a internet")
            } finally {
                _calendarLoading.value = false
            }
        }
    }

    fun clearEvent() {
        _events.value = null
    }
}

class MusicianProfileViewModelFactory(
    private val api: ApiService,
    private val musicianId: Int
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MusicianProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MusicianProfileViewModel(api, musicianId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}