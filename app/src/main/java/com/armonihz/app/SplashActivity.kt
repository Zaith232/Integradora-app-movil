package com.armonihz.app

import android.animation.Animator
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.airbnb.lottie.LottieAnimationView
import com.google.firebase.auth.FirebaseAuth

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val lottie = findViewById<LottieAnimationView>(R.id.lottieSplash)
        val userText = findViewById<TextView>(R.id.tvSubtitle)

        val user = FirebaseAuth.getInstance().currentUser

        lottie.speed = 2f   // 2f = el doble de rápido


        lottie.addAnimatorListener(object : Animator.AnimatorListener {
            override fun onAnimationEnd(animation: Animator) {
                startActivity(Intent(this@SplashActivity, LoginActivity::class.java))
                finish()
            }

            override fun onAnimationStart(animation: Animator) {}
            override fun onAnimationCancel(animation: Animator) {}
            override fun onAnimationRepeat(animation: Animator) {}
        })


        if (user != null) {
            val nombre = user.displayName

            if (!nombre.isNullOrEmpty()) {
                // 🔹 Ahora esto funcionará al instante tanto para Google como para Correo
                userText.text = "Bienvenido, $nombre"
            } else {
                // 🔹 Por si inicias sesión con una de tus cuentas de prueba antiguas
                userText.text = "Bienvenido, usuario(a)"
            }
        } else {
            // Si no hay sesión iniciada
            userText.text = "Bienvenido, usuario(a)"
        }

    }
}
