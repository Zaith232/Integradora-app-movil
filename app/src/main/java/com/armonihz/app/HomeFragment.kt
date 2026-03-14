package com.armonihz.app

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.armonihz.app.databinding.FragmentHomeBinding
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.network.model.MusicianProfileDetailResponse
import com.armonihz.app.ui.adapters.MusicianAdapter
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var musicianAdapter: MusicianAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupNavigation()
        loadMusiciansFromApi()
    }

    private fun setupRecyclerView() {
        musicianAdapter = MusicianAdapter(
            musiciansList = emptyList(),
            onMusicianClick = { musicianId ->
                val fragment = MusicianProfileFragment.newInstance(musicianId)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        )

        binding.rvMusicians.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = musicianAdapter
        }
    }

    private fun loadMusiciansFromApi() {
        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        lifecycleScope.launch {
            try {
                // 1. Llamamos a la API (ahora devuelve un JsonObject genérico)
                val response = api.getAllMusicians()

                if (!isAdded) return@launch

                if (response.isSuccessful && response.body() != null) {

                    val jsonResponse = response.body()!!

                    // 2. Extraemos el arreglo 'data' manualmente sorteando la paginación de Laravel
                    // Entramos al primer "data" (objeto) y luego al segundo "data" (arreglo)
                    val dataObject = jsonResponse.getAsJsonObject("data")
                    val musiciansArray = dataObject.getAsJsonArray("data")

                    // 3. Convertimos ese arreglo JSON a nuestra lista de Kotlin
                    val gson = com.google.gson.Gson()
                    val type = object : com.google.gson.reflect.TypeToken<List<MusicianProfileDetailResponse>>() {}.type
                    val musiciansList: List<MusicianProfileDetailResponse> = gson.fromJson(musiciansArray, type)

                    // 4. Actualizamos el adaptador
                    musicianAdapter.updateData(musiciansList)

                } else {
                    Log.e("API_ERROR", "Error al cargar músicos: ${response.code()}")
                    Toast.makeText(context, "No se pudieron cargar los músicos", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                if (!isAdded) return@launch
                Log.e("API_ERROR", "Excepción: ${e.message}")
                Toast.makeText(context, "Error de conexión", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupNavigation() {
        // 1. Configuración del buscador
        binding.searchInput.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ResultsFragment())
                .addToBackStack(null)
                .commit()
        }

        // 2. Configuración del nuevo Bottom Navigation
        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    // Ya estamos en HomeFragment, normalmente aquí no se hace nada
                    // o se hace scroll hacia arriba en la lista
                    true
                }
                R.id.nav_events -> {
                    open(MyEventsFragment())
                    true
                }
                R.id.nav_favorites -> {
                    open(FavoritesFragment())
                    true
                }
                R.id.nav_profile -> {
                    open(UserProfileFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun open(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}