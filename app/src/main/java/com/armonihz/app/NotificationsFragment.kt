package com.armonihz.app

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.ui.adapters.NotificationAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class NotificationsFragment : Fragment() {

    private lateinit var rvNotifications: RecyclerView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_notifications, container, false)

        rvNotifications = view.findViewById(R.id.rvNotifications)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)

        // Configurar el RecyclerView con el clic en la tarjeta
        adapter = NotificationAdapter(emptyList()) { solicitudTocada ->
            // 🔥 AQUÍ ABRIMOS EL PANEL DE DETALLES
            val bottomSheet = RequestDetailBottomSheet(solicitudTocada)
            bottomSheet.show(parentFragmentManager, "RequestDetail")
        }

        rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        rvNotifications.adapter = adapter

        // Configurar la navegación inferior
        val bottomNav = view.findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.nav_notifications
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> { open(HomeFragment()); true }
                R.id.nav_events -> { open(MyEventsFragment()); true }
                R.id.nav_favorites -> { open(FavoritesFragment()); true }
                R.id.nav_notifications -> true
                R.id.nav_profile -> { open(UserProfileFragment()); true }
                else -> false
            }
        }

        // Llamar a Laravel
        cargarNotificaciones()

        return view
    }

    private fun cargarNotificaciones() {
        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        lifecycleScope.launch {
            try {
                val response = api.getMyHiringRequests()
                if (response.isSuccessful && response.body() != null) {
                    val solicitudes = response.body()!!.data

                    if (solicitudes.isEmpty()) {
                        rvNotifications.visibility = View.GONE
                        layoutEmptyState.visibility = View.VISIBLE
                    } else {
                        rvNotifications.visibility = View.VISIBLE
                        layoutEmptyState.visibility = View.GONE
                        adapter.updateData(solicitudes)
                    }
                } else {
                    Toast.makeText(context, "No se pudieron cargar las solicitudes", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("Notificaciones", "Error de red: ${e.message}")
                Toast.makeText(context, "Error de conexión", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun open(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}