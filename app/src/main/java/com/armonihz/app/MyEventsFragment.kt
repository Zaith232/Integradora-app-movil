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
        loadMyEvents() // Llamamos a la API al abrir la pantalla
    }

    private fun setupRecyclerView() {
        eventAdapter = EventAdapter(
            eventsList = emptyList(),
            onEventClick = { eventId ->
                val fragment = EventProposalsFragment.newInstance(eventId)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .addToBackStack(null)
                    .commit()
            },
            onEditClick = { event ->
                val fragment = EditEventFragment.newInstance(event)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        )

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
            // ⬅️ VALIDACIÓN: Evita crash si sales del fragmento rápido
            if (!isAdded) return@addOnSuccessListener

            val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

            lifecycleScope.launch {
                try {
                    val response = api.getMyEvents()

                    // ⬅️ VALIDACIÓN
                    if (!isAdded) return@launch

                    if (response.isSuccessful && response.body() != null) {
                        eventAdapter.updateData(response.body()!!)
                    } else {
                        Log.e("API_ERROR", "Error: ${response.code()}")
                        Toast.makeText(context, "No se pudieron cargar los eventos", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    // ⬅️ VALIDACIÓN
                    if (!isAdded) return@launch
                    Log.e("API_ERROR", "Excepción: ${e.message}")
                }
            }
        }.addOnFailureListener {
            // ⬅️ VALIDACIÓN
            if (!isAdded) return@addOnFailureListener
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

        binding.bottomNavigation.selectedItemId = R.id.nav_events

        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {
                    open(HomeFragment())
                    true
                }
                R.id.nav_events -> true
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