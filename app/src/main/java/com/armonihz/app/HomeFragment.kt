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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.firebase.auth.FirebaseAuth
import android.text.Editable
import android.text.TextWatcher
import com.google.android.material.chip.Chip

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var musicianAdapter: MusicianAdapter
    private var allMusicians: List<MusicianProfileDetailResponse> = emptyList()

    companion object {
        // Caché en memoria para géneros — no cambian frecuentemente
        private var cachedGenres: List<com.armonihz.app.network.model.Genre>? = null
    }

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
        setupRefresh()

        // Mostrar datos de Firebase Auth de inmediato sin esperar la API
        val user = FirebaseAuth.getInstance().currentUser
        val primerNombre = user?.displayName?.split(" ")?.firstOrNull() ?: "Usuario"
        binding.tvGreeting.text = "Hola, $primerNombre 👋"

        user?.photoUrl?.let {
            Glide.with(this)
                .load(it)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .circleCrop()
                .into(binding.ivProfile)
        }

        // Si hay géneros cacheados, pintarlos de inmediato sin esperar la red
        cachedGenres?.let { pintarGeneros(it) }

        // Mostrar shimmer y arrancar carga
        mostrarShimmer(true)

        lifecycleScope.launch {
            cargarTodoEnParalelo()
        }
    }

    private suspend fun cargarTodoEnParalelo() {
        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        try {
            coroutineScope {
                // Las 3 llamadas arrancan al mismo tiempo
                val musicianosDeferred = async {
                    try { api.getAllMusicians() } catch (e: Exception) { null }
                }
                val perfilDeferred = async {
                    try { api.getProfile() } catch (e: Exception) { null }
                }
                // Solo pedir géneros si no están cacheados
                val generosDeferred = if (cachedGenres == null) {
                    async { try { api.getGenres() } catch (e: Exception) { null } }
                } else null

                // Músicos tienen prioridad — en cuanto lleguen los mostramos
                val musicianosResponse = musicianosDeferred.await()
                if (!isAdded) return@coroutineScope

                procesarMusicos(musicianosResponse)
                mostrarShimmer(false) // Apagar shimmer en cuanto tengamos músicos

                // Perfil y géneros llegan después sin bloquear la lista
                val perfilResponse  = perfilDeferred.await()
                val generosResponse = generosDeferred?.await()

                if (!isAdded) return@coroutineScope

                procesarPerfil(perfilResponse)

                generosResponse?.body()?.let { genres ->
                    cachedGenres = genres
                    pintarGeneros(genres)
                }
            }
        } finally {
            // ⬅️ NUEVO: Apagamos la animación de recarga al terminar todo (éxito o error)
            if (isAdded) {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun procesarMusicos(response: retrofit2.Response<com.google.gson.JsonObject>?) {
        response?.body()?.let { jsonResponse ->
            try {
                val dataObject     = jsonResponse.getAsJsonObject("data")
                val musiciansArray = dataObject.getAsJsonArray("data")
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<MusicianProfileDetailResponse>>() {}.type
                val musiciansList: List<MusicianProfileDetailResponse> = gson.fromJson(musiciansArray, type)

                allMusicians = musiciansList
                filterMusicians()

            } catch (e: Exception) {
                Log.e("API_ERROR", "Error procesando músicos: ${e.message}")
                if (isAdded) Toast.makeText(requireContext(), "Error al cargar músicos", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            if (isAdded) Toast.makeText(requireContext(), "No se pudieron cargar los músicos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun procesarPerfil(response: retrofit2.Response<com.armonihz.app.network.model.ProfileResponse>?) {
        response?.body()?.let { profile ->
            try {
                // Como ya está tipado, usamos 'profile' directamente
                val nombre = profile.nombre?.split(" ")?.firstOrNull() ?: return@let
                binding.tvGreeting.text = "Hola, $nombre 👋"

                val photoUrl = profile.photoUrl
                if (!photoUrl.isNullOrEmpty()) {
                    Glide.with(this)
                        .load("$photoUrl?t=${System.currentTimeMillis()}")
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .circleCrop()
                        .into(binding.ivProfile)
                }

                // Esto evita el error de "if must have both main and else branches"
                Unit
            } catch (e: Exception) {
                Log.e("API_ERROR", "Error procesando perfil: ${e.message}")
            }
        }
    }

    private fun pintarGeneros(genres: List<com.armonihz.app.network.model.Genre>) {
        if (!isAdded) return

        binding.chipGroupCategories.removeAllViews()

        for (genre in genres) {
            // 1. Inflar el diseño personalizado que contiene el estilo Filter
            val chip = layoutInflater.inflate(R.layout.item_category_chip, binding.chipGroupCategories, false) as Chip

            // 2. Asignar los valores al chip
            chip.text = genre.name
            chip.tag = genre.name

            // 3. Importante: Generar un ID único para que el singleSelection funcione
            chip.id = View.generateViewId()

            // 4. Agregarlo al ChipGroup
            binding.chipGroupCategories.addView(chip)
        }
    }

    private fun mostrarShimmer(mostrar: Boolean) {
        if (!isAdded) return
        if (mostrar) {
            binding.shimmerLayout.startShimmer()
            binding.shimmerLayout.visibility = View.VISIBLE
            binding.rvMusicians.visibility = View.GONE
            binding.tvEmptyState.visibility = View.GONE
        } else {
            binding.shimmerLayout.stopShimmer()
            binding.shimmerLayout.visibility = View.GONE
            // El RecyclerView o el empty state se muestran en filterMusicians()
        }
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

    private fun setupNavigation() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { filterMusicians() }
        })

        binding.chipGroupCategories.setOnCheckedStateChangeListener { _, _ ->
            filterMusicians()
        }

        binding.ivProfile.setOnClickListener {
            binding.bottomNavigation.selectedItemId = R.id.nav_profile
        }

        binding.tvGreeting.setOnClickListener {
            binding.bottomNavigation.selectedItemId = R.id.nav_profile
        }

        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> true
                R.id.nav_events -> { open(MyEventsFragment()); true }
                R.id.nav_favorites -> { open(FavoritesFragment()); true }
                R.id.nav_notifications -> { open(NotificationsFragment()); true }
                R.id.nav_profile -> { open(UserProfileFragment()); true }
                else -> false
            }
        }
    }

    private fun setupRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            // Cuando jalen hacia abajo, volvemos a llamar a la API
            lifecycleScope.launch {
                cargarTodoEnParalelo()
            }
        }
    }

    private fun open(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    private fun filterMusicians() {
        val query = binding.searchInput.text.toString().trim().lowercase()

        val selectedChipId = binding.chipGroupCategories.checkedChipId
        val categoryFilter = if (selectedChipId != View.NO_ID) {
            binding.chipGroupCategories.findViewById<Chip>(selectedChipId)?.tag as? String
        } else null

        val filteredList = allMusicians.filter { musician ->
            val matchesName = query.isEmpty() || musician.stage_name.lowercase().contains(query)
            val matchesCategory = categoryFilter == null || musician.genres?.any { genre ->
                genre.name.lowercase().contains(categoryFilter.lowercase())
            } == true
            matchesName && matchesCategory
        }

        musicianAdapter.updateData(filteredList)

        if (filteredList.isEmpty()) {
            binding.rvMusicians.visibility = View.GONE
            binding.tvEmptyState.visibility = View.VISIBLE
        } else {
            binding.rvMusicians.visibility = View.VISIBLE
            binding.tvEmptyState.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}