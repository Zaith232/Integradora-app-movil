package com.armonihz.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.armonihz.app.databinding.FragmentAddEventBinding
import android.app.DatePickerDialog
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import java.util.Calendar
import androidx.lifecycle.lifecycleScope
import android.util.Log
import kotlinx.coroutines.launch

class AddEventFragment : Fragment() {


    private var _binding: FragmentAddEventBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEventBinding.inflate(inflater, container, false)

        setupSpinner()

        // 🔹 PASO 2: DatePicker para la fecha
        binding.etFecha.setOnClickListener {

            val calendario = Calendar.getInstance()

            val year = calendario.get(Calendar.YEAR)
            val month = calendario.get(Calendar.MONTH)
            val day = calendario.get(Calendar.DAY_OF_MONTH)

            val datePicker = DatePickerDialog(
                requireContext(),
                { _, selectedYear, selectedMonth, selectedDay ->

                    // Formato DD/MM/YYYY
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

            // Evita seleccionar fechas pasadas
            datePicker.datePicker.minDate = System.currentTimeMillis()

            datePicker.show()
        }

        binding.btnPublish.setOnClickListener {
            Toast.makeText(context, "Solicitud publicada con éxito", Toast.LENGTH_SHORT).show()
        }

        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        lifecycleScope.launch {
            try {
                val response = api.getTest()
                Log.d("API_TEST", response.body()?.message ?: "null")
            } catch (e: Exception) {
                Log.e("API_TEST", e.message ?: "error")
            }
        }

        return binding.root


    }

    private fun setupSpinner() {
        val options = arrayOf("Mariachi", "Banda", "Trío", "Grupo Versátil")
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, options)
        binding.spinnerMusicType.adapter = adapter
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }


}