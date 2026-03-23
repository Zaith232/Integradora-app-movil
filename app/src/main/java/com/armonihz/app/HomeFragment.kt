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

        // ✅ Mostrar datos locales de Firebase Auth de inmediato
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

        // ✅ Cargar todo en paralelo
        lifecycleScope.launch {
            cargarTodoEnParalelo()
        }
    }

    private suspend fun cargarTodoEnParalelo() {
        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        // ✅ Las 3 llamadas arrancan al mismo tiempo
        val musicianosDeferred = lifecycleScope.async {
            try { api.getAllMusicians() } catch (e: Exception) { null }
        }
        val perfilDeferred = lifecycleScope.async {
            try { api.getProfile() } catch (e: Exception) { null }
        }
        val generosDeferred = lifecycleScope.async {
            try { api.getGenres() } catch (e: Exception) { null }
        }

        // ✅ Esperar los 3 resultados juntos
        val musicianosResponse = musicianosDeferred.await()
        val perfilResponse     = perfilDeferred.await()
        val generosResponse    = generosDeferred.await()

        if (!isAdded) return

        // ✅ Procesar perfil
        perfilResponse?.body()?.let { body ->
            val nombre = body.nombre?.split(" ")?.firstOrNull() ?: "Usuario"
            binding.tvGreeting.text = "Hola, $nombre 👋"

            val photoUrl = body.photoUrl
            if (!photoUrl.isNullOrEmpty()) {
                Glide.with(this)
                    .load("$photoUrl?t=${System.currentTimeMillis()}")
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .circleCrop()
                    .into(binding.ivProfile)
            }
        }

        // ✅ Procesar géneros
        generosResponse?.body()?.let { genres ->
            binding.chipGroupCategories.removeAllViews()
            for (genre in genres) {
                val chip = Chip(requireContext()).apply {
                    text = genre.name
                    isCheckable = true
                    setChipDrawable(
                        com.google.android.material.chip.ChipDrawable.createFromAttributes(
                            requireContext(), null, 0,
                            com.google.android.material.R.style.Widget_MaterialComponents_Chip_Choice
                        )
                    )
                    tag = genre.name
                }
                binding.chipGroupCategories.addView(chip)
            }
        }

        // ✅ Procesar músicos
        musicianosResponse?.body()?.let { jsonResponse ->
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
            override fun afterTextChanged(s: Editable?) {
                filterMusicians()
            }
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

    private fun filterMusicians() {
        val query = binding.searchInput.text.toString().trim().lowercase()

        val selectedChipId = binding.chipGroupCategories.checkedChipId

        val categoryFilter = if (selectedChipId != View.NO_ID) {
            val selectedChip = binding.chipGroupCategories.findViewById<Chip>(selectedChipId)
            selectedChip?.tag as? String
        } else {
            null
        }

        val filteredList = allMusicians.filter { musician ->
            val matchesName = query.isEmpty() || musician.stage_name.lowercase().contains(query)
            val matchesCategory = categoryFilter == null || musician.genres?.any { genre ->
                genre.name.lowercase().contains(categoryFilter.lowercase())
            } == true
            matchesName && matchesCategory
        }

        musicianAdapter.updateData(filteredList)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}