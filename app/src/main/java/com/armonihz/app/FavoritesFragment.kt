package com.armonihz.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.ui.adapters.FavoritesAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView
import kotlinx.coroutines.launch

class FavoritesFragment : Fragment() {

    private lateinit var rvFavorites: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var adapter: FavoritesAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_favorites, container, false)

        rvFavorites = view.findViewById(R.id.rvFavorites)
        progressBar = view.findViewById(R.id.progressBar)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)

        setupNavigation(view)
        loadFavorites()

        return view
    }

    private fun loadFavorites() {
        progressBar.visibility = View.VISIBLE
        rvFavorites.visibility = View.GONE
        tvEmptyState.visibility = View.GONE

        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        // ⬅️ CAMBIO 1: Usar viewLifecycleOwner.lifecycleScope
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = api.getMyFavorites()

                // Si cambiamos de pestaña justo en este milisegundo, evitamos el crash
                if (!isAdded) return@launch

                progressBar.visibility = View.GONE

                if (response.isSuccessful && response.body() != null) {
                    val favoritesList = response.body()!!.data.toMutableList()

                    if (favoritesList.isEmpty()) {
                        tvEmptyState.visibility = View.VISIBLE
                    } else {
                        rvFavorites.visibility = View.VISIBLE
                        adapter = FavoritesAdapter(
                            favoritesList,
                            onMusicianClick = { musicianId ->
                                open(MusicianProfileFragment.newInstance(musicianId))
                            },
                            onRemoveClick = { musician, position ->
                                removeFavorite(api, musician.id, position)
                            }
                        )
                        rvFavorites.adapter = adapter
                    }
                } else {
                    // ⬅️ CAMBIO 2: Toast seguro con context?.let
                    context?.let {
                        Toast.makeText(it, "Error al cargar favoritos", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (!isAdded) return@launch

                progressBar.visibility = View.GONE
                context?.let {
                    Toast.makeText(it, "Error de conexión", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun removeFavorite(api: ApiService, musicianId: Int, position: Int) {
        adapter.removeItem(position)

        if (adapter.itemCount == 0) {
            tvEmptyState.visibility = View.VISIBLE
        }

        // ⬅️ CAMBIO 1: Usar viewLifecycleOwner.lifecycleScope
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = api.removeFavorite(musicianId)
                if (!isAdded) return@launch

                if (!response.isSuccessful) {
                    context?.let {
                        Toast.makeText(it, "Error al quitar de favoritos", Toast.LENGTH_SHORT).show()
                    }
                    loadFavorites()
                }
            } catch (e: Exception) {
                if (!isAdded) return@launch

                context?.let {
                    Toast.makeText(it, "Error de conexión", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupNavigation(view: View) {
        val bottomNavigation = view.findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNavigation.selectedItemId = R.id.nav_favorites

        bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> { open(HomeFragment()); true }
                R.id.nav_events -> { open(MyEventsFragment()); true }
                R.id.nav_favorites -> true
                R.id.nav_profile -> { open(UserProfileFragment()); true }
                else -> false
            }
        }
    }

    private fun open(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}