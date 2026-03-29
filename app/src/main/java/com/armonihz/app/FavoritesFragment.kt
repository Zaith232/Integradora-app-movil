package com.armonihz.app

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.network.model.MusicianProfileDetailResponse
import com.armonihz.app.ui.adapters.FavoritesAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class FavoritesFragment : Fragment() {

    private lateinit var rvFavorites: RecyclerView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var progressBar: ProgressBar
    private lateinit var adapter: FavoritesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_favorites, container, false)

        rvFavorites = view.findViewById(R.id.rvFavorites)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)
        progressBar = view.findViewById(R.id.progressBar)

        // 1. Configurar RecyclerView y Adaptador
        rvFavorites.layoutManager = LinearLayoutManager(requireContext())
        adapter = FavoritesAdapter(
            favorites = mutableListOf(),
            onMusicianClick = { musicianId ->
                // Abrir el perfil del músico
                val fragment = MusicianProfileFragment.newInstance(musicianId)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .addToBackStack(null) // Para poder volver atrás con el botón físico
                    .commit()
            },
            onRemoveClick = { musician, position ->
                // Eliminar favorito
                removeFavorite(musician, position)
            }
        )
        rvFavorites.adapter = adapter

        // 2. Configurar Navegación inferior
        val bottomNav = view.findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.nav_favorites
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> { open(HomeFragment()); true }
                R.id.nav_events -> { open(MyEventsFragment()); true }
                R.id.nav_favorites -> true
                R.id.nav_notifications -> { open(NotificationsFragment()); true }
                R.id.nav_profile -> { open(UserProfileFragment()); true }
                else -> false
            }
        }

        // 3. Obtener los datos del servidor
        cargarFavoritos()

        return view
    }

    private fun cargarFavoritos() {
        progressBar.visibility = View.VISIBLE
        rvFavorites.visibility = View.GONE
        layoutEmptyState.visibility = View.GONE

        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        lifecycleScope.launch {
            try {
                val response = api.getMyFavorites()
                progressBar.visibility = View.GONE

                if (response.isSuccessful && response.body() != null) {
                    val favoritos = response.body()!!.data

                    if (favoritos.isEmpty()) {
                        mostrarEstadoVacio()
                    } else {
                        mostrarLista(favoritos)
                    }
                } else {
                    Toast.makeText(requireContext(), "Error al cargar favoritos", Toast.LENGTH_SHORT).show()
                    mostrarEstadoVacio()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Log.e("FAVORITOS_API", "Error: ${e.message}")
                Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
                mostrarEstadoVacio()
            }
        }
    }

    private fun removeFavorite(musician: MusicianProfileDetailResponse, position: Int) {
        // 🔥 ELIMINACIÓN OPTIMISTA 🔥
        // Lo quitamos de la vista inmediatamente para que se sienta rápido sin esperar al servidor
        adapter.removeItem(position)

        // Verificamos si, al borrar este, la lista se quedó vacía
        checkEmptyState()

        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        lifecycleScope.launch {
            try {
                val response = api.removeFavorite(musician.id)
                if (!response.isSuccessful) {
                    // Opcional: Si el servidor falla, podrías recargar la lista entera llamando a cargarFavoritos()
                    Toast.makeText(requireContext(), "Ocurrió un error al sincronizar", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Falla silenciosa de conexión, la UI ya hizo su trabajo
                Toast.makeText(requireContext(), "Revisa tu conexión a internet", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mostrarLista(favoritos: List<MusicianProfileDetailResponse>) {
        layoutEmptyState.visibility = View.GONE
        rvFavorites.visibility = View.VISIBLE
        adapter.updateData(favoritos)
    }

    private fun mostrarEstadoVacio() {
        rvFavorites.visibility = View.GONE
        layoutEmptyState.visibility = View.VISIBLE
    }

    private fun checkEmptyState() {
        if (adapter.itemCount == 0) {
            mostrarEstadoVacio()
        }
    }

    private fun open(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}