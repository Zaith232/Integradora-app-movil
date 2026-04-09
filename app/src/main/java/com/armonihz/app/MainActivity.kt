package com.armonihz.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.network.model.FcmTokenRequest
import com.armonihz.app.utils.ThemeManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : AppCompatActivity() {

    private val api: ApiService by lazy {
        RetrofitClient.getInstance(this).create(ApiService::class.java)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) obtenerYEnviarTokenFcm()
        else Log.w("FCM", "Permiso de notificaciones denegado")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ThemeManager.applyTheme(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            abrirFragmentoInicial()
        }

        setupBottomNavigation()
        configurarNotificaciones()
    }

    // ── Navegación inicial ────────────────────────────────────────────────────

    private fun abrirFragmentoInicial() {
        // Leemos el candado persistente en la memoria del teléfono
        val prefs = getSharedPreferences("ArmonihzPrefs", Context.MODE_PRIVATE)
        val faltaPerfil = prefs.getBoolean("perfil_incompleto", false)

        val fragment = when {
            // 1. Prioridad: Notificaciones Push
            intent.getStringExtra("hiring_request_id") != null -> {
                val hiringRequestId = intent.getStringExtra("hiring_request_id")!!
                NotificationsFragment().apply {
                    arguments = Bundle().apply { putString("hiring_request_id", hiringRequestId) }
                }
            }

            // 2. Prioridad: Candado de seguridad (Si no ha aceptado términos/perfil)
            faltaPerfil -> CompleteProfileFragment()

            // 3. Navegación normal
            intent.getBooleanExtra("ir_a_editar_perfil", false) -> EditProfileFragment()
            else -> HomeFragment()
        }

        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    // ── Notificaciones FCM ────────────────────────────────────────────────────

    private fun configurarNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                obtenerYEnviarTokenFcm()
            } else {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            obtenerYEnviarTokenFcm()
        }
    }

    private fun obtenerYEnviarTokenFcm() {
        lifecycleScope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                api.updateFcmToken(FcmTokenRequest(fcm_token = token))
            } catch (e: Exception) {
                Log.e("FCM", "Error enviando token", e)
            }
        }
    }

    private fun setupBottomNavigation() {
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)

        // 🔥 MEJORA: Usamos LifecycleCallbacks para ocultar/mostrar la barra correctamente
        // sin depender del BackStack, lo que evita que la barra aparezca en 'CompleteProfile'
        supportFragmentManager.registerFragmentLifecycleCallbacks(object : androidx.fragment.app.FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(fm: androidx.fragment.app.FragmentManager, f: Fragment, v: View, savedInstanceState: Bundle?) {
                super.onFragmentViewCreated(fm, f, v, savedInstanceState)

                val sinNav = f is CompleteProfileFragment ||
                        f is EditProfileFragment ||
                        f is MusicianProfileFragment ||
                        f is AddEventFragment ||
                        f is EditEventFragment ||
                        f is SettingsFragment ||
                        f is MyReviewsFragment ||
                        f is ChangePhotoFragment

                bottomNav.visibility = if (sinNav) View.GONE else View.VISIBLE
            }
        }, true)

        bottomNav.setOnItemSelectedListener { menuItem ->
            val current = supportFragmentManager.findFragmentById(R.id.fragmentContainer)
            when (menuItem.itemId) {
                R.id.nav_home -> { if (current !is HomeFragment) navigate(HomeFragment()); true }
                R.id.nav_events -> { if (current !is MyEventsFragment) navigate(MyEventsFragment()); true }
                R.id.nav_favorites -> { if (current !is FavoritesFragment) navigate(FavoritesFragment()); true }
                R.id.nav_notifications -> { if (current !is NotificationsFragment) navigate(NotificationsFragment()); true }
                R.id.nav_profile -> { if (current !is UserProfileFragment) navigate(UserProfileFragment()); true }
                else -> false
            }
        }
    }

    private fun navigate(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}