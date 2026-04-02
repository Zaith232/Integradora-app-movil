package com.armonihz.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.armonihz.app.databinding.FragmentEditEventBinding
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.network.model.EventRequest
import com.armonihz.app.network.model.EventResponse
import com.armonihz.app.viewmodel.AddEventUiState
import com.armonihz.app.viewmodel.EditEventViewModel
import com.armonihz.app.viewmodel.EditEventViewModelFactory
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class EditEventFragment : Fragment() {

    private var _binding: FragmentEditEventBinding? = null
    private val binding get() = _binding!!

    private var eventToEdit: EventResponse? = null

    companion object {
        private const val ARG_EVENT = "event_data"

        fun newInstance(event: EventResponse): EditEventFragment {
            return EditEventFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_EVENT, event)
                }
            }
        }
    }

    private val api: ApiService by lazy {
        RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)
    }

    private val viewModel: EditEventViewModel by viewModels {
        EditEventViewModelFactory(api)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        eventToEdit = arguments?.getSerializable(ARG_EVENT) as? EventResponse
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEditEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.cargarCiudades(requireContext().assets)

        setupDatePicker()
        setupTimePicker()
        setupFormValidation()
        prefillData()
        observarViewModel()

        binding.btnSaveEvent.setOnClickListener { intentarActualizar() }
        binding.btnBackEditEvent.setOnClickListener { parentFragmentManager.popBackStack() }
    }

    // ── Observers ────────────────────────────────────────────────────────────

    private fun observarViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.genres.collect { genres ->
                        val nombres = genres.map { it.name }
                        val adapter = ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_spinner_dropdown_item,
                            nombres
                        )
                        binding.spinnerMusicType.adapter = adapter

                        // Seleccionar el género actual del evento
                        eventToEdit?.let { event ->
                            val index = genres.indexOfFirst { it.name == event.tipoMusica }
                            if (index >= 0) binding.spinnerMusicType.setSelection(index)
                        }
                    }
                }

                launch {
                    viewModel.ciudades.collect { ciudades ->
                        val adapter = ArrayAdapter(
                            requireContext(),
                            android.R.layout.simple_dropdown_item_1line,
                            ciudades
                        )
                        binding.etLocation.setAdapter(adapter)
                    }
                }

                launch {
                    viewModel.uiState.collect { state ->
                        when (state) {
                            is AddEventUiState.Idle -> setUiEnabled(true)
                            is AddEventUiState.Loading -> setUiEnabled(false)
                            is AddEventUiState.Success -> {
                                Toast.makeText(
                                    context,
                                    getString(R.string.event_updated),
                                    Toast.LENGTH_SHORT
                                ).show()
                                parentFragmentManager.popBackStack()
                            }
                            is AddEventUiState.Error -> {
                                setUiEnabled(true)
                                Toast.makeText(context, state.message, Toast.LENGTH_LONG).show()
                                viewModel.resetState()
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Pre-fill ─────────────────────────────────────────────────────────────

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

    // ── Validación ───────────────────────────────────────────────────────────

    private fun setupFormValidation() {
        binding.btnSaveEvent.isEnabled = false
        listOf(
            binding.etTitulo,
            binding.etFecha,
            binding.etDuracion,
            binding.etLocation,
            binding.etBudget
        ).forEach { it.addTextChangedListener { validarCampos() } }
    }

    private fun validarCampos() {
        binding.btnSaveEvent.isEnabled =
            binding.etTitulo.text!!.isNotBlank() &&
                    binding.etFecha.text!!.isNotBlank() &&
                    binding.etDuracion.text!!.isNotBlank() &&
                    binding.etLocation.text!!.isNotBlank() &&
                    binding.etBudget.text!!.isNotBlank() &&
                    viewModel.genres.value.isNotEmpty()
    }

    // ── Actualizar ───────────────────────────────────────────────────────────

    private fun intentarActualizar() {
        val event = eventToEdit ?: return

        val presupuesto = binding.etBudget.text.toString().toDoubleOrNull()
        if (presupuesto == null || presupuesto <= 0) {
            Toast.makeText(context, getString(R.string.invalid_budget), Toast.LENGTH_SHORT).show()
            return
        }

        val genres = viewModel.genres.value
        val selectedPosition = binding.spinnerMusicType.selectedItemPosition
        if (selectedPosition < 0 || selectedPosition >= genres.size) {
            Toast.makeText(context, getString(R.string.genres_not_loaded), Toast.LENGTH_SHORT).show()
            return
        }

        val fechaISO = convertirFechaAISO(binding.etFecha.text.toString())
            ?: run {
                Toast.makeText(context, getString(R.string.invalid_date), Toast.LENGTH_SHORT).show()
                return
            }

        val request = EventRequest(
            titulo = binding.etTitulo.text.toString().trim(),
            genre_id = genres[selectedPosition].id,
            fecha = fechaISO,
            duracion = binding.etDuracion.text.toString(),
            ubicacion = binding.etLocation.text.toString().trim(),
            descripcion = binding.etDescription.text?.toString()?.trim() ?: "",
            presupuesto = presupuesto
        )

        viewModel.actualizarEvento(event.id, request)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun setUiEnabled(enabled: Boolean) {
        binding.btnSaveEvent.isEnabled = enabled
        binding.loader.root.isVisible = !enabled
        binding.etTitulo.isEnabled = enabled
        binding.etFecha.isEnabled = enabled
        binding.etDuracion.isEnabled = enabled
        binding.etLocation.isEnabled = enabled
        binding.etBudget.isEnabled = enabled
        binding.etDescription.isEnabled = enabled
        binding.spinnerMusicType.isEnabled = enabled
    }

    /** dd/MM/yyyy → yyyy-MM-dd */
    private fun convertirFechaAISO(fecha: String): String? {
        return try {
            val inputFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val outputFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val date = inputFmt.parse(fecha) ?: return null
            outputFmt.format(date)
        } catch (e: Exception) {
            null
        }
    }

    // ── Pickers ──────────────────────────────────────────────────────────────

    private fun setupDatePicker() {
        binding.etFecha.setOnClickListener {
            val cal = Calendar.getInstance()

            // Pre-poblar el calendar con la fecha actual del campo si existe
            val current = binding.etFecha.text.toString()
            if (current.isNotEmpty()) {
                try {
                    val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    val parsed = fmt.parse(current)
                    if (parsed != null) cal.time = parsed
                } catch (e: Exception) { /* ignorar, usar fecha actual */ }
            }

            DatePickerDialog(
                requireContext(),
                { _, y, m, d ->
                    binding.etFecha.setText("%02d/%02d/%04d".format(d, m + 1, y))
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH)
            ).apply {
                datePicker.minDate = System.currentTimeMillis()
            }.show()
        }
    }

    private fun setupTimePicker() {
        binding.etDuracion.setOnClickListener {
            val cal = Calendar.getInstance()
            val horaActual = cal.get(Calendar.HOUR_OF_DAY)
            val minActual = cal.get(Calendar.MINUTE)

            TimePickerDialog(requireContext(), { _, h1, m1 ->
                val finHoraInicial = if (m1 == 59) (h1 + 1).coerceAtMost(23) else h1
                val finMinInicial = if (m1 == 59) 0 else m1 + 1

                TimePickerDialog(requireContext(), { _, h2, m2 ->
                    if (h2 * 60 + m2 <= h1 * 60 + m1) {
                        Toast.makeText(
                            context,
                            getString(R.string.invalid_end_time),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@TimePickerDialog
                    }
                    binding.etDuracion.setText(
                        "%02d:%02d – %02d:%02d".format(h1, m1, h2, m2)
                    )
                }, finHoraInicial, finMinInicial, true).show()

            }, horaActual, minActual, true).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}