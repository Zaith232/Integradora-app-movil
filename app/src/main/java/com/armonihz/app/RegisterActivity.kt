package com.armonihz.app

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import android.widget.Toast
import com.armonihz.app.databinding.ActivityRegisterBinding
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.viewmodel.PasswordStrength
import com.armonihz.app.viewmodel.RegisterUiState
import com.armonihz.app.viewmodel.RegisterViewModel
import com.armonihz.app.viewmodel.RegisterViewModelFactory
import kotlinx.coroutines.launch
import android.graphics.Color
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.view.View
import android.widget.TextView
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.InputStream

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding


    // 🔥 NUEVO: Inicializamos la API
    private val api: ApiService by lazy {
        RetrofitClient.getInstance(this).create(ApiService::class.java)
    }

    // 🔥 NUEVO: Le pasamos la API al ViewModel
    private val viewModel: RegisterViewModel by viewModels {
        RegisterViewModelFactory(api)
    }

    // ── Filtro solo letras ────────────────────────────────────────────────────

    private val soloLetrasFiltro = InputFilter { source, _, _, _, _, _ ->
        if (source.isEmpty()) return@InputFilter null
        val permitido = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzÁÉÍÓÚáéíóúÑñ "
        val resultado = source.filter { it in permitido }
        if (resultado.isEmpty()) "" else resultado
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupInsets()
        setupInputFilters()
        setupWatchers()
        setupBackHandler()
        setupListeners()
        observarViewModel()
        configurarTerminosYCondiciones()
    }

    // ── Setup ─────────────────────────────────────────────────────────────────

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun setupInputFilters() {
        val filtros = arrayOf(soloLetrasFiltro, InputFilter.LengthFilter(40))
        binding.etName.filters = filtros
        binding.etLName.filters = filtros

        configurarCapitalizacion(binding.etName)
        configurarCapitalizacion(binding.etLName)
    }

    private fun setupWatchers() {
        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.onPasswordChanged(
                    s?.toString() ?: "",
                    binding.etConfirmPassword.text.toString()
                )
            }
        })

        binding.etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.onConfirmPasswordChanged(
                    binding.etPassword.text.toString(),
                    s?.toString() ?: ""
                )
            }
        })
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { verificarSalida() }
        })
    }

    private fun setupListeners() {
        binding.btnRegister.setOnClickListener {
            // 🔥 NUEVO: Validar el CheckBox antes de registrar
            if (!binding.cbTerms.isChecked) {
                Toast.makeText(this, "Debes aceptar los Términos y Condiciones para continuar", Toast.LENGTH_LONG).show()
                return@setOnClickListener // Detiene la ejecución aquí si no está marcado
            }

            // Si está marcado, procedemos normal
            viewModel.registrar(
                nombre          = binding.etName.text.toString().trim(),
                apellido        = binding.etLName.text.toString().trim(),
                correo          = binding.etEmail.text.toString().trim(),
                password        = binding.etPassword.text.toString(),
                confirmPassword = binding.etConfirmPassword.text.toString()
            )
        }

        binding.textLogin.setOnClickListener { verificarSalida() }
    }

    // ── Observers ─────────────────────────────────────────────────────────────

    private fun observarViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                // Estado del registro
                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is RegisterUiState.Idle -> setUiEnabled(true)

                            is RegisterUiState.Loading -> setUiEnabled(false)

                            is RegisterUiState.Success -> {
                                Toast.makeText(
                                    this@RegisterActivity,
                                    getString(R.string.register_success),
                                    Toast.LENGTH_LONG
                                ).show()
                                startActivity(Intent(this@RegisterActivity, LoginActivity::class.java))
                                finish()
                            }

                            is RegisterUiState.Error -> {
                                setUiEnabled(true)
                                Toast.makeText(
                                    this@RegisterActivity,
                                    state.message,
                                    Toast.LENGTH_LONG
                                ).show()
                                viewModel.resetState()
                            }
                        }
                    }
                }

                // Fuerza de contraseña
                launch {
                    viewModel.passwordStrength.collect { strength ->
                        when (strength) {
                            is PasswordStrength.Empty -> {
                                binding.tvPasswordHint.text = getString(R.string.password_hint_default)
                                binding.tvPasswordHint.setTextColor(
                                    ContextCompat.getColor(this@RegisterActivity, R.color.hint_default)
                                )
                            }
                            is PasswordStrength.Weak -> {
                                binding.tvPasswordHint.text = strength.message
                                binding.tvPasswordHint.setTextColor(
                                    ContextCompat.getColor(this@RegisterActivity, R.color.error_red)
                                )
                            }
                            is PasswordStrength.Strong -> {
                                binding.tvPasswordHint.text = getString(R.string.password_strong)
                                binding.tvPasswordHint.setTextColor(
                                    ContextCompat.getColor(this@RegisterActivity, R.color.success_green)
                                )
                            }
                        }
                    }
                }

                // Coincidencia de contraseñas
                launch {
                    viewModel.passwordsMatch.collect { match ->
                        when (match) {
                            null -> binding.tvConfirmPasswordHint.text = ""
                            true -> {
                                binding.tvConfirmPasswordHint.text =
                                    getString(R.string.passwords_match)
                                binding.tvConfirmPasswordHint.setTextColor(
                                    ContextCompat.getColor(this@RegisterActivity, R.color.success_green)
                                )
                            }
                            false -> {
                                binding.tvConfirmPasswordHint.text =
                                    getString(R.string.passwords_no_match)
                                binding.tvConfirmPasswordHint.setTextColor(
                                    ContextCompat.getColor(this@RegisterActivity, R.color.error_red)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private fun setUiEnabled(enabled: Boolean) {
        binding.btnRegister.isEnabled = enabled
        binding.progressBar.isVisible = !enabled
        binding.btnRegister.text = if (enabled)
            getString(R.string.register_button)
        else
            getString(R.string.registering)
    }

    private fun verificarSalida() {
        val hayDatos = listOf(
            binding.etName,
            binding.etLName,
            binding.etEmail,
            binding.etPassword,
            binding.etConfirmPassword
        ).any { it.text.toString().trim().isNotEmpty() }

        if (hayDatos) {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.exit_confirm_title))
                .setMessage(getString(R.string.exit_confirm_message))
                .setPositiveButton(getString(R.string.exit_confirm_yes)) { _, _ -> finish() }
                .setNegativeButton(getString(R.string.exit_confirm_no), null)
                .show()
        } else {
            finish()
        }
    }

    // ── Capitalización automática ─────────────────────────────────────────────

    private fun configurarCapitalizacion(editText: android.widget.EditText) {
        editText.addTextChangedListener(object : TextWatcher {
            private var editando = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (editando) return
                editando = true

                val texto = s.toString()
                val limpio = texto.replace("\\s+".toRegex(), " ").trimStart()
                val capitalizado = limpio.split(" ").joinToString(" ") {
                    it.lowercase().replaceFirstChar { c -> c.uppercase() }
                }

                if (capitalizado != texto) {
                    editText.setText(capitalizado)
                    editText.setSelection(capitalizado.length)
                }

                editando = false
            }
        })
    }

    private fun configurarTerminosYCondiciones() {
        val textoCompleto = "Acepto los Términos y Condiciones"
        val spannableString = SpannableString(textoCompleto)

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                mostrarPantallaDeTerminos()
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = ContextCompat.getColor(this@RegisterActivity, R.color.md_primary)
                ds.isUnderlineText = false
                ds.isFakeBoldText = true
            }
        }

        val startIndex = textoCompleto.indexOf("Términos")
        val endIndex = textoCompleto.length

        spannableString.setSpan(clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        // Usamos binding en lugar de findViewById
        binding.tvTermsText.text = spannableString
        binding.tvTermsText.movementMethod = LinkMovementMethod.getInstance()
        binding.tvTermsText.highlightColor = Color.TRANSPARENT
    }

    private fun leerTextoDesdeRaw(): String {
        return try {
            val inputStream: InputStream = resources.openRawResource(R.raw.terminos_condiciones)
            inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Error al cargar los términos y condiciones. Por favor, intenta de nuevo."
        }
    }

    private fun mostrarPantallaDeTerminos() {
        val textoLegal = leerTextoDesdeRaw()

        MaterialAlertDialogBuilder(this)
            .setTitle("Términos y Condiciones")
            .setMessage(textoLegal)
            .setPositiveButton("Aceptar") { dialog, _ ->
                // Usamos binding en lugar de findViewById
                binding.cbTerms.isChecked = true
                dialog.dismiss()
            }
            .setNegativeButton("Cerrar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}