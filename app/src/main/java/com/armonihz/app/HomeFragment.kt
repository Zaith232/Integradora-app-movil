package com.armonihz.app

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var musicianAdapter: MusicianAdapter
    private var allMusicians: List<MusicianProfileDetailResponse> = emptyList()

    // --- VARIABLES DE PAGINACIÓN ---
    private var currentPage = 1
    private var isLoadingPagination = false
    private var isLastPage = false

    // --- VARIABLE DEL FILTRO DE GÉNEROS ---
    private var selectedGenreFilter: String? = null

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
        binding.tvGreeting.text = "Hola, $primerNombre"

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
        // Al recargar, reiniciamos la paginación a la página 1
        currentPage = 1
        isLastPage = false

        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        try {
            coroutineScope {
                // Las 3 llamadas arrancan al mismo tiempo
                val musicianosDeferred = async {
                    try { api.getAllMusicians(currentPage) } catch (e: Exception) { null }
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

                procesarMusicos(musicianosResponse, isRefresh = true)
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
            if (isAdded) {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    // --- FUNCIÓN PARA CARGAR MÁS PÁGINAS AL HACER SCROLL ---
    private fun loadMoreMusicians() {
        if (isLoadingPagination || isLastPage) return

        isLoadingPagination = true
        currentPage++

        // 🔥 MOSTRAMOS LA RUEDITA AL INICIAR LA CARGA
        binding.pbPagination.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)
                val response = api.getAllMusicians(currentPage)
                if (!isAdded) return@launch

                // Procesamos los datos indicando que NO es una recarga (isRefresh = false)
                procesarMusicos(response, isRefresh = false)
            } catch (e: Exception) {
                Log.e("PAGINATION_ERROR", "Error cargando más músicos: ${e.message}")
                currentPage-- // Revertimos la página si falla la conexión
            } finally {
                isLoadingPagination = false
                // 🔥 OCULTAMOS LA RUEDITA AL TERMINAR (pase lo que pase)
                if (isAdded && _binding != null) { // 👉 PROTEGER EL BINDING AQUÍ
                    binding.pbPagination.visibility = View.GONE
                }
            }
        }
    }

    private fun procesarMusicos(response: retrofit2.Response<com.google.gson.JsonObject>?, isRefresh: Boolean) {
        response?.body()?.let { jsonResponse ->
            try {
                val dataObject = jsonResponse.getAsJsonObject("data")
                val musiciansArray = dataObject.getAsJsonArray("data")

                // Leer la paginación
                val metaObject = dataObject.getAsJsonObject("meta")
                val current = metaObject.get("current_page")?.asInt ?: 1
                val last = metaObject.get("last_page")?.asInt ?: 1

                isLastPage = current >= last

                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<MusicianProfileDetailResponse>>() {}.type
                val newMusicians: List<MusicianProfileDetailResponse> = gson.fromJson(musiciansArray, type)

                if (isRefresh) {
                    allMusicians = newMusicians
                } else {
                    val mutableList = allMusicians.toMutableList()
                    mutableList.addAll(newMusicians)
                    // ELIMINA DUPLICADOS
                    allMusicians = mutableList.distinctBy { it.id }
                }

                filterMusicians()

            } catch (e: Exception) {
                Log.e("API_ERROR", "Error procesando músicos: ${e.message}")
                if (isAdded && isRefresh) Toast.makeText(requireContext(), "Error al cargar músicos", Toast.LENGTH_SHORT).show()
            }
        } ?: run {
            if (isAdded && isRefresh) Toast.makeText(requireContext(), "No se pudieron cargar los músicos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun procesarPerfil(response: retrofit2.Response<com.armonihz.app.network.model.ProfileResponse>?) {
        response?.body()?.let { profile ->
            try {
                val nombre = profile.nombre?.split(" ")?.firstOrNull() ?: return@let
                binding.tvGreeting.text = "Hola, $nombre"

                val photoUrl = profile.photoUrl
                if (!photoUrl.isNullOrEmpty()) {
                    Glide.with(this)
                        .load("$photoUrl?t=${System.currentTimeMillis()}")
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .circleCrop()
                        .into(binding.ivProfile)
                }
            } catch (e: Exception) {
                Log.e("API_ERROR", "Error procesando perfil: ${e.message}")
            }
        }
    }

    private fun pintarGeneros(genres: List<com.armonihz.app.network.model.Genre>) {
        if (!isAdded) return

        binding.btnFilterGenre.setOnClickListener {
            mostrarDialogoGeneros(genres)
        }
    }

    private fun mostrarDialogoGeneros(genres: List<com.armonihz.app.network.model.Genre>) {
        // Creamos una lista de puros nombres para mostrar en el diálogo
        val genreNames = genres.map { it.name }.toTypedArray()

        // Buscamos cuál estaba seleccionado para marcarlo
        val checkedItem = genreNames.indexOf(selectedGenreFilter)

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Filtrar por género")
            .setSingleChoiceItems(genreNames, checkedItem) { dialog, which ->
                // Cuando el usuario toca uno, lo guardamos y filtramos
                selectedGenreFilter = genreNames[which]
                binding.btnFilterGenre.text = selectedGenreFilter // Cambiamos el texto del botón
                filterMusicians()
                dialog.dismiss()
            }
            .setNeutralButton("Limpiar filtro") { dialog, _ ->
                // Para quitar el filtro y ver todos de nuevo
                selectedGenreFilter = null
                binding.btnFilterGenre.text = "Todos los géneros"
                filterMusicians()
            }
            .setNegativeButton("Cancelar", null)
            .show()
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

        // --- DETECCIÓN DEL SCROLL INFINITO ---
        binding.nestedScrollView.setOnScrollChangeListener { v: androidx.core.widget.NestedScrollView, _, scrollY, _, oldScrollY ->
            if (scrollY > oldScrollY) {
                val view = v.getChildAt(v.childCount - 1)
                val diff = (view.bottom - (v.height + v.scrollY))

                if (diff <= 100) {
                    loadMoreMusicians()
                }
            }
        }
    }

    private fun setupNavigation() {
        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { filterMusicians() }
        })
    }

    private fun setupRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            lifecycleScope.launch {
                cargarTodoEnParalelo()
            }
        }
    }

    private fun filterMusicians() {
        val query = binding.searchInput.text.toString().trim().lowercase()

        val filteredList = allMusicians.filter { musician ->
            // 🔥 Buscamos en el nombre OR (||) en la ubicación
            val matchesSearch = query.isEmpty() ||
                    musician.stage_name.lowercase().contains(query) ||
                    (musician.location?.lowercase()?.contains(query) == true)

            // 🔥 Buscamos por la categoría seleccionada en el diálogo
            val matchesCategory = selectedGenreFilter == null || musician.genres?.any { genre ->
                genre.name.lowercase().contains(selectedGenreFilter!!.lowercase())
            } == true

            matchesSearch && matchesCategory
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