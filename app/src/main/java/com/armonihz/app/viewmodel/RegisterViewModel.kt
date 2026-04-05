package com.armonihz.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.armonihz.app.network.ApiService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

sealed class RegisterUiState {
    object Idle : RegisterUiState()
    object Loading : RegisterUiState()
    object Success : RegisterUiState()
    data class Error(val message: String) : RegisterUiState()
}

sealed class PasswordStrength {
    object Empty : PasswordStrength()
    data class Weak(val message: String) : PasswordStrength()
    object Strong : PasswordStrength()
}

// 🔥 NUEVO: Agregamos ApiService al constructor
class RegisterViewModel(private val api: ApiService) : ViewModel() {

    private val _uiState = MutableStateFlow<RegisterUiState>(RegisterUiState.Idle)
    val uiState: StateFlow<RegisterUiState> = _uiState

    private val _passwordStrength = MutableStateFlow<PasswordStrength>(PasswordStrength.Empty)
    val passwordStrength: StateFlow<PasswordStrength> = _passwordStrength

    private val _passwordsMatch = MutableStateFlow<Boolean?>(null)
    val passwordsMatch: StateFlow<Boolean?> = _passwordsMatch

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance()

    // ── Validación en tiempo real ─────────────────────────────────────────────

    fun onPasswordChanged(password: String, confirmPassword: String) {
        _passwordStrength.value = evaluarPassword(password)
        if (confirmPassword.isNotEmpty()) {
            _passwordsMatch.value = password == confirmPassword
        }
    }

    fun onConfirmPasswordChanged(password: String, confirmPassword: String) {
        _passwordsMatch.value = when {
            confirmPassword.isEmpty() -> null
            else -> password == confirmPassword
        }
    }

    private fun evaluarPassword(password: String): PasswordStrength {
        return when {
            password.isEmpty() -> PasswordStrength.Empty
            password.length < 8 -> PasswordStrength.Weak("Faltan caracteres (mínimo 8)")
            !password.any { it.isUpperCase() } -> PasswordStrength.Weak("Falta una letra mayúscula")
            !password.any { it.isDigit() } -> PasswordStrength.Weak("Falta al menos un número")
            else -> PasswordStrength.Strong
        }
    }

    // ── Registro ──────────────────────────────────────────────────────────────

    fun registrar(
        nombre: String,
        apellido: String,
        correo: String,
        password: String,
        confirmPassword: String
    ) {
        if (nombre.isBlank() || apellido.isBlank() || correo.isBlank() ||
            password.isBlank() || confirmPassword.isBlank()
        ) {
            _uiState.value = RegisterUiState.Error("Completa todos los campos")
            return
        }

        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
            _uiState.value = RegisterUiState.Error("Correo inválido")
            return
        }

        if (evaluarPassword(password) is PasswordStrength.Weak ||
            evaluarPassword(password) is PasswordStrength.Empty
        ) {
            _uiState.value = RegisterUiState.Error("La contraseña no cumple los requisitos")
            return
        }

        if (password != confirmPassword) {
            _uiState.value = RegisterUiState.Error("Las contraseñas no coinciden")
            return
        }

        _uiState.value = RegisterUiState.Loading

        viewModelScope.launch {
            try {
                // Paso 1: Crear usuario en Firebase Auth
                val result = auth.createUserWithEmailAndPassword(correo, password).await()
                val user = result.user ?: run {
                    _uiState.value = RegisterUiState.Error("Error inesperado al crear cuenta")
                    return@launch
                }

                // Paso 2: Actualizar perfil con nombre completo
                val profileUpdates = UserProfileChangeRequest.Builder()
                    .setDisplayName("$nombre $apellido")
                    .build()
                user.updateProfile(profileUpdates).await()

                // Paso 3: Guardar en Realtime Database
                val userMap = mapOf(
                    "nombre"   to nombre,
                    "apellido" to apellido,
                    "correo"   to correo,
                    "rol"      to "usuario"
                )
                database.reference
                    .child("usuarios")
                    .child(user.uid)
                    .setValue(userMap)
                    .await()

                // 🔥 PASO 4 (MODIFICADO): Pedirle a Laravel que mande el correo bonito
                val token = user.getIdToken(false).await().token
                if (token != null) {
                    try {
                        api.sendCustomVerification("Bearer $token")
                    } catch (e: Exception) {
                        // Si falla el correo, de todas formas lo registramos, pero podrías manejarlo
                    }
                }

                // Paso 5: Cerrar sesión hasta que verifique
                auth.signOut()

                _uiState.value = RegisterUiState.Success

            } catch (e: FirebaseAuthUserCollisionException) {
                _uiState.value = RegisterUiState.Error(
                    "Este correo ya está registrado. Intenta iniciar sesión."
                )
            } catch (e: Exception) {
                _uiState.value = RegisterUiState.Error(
                    e.message ?: "Error desconocido al registrar"
                )
            }
        }
    }

    fun resetState() {
        _uiState.value = RegisterUiState.Idle
    }
}

// 🔥 NUEVO: Pasamos el ApiService por el Factory
class RegisterViewModelFactory(private val api: ApiService) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RegisterViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RegisterViewModel(api) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}