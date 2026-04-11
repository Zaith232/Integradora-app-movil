package com.armonihz.app

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.armonihz.app.databinding.FragmentAddEventBinding
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.network.model.EventRequest
import com.armonihz.app.viewmodel.AddEventUiState
import com.armonihz.app.viewmodel.AddEventViewModel
import com.armonihz.app.viewmodel.AddEventViewModelFactory
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

class AddEventFragment : Fragment() {

    private var _binding: FragmentAddEventBinding? = null
    private val binding get() = _binding!!

    private val api: ApiService by lazy {
        RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)
    }

    private val viewModel: AddEventViewModel by viewModels {
        AddEventViewModelFactory(api)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAddEventBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.cargarCiudades(requireContext().assets)

        setupDatePicker()
        setupTimePicker()
        setupFormValidation()
        observarViewModel()

        binding.btnPublish.setOnClickListener { intentarPublicar() }
        binding.btnBackAddEvent.setOnClickListener { parentFragmentManager.popBackStack() }
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
                                Toast.makeText(context, getString(R.string.event_created), Toast.LENGTH_SHORT).show()
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

    // ── Validación ───────────────────────────────────────────────────────────

    private fun setupFormValidation() {
        binding.btnPublish.isEnabled = false
        listOf(
            binding.etTitulo,
            binding.etFecha,
            binding.etDuracion,
            binding.etLocation,
            binding.etBudget
        ).forEach { it.addTextChangedListener { validarCampos() } }
    }

    private fun validarCampos() {
        binding.btnPublish.isEnabled =
            binding.etTitulo.text!!.isNotBlank() &&
                    binding.etFecha.text!!.isNotBlank() &&
                    binding.etDuracion.text!!.isNotBlank() &&
                    binding.etLocation.text!!.isNotBlank() &&
                    binding.etBudget.text!!.isNotBlank() &&
                    viewModel.genres.value.isNotEmpty()
    }

    // ── Publicar ─────────────────────────────────────────────────────────────

    private fun intentarPublicar() {
        val presupuesto = binding.etBudget.text.toString().toDoubleOrNull()
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (presupuesto == null || presupuesto <= 0) {
            Toast.makeText(context, getString(R.string.invalid_budget), Toast.LENGTH_SHORT).show()
            return
        }

        val selectedPosition = binding.spinnerMusicType.selectedItemPosition
        val genres = viewModel.genres.value
        if (selectedPosition < 0 || selectedPosition >= genres.size) {
            Toast.makeText(context, getString(R.string.genres_not_loaded), Toast.LENGTH_SHORT).show()
            return
        }

        // Convertir fecha de dd/MM/yyyy → yyyy-MM-dd (ISO 8601)
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
            presupuesto = presupuesto,
            email = user?.email,           // NUEVO: Agrega el correo si existe
            telefono = user?.phoneNumber   // NUEVO: Agrega el teléfono si existe
        )

        viewModel.publicarEvento(request)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun setUiEnabled(enabled: Boolean) {
        binding.btnPublish.isEnabled = enabled
        binding.loader.root.visibility = if (enabled) View.GONE else View.VISIBLE
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

    // ── Pickers Modernos (Material Design) ───────────────────────────────────

    private fun setupDatePicker() {
        binding.etFecha.setOnClickListener {
            // 1. Restricción para que no se puedan elegir fechas pasadas
            val constraintsBuilder = CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now())

            // 2. Construir el calendario Material
            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Selecciona la fecha")
                .setCalendarConstraints(constraintsBuilder.build())
                .setSelection(MaterialDatePicker.todayInUtcMilliseconds())
                .build()

            // 3. Escuchar la selección y formatear
            datePicker.addOnPositiveButtonClickListener { selection ->
                // MaterialDatePicker trabaja en UTC internamente, por lo que ajustamos el timezone
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val dateString = sdf.format(Date(selection))

                binding.etFecha.setText(dateString)
            }

            datePicker.show(parentFragmentManager, "MATERIAL_DATE_PICKER")
        }
    }

    private fun setupTimePicker() {
        binding.etDuracion.setOnClickListener {
            val dateText = binding.etFecha.text.toString()

            // 1. Validar que primero se haya elegido una fecha
            if (dateText.isBlank()) {
                Toast.makeText(context, "Por favor, selecciona una fecha primero.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val cal = Calendar.getInstance()
            val horaActual = cal.get(Calendar.HOUR_OF_DAY)
            val minActual = cal.get(Calendar.MINUTE)

            // Crear el reloj para la Hora de Inicio
            val startPicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H)
                .setHour(horaActual)
                .setMinute(minActual)
                .setTitleText("Hora de inicio")
                .build()

            startPicker.addOnPositiveButtonClickListener {
                val h1 = startPicker.hour
                val m1 = startPicker.minute

                // 2. Validar que la hora no sea en el pasado si el evento es hoy
                if (isSelectedDateToday(dateText)) {
                    val currentTotalMinutes = horaActual * 60 + minActual
                    val selectedTotalMinutes = h1 * 60 + m1

                    if (selectedTotalMinutes < currentTotalMinutes) {
                        Toast.makeText(context, "No puedes seleccionar una hora en el pasado.", Toast.LENGTH_SHORT).show()
                        return@addOnPositiveButtonClickListener // Detiene el flujo
                    }
                }

                // Calcular hora inicial sugerida para el fin (+1 minuto)
                val finHoraInicial = if (m1 == 59) (h1 + 1).coerceAtMost(23) else h1
                val finMinInicial = if (m1 == 59) 0 else m1 + 1

                // Crear el reloj para la Hora de Fin
                val endPicker = MaterialTimePicker.Builder()
                    .setTimeFormat(TimeFormat.CLOCK_12H)
                    .setHour(finHoraInicial)
                    .setMinute(finMinInicial)
                    .setTitleText("Hora de fin")
                    .build()

                endPicker.addOnPositiveButtonClickListener {
                    val h2 = endPicker.hour
                    val m2 = endPicker.minute

                    val inicioTotal = h1 * 60 + m1
                    val finTotal = h2 * 60 + m2

                    // Validar que la hora de fin sea mayor a la de inicio
                    if (finTotal <= inicioTotal) {
                        Toast.makeText(
                            context,
                            getString(R.string.invalid_end_time),
                            Toast.LENGTH_SHORT
                        ).show()
                        return@addOnPositiveButtonClickListener
                    }

                    // Escribir en el EditText
                    binding.etDuracion.setText("%02d:%02d - %02d:%02d".format(h1, m1, h2, m2))
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