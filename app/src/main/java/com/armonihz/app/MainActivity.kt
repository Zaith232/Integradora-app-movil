package com.armonihz.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.network.model.FcmTokenRequest
import com.armonihz.app.utils.ThemeManager
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    // 1. Declarar el launcher para pedir permisos de notificación (Requerido en Android 13+)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            obtenerYEnviarTokenFcm()
        } else {
            Log.w("FCM", "Permiso de notificaciones denegado por el usuario")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, HomeFragment())
                .commit()
        }

        // 2. Disparar el proceso de notificaciones al entrar a la pantalla principal
        configurarNotificaciones()
    }

    // 3. Función para verificar la versión de Android y pedir permisos si aplica
    private fun configurarNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                // Ya tenemos permiso, pedimos el token
                obtenerYEnviarTokenFcm()
            } else {
                // No tenemos permiso, lanzamos la ventana para preguntar al usuario
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            // En versiones anteriores a Android 13, el permiso viene por defecto al instalar
            obtenerYEnviarTokenFcm()
        }
    }

    // 4. Obtener el Token físico del dispositivo y mandarlo a Laravel
    private fun obtenerYEnviarTokenFcm() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("FCM", "No se pudo obtener el token de Firebase", task.exception)
                return@addOnCompleteListener
            }

            // Este es el token FCM que identifica al celular
            val token = task.result
            Log.d("FCM", "FCM Token obtenido: $token")

            // Lo enviamos a Laravel usando tu RetrofitClient
            lifecycleScope.launch {
                try {
                    val api = RetrofitClient.getInstance(this@MainActivity).create(ApiService::class.java)
                    val request = FcmTokenRequest(fcm_token = token)
                    val response = api.updateFcmToken(request)

                    if (response.isSuccessful) {
                        Log.d("FCM", "Token guardado en Laravel correctamente")
                    } else {
                        Log.e("FCM", "Error al guardar token: ${response.errorBody()?.string()}")
                    }
                } catch (e: Exception) {
                    Log.e("FCM", "Excepción al enviar FCM token", e)
                }
            }
        }
    }
}