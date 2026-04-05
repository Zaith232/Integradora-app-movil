package com.armonihz.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.model.Genre
import com.armonihz.app.network.model.MusicianProfileDetailResponse
import com.armonihz.app.network.model.ProfileResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val musicians: List<MusicianProfileDetailResponse> = emptyList(),
    val filteredMusicians: List<MusicianProfileDetailResponse> = emptyList(),
    val genres: List<Genre> = emptyList(),
    val greeting: String = "",
    val photoUrl: String? = null,
    val error: String? = null
)

class HomeViewModel(
    private val api: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    // Filtros como StateFlow separados para combinarlos reactivamente
    private val _searchQuery = MutableStateFlow("")
    private val _categoryFilter = MutableStateFlow<String?>(null)

    // Géneros cacheados a nivel de ViewModel (sobreviven rotaciones)
    private var cachedGenres: List<Genre>? = null

    init {
        // Mostrar nombre de Firebase de inmediato mientras carga la API
        val user = FirebaseAuth.getInstance().currentUser
        val nombre = user?.displayName?.split(" ")?.firstOrNull() ?: "Usuario"
        _uiState.value = _uiState.value.copy(greeting = "Hola, $nombre 👋")

        // Reactivo: cuando cambia query o categoría, re-filtrar automáticamente
        viewModelScope.launch {
            combine(_searchQuery, _categoryFilter) { query, category ->
                Pair(query, category)
            }.collect { (query, category) ->
                aplicarFiltros(query, category)
            }
        }

        cargarTodo()
    }

    fun cargarTodo() {
        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            try {
                coroutineScope {
                    val musicianosDeferred = async {
                        try { api.getAllMusicians() } catch (e: Exception) { null }
                    }
                    val perfilDeferred = async {
                        try { api.getProfile() } catch (e: Exception) { null }
                    }
                    val generosDeferred = if (cachedGenres == null) {
                        async { try { api.getGenres() } catch (e: Exception) { null } }
                    } else null

                    // Músicos primero — desactiva shimmer en cuanto llegan
                    val musicianosResponse = musicianosDeferred.await()
                    procesarMusicos(musicianosResponse)
                    _uiState.value = _uiState.value.copy(isLoading = false)

                    // Perfil y géneros sin bloquear
                    val perfilResponse = perfilDeferred.await()
                    val generosResponse = generosDeferred?.await()

                    procesarPerfil(perfilResponse)

                    val genres = generosResponse?.body()
                    if (genres != null) {
                        cachedGenres = genres
                        _uiState.value = _uiState.value.copy(genres = genres)
                    } else {
                        cachedGenres?.let {
                            _uiState.value = _uiState.value.copy(genres = it)
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Error de conexión"
                )
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query.trim().lowercase()
    }

    fun onCategorySelected(category: String?) {
        _categoryFilter.value = category
    }

    private fun aplicarFiltros(query: String, category: String?) {
        val filtered = _uiState.value.musicians.filter { musician ->
            val matchesName = query.isEmpty() ||
                    musician.stage_name.lowercase().contains(query)
            val matchesCategory = category == null ||
                    musician.genres?.any {
                        it.name.lowercase().contains(category.lowercase())
                    } == true
            matchesName && matchesCategory
        }
        _uiState.value = _uiState.value.copy(filteredMusicians = filtered)
    }

    private fun procesarMusicos(
        response: retrofit2.Response<com.google.gson.JsonObject>?
    ) {
        response?.body()?.let { json ->
            try {
                val array = json.getAsJsonObject("data").getAsJsonArray("data")
                val type = object : TypeToken<List<MusicianProfileDetailResponse>>() {}.type
                val lista: List<MusicianProfileDetailResponse> = Gson().fromJson(array, type)
                _uiState.value = _uiState.value.copy(musicians = lista)
                aplicarFiltros(_searchQuery.value, _categoryFilter.value)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Error al procesar músicos")
            }
        } ?: run {
            _uiState.value = _uiState.value.copy(error = "No se pudieron cargar los músicos")
        }
    }

    private fun procesarPerfil(response: retrofit2.Response<ProfileResponse>?) {
        response?.body()?.let { profile ->
            val nombre = profile.nombre?.split(" ")?.firstOrNull() ?: return@let
            _uiState.value = _uiState.value.copy(
                greeting = "Hola, $nombre 👋",
                photoUrl = profile.photoUrl
            )
        }
    }
}

class HomeViewModelFactory(
    private val api: ApiService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(api) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}