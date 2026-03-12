package com.armonihz.app

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.armonihz.app.auth.TokenManager
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import android.util.Log
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.network.model.SyncGooglePhotoRequest

class LoginActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

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

            auth.signInWithEmailAndPassword(correo, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = auth.currentUser

                        if (user != null && user.isEmailVerified) {
                            user.getIdToken(true).addOnSuccessListener { result ->
                                val firebaseToken = result.token
                                if (firebaseToken != null) {
                                    TokenManager.saveToken(this@LoginActivity, firebaseToken)
                                    syncClient()
                                    entrarAlMain()
                                }
                            }
                        } else {
                            auth.signOut()
                            Toast.makeText(this, "Por favor, verifica tu correo en tu bandeja de entrada antes de entrar.", Toast.LENGTH_LONG).show()
                        }
                    } else {
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

        val token = TokenManager.getToken(this)
        if (token != null) {
            entrarAlMain()
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
                            syncClient()
                            syncGooglePhotoIfNeeded()
                            entrarAlMain()
                        }
                    }
                } else {
                    Toast.makeText(this, "Error Firebase", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun syncGooglePhotoIfNeeded() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val googlePhotoUrl = user.photoUrl?.toString() ?: return

        lifecycleScope.launch {
            try {
                // ⬅️ Ya no enviamos "Bearer $firebaseToken" manual
                val api = RetrofitClient.getInstance(this@LoginActivity).create(ApiService::class.java)
                api.syncGooglePhoto(SyncGooglePhotoRequest(googlePhotoUrl))
            } catch (e: Exception) {
                Log.e("SYNC_PHOTO", "No se pudo sincronizar foto")
            }
        }
    }

    private fun syncClient() {
        val user = FirebaseAuth.getInstance().currentUser ?: return

        lifecycleScope.launch {
            try {
                // ⬅️ Ya no enviamos el token manualmente
                val api = RetrofitClient.getInstance(this@LoginActivity).create(ApiService::class.java)

                val name = user.displayName ?: ""
                val email = user.email ?: ""

                api.syncClient(mapOf("name" to name, "email" to email))
            } catch (e: Exception) {
                Log.e("SYNC_CLIENT", "No se pudo sincronizar cliente")
            }
        }
    }

    private fun entrarAlMain() {
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

        val etEmail = dialogView.findViewById<EditText>(R.id.etDialogEmail)
        val btnCancel = dialogView.findViewById<Button>(R.id.btnDialogCancel)
        val btnSend = dialogView.findViewById<Button>(R.id.btnDialogSend)

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

            auth.sendPasswordResetEmail(email)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Si el correo está registrado, recibirás un enlace.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Error al enviar correo", Toast.LENGTH_LONG).show()
                    }
                }
            dialog.dismiss()
        }
        dialog.show()
    }
}