package com.armonihz.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.model.SyncGooglePhotoRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class LoginUiState {
    object Idle : LoginUiState()
    object Loading : LoginUiState()
    data class Success(val irACompletarPerfil: Boolean) : LoginUiState()
    data class Error(val message: String) : LoginUiState()
    object EmailNotVerified : LoginUiState()
    object PasswordResetSent : LoginUiState()
    data class PasswordResetError(val message: String) : LoginUiState()
}

class LoginViewModel(
    private val api: ApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow<LoginUiState>(LoginUiState.Idle)
    val uiState: StateFlow<LoginUiState> = _uiState

    private val auth = FirebaseAuth.getInstance()

    // ── Sesión activa ─────────────────────────────────────────────────────────

    fun checkSesionActiva() {
        val user = auth.currentUser ?: return
        _uiState.value = LoginUiState.Loading

        viewModelScope.launch {
            try {
                val token = user.getIdToken(false).await().token
                if (token != null) {
                    launch { syncClient(esGoogle = false) }
                    _uiState.value = LoginUiState.Success(irACompletarPerfil = false)
                } else {
                    _uiState.value = LoginUiState.Idle
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Idle
            }
        }
    }

    // ── Login email/password ──────────────────────────────────────────────────

    fun loginConEmail(correo: String, password: String) {
        if (correo.isBlank() || password.isBlank()) {
            _uiState.value = LoginUiState.Error("Completa todos los campos")
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            _uiState.value = LoginUiState.Error("Correo inválido")
            return
        }

        _uiState.value = LoginUiState.Loading

        viewModelScope.launch {
            try {
                val result = auth.signInWithEmailAndPassword(correo, password).await()
                val user = result.user

                if (user == null) {
                    _uiState.value = LoginUiState.Error("Error inesperado")
                    return@launch
                }

                if (!user.isEmailVerified) {
                    auth.signOut()
                    _uiState.value = LoginUiState.EmailNotVerified
                    return@launch
                }

                val token = user.getIdToken(true).await().token
                if (token != null) {
                    launch { syncClient(esGoogle = false) }
                    _uiState.value = LoginUiState.Success(irACompletarPerfil = false)
                } else {
                    _uiState.value = LoginUiState.Error("Token inválido")
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error("Credenciales incorrectas")
            }
        }
    }

    // ── Login Google ──────────────────────────────────────────────────────────

    fun loginConGoogle(idToken: String) {
        _uiState.value = LoginUiState.Loading

        viewModelScope.launch {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val result = auth.signInWithCredential(credential).await()
                val user = auth.currentUser ?: run {
                    _uiState.value = LoginUiState.Error("Error de autenticación")
                    return@launch
                }

                val isNewUser = result.additionalUserInfo?.isNewUser ?: false
                user.getIdToken(true).await()

                val syncJob = launch { syncClient(esGoogle = true) }
                launch { syncGooglePhotoIfNeeded() }
                syncJob.join()

                if (isNewUser) {
                    _uiState.value = LoginUiState.Success(irACompletarPerfil = true)
                    return@launch
                }

                // Usuario existente: verificar si le falta info
                val irACompletar = try {
                    val response = api.getClientProfile()
                    if (response.isSuccessful && response.body() != null) {
                        val perfil = response.body()!!
                        perfil.nombre.isNullOrEmpty() || perfil.apellido.isNullOrEmpty()
                    } else false
                } catch (e: Exception) {
                    false
                }

                _uiState.value = LoginUiState.Success(irACompletarPerfil = irACompletar)

            } catch (e: Exception) {
                _uiState.value = LoginUiState.Error("Error al iniciar con Google")
            }
        }
    }

    // ── Recuperar contraseña ──────────────────────────────────────────────────

    fun enviarRecuperacionPassword(email: String) {
        if (email.isBlank()) {
            _uiState.value = LoginUiState.Error("Ingresa un correo")
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.value = LoginUiState.Error("Correo inválido")
            return
        }

        viewModelScope.launch {
            try {
                // 🔥 AQUÍ REEMPLAZAMOS FIREBASE POR LARAVEL
                val response = api.sendPasswordReset(mapOf("email" to email))

                if (response.isSuccessful) {
                    _uiState.value = LoginUiState.PasswordResetSent
                } else {
                    _uiState.value = LoginUiState.PasswordResetError("No se pudo enviar el correo")
                }
            } catch (e: Exception) {
                _uiState.value = LoginUiState.PasswordResetError("Error de conexión")
            }
        }
    }

    fun resetState() {
        _uiState.value = LoginUiState.Idle
    }

    // ── Helpers privados ──────────────────────────────────────────────────────

    private suspend fun syncClient(esGoogle: Boolean) {
        val user = auth.currentUser ?: return

        try {
            val email = user.email ?: ""
            val photoUrl = user.photoUrl?.toString() ?: ""

            if (!esGoogle) {
                val snapshot = FirebaseDatabase.getInstance()
                    .getReference("usuarios")
                    .child(user.uid)
                    .get()
                    .await()

                val nombre = snapshot.child("nombre").getValue(String::class.java)
                val apellido = snapshot.child("apellido").getValue(String::class.java)

                if (!nombre.isNullOrEmpty() && !apellido.isNullOrEmpty()) {
                    api.syncClient(mapOf(
                        "name"     to "$nombre $apellido",
                        "email"    to email,
                        "nombre"   to nombre,
                        "apellido" to apellido,
                        "photoUrl" to photoUrl
                    ))
                } else {
                    api.syncClient(mapOf(
                        "name"     to (user.displayName ?: ""),
                        "email"    to email,
                        "photoUrl" to photoUrl
                    ))
                }
            } else {
                api.syncClient(mapOf(
                    "name"     to (user.displayName ?: ""),
                    "email"    to email,
                    "photoUrl" to photoUrl
                ))
            }
        } catch (e: Exception) {
            // Falla silenciosa — no bloquear el login por esto
        }
    }

    private suspend fun syncGooglePhotoIfNeeded() {
        val user = auth.currentUser ?: return
        val photoUrl = user.photoUrl?.toString() ?: return

        try {
            api.syncGooglePhoto(SyncGooglePhotoRequest(photoUrl))
        } catch (e: Exception) {
            // Falla silenciosa
        }
    }
}

class LoginViewModelFactory(
    private val api: ApiService
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(api) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}