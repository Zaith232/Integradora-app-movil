package com.armonihz.app

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.armonihz.app.databinding.FragmentMyEventsBinding
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.network.model.EventResponse
import com.armonihz.app.ui.adapters.EventAdapter
import com.google.firebase.auth.FirebaseAuth
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MyEventsFragment : Fragment() {

    private var _binding: FragmentMyEventsBinding? = null
    private val binding get() = _binding!!

    private lateinit var eventAdapter: EventAdapter
    private var allEvents: List<EventResponse> = emptyList()

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
        setupRefresh()
        setupChipFilters()

        val hasCache = loadCachedEvents()

        if (!hasCache) {
            binding.progressLoader.visibility = View.VISIBLE
        }

        loadMyEvents()
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
            },

            onDeleteClick = { event ->
                confirmDelete(event)
            }
        )

        binding.rvEvents.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = eventAdapter
            itemAnimator = DefaultItemAnimator()
        }
    }

    private fun setupRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadMyEvents()
        }
    }

    private fun setupChipFilters() {
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val filteredList = when {
                checkedIds.contains(R.id.chipActive) -> allEvents.filter {
                    it.status == "open" && !isExpired(it.fecha) && daysUntil(it.fecha) > 2
                }
                checkedIds.contains(R.id.chipExpiring) -> allEvents.filter {
                    it.status == "open" && !isExpired(it.fecha) && daysUntil(it.fecha) in 0..2
                }
                checkedIds.contains(R.id.chipClosed) -> allEvents.filter {
                    it.status != "open" || isExpired(it.fecha)
                }
                else -> allEvents
            }

            eventAdapter.updateData(filteredList)

            if (filteredList.isEmpty()) {
                binding.layoutEmpty.visibility = View.VISIBLE
                binding.rvEvents.visibility = View.GONE
            } else {
                binding.layoutEmpty.visibility = View.GONE
                binding.rvEvents.visibility = View.VISIBLE
            }
        }
    }

    private fun loadMyEvents() {

        val user = FirebaseAuth.getInstance().currentUser

        if (user == null) {
            Toast.makeText(context, "Debes iniciar sesión", Toast.LENGTH_SHORT).show()
            return
        }

        user.getIdToken(true).addOnSuccessListener {

            if (!isAdded || _binding == null) return@addOnSuccessListener

            val api = RetrofitClient
                .getInstance(requireContext())
                .create(ApiService::class.java)

            lifecycleScope.launch {

                try {

                    val response = api.getMyEvents()

                    if (!isAdded || _binding == null) return@launch

                    if (response.isSuccessful && response.body() != null) {

                        val events = response.body()!!

                        saveCache(events)

                        allEvents = events
                        binding.chipGroupFilter.check(R.id.chipAll)

                        if (events.isEmpty()) {
                            binding.layoutEmpty.visibility = View.VISIBLE
                            binding.rvEvents.visibility = View.GONE
                        } else {
                            binding.layoutEmpty.visibility = View.GONE
                            binding.rvEvents.visibility = View.VISIBLE
                        }

                        eventAdapter.updateData(events)
                        binding.rvEvents.scheduleLayoutAnimation()

                    } else {
                        Log.e("API_ERROR", "Error: ${response.code()}")
                    }

                } catch (e: Exception) {
                    Log.e("API_ERROR", "Excepción: ${e.message}")
                }

                if (!isAdded || _binding == null) return@launch

                binding.progressLoader.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun confirmDelete(event: EventResponse) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Eliminar evento")
            .setMessage("¿Seguro que deseas eliminar este evento?")
            .setPositiveButton("Eliminar") { _, _ ->
                deleteEvent(event.id)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun deleteEvent(eventId: Int) {

        val api = RetrofitClient
            .getInstance(requireContext())
            .create(ApiService::class.java)

        lifecycleScope.launch {

            try {

                val response = api.deleteEvent(eventId)

                if (response.isSuccessful) {
                    Toast.makeText(context, "Evento eliminado", Toast.LENGTH_SHORT).show()
                    loadMyEvents()
                } else {
                    Toast.makeText(context, "No se pudo eliminar", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                Toast.makeText(context, "Error de conexión", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveCache(events: List<EventResponse>) {
        val prefs = requireContext().getSharedPreferences("cache", 0)
        prefs.edit()
            .putString("events_cache", Gson().toJson(events))
            .apply()
    }

    private fun loadCachedEvents(): Boolean {
        val prefs = requireContext().getSharedPreferences("cache", 0)
        val json = prefs.getString("events_cache", null)

        if (json != null) {
            val type = object : TypeToken<List<EventResponse>>() {}.type
            val cachedEvents: List<EventResponse> = Gson().fromJson(json, type)
            allEvents = cachedEvents
            eventAdapter.updateData(cachedEvents)
            return true
        }

        return false
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
                R.id.nav_home -> { open(HomeFragment()); true }
                R.id.nav_events -> true
                R.id.nav_favorites -> { open(FavoritesFragment()); true }
                R.id.nav_notifications -> { open(NotificationsFragment()); true }
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

    // --- Funciones de fecha ---

    private fun daysUntil(dateString: String): Long {
        return try {
            val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val eventDate = format.parse(dateString) ?: return Long.MAX_VALUE
            val calendarToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            (eventDate.time - calendarToday.time.time) / (1000 * 60 * 60 * 24)
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
    }

    private fun isExpired(dateString: String): Boolean {
        return try {
            val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val eventDate = format.parse(dateString) ?: return false
            val calendarToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            eventDate.before(calendarToday.time)
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}