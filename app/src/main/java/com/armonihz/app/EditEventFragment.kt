package com.armonihz.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.armonihz.app.databinding.FragmentEditEventBinding
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.network.model.EventRequest
import com.armonihz.app.network.model.EventResponse
import com.armonihz.app.network.model.Genre
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Calendar
import java.util.Locale

class EditEventFragment : Fragment() {

    private var _binding: FragmentEditEventBinding? = null
    private val binding get() = _binding!!

    private var eventToEdit: EventResponse? = null
    private var genresList: List<Genre> = emptyList()
    private var ciudadesDisponibles: List<String> = emptyList()

    companion object {
        private const val ARG_EVENT = "event_data"

        fun newInstance(event: EventResponse): EditEventFragment {
            val fragment = EditEventFragment()
            val args = Bundle()
            args.putSerializable(ARG_EVENT, event)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            eventToEdit = it.getSerializable(ARG_EVENT) as? EventResponse
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Cargar datos dinámicos
        cargarGeneros()
        cargarCiudadesDesdeAssets()

        // 2. Configurar UI
        setupDatePicker()
        setupTimePicker()
        setupFormValidation()
        prefillData()

        // 3. Listeners
        binding.btnSaveEvent.setOnClickListener { updateEvent() }
        binding.btnBackEditEvent.setOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun setupFormValidation() {
        binding.btnSaveEvent.isEnabled = false
        val fields = listOf(binding.etTitulo, binding.etFecha, binding.etDuracion, binding.etLocation, binding.etBudget)

        fields.forEach { et ->
            et.addTextChangedListener { validarCampos() }
        }
    }

    private fun validarCampos() {
        val isValid = binding.etTitulo.text!!.isNotEmpty() &&
                binding.etFecha.text!!.isNotEmpty() &&
                binding.etDuracion.text!!.isNotEmpty() &&
                binding.etLocation.text!!.isNotEmpty() &&
                binding.etBudget.text!!.isNotEmpty()

        binding.btnSaveEvent.isEnabled = isValid
    }

    private fun cargarGeneros() {
        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        lifecycleScope.launch {
            try {
                val response = api.getGenres()
                if (response.isSuccessful) {
                    genresList = response.body() ?: emptyList()
                    val nombres = genresList.map { it.name }

                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, nombres)
                    binding.spinnerMusicType.adapter = adapter

                    // Seleccionar automáticamente el género actual del evento
                    eventToEdit?.let { event ->
                        val index = genresList.indexOfFirst { it.name == event.tipoMusica }
                        if (index >= 0) binding.spinnerMusicType.setSelection(index)
                    }
                }
            } catch (e: Exception) {
                Log.e("API_ERROR", "Error al cargar géneros: ${e.message}")
            }
        }
    }

    private fun prefillData() {
        eventToEdit?.let { event ->
            binding.etTitulo.setText(event.titulo)
            binding.etFecha.setText(event.fecha)
            binding.etDuracion.setText(event.duracion)
            binding.etLocation.setText(event.ubicacion)
            binding.etBudget.setText(event.presupuesto.toString())
            binding.etDescription.setText(event.descripcion ?: "")
        }
    }

    private fun updateEvent() {
        if (genresList.isEmpty() || eventToEdit == null) return

        val presupuesto = binding.etBudget.text.toString().toDoubleOrNull() ?: 0.0
        val genreId = genresList[binding.spinnerMusicType.selectedItemPosition].id

        val request = EventRequest(
            titulo = binding.etTitulo.text.toString(),
            genre_id = genreId,
            fecha = binding.etFecha.text.toString(),
            duracion = binding.etDuracion.text.toString(),
            ubicacion = binding.etLocation.text.toString(),
            descripcion = binding.etDescription.text.toString(),
            presupuesto = presupuesto
        )

        lifecycleScope.launch {
            try {
                _binding?.btnSaveEvent?.isEnabled = false

                val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)
                val res = api.updateEvent(eventToEdit!!.id, request)

                if (!isAdded || _binding == null) return@launch

                if (res.isSuccessful) {
                    Toast.makeText(requireContext(), "Evento actualizado con éxito", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                } else {
                    _binding?.btnSaveEvent?.isEnabled = true
                    Toast.makeText(requireContext(), "Error al actualizar", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                _binding?.btnSaveEvent?.isEnabled = true
                if (context != null) Toast.makeText(requireContext(), "Error de red", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupDatePicker() {
        binding.etFecha.setOnClickListener {
            val cal = Calendar.getInstance()
            // Intentar parsear fecha actual si existe
            val current = binding.etFecha.text.toString()
            if (current.isNotEmpty()) {
                try {
                    val parts = current.split("/")
                    cal.set(parts[2].toInt(), parts[1].toInt() - 1, parts[0].toInt())
                } catch (e: Exception) {}
            }

            DatePickerDialog(requireContext(), { _, y, m, d ->
                binding.etFecha.setText("%02d/%02d/%04d".format(d, m + 1, y))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                datePicker.minDate = System.currentTimeMillis()
            }.show()
        }
    }

    private fun setupTimePicker() {
        binding.etDuracion.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(requireContext(), { _, h1, m1 ->
                TimePickerDialog(requireContext(), { _, h2, m2 ->
                    if (h2 * 60 + m2 <= h1 * 60 + m1) {
                        Toast.makeText(context, "Hora de fin debe ser posterior", Toast.LENGTH_SHORT).show()
                        return@TimePickerDialog
                    }
                    binding.etDuracion.setText("%02d:%02d a %02d:%02d".format(h1, m1, h2, m2))
                }, h1 + 2, m1, true).show()
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }
    }

    private fun cargarCiudadesDesdeAssets() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = requireContext().assets.open("municipios_mexico.json").bufferedReader().use { it.readText() }
                val arr = JSONArray(json)
                val lista = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    lista.add("${o.getString("municipio")}, ${o.getString("estado")}")
                }
                withContext(Dispatchers.Main) {
                    ciudadesDisponibles = lista
                    val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, ciudadesDisponibles)
                    binding.etLocation.setAdapter(adapter)
                }
            } catch (e: Exception) { Log.e("JSON", "Error: ${e.message}") }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}