    package com.armonihz.app

    import android.content.Intent
    import android.os.Bundle
    import android.util.Log
    import android.widget.*
    import androidx.appcompat.app.AppCompatActivity
    import androidx.lifecycle.lifecycleScope
    import com.armonihz.app.auth.TokenManager
    import com.armonihz.app.network.ApiService
    import com.armonihz.app.network.RetrofitClient
    import com.armonihz.app.network.model.SyncGooglePhotoRequest
    import com.google.android.gms.auth.api.signin.*
    import com.google.android.gms.common.api.ApiException
    import com.google.firebase.auth.FirebaseAuth
    import com.google.firebase.auth.GoogleAuthProvider
    import kotlinx.coroutines.async
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.tasks.await

    class LoginActivity : AppCompatActivity() {

        private lateinit var auth: FirebaseAuth
        private lateinit var googleSignInClient: GoogleSignInClient
        private var isNavigating = false // ✅ Evita doble navegación

        private val RC_SIGN_IN = 100

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_login)

            auth = FirebaseAuth.getInstance()

            val etCorreo = findViewById<EditText>(R.id.etEmail)
            val etPassword = findViewById<EditText>(R.id.etPassword)
            val btnLogin = findViewById<Button>(R.id.btnLogin)
            val btnGoogle = findViewById<Button>(R.id.btnGoogle)
            val textRegister = findViewById<TextView>(R.id.textRegister)
            val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)

            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()

            googleSignInClient = GoogleSignIn.getClient(this, gso)

            btnLogin.setOnClickListener {
                val correo = etCorreo.text.toString().trim()
                val password = etPassword.text.toString().trim()

                if (correo.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, "Completa los campos", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                btnLogin.isEnabled = false
                btnLogin.text = "Entrando..."

                auth.signInWithEmailAndPassword(correo, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            val user = auth.currentUser

                            if (user != null && user.isEmailVerified) {
                                user.getIdToken(true).addOnSuccessListener { result ->
                                    val firebaseToken = result.token
                                    if (firebaseToken != null) {
                                        TokenManager.saveToken(this, firebaseToken)
                                        lifecycleScope.launch {
                                            // ✅ syncClient en paralelo, no bloquea la navegación
                                            launch { syncClient(esGoogle = false) }
                                            entrarAlMain()
                                        }
                                    }
                                }
                            } else {
                                auth.signOut()
                                btnLogin.isEnabled = true
                                btnLogin.text = "Iniciar sesión"
                                Toast.makeText(this, "Verifica tu correo antes de entrar", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            btnLogin.isEnabled = true
                            btnLogin.text = "Iniciar sesión"
                            Toast.makeText(this, "Credenciales incorrectas", Toast.LENGTH_SHORT).show()
                        }
                    }
            }

            btnGoogle.setOnClickListener {
                startActivityForResult(googleSignInClient.signInIntent, RC_SIGN_IN)
            }

            textRegister.setOnClickListener {
                startActivity(Intent(this, RegisterActivity::class.java))
            }

            tvForgotPassword.setOnClickListener {
                val correoEscrito = etCorreo.text.toString().trim()
                mostrarDialogoRecuperarPassword(correoEscrito)
            }
        }

        override fun onStart() {
            super.onStart()

            // ✅ Si ya está navegando, no hacer nada
            if (isNavigating) return

            val user = FirebaseAuth.getInstance().currentUser ?: return

            lifecycleScope.launch {
                try {
                    val tokenResult = user.getIdToken(false).await() // ✅ false = usa caché, más rápido
                    val firebaseToken = tokenResult.token

                    if (firebaseToken != null) {
                        TokenManager.saveToken(this@LoginActivity, firebaseToken)
                        // ✅ syncClient en paralelo, no bloquea
                        launch { syncClient(esGoogle = false) }
                        entrarAlMain()
                    }
                } catch (e: Exception) {
                    Log.e("LOGIN", "Error obteniendo token", e)
                }
            }
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)

            if (requestCode == RC_SIGN_IN) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    firebaseAuthWithGoogle(account.idToken!!)
                } catch (e: ApiException) {
                    Toast.makeText(this, "Error con Google", Toast.LENGTH_SHORT).show()
                }
            }
        }

        private fun firebaseAuthWithGoogle(idToken: String) {
            val credential = GoogleAuthProvider.getCredential(idToken, null)

            auth.signInWithCredential(credential)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        auth.currentUser?.getIdToken(true)?.addOnSuccessListener { result ->
                            val firebaseToken = result.token
                            if (firebaseToken != null) {
                                TokenManager.saveToken(this, firebaseToken)
                                lifecycleScope.launch {
                                    // Ejecutamos la sincronización básica primero
                                    val syncJob = launch { syncClient(esGoogle = true) }
                                    launch { syncGooglePhotoIfNeeded() }

                                    // Esperamos a que termine de sincronizar para asegurarnos de que el registro exista en DB
                                    syncJob.join()

                                    try {
                                        // Consultamos al backend si el perfil está completo
                                        val api = RetrofitClient.getInstance(this@LoginActivity).create(ApiService::class.java)
                                        val response = api.getClientProfile()

                                        if (response.isSuccessful && response.body() != null) {
                                            // 1. Quitamos el .data y tomamos el body directamente
                                            val perfil = response.body()!!

                                            // 2. Verificamos si falta nombre o apellido
                                            val faltaInformacion = perfil.nombre.isNullOrEmpty() || perfil.apellido.isNullOrEmpty()

                                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
                                            // Solo mandamos a completar perfil si falta información
                                            intent.putExtra("ir_a_completar_perfil", faltaInformacion)
                                            startActivity(intent)
                                            finish()
                                        } else {
                                            // Si falla la consulta por alguna razón, por seguridad lo mandamos al main normal
                                            entrarAlMain()
                                        }
                                    } catch (e: Exception) {
                                        Log.e("LOGIN", "Error verificando perfil completo", e)
                                        // Si no hay internet o falla la API, intentamos entrar al main normal
                                        entrarAlMain()
                                    }
                                }
                            }
                        }
                    } else {
                        Toast.makeText(this, "Error Firebase", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        private suspend fun syncClient(esGoogle: Boolean) {
            val user = FirebaseAuth.getInstance().currentUser ?: return

            try {
                val api = RetrofitClient.getInstance(this).create(ApiService::class.java)
                val email = user.email ?: ""

                if (!esGoogle) {
                    // ✅ Usuario email: leer de Realtime DB para separar nombre/apellido
                    val snapshot = com.google.firebase.database.FirebaseDatabase
                        .getInstance()
                        .getReference("usuarios")
                        .child(user.uid)
                        .get()
                        .await()

                    val nombre   = snapshot.child("nombre").getValue(String::class.java)
                    val apellido = snapshot.child("apellido").getValue(String::class.java)

                    if (!nombre.isNullOrEmpty() && !apellido.isNullOrEmpty()) {
                        api.syncClient(mapOf(
                            "name"     to "$nombre $apellido",
                            "email"    to email,
                            "nombre"   to nombre,
                            "apellido" to apellido
                        ))
                    } else {
                        api.syncClient(mapOf("name" to (user.displayName ?: ""), "email" to email))
                    }
                } else {
                    // ✅ Usuario Google: no consultar Realtime DB, backend hace el explode
                    api.syncClient(mapOf(
                        "name"  to (user.displayName ?: ""),
                        "email" to email
                    ))
                }

                Log.d("SYNC_CLIENT", "Cliente sincronizado")
            } catch (e: Exception) {
                Log.e("SYNC_CLIENT", "Error sincronizando cliente", e)
            }
        }



        private suspend fun syncGooglePhotoIfNeeded() {
            val user = FirebaseAuth.getInstance().currentUser ?: return
            val googlePhotoUrl = user.photoUrl?.toString() ?: return

            try {
                val api = RetrofitClient.getInstance(this).create(ApiService::class.java)
                api.syncGooglePhoto(SyncGooglePhotoRequest(googlePhotoUrl))
            } catch (e: Exception) {
                Log.e("SYNC_PHOTO", "No se pudo sincronizar foto", e)
            }
        }

        private fun entrarAlMain() {
            if (isNavigating) return
            isNavigating = true

            Toast.makeText(this, "Bienvenido", Toast.LENGTH_SHORT).show()
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }

        private fun mostrarDialogoRecuperarPassword(correoInicial: String) {
            val dialogView = layoutInflater.inflate(R.layout.dialog_forgot_password, null)
            val builder = androidx.appcompat.app.AlertDialog.Builder(this)
            builder.setView(dialogView)

            val dialog = builder.create()
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

            val etEmail  = dialogView.findViewById<EditText>(R.id.etDialogEmail)
            val btnCancel = dialogView.findViewById<Button>(R.id.btnDialogCancel)
            val btnSend   = dialogView.findViewById<Button>(R.id.btnDialogSend)

            etEmail.setText(correoInicial)

            btnCancel.setOnClickListener { dialog.dismiss() }

            btnSend.setOnClickListener {
                val email = etEmail.text.toString().trim()

                if (email.isEmpty()) {
                    etEmail.error = "Ingresa un correo"
                    return@setOnClickListener
                }

                if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                    etEmail.error = "Correo inválido"
                    return@setOnClickListener
                }

                auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                    Toast.makeText(
                        this,
                        if (task.isSuccessful) "Si el correo está registrado, recibirás un enlace."
                        else "Error al enviar correo",
                        Toast.LENGTH_LONG
                    ).show()
                }

                dialog.dismiss()
            }

            dialog.show()
        }
    }