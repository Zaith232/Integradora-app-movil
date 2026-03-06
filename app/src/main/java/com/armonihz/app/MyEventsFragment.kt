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
import com.armonihz.app.databinding.FragmentMyEventsBinding
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.ui.adapters.EventAdapter
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class MyEventsFragment : Fragment() {

    private var _binding: FragmentMyEventsBinding? = null
    private val binding get() = _binding!!

    private lateinit var eventAdapter: EventAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyEventsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupListeners()
        loadMyEvents() // ⬅️ Llamamos a la API al abrir la pantalla
    }

    private fun setupRecyclerView() {
        eventAdapter = EventAdapter(emptyList())
        binding.rvEvents.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = eventAdapter
        }
    }

    private fun loadMyEvents() {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(context, "Debes iniciar sesión", Toast.LENGTH_SHORT).show()
            return
        }

        user.getIdToken(true).addOnSuccessListener { result ->
            val token = "Bearer ${result.token}"
            val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

            lifecycleScope.launch {
                try {
                    val response = api.getMyEvents(token)
                    if (response.isSuccessful && response.body() != null) {
                        eventAdapter.updateData(response.body()!!)
                    } else {
                        Log.e("API_ERROR", "Error: ${response.code()}")
                        Toast.makeText(context, "No se pudieron cargar los eventos", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e("API_ERROR", "Excepción: ${e.message}")
                }
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Error de autenticación", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupListeners() {
        binding.fabAddEvent.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, AddEventFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.btnHome.setOnClickListener { open(HomeFragment()) }
        binding.btnFavorite.setOnClickListener { open(FavoritesFragment()) }
        binding.btnProfile.setOnClickListener { open(UserProfileFragment()) }
        binding.btnEvent.setOnClickListener { }
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