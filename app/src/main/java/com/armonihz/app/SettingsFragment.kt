package com.armonihz.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.armonihz.app.auth.TokenManager
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.utils.ThemeManager
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class SettingsFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var btnDeleteAccount: MaterialButton
    private lateinit var loader: View
    private var isDeleting = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        auth = FirebaseAuth.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)

        val btnBack = view.findViewById<ImageView>(R.id.btnBack)
        val tvEditProfile = view.findViewById<TextView>(R.id.tvEditProfile)
        val tvEditPhoto = view.findViewById<TextView>(R.id.tvEditPhoto)
        val tvChangePassword = view.findViewById<TextView>(R.id.tvChangePassword)
        val switchNotifications = view.findViewById<SwitchMaterial>(R.id.switchNotifications)
        val tvThemeSelector = view.findViewById<TextView>(R.id.tvThemeSelector)

        btnDeleteAccount = view.findViewById(R.id.btnDeleteAccount)
        loader = view.findViewById(R.id.loader)

        val user = auth.currentUser
        val esUsuarioGoogle = user?.providerData?.any { it.providerId == "google.com" } == true

        if (esUsuarioGoogle) {
            tvChangePassword.isEnabled = false
            tvChangePassword.alpha = 0.5f
        }

        btnBack.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }

        tvEditProfile.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, EditProfileFragment())
                .addToBackStack(null)
                .commit()
        }

        tvEditPhoto.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ChangePhotoFragment())
                .addToBackStack(null)
                .commit()
        }

        tvChangePassword.setOnClickListener {
            if (esUsuarioGoogle) {
                Toast.makeText(requireContext(), "Contraseña gestionada por Google", Toast.LENGTH_LONG).show()
            } else {
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, ChangePasswordFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }

        switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            Toast.makeText(
                requireContext(),
                if (isChecked) "Notificaciones activadas" else "Notificaciones desactivadas",
                Toast.LENGTH_SHORT
            ).show()
        }

        tvThemeSelector.setOnClickListener {
            mostrarSelectorTema()
        }

        btnDeleteAccount.setOnClickListener {
            if (!isDeleting) mostrarConfirmacionEliminarCuenta()
        }

        return view
    }

    private fun mostrarSelectorTema() {
        val opciones = arrayOf("Seguir sistema", "Modo claro", "Modo oscuro")

        val seleccionActual = when (ThemeManager.getTheme(requireContext())) {
            ThemeManager.MODE_LIGHT -> 1
            ThemeManager.MODE_DARK -> 2
            else -> 0
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Tema de la aplicación")
            .setSingleChoiceItems(opciones, seleccionActual) { dialog, which ->

                when (which) {
                    0 -> ThemeManager.saveTheme(requireContext(), ThemeManager.MODE_SYSTEM)
                    1 -> ThemeManager.saveTheme(requireContext(), ThemeManager.MODE_LIGHT)
                    2 -> ThemeManager.saveTheme(requireContext(), ThemeManager.MODE_DARK)
                }

                requireActivity().recreate()
                dialog.dismiss()
            }
            .show()
    }

    // --- PASO 1: CONFIRMAR ---
    private fun mostrarConfirmacionEliminarCuenta() {
        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("¿Eliminar cuenta permanentemente?")
            .setMessage("Se borrarán todos tus datos. Esta acción no se puede deshacer.")
            .setPositiveButton("Sí, eliminar") { _, _ ->
                iniciarProcesoEliminacion()
            }
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            .setTextColor(resources.getColor(android.R.color.holo_red_dark, requireContext().theme))
    }

    // --- PASO 2: REAUTENTICAR PRIMERO ---
    private fun iniciarProcesoEliminacion() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(requireContext(), "Usuario no autenticado", Toast.LENGTH_LONG).show()
            return
        }

        isDeleting = true
        btnDeleteAccount.isEnabled = false
        showLoader()

        val esGoogle = user.providerData.any { it.providerId == "google.com" }

        if (esGoogle) {
            reautenticarConGoogle()
        } else {
            reautenticarConEmail(user)
        }
    }

    private fun reautenticarConEmail(user: FirebaseUser) {
        val email = user.email ?: return
        val passwordInput = EditText(requireContext())
        passwordInput.hint = "Ingresa tu contraseña"

        AlertDialog.Builder(requireContext())
            .setTitle("Reautenticación requerida")
            .setMessage("Para eliminar tu cuenta ingresa tu contraseña")
            .setView(passwordInput)
            .setPositiveButton("Confirmar") { _, _ ->

                val password = passwordInput.text.toString()

                if (password.isEmpty()) {
                    cancelarEliminacion("Contraseña requerida")
                    return@setPositiveButton
                }

                showLoader() // Mostramos el loader mientras verifica
                val credential = EmailAuthProvider.getCredential(email, password)

                user.reauthenticate(credential).addOnCompleteListener { authTask ->
                    if (authTask.isSuccessful) {
                        eliminarDatosBackendYFirebase(user) // Si es correcta, procedemos a borrar
                    } else {
                        cancelarEliminacion("Contraseña incorrecta")
                    }
                }
            }
            .setNegativeButton("Cancelar") { _, _ ->
                cancelarEliminacion(null)
            }
            .setCancelable(false)
            .show()
    }

    private fun reautenticarConGoogle() {
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            startActivityForResult(signInIntent, 999)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 999) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)
                val credential = GoogleAuthProvider.getCredential(account.idToken, null)
                val user = auth.currentUser ?: return

                user.reauthenticate(credential).addOnCompleteListener { authTask ->
                    if (authTask.isSuccessful) {
                        eliminarDatosBackendYFirebase(user) // Si es correcta, procedemos a borrar
                    } else {
                        cancelarEliminacion("Error al reautenticar con Google")
                    }
                }
            } catch (e: Exception) {
                cancelarEliminacion("Reautenticación cancelada")
            }
        }
    }

    // --- PASO 3 y 4: BORRAR BACKEND Y LUEGO FIREBASE ---
    private fun eliminarDatosBackendYFirebase(user: FirebaseUser) {

        lifecycleScope.launch {
            try {

                // 🛑 EL FIX: Pedimos a Firebase un token fresco (true = forzar refresco)
                val tokenResult = user.getIdToken(true).await()
                val freshToken = tokenResult.token

                // Guardamos el token nuevo para que Retrofit lo use
                if (freshToken != null) {
                    TokenManager.saveToken(requireContext(), freshToken)
                }

                // Ahora sí instanciamos la API y hacemos la llamada
                val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)
                val response = api.deleteAccount()

                if (response.isSuccessful || response.code() == 404) {

                    // Si Laravel lo borró con éxito, borramos en Firebase
                    user.delete().addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            cerrarSesionYSalir() // Todo exitoso, lo sacamos de la app
                        } else {
                            cancelarEliminacion("Error de Firebase: No se pudo eliminar la cuenta.")
                        }
                    }

                } else {
                    cancelarEliminacion("Error en el servidor al eliminar cuenta (HTTP ${response.code()}).")
                }

            } catch (e: Exception) {
                cancelarEliminacion("Error de red al intentar eliminar cuenta.")
            }
        }
    }

    // --- PASO 5: CERRAR TODO ---
    private fun cerrarSesionYSalir() {
        hideLoader()
        isDeleting = false

        // Limpiamos todos los tokens y sesiones locales
        googleSignInClient.signOut()
        TokenManager.clearToken(requireContext())
        auth.signOut()

        Toast.makeText(requireContext(), "Cuenta eliminada correctamente", Toast.LENGTH_LONG).show()

        val intent = Intent(requireContext(), LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun cancelarEliminacion(mensaje: String?) {
        hideLoader()
        isDeleting = false
        btnDeleteAccount.isEnabled = true
        if (mensaje != null) {
            Toast.makeText(requireContext(), mensaje, Toast.LENGTH_LONG).show()
        }
    }

    private fun showLoader() {
        loader.visibility = View.VISIBLE
    }

    private fun hideLoader() {
        loader.visibility = View.GONE
    }
}