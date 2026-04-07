package com.armonihz.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.network.model.EventRequest
import com.armonihz.app.network.model.Genre
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed class AddEventUiState {
    object Idle : AddEventUiState()
    object Loading : AddEventUiState()
    object Success : AddEventUiState()
    data class Error(val message: String) : AddEventUiState()
}

class AddEventViewModel(
    private val api: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow<AddEventUiState>(AddEventUiState.Idle)
    val uiState: StateFlow<AddEventUiState> = _uiState

    private val _genres = MutableStateFlow<List<Genre>>(emptyList())
    val genres: StateFlow<List<Genre>> = _genres

    private val _ciudades = MutableStateFlow<List<String>>(emptyList())
    val ciudades: StateFlow<List<String>> = _ciudades

    init {
        cargarGeneros()
    }

    private fun cargarGeneros() {
        viewModelScope.launch {
            try {
                val response = api.getGenres()
                if (response.isSuccessful) {
                    _genres.value = response.body() ?: emptyList()
                } else {
                    _uiState.value = AddEventUiState.Error("Error al cargar géneros")
                }
            } catch (e: Exception) {
                _uiState.value = AddEventUiState.Error("Sin conexión al cargar géneros")
            }
        }
    }

    fun cargarCiudades(assets: android.content.res.AssetManager) {
        if (_ciudades.value.isNotEmpty()) return // ya cargadas, no repetir

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = assets.open("municipios_mexico.json").bufferedReader().use { it.readText() }
                val arr = org.json.JSONArray(json)
                val lista = (0 until arr.length()).map {
                    val o = arr.getJSONObject(it)
                    "${o.getString("municipio")}, ${o.getString("estado")}"
                }
                withContext(Dispatchers.Main) {
                    _ciudades.value = lista
                }
            } catch (e: Exception) {
                _uiState.value = AddEventUiState.Error("Error al cargar ciudades")
            }
        }
    }

    fun publicarEvento(request: EventRequest) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            _uiState.value = AddEventUiState.Error("No autenticado")
            return
        }

        if (_genres.value.isEmpty()) {
            _uiState.value = AddEventUiState.Error("Géneros no cargados aún")
            return
        }

        _uiState.value = AddEventUiState.Loading

        viewModelScope.launch {
            try {
                val token = user.getIdToken(false).await().token
                    ?: run {
                        _uiState.value = AddEventUiState.Error("Token inválido")
                        return@launch
                    }

                val response = api.createEvent("Bearer $token", request)

                if (response.isSuccessful) {
                    _uiState.value = AddEventUiState.Success
                } else {
                    _uiState.value = AddEventUiState.Error("Error en el servidor (${response.code()})")
                }
            } catch (e: Exception) {
                _uiState.value = AddEventUiState.Error("Error de red: ${e.message}")
            }
        }
    }

    fun resetState() {
        _uiState.value = AddEventUiState.Idle
    }
}