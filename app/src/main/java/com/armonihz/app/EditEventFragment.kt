package com.armonihz.app

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.armonihz.app.databinding.FragmentEditEventBinding
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.network.model.EventRequest
import com.armonihz.app.network.model.EventResponse
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Calendar

class EditEventFragment : Fragment() {

    private var _binding: FragmentEditEventBinding? = null
    private val binding get() = _binding!!

    private var eventToEdit: EventResponse? = null

    private val musicTypes = arrayOf(
        "Mariachi",
        "Norteño",
        "Banda",
        "Grupo Versátil",
        "Trío",
        "DJ",
        "Solista",
        "Otro"
    )

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

        setupSpinner()
        setupDatePicker() // ⬅️ Inicializamos el calendario
        prefillData()

        binding.btnSaveEvent.setOnClickListener {
            updateEvent()
        }
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            musicTypes
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerMusicType.adapter = adapter
    }

    // ⬅️ FUNCIÓN DEL CALENDARIO AGREGADA
    private fun setupDatePicker() {
        binding.etFecha.setOnClickListener {
            val calendario = Calendar.getInstance()

            // Opcional: Si quieres que el calendario se abra en la fecha que ya estaba guardada,
            // requeriría un poco de lógica para separar el String (DD/MM/AAAA),
            // pero usar la fecha actual como punto de partida funciona perfecto.
            val year = calendario.get(Calendar.YEAR)
            val month = calendario.get(Calendar.MONTH)
            val day = calendario.get(Calendar.DAY_OF_MONTH)

            val datePicker = DatePickerDialog(
                requireContext(),
                { _, selectedYear, selectedMonth, selectedDay ->
                    val fechaSeleccionada = String.format(
                        "%02d/%02d/%04d",
                        selectedDay,
                        selectedMonth + 1,
                        selectedYear
                    )
                    binding.etFecha.setText(fechaSeleccionada)
                },
                year,
                month,
                day
            )
            datePicker.datePicker.minDate = System.currentTimeMillis()
            datePicker.show()
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

            val spinnerPosition = musicTypes.indexOf(event.tipoMusica)
            if (spinnerPosition >= 0) {
                binding.spinnerMusicType.setSelection(spinnerPosition)
            } else {
                binding.spinnerMusicType.setSelection(musicTypes.size - 1)
            }
        }
    }

    private fun updateEvent() {
        val titulo = binding.etTitulo.text.toString().trim()
        val fecha = binding.etFecha.text.toString().trim()
        val duracion = binding.etDuracion.text.toString().trim()
        val tipoMusica = binding.spinnerMusicType.selectedItem.toString()
        val ubicacion = binding.etLocation.text.toString().trim()
        val presupuestoStr = binding.etBudget.text.toString().trim()
        val descripcion = binding.etDescription.text.toString().trim()

        if (titulo.isEmpty() || fecha.isEmpty() || presupuestoStr.isEmpty()) {
            Toast.makeText(context, "Por favor, llena los campos principales", Toast.LENGTH_SHORT).show()
            return
        }

        // Bloqueamos el botón para evitar múltiples clics
        binding.btnSaveEvent.isEnabled = false

        val request = EventRequest(
            titulo = titulo,
            tipoMusica = tipoMusica,
            fecha = fecha,
            duracion = duracion,
            ubicacion = ubicacion,
            descripcion = descripcion,
            presupuesto = presupuestoStr.toDoubleOrNull() ?: 0.0
        )

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            binding.btnSaveEvent.isEnabled = true
            return
        }

        user.getIdToken(true).addOnSuccessListener { result ->
            val token = "Bearer ${result.token}"
            val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

            lifecycleScope.launch {
                try {
                    val response = api.updateEvent(token, eventToEdit!!.id, request)
                    if (response.isSuccessful) {
                        Toast.makeText(context, "Evento actualizado", Toast.LENGTH_SHORT).show()
                        parentFragmentManager.popBackStack()
                    } else {
                        Toast.makeText(context, "Error al actualizar", Toast.LENGTH_SHORT).show()
                        Log.e("API_ERROR", "Error: ${response.code()}")
                        binding.btnSaveEvent.isEnabled = true
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Error de conexión", Toast.LENGTH_SHORT).show()
                    Log.e("API_ERROR", "Exception: ${e.message}")
                    binding.btnSaveEvent.isEnabled = true
                }
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Error de autenticación", Toast.LENGTH_SHORT).show()
            binding.btnSaveEvent.isEnabled = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}