package com.armonihz.app

import android.animation.Animator
import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import com.armonihz.app.utils.ThemeManager
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val lottie = findViewById<LottieAnimationView>(R.id.lottieSplash)
        val userText = findViewById<TextView>(R.id.tvSubtitle)

        val user = FirebaseAuth.getInstance().currentUser

        lottie.speed = 2f   // 2f = el doble de rápido

        // 1. Configuramos el saludo
        if (user != null) {
            val nombre = user.displayName
            if (!nombre.isNullOrEmpty()) {
                userText.text = "Bienvenido, $nombre"
            } else {
                userText.text = "Bienvenido, usuario(a)"
            }
        } else {
            userText.text = "Bienvenido, usuario(a)"
        }

        // 2. Esperamos a que termine la animación para decidir a dónde ir
        lottie.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationEnd(animation: Animator) {
                // 🔥 AQUÍ ESTÁ LA MAGIA: Decidimos el destino
                if (user != null && user.isEmailVerified) {
                    // Si hay sesión y está verificado -> Directo al Home (MainActivity)
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                } else {
                    // Si no hay sesión o no ha verificado su correo -> Al Login
                    startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                }
                finish() // Cerramos el splash
            }

            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })
    }
}