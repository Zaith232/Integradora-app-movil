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
import com.armonihz.app.databinding.FragmentEventProposalsBinding
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.ui.adapters.ProposalAdapter
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

class EventProposalsFragment : Fragment() {

    private var _binding: FragmentEventProposalsBinding? = null
    private val binding get() = _binding!!

    private var eventId: Int = -1
    private lateinit var proposalAdapter: ProposalAdapter

    companion object {
        private const val ARG_EVENT_ID = "event_id"

        fun newInstance(eventId: Int): EventProposalsFragment {
            val fragment = EventProposalsFragment()
            val args = Bundle()
            args.putInt(ARG_EVENT_ID, eventId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            eventId = it.getInt(ARG_EVENT_ID, -1)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentEventProposalsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()

        if (eventId != -1) {
            loadProposals()
        }
    }

    private fun setupRecyclerView() {
        proposalAdapter = ProposalAdapter(
            proposalsList = emptyList(),
            onAcceptClick = { applicationId ->
                acceptApplication(applicationId)
            },
            onMusicianClick = { musicianId ->
                val fragment = MusicianProfileFragment.newInstance(musicianId)
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .addToBackStack(null)
                    .commit()
            }
        )

        binding.rvProposals.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = proposalAdapter
        }
    }

    private fun loadProposals() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        lifecycleScope.launch {
            try {
                // ⬅️ Se eliminó la petición a Firebase y el envío del token
                val response = api.getEventApplications(eventId)
                if (response.isSuccessful && response.body() != null) {
                    proposalAdapter.updateData(response.body()!!.applications)
                } else {
                    Toast.makeText(context, "Error al cargar propuestas", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("API_ERROR", "Excepción: ${e.message}")
            }
        }
    }

    private fun acceptApplication(applicationId: Int) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(context, "Debes iniciar sesión", Toast.LENGTH_SHORT).show()
            return
        }

        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        lifecycleScope.launch {
            try {
                // ⬅️ Se eliminó la petición a Firebase y el envío del token
                val response = api.acceptApplication(eventId, applicationId)

                if (response.isSuccessful && response.body() != null) {
                    Toast.makeText(context, response.body()!!.message, Toast.LENGTH_SHORT).show()
                    loadProposals()
                } else {
                    Toast.makeText(context, "Error al aceptar al músico", Toast.LENGTH_SHORT).show()
                    Log.e("API_ERROR", "Código: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("API_ERROR", "Excepción al aceptar: ${e.message}")
                Toast.makeText(context, "Error de conexión", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}