package com.armonihz.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.armonihz.app.auth.TokenManager
import com.armonihz.app.databinding.ActivityLoginBinding
import com.armonihz.app.databinding.DialogForgotPasswordBinding
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.viewmodel.LoginUiState
import com.armonihz.app.viewmodel.LoginViewModel
import com.armonihz.app.viewmodel.LoginViewModelFactory
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var googleSignInClient: GoogleSignInClient

    private val api: ApiService by lazy {
        RetrofitClient.getInstance(this).create(ApiService::class.java)
    }

    private val viewModel: LoginViewModel by viewModels {
        LoginViewModelFactory(api)
    }

    // ── Google Sign-In moderno (reemplaza onActivityResult) ──────────────────
    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            viewModel.loginConGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Toast.makeText(this, getString(R.string.error_google), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupGoogleSignIn()
        setupListeners()
        observarViewModel()
    }

    override fun onStart() {
        super.onStart()

    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener {
            val correo = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            viewModel.loginConEmail(correo, password)
        }

        binding.btnGoogle.setOnClickListener {
            googleSignInLauncher.launch(googleSignInClient.signInIntent)
        }

        binding.textRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        binding.tvForgotPassword.setOnClickListener {
            val correo = binding.etEmail.text.toString().trim()
            mostrarDialogoRecuperarPassword(correo)
        }
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observarViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    when (state) {
                        is LoginUiState.Idle -> setUiEnabled(true)

                        is LoginUiState.Loading -> setUiEnabled(false)

                        is LoginUiState.Success -> {
                            // Guardar token actualizado
                            val user = FirebaseAuth.getInstance().currentUser
                            val token = user?.getIdToken(false)?.await()?.token
                            if (token != null) TokenManager.saveToken(this@LoginActivity, token)

                            // 🔥 NUEVO: Guardamos el candado en la memoria física del teléfono
                            val prefs = getSharedPreferences("ArmonihzPrefs", Context.MODE_PRIVATE)
                            prefs.edit().putBoolean("perfil_incompleto", state.irACompletarPerfil).apply()

                            Toast.makeText(
                                this@LoginActivity,
                                getString(R.string.welcome),
                                Toast.LENGTH_SHORT
                            ).show()

                            // Ya no necesitamos enviar el putExtra, la memoria se encarga
                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        }

                        is LoginUiState.EmailNotVerified -> {
                            setUiEnabled(true)
                            Toast.makeText(
                                this@LoginActivity,
                                getString(R.string.verify_email),
                                Toast.LENGTH_LONG
                            ).show()
                            viewModel.resetState()
                        }

                        is LoginUiState.Error -> {
                            setUiEnabled(true)
                            Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_SHORT).show()
                            viewModel.resetState()
                        }

                        is LoginUiState.PasswordResetSent -> {
                            Toast.makeText(
                                this@LoginActivity,
                                getString(R.string.password_reset_sent),
                                Toast.LENGTH_LONG
                            ).show()
                            viewModel.resetState()
                        }

                        is LoginUiState.PasswordResetError -> {
                            Toast.makeText(this@LoginActivity, state.message, Toast.LENGTH_SHORT).show()
                            viewModel.resetState()
                        }
                    }
                }
            }
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun setUiEnabled(enabled: Boolean) {
        binding.btnLogin.isEnabled = enabled
        binding.btnGoogle.isEnabled = enabled

        // Bloquear/Desbloquear los campos de texto
        binding.etEmail.isEnabled = enabled
        binding.etPassword.isEnabled = enabled

        // Cambiar el texto del botón principal
        binding.btnLogin.text = if (enabled) "ENTRAR" else "INICIANDO SESIÓN..."

        // Mostrar u ocultar la barra de carga
        binding.progressBar.isVisible = !enabled
    }

    // ── Diálogo recuperar contraseña ──────────────────────────────────────────

    private fun mostrarDialogoRecuperarPassword(correoInicial: String) {
        val dialogBinding = DialogForgotPasswordBinding.inflate(layoutInflater)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.etDialogEmail.setText(correoInicial)

        dialogBinding.btnDialogCancel.setOnClickListener { dialog.dismiss() }

        dialogBinding.btnDialogSend.setOnClickListener {
            val email = dialogBinding.etDialogEmail.text.toString().trim()
            viewModel.enviarRecuperacionPassword(email)
            dialog.dismiss()
        }

        dialog.show()
    }
}