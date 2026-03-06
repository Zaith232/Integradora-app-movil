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
import com.armonihz.app.databinding.FragmentAddEventBinding
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.network.model.EventRequest // Asegúrate de que esta ruta coincida con tu paquete de modelos
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.util.Calendar

class AddEventFragment : Fragment() {

    private var _binding: FragmentAddEventBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupSpinner()
        setupDatePicker()

        binding.btnPublish.setOnClickListener {
            publicarEvento()
        }
    }

    private fun setupSpinner() {
        val options = arrayOf("Mariachi", "Banda", "Trío", "Grupo Versátil")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, options)
        binding.spinnerMusicType.adapter = adapter
    }

    private fun setupDatePicker() {
        binding.etFecha.setOnClickListener {
            val calendario = Calendar.getInstance()
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

    private fun publicarEvento() {
        // 1. Recolectar y validar datos
        val titulo = binding.etTitulo.text.toString().trim()
        val tipoMusica = binding.spinnerMusicType.selectedItem.toString()
        val fecha = binding.etFecha.text.toString().trim()
        val duracion = binding.etDuracion.text.toString().trim()
        val ubicacion = binding.etLocation.text.toString().trim()
        val descripcion = binding.etDescription.text.toString().trim()
        val presupuestoStr = binding.etBudget.text.toString().trim()

        if (titulo.isEmpty() || fecha.isEmpty() || ubicacion.isEmpty() || presupuestoStr.isEmpty()) {
            Toast.makeText(context, "Por favor llena los campos obligatorios", Toast.LENGTH_SHORT).show()
            return
        }

        val presupuesto = presupuestoStr.toDoubleOrNull() ?: 0.0
        val nuevoEvento = EventRequest(titulo, tipoMusica, fecha, duracion, ubicacion, descripcion, presupuesto)

        // 2. Verificar usuario autenticado
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(context, "Debes iniciar sesión para publicar", Toast.LENGTH_SHORT).show()
            return
        }

        // Bloquear botón mientras se procesa la solicitud
        binding.btnPublish.isEnabled = false

        // 3. Obtener Token y enviar a la API
        user.getIdToken(true).addOnSuccessListener { result ->
            val token = "Bearer ${result.token}"
            val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

            lifecycleScope.launch {
                try {
                    val response = api.createEvent(token, nuevoEvento)
                    if (response.isSuccessful) {
                        Toast.makeText(context, "¡Evento publicado con éxito!", Toast.LENGTH_SHORT).show()

                        // Cierra este fragmento y regresa a la lista de eventos
                        parentFragmentManager.popBackStack()
                    } else {
                        Toast.makeText(context, "Error al publicar: ${response.code()}", Toast.LENGTH_SHORT).show()
                        binding.btnPublish.isEnabled = true
                    }
                } catch (e: Exception) {
                    Log.e("API_ERROR", "Error de red: ${e.message}")
                    Toast.makeText(context, "Error de conexión", Toast.LENGTH_SHORT).show()
                    binding.btnPublish.isEnabled = true
                }
            }
        }.addOnFailureListener {
            Toast.makeText(context, "Error de autenticación con Firebase", Toast.LENGTH_SHORT).show()
            binding.btnPublish.isEnabled = true
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}