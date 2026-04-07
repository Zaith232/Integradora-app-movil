package com.armonihz.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.model.HiringRequestItem
import com.armonihz.app.network.model.ReviewRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class NotificationsUiState {
    object Loading : NotificationsUiState()
    object Empty : NotificationsUiState()
    data class Success(
        val solicitudes: List<HiringRequestItem>,
        val idAAbrir: String? = null
    ) : NotificationsUiState()
    data class Error(val message: String) : NotificationsUiState()
}

class NotificationsViewModel(
    private val api: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow<NotificationsUiState>(NotificationsUiState.Loading)
    val uiState: StateFlow<NotificationsUiState> = _uiState

    private val _reviewLoading = MutableStateFlow(false)
    val reviewLoading: StateFlow<Boolean> = _reviewLoading

    private val _reviewState = MutableStateFlow<ReviewState?>(null)
    val reviewState: StateFlow<ReviewState?> = _reviewState

    private var idAAbrir: String? = null

    sealed class ReviewState {
        object Success : ReviewState()
        data class Error(val message: String) : ReviewState()
    }

    init {
        cargarSolicitudes()
    }

    fun setIdAAbrir(id: String) {
        idAAbrir = id
    }

    fun clearIdAAbrir() {
        val current = _uiState.value
        if (current is NotificationsUiState.Success) {
            _uiState.value = current.copy(idAAbrir = null)
        }
        idAAbrir = null
    }

    fun clearReviewState() {
        _reviewState.value = null
    }

    fun cargarSolicitudes() {
        _uiState.value = NotificationsUiState.Loading

        viewModelScope.launch {
            try {
                val response = api.getMyHiringRequests()

                if (response.isSuccessful && response.body() != null) {
                    val solicitudes = response.body()!!.data

                    if (solicitudes.isEmpty()) {
                        _uiState.value = NotificationsUiState.Empty
                    } else {
                        _uiState.value = NotificationsUiState.Success(
                            solicitudes = solicitudes,
                            idAAbrir = idAAbrir
                        )
                    }
                } else {
                    _uiState.value = NotificationsUiState.Error("No se pudieron cargar las solicitudes")
                }
            } catch (e: Exception) {
                _uiState.value = NotificationsUiState.Error("Error de conexión")
            }
        }
    }

    fun enviarResena(request: ReviewRequest, onSuccess: () -> Unit) {
        _reviewLoading.value = true

        viewModelScope.launch {
            try {
                val response = api.createReview(request)

                if (response.isSuccessful) {
                    _reviewState.value = ReviewState.Success
                    // Actualizar el item en la lista local sin recargar todo
                    actualizarItemResena(request.musician_profile_id)
                    onSuccess()
                } else {
                    _reviewState.value = ReviewState.Error("Error al enviar reseña")
                }
            } catch (e: Exception) {
                _reviewState.value = ReviewState.Error("Error de conexión")
            } finally {
                _reviewLoading.value = false
            }
        }
    }

    // Marca el item como reseñado localmente sin recargar toda la lista
    private fun actualizarItemResena(musicianProfileId: Int) {
        val current = _uiState.value
        if (current is NotificationsUiState.Success) {
            val listaActualizada = current.solicitudes.map { item ->
                if (item.musician_profile?.id == musicianProfileId) {
                    item.copy(has_review = true)
                } else {
                    item
                }
            }
            _uiState.value = current.copy(solicitudes = listaActualizada)
        }
    }
}

class NotificationsViewModelFactory(
    private val api: ApiService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(NotificationsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return NotificationsViewModel(api) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}