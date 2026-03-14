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
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
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

        setupSpinner()
        setupDatePicker()
        setupTimePicker()

        setupFormValidation()
        prefillData()

        cargarCiudadesDesdeAssets()

        binding.btnSaveEvent.setOnClickListener {
            updateEvent()
        }

        binding.btnBackEditEvent.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun setupFormValidation() {
        binding.btnSaveEvent.isEnabled = false

        binding.etTitulo.addTextChangedListener { validarCamposObligatorios() }
        binding.etFecha.addTextChangedListener { validarCamposObligatorios() }
        binding.etDuracion.addTextChangedListener { validarCamposObligatorios() }
        binding.etLocation.addTextChangedListener { validarCamposObligatorios() }
        binding.etBudget.addTextChangedListener { validarCamposObligatorios() }
    }

    private fun validarCamposObligatorios() {
        val titulo = binding.etTitulo.text.toString().trim()
        val fecha = binding.etFecha.text.toString().trim()
        val duracion = binding.etDuracion.text.toString().trim()
        val ubicacion = binding.etLocation.text.toString().trim()
        val presupuesto = binding.etBudget.text.toString().trim()

        binding.btnSaveEvent.isEnabled = titulo.isNotEmpty() &&
                fecha.isNotEmpty() &&
                duracion.isNotEmpty() &&
                ubicacion.isNotEmpty() &&
                presupuesto.isNotEmpty()
    }

    private fun cargarCiudadesDesdeAssets() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream = requireContext().assets.open("municipios_mexico.json")
                val size = inputStream.available()
                val buffer = ByteArray(size)
                inputStream.read(buffer)
                inputStream.close()

                val jsonString = String(buffer, Charsets.UTF_8)
                val jsonArray = JSONArray(jsonString)

                val listaTemporal = mutableListOf<String>()

                for (i in 0 until jsonArray.length()) {
                    val objetoCiudad = jsonArray.getJSONObject(i)
                    val municipio = objetoCiudad.getString("municipio")
                    val estado = objetoCiudad.getString("estado")

                    listaTemporal.add("$municipio, $estado")
                }

                withContext(Dispatchers.Main) {
                    ciudadesDisponibles = listaTemporal
                    setupLocationAutoComplete()
                }
            } catch (e: Exception) {
                Log.e("CIUDADES", "Error al cargar el archivo JSON: ${e.message}")
            }
        }
    }

    private fun setupLocationAutoComplete() {
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            ciudadesDisponibles
        )
        binding.etLocation.setAdapter(adapter)

        binding.etLocation.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val textoIngresado = binding.etLocation.text.toString()
                if (textoIngresado.isNotEmpty() && !ciudadesDisponibles.contains(textoIngresado)) {
                    binding.etLocation.setText("")
                    Toast.makeText(context, "Por favor, selecciona una ciudad de la lista", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupTimePicker() {
        binding.etDuracion.setOnClickListener {
            val calendario = Calendar.getInstance()
            val horaActual = calendario.get(Calendar.HOUR_OF_DAY)
            val minutoActual = calendario.get(Calendar.MINUTE)

            val timePickerInicio = TimePickerDialog(
                requireContext(),
                { _, horaInicio, minutoInicio ->

                    val timePickerFin = TimePickerDialog(
                        requireContext(),
                        { _, horaFin, minutoFin ->

                            val inicioMin = horaInicio * 60 + minutoInicio
                            val finMin = horaFin * 60 + minutoFin

                            if (finMin <= inicioMin) {
                                Toast.makeText(
                                    context,
                                    "La hora de finalización debe ser mayor que la de inicio",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@TimePickerDialog
                            }

                            val horaInicioStr = String.format("%02d:%02d", horaInicio, minutoInicio)
                            val horaFinStr = String.format("%02d:%02d", horaFin, minutoFin)

                            binding.etDuracion.setText("$horaInicioStr a $horaFinStr")
                        },
                        horaInicio + 2,
                        minutoInicio,
                        true
                    )
                    timePickerFin.setTitle("Selecciona a qué hora termina")
                    timePickerFin.show()

                },
                horaActual,
                minutoActual,
                true
            )
            timePickerInicio.setTitle("Selecciona a qué hora empieza")
            timePickerInicio.show()
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

    private fun setupDatePicker() {
        binding.etFecha.setOnClickListener {
            val calendario = Calendar.getInstance()

            val fechaGuardada = binding.etFecha.text.toString()

            if (fechaGuardada.isNotEmpty() && fechaGuardada.contains("/")) {
                try {
                    val partes = fechaGuardada.split("/")
                    if (partes.size == 3) {
                        val day = partes[0].toInt()
                        val month = partes[1].toInt() - 1
                        val year = partes[2].toInt()

                        calendario.set(year, month, day)
                    }
                } catch (e: Exception) {
                    Log.e("DatePicker", "Error al parsear la fecha guardada: ${e.message}")
                }
            }

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

    private fun showLoader() {
        binding.loader.root.visibility = View.VISIBLE
    }

    private fun hideLoader() {
        binding.loader.root.visibility = View.GONE
    }

    private fun updateEvent() {
        val titulo = binding.etTitulo.text.toString().trim()
        val fecha = binding.etFecha.text.toString().trim()
        val duracion = binding.etDuracion.text.toString().trim()
        val tipoMusica = binding.spinnerMusicType.selectedItem.toString()
        val ubicacion = binding.etLocation.text.toString().trim()
        val presupuestoStr = binding.etBudget.text.toString().trim()
        val descripcion = binding.etDescription.text.toString().trim()

        if (titulo.isEmpty() || fecha.isEmpty() || ubicacion.isEmpty() || presupuestoStr.isEmpty() || duracion.isEmpty()) {
            return
        }

        val presupuesto = presupuestoStr.toDoubleOrNull() ?: 0.0
        if (presupuesto < 500.0) {
            Toast.makeText(context, "El presupuesto mínimo debe ser de $500", Toast.LENGTH_SHORT).show()
            return
        }
        if (presupuesto > 100000.0) {
            Toast.makeText(context, "El presupuesto máximo permitido es $100,000", Toast.LENGTH_SHORT).show()
            return
        }

        binding.btnSaveEvent.isEnabled = false
        showLoader()

        val request = EventRequest(
            titulo = titulo,
            tipoMusica = tipoMusica,
            fecha = fecha,
            duracion = duracion,
            ubicacion = ubicacion,
            descripcion = descripcion,
            presupuesto = presupuesto
        )

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            binding.btnSaveEvent.isEnabled = true
            hideLoader()
            return
        }

        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        lifecycleScope.launch {
            try {
                // ⬅️ Se eliminó la petición a Firebase y el envío del token
                val response = api.updateEvent(eventToEdit!!.id, request)
                if (response.isSuccessful) {
                    Toast.makeText(context, "Evento actualizado con éxito", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                } else {
                    Toast.makeText(context, "Error al actualizar: ${response.code()}", Toast.LENGTH_SHORT).show()
                    Log.e("API_ERROR", "Error: ${response.code()}")
                    binding.btnSaveEvent.isEnabled = true
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error de conexión", Toast.LENGTH_SHORT).show()
                Log.e("API_ERROR", "Exception: ${e.message}")
                binding.btnSaveEvent.isEnabled = true
            } finally {
                hideLoader()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}