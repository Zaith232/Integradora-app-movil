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
import com.armonihz.app.databinding.FragmentAddEventBinding
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.network.model.EventRequest
import com.armonihz.app.network.model.Genre
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.util.Calendar

class AddEventFragment : Fragment() {

    private var _binding: FragmentAddEventBinding? = null
    private val binding get() = _binding!!

    private var ciudadesDisponibles: List<String> = emptyList()
    private var genresList: List<Genre> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        cargarGeneros()
        setupDatePicker()
        setupTimePicker()
        setupFormValidation()
        cargarCiudadesDesdeAssets()

        binding.btnPublish.setOnClickListener { publicarEvento() }
        binding.btnBackAddEvent.setOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun setupFormValidation() {
        binding.btnPublish.isEnabled = false

        binding.etTitulo.addTextChangedListener { validarCampos() }
        binding.etFecha.addTextChangedListener { validarCampos() }
        binding.etDuracion.addTextChangedListener { validarCampos() }
        binding.etLocation.addTextChangedListener { validarCampos() }
        binding.etBudget.addTextChangedListener { validarCampos() }
    }

    private fun validarCampos() {
        binding.btnPublish.isEnabled =
            binding.etTitulo.text!!.isNotEmpty() &&
                    binding.etFecha.text!!.isNotEmpty() &&
                    binding.etDuracion.text!!.isNotEmpty() &&
                    binding.etLocation.text!!.isNotEmpty() &&
                    binding.etBudget.text!!.isNotEmpty()
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
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error al cargar géneros", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupTimePicker() {
        binding.etDuracion.setOnClickListener {
            val cal = Calendar.getInstance()

            TimePickerDialog(requireContext(), { _, h1, m1 ->
                TimePickerDialog(requireContext(), { _, h2, m2 ->

                    if (h2 * 60 + m2 <= h1 * 60 + m1) {
                        Toast.makeText(context, "Hora inválida", Toast.LENGTH_SHORT).show()
                        return@TimePickerDialog
                    }

                    binding.etDuracion.setText(
                        "%02d:%02d a %02d:%02d".format(h1, m1, h2, m2)
                    )
                }, h1 + 2, m1, true).show()

            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }
    }

    private fun setupDatePicker() {
        binding.etFecha.setOnClickListener {
            val cal = Calendar.getInstance()

            DatePickerDialog(requireContext(), { _, y, m, d ->
                binding.etFecha.setText("%02d/%02d/%04d".format(d, m + 1, y))
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).apply {
                datePicker.minDate = System.currentTimeMillis()
            }.show()
        }
    }

    private fun cargarCiudadesDesdeAssets() {
        lifecycleScope.launch(Dispatchers.IO) {
            val json = requireContext().assets.open("municipios_mexico.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(json)
            val lista = mutableListOf<String>()

            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                lista.add("${o.getString("municipio")}, ${o.getString("estado")}")
            }

            withContext(Dispatchers.Main) {
                ciudadesDisponibles = lista
                val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, lista)
                binding.etLocation.setAdapter(adapter)
            }
        }
    }

    private fun publicarEvento() {
        val presupuesto = binding.etBudget.text.toString().toDoubleOrNull() ?: 0.0

        if (presupuesto <= 0) {
            Toast.makeText(context, "Presupuesto inválido", Toast.LENGTH_SHORT).show()
            return
        }

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

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(context, "No autenticado", Toast.LENGTH_SHORT).show()
            return
        }

        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        lifecycleScope.launch {
            try {
                // Deshabilitar botón al iniciar para evitar el doble clic
                _binding?.btnPublish?.isEnabled = false

                val res = api.createEvent(request)

                // IMPORTANTE: Verificar si el binding sigue vivo antes de continuar
                val currentBinding = _binding ?: return@launch

                if (res.isSuccessful) {
                    Toast.makeText(context, "Evento creado", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                } else {
                    currentBinding.btnPublish.isEnabled = true
                    Toast.makeText(context, "Error en el servidor", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Verificar binding otra vez en el catch
                _binding?.btnPublish?.isEnabled = true
                if (context != null) {
                    Toast.makeText(context, "Error de red", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}