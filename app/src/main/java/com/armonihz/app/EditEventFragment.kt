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
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Date
import java.util.TimeZone

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

            // 👇 CORRECCIÓN: Transformar la fecha de DB (yyyy-MM-dd) a UI (dd/MM/yyyy)
            try {
                val apiFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val uiFmt = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val dateObj = apiFmt.parse(event.fecha)
                if (dateObj != null) {
                    binding.etFecha.setText(uiFmt.format(dateObj))
                } else {
                    binding.etFecha.setText(event.fecha)
                }
            } catch (e: Exception) {
                binding.etFecha.setText(event.fecha)
            }

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
// ── Pickers Modernos (Material Design) ───────────────────────────────────

    private fun setupDatePicker() {
        binding.etFecha.setOnClickListener {
            val constraintsBuilder = CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())

            // Intentar leer la fecha actual para que el calendario inicie ahí
            var preselectedTime = MaterialDatePicker.todayInUtcMilliseconds()
            val currentText = binding.etFecha.text.toString()
            if (currentText.isNotEmpty()) {
                try {
                    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                    sdf.timeZone = TimeZone.getTimeZone("UTC")
                    val parsedDate = sdf.parse(currentText)
                    if (parsedDate != null && parsedDate.time > preselectedTime) {
                        preselectedTime = parsedDate.time
                    }
                } catch (e: Exception) { }
            }

            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Selecciona la fecha")
                .setCalendarConstraints(constraintsBuilder.build())
                .setSelection(preselectedTime)
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                binding.etFecha.setText(sdf.format(Date(selection)))
            }

            datePicker.show(parentFragmentManager, "MATERIAL_DATE_PICKER")
        }
    }

    private fun setupTimePicker() {
        binding.etDuracion.setOnClickListener {
            val dateText = binding.etFecha.text.toString()

            // 1. Validar que haya fecha
            if (dateText.isBlank()) {
                Toast.makeText(context, "Por favor, selecciona una fecha primero.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Extraer la hora de inicio que ya está en el campo (Ej: "14:00")
            var h1 = 12
            var m1 = 0
            val textDuracion = binding.etDuracion.text.toString()

            if (textDuracion.contains("–") || textDuracion.contains("-")) {
                try {
                    val parts = textDuracion.split(Regex("[–-]"))
                    val startParts = parts[0].trim().split(":")
                    h1 = startParts[0].toInt()
                    m1 = startParts[1].toInt()
                } catch(e: Exception){}
            } else {
                val cal = Calendar.getInstance()
                h1 = cal.get(Calendar.HOUR_OF_DAY)
                m1 = cal.get(Calendar.MINUTE)
            }

            val startPicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(h1)
                .setMinute(m1)
                .setTitleText("Hora de inicio")
                .build()

            startPicker.addOnPositiveButtonClickListener {
                val newH1 = startPicker.hour
                val newM1 = startPicker.minute

                // 2. Validar contra el pasado si es hoy
                if (isSelectedDateToday(dateText)) {
                    val cal = Calendar.getInstance()
                    val currentTotalMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
                    val selectedTotalMinutes = newH1 * 60 + newM1

                    if (selectedTotalMinutes < currentTotalMinutes) {
                        Toast.makeText(context, "No puedes seleccionar una hora en el pasado.", Toast.LENGTH_SHORT).show()
                        return@addOnPositiveButtonClickListener
                    }
                }

                val finHoraInicial = if (newM1 == 59) (newH1 + 1).coerceAtMost(23) else newH1
                val finMinInicial = if (newM1 == 59) 0 else newM1 + 1

                val endPicker = MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setHour(finHoraInicial)
                    .setMinute(finMinInicial)
                    .setTitleText("Hora de fin")
                    .build()

                endPicker.addOnPositiveButtonClickListener {
                    val newH2 = endPicker.hour
                    val newM2 = endPicker.minute

                    val inicioTotal = newH1 * 60 + newM1
                    val finTotal = newH2 * 60 + newM2

                    if (finTotal <= inicioTotal) {
                        Toast.makeText(context, getString(R.string.invalid_end_time), Toast.LENGTH_SHORT).show()
                        return@addOnPositiveButtonClickListener
                    }

                    binding.etDuracion.setText("%02d:%02d - %02d:%02d".format(newH1, newM1, newH2, newM2))
                }
                endPicker.show(parentFragmentManager, "END_TIME_PICKER")
            }
            startPicker.show(parentFragmentManager, "START_TIME_PICKER")
        }
    }

    private fun isSelectedDateToday(dateText: String): Boolean {
        if (dateText.isBlank()) return false
        return try {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            val selectedDate = sdf.parse(dateText) ?: return false

            val calToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val calSelected = Calendar.getInstance().apply {
                time = selectedDate
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            calSelected.timeInMillis == calToday.timeInMillis
        } catch (e: Exception) {
            false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}