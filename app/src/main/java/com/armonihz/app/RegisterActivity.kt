package com.armonihz.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.*
import android.util.Patterns
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.FirebaseDatabase

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    // Variables a nivel de clase para poder leerlas al intentar salir
    private lateinit var etNombre: EditText
    private lateinit var etLastName: EditText
    private lateinit var etCorreo: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText

    // 🔤 Filtro solo letras (con acentos y espacios)
    private val soloLetrasFiltro = InputFilter { source, _, _, _, _, _ ->
        if (source.isEmpty()) return@InputFilter null

        val permitido = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzÁÉÍÓÚáéíóúÑñ "
        val resultado = StringBuilder()

        for (char in source) {
            if (permitido.contains(char)) {
                resultado.append(char)
            }
        }

        if (resultado.isEmpty()) "" else resultado.toString()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val textLogin = findViewById<TextView>(R.id.textLogin)
        val tvPasswordHint = findViewById<TextView>(R.id.tvPasswordHint)
        val tvConfirmPasswordHint = findViewById<TextView>(R.id.tvConfirmPasswordHint)

        // Inicializamos las variables
        etNombre = findViewById(R.id.etName)
        etLastName = findViewById(R.id.etLName)
        etCorreo = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)

        // 🔒 Aplicar filtros de solo letras
        etNombre.filters = arrayOf(soloLetrasFiltro, InputFilter.LengthFilter(40))
        etLastName.filters = arrayOf(soloLetrasFiltro, InputFilter.LengthFilter(40))

        configurarNombreWatcher(etNombre)
        configurarNombreWatcher(etLastName)

        // 🛑 CONFIGURACIÓN DEL BOTÓN/GESTO DE "ATRÁS" DEL SISTEMA
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                verificarSalida()
            }
        })

        // 🛑 CONFIGURACIÓN DEL TEXTO "YA TENGO CUENTA"
        textLogin.setOnClickListener {
            verificarSalida()
        }

        btnRegister.setOnClickListener {

            val nombre = etNombre.text.toString().trim()
            val apellido = etLastName.text.toString().trim()
            val correo = etCorreo.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (nombre.isEmpty() || apellido.isEmpty() || correo.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Por favor, completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!Patterns.EMAIL_ADDRESS.matcher(correo).matches()) {
                etCorreo.error = "Ingresa un correo válido"
                return@setOnClickListener
            }

            if (password.length < 8) {
                etPassword.error = "La contraseña debe tener al menos 8 caracteres"
                return@setOnClickListener
            }

            if (!password.any { it.isDigit() } || !password.any { it.isUpperCase() }) {
                etPassword.error = "Usa al menos un número y una mayúscula"
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                etConfirmPassword.error = "Las contraseñas no coinciden"
                return@setOnClickListener
            }

            btnRegister.isEnabled = false
            btnRegister.text = "Registrando..."

            auth.createUserWithEmailAndPassword(correo, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {

                        val user = auth.currentUser
                        val userId = user?.uid

                        // 1. Enviamos el correo de verificación
                        user?.sendEmailVerification()
                            ?.addOnCompleteListener { emailTask ->
                                if (emailTask.isSuccessful) {
                                    Toast.makeText(this, "Cuenta creada. Por favor, revisa tu correo para verificarlo.", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(this, "Error al enviar correo de verificación: ${emailTask.exception?.message}", Toast.LENGTH_SHORT).show()
                                }
                            }

                        // 2. Actualizamos el perfil con el nombre
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName("$nombre $apellido")
                            .build()

                        user?.updateProfile(profileUpdates)

                        // 3. Guardamos los datos en la base de datos
                        val userMap = HashMap<String, Any>()
                        userMap["nombre"] = nombre
                        userMap["apellido"] = apellido
                        userMap["correo"] = correo
                        userMap["rol"] = "usuario"

                        userId?.let {
                            database.reference
                                .child("usuarios")
                                .child(it)
                                .setValue(userMap)
                                .addOnSuccessListener {
                                    // 4. Cerramos sesión para que tengan que loguearse después de verificar
                                    auth.signOut()

                                    startActivity(Intent(this, LoginActivity::class.java))
                                    finish()
                                }
                                .addOnFailureListener { e ->
                                    btnRegister.isEnabled = true
                                    btnRegister.text = "Registrar"
                                    Toast.makeText(this, "Error al guardar perfil: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }

                    } else {
                        btnRegister.isEnabled = true
                        btnRegister.text = "Registrar"

                        // Verificamos si el error es porque el correo ya existe
                        if (task.exception is com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                            Toast.makeText(this, "Este correo ya está registrado. Intenta iniciar sesión (quizás usaste Google).", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
        }

        // 🔐 Validación visual de contraseña
        etPassword.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val password = s.toString()

                val hasMinLength = password.length >= 8
                val hasUppercase = password.any { it.isUpperCase() }
                val hasDigit = password.any { it.isDigit() }

                when {
                    password.isEmpty() -> {
                        tvPasswordHint.text = "Mínimo 8 caracteres, 1 mayúscula y 1 número"
                        tvPasswordHint.setTextColor(Color.GRAY)
                    }
                    !hasMinLength -> {
                        tvPasswordHint.text = "Faltan caracteres (mínimo 8)"
                        tvPasswordHint.setTextColor(Color.RED)
                    }
                    !hasUppercase -> {
                        tvPasswordHint.text = "Falta agregar una letra mayúscula"
                        tvPasswordHint.setTextColor(Color.RED)
                    }
                    !hasDigit -> {
                        tvPasswordHint.text = "Falta agregar al menos un número"
                        tvPasswordHint.setTextColor(Color.RED)
                    }
                    else -> {
                        tvPasswordHint.text = "¡Contraseña segura!"
                        tvPasswordHint.setTextColor(Color.parseColor("#00897B"))
                    }
                }

                val confirmPass = etConfirmPassword.text.toString()
                if (confirmPass.isNotEmpty()) {
                    if (password == confirmPass) {
                        tvConfirmPasswordHint.text = "Las contraseñas coinciden"
                        tvConfirmPasswordHint.setTextColor(Color.parseColor("#00897B"))
                    } else {
                        tvConfirmPasswordHint.text = "Las contraseñas no coinciden"
                        tvConfirmPasswordHint.setTextColor(Color.RED)
                    }
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val confirmPassword = s.toString()
                val originalPassword = etPassword.text.toString()

                when {
                    confirmPassword.isEmpty() -> tvConfirmPasswordHint.text = ""
                    confirmPassword == originalPassword -> {
                        tvConfirmPasswordHint.text = "Las contraseñas coinciden"
                        tvConfirmPasswordHint.setTextColor(Color.parseColor("#00897B"))
                    }
                    else -> {
                        tvConfirmPasswordHint.text = "Las contraseñas no coinciden"
                        tvConfirmPasswordHint.setTextColor(Color.RED)
                    }
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    // 🛑 MÉTODO PARA VERIFICAR SI HAY DATOS ANTES DE SALIR
    private fun verificarSalida() {
        val hayDatos = etNombre.text.toString().trim().isNotEmpty() ||
                etLastName.text.toString().trim().isNotEmpty() ||
                etCorreo.text.toString().trim().isNotEmpty() ||
                etPassword.text.toString().trim().isNotEmpty() ||
                etConfirmPassword.text.toString().trim().isNotEmpty()

        if (hayDatos) {
            AlertDialog.Builder(this)
                .setTitle("¿Estás seguro de salir?")
                .setMessage("Si sales ahora, perderás todos los datos que has ingresado.")
                .setPositiveButton("Salir") { _, _ ->
                    finish() // Cierra la pantalla si el usuario confirma
                }
                .setNegativeButton("Cancelar", null) // Cierra el diálogo y se queda en la pantalla
                .show()
        } else {
            // Si todo está vacío, sale directamente sin preguntar
            finish()
        }
    }

    // 🔠 Capitalización automática
    private fun configurarNombreWatcher(editText: EditText) {
        editText.addTextChangedListener(object : TextWatcher {
            private var editando = false

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

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
}