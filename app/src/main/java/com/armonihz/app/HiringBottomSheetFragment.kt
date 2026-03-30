package com.armonihz.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.network.model.HiringRequestPayload
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar // Añadimos Calendar para manejar la conversión de AM/PM
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointForward

class HiringBottomSheetFragment(private val musicianId: Int) : BottomSheetDialogFragment() {

    // Formato 24H para la base de datos
    private var selectedDate: String = ""
    private var selectedStartTime: String = ""
    private var selectedEndTime: String = ""

    // Formato 12H con AM/PM para mostrarle al usuario
    private var displayStartTime: String = ""
    private var displayEndTime: String = ""

    private var ocupados: List<RangoOcupado> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_hiring_bottom_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val btnPickDate = view.findViewById<MaterialButton>(R.id.btnPickDate)
        val btnStartTime = view.findViewById<MaterialButton>(R.id.btnStartTime)
        val btnEndTime = view.findViewById<MaterialButton>(R.id.btnEndTime)
        val etLocation = view.findViewById<TextInputEditText>(R.id.etLocation)
        val etDescription = view.findViewById<TextInputEditText>(R.id.etDescription)
        val etBudget = view.findViewById<TextInputEditText>(R.id.etBudget)
        val btnSubmit = view.findViewById<MaterialButton>(R.id.btnSubmitHiring)

        cargarDisponibilidad()

        btnPickDate.setOnClickListener {
            // Creamos una restricción para que solo se puedan seleccionar fechas de hoy en adelante
            val constraintsBuilder = CalendarConstraints.Builder()
                .setValidator(DateValidatorPointForward.now()) // 🔥 Esta es la clave

            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Selecciona la fecha")
                .setCalendarConstraints(constraintsBuilder.build()) // Aplicamos la restricción al DatePicker
                .build()

            datePicker.addOnPositiveButtonClickListener { selection ->
                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                selectedDate = sdf.format(Date(selection))
                btnPickDate.text = selectedDate
            }
            datePicker.show(parentFragmentManager, "DATE_PICKER")
        }

        btnStartTime.setOnClickListener {
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H) // 🔥 CAMBIADO A 12H
                .setTitleText("Hora de inicio")
                .build()

            timePicker.addOnPositiveButtonClickListener {
                // 1. Guardamos formato 24H para Laravel
                selectedStartTime = String.format(Locale.getDefault(), "%02d:%02d:00", timePicker.hour, timePicker.minute)

                // 2. Formateamos a AM/PM para el botón
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, timePicker.hour)
                cal.set(Calendar.MINUTE, timePicker.minute)
                displayStartTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)

                btnStartTime.text = "Inicio: $displayStartTime"
            }
            timePicker.show(parentFragmentManager, "START_TIME")
        }

        btnEndTime.setOnClickListener {
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_12H) // 🔥 CAMBIADO A 12H
                .setTitleText("Hora de fin")
                .build()

            timePicker.addOnPositiveButtonClickListener {
                // 1. Guardamos formato 24H para Laravel
                selectedEndTime = String.format(Locale.getDefault(), "%02d:%02d:00", timePicker.hour, timePicker.minute)

                // 2. Formateamos a AM/PM para el botón
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, timePicker.hour)
                cal.set(Calendar.MINUTE, timePicker.minute)
                displayEndTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(cal.time)

                btnEndTime.text = "Fin: $displayEndTime"
            }
            timePicker.show(parentFragmentManager, "END_TIME")
        }

        btnSubmit.setOnClickListener {
            if (selectedDate.isEmpty() || selectedStartTime.isEmpty() || selectedEndTime.isEmpty()) {
                Toast.makeText(context, "Selecciona fecha, hora de inicio y fin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val budgetStr = etBudget.text.toString()
            if (budgetStr.isEmpty()) {
                etBudget.error = "Requerido"
                return@setOnClickListener
            }

            var endDateForDatabase = selectedDate
            if (selectedEndTime < selectedStartTime) {
                try {
                    val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val parsedDate = sdfDate.parse(selectedDate)
                    if (parsedDate != null) {
                        val cal = Calendar.getInstance()
                        cal.time = parsedDate
                        cal.add(Calendar.DAY_OF_YEAR, 1)
                        endDateForDatabase = sdfDate.format(cal.time)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val usuarioInicioStr = "$selectedDate $selectedStartTime"
            val usuarioFinStr = "$endDateForDatabase $selectedEndTime"

            val choque = revisarSiHayChoque(usuarioInicioStr, usuarioFinStr)

            if (choque != null) {
                // 🔥 El mensaje de error ahora usa horaInicio y horaFin con AM/PM
                Toast.makeText(
                    context,
                    "El músico ya tiene un evento ese día de ${choque.horaInicio} a ${choque.horaFin}. Por favor, elige otro horario.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            // 🔥 La descripción que verá el músico ahora incluye AM/PM
            val finalDescription = "⏰ Horario: $displayStartTime a $displayEndTime\n\n" + etLocation.text.toString() + "\n\n" + etDescription.text.toString()

            val payload = HiringRequestPayload(
                musician_profile_id = musicianId,
                event_date = usuarioInicioStr,
                end_time = usuarioFinStr,
                event_location = etLocation.text.toString(),
                description = finalDescription,
                budget = budgetStr.toDouble()
            )

            enviarSolicitud(payload, btnSubmit)
        }
    }

    private fun cargarDisponibilidad() {
        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)
        lifecycleScope.launch {
            try {
                val response = api.getMusicianAvailability(musicianId)
                if (response.isSuccessful && response.body() != null) {
                    val busyDatesFromApi = response.body()!!.data
                    val listaOcupados = mutableListOf<RangoOcupado>()

                    val sdfBackend = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    // 🔥 NUEVO: Dile a Android que no reste ni sume horas por la zona horaria UTC
                    sdfBackend.timeZone = TimeZone.getTimeZone("UTC")

                    val sdfSoloHoraAmPm = SimpleDateFormat("hh:mm a", Locale.getDefault())
                    // Y también le decimos al de salida que use la misma referencia
                    sdfSoloHoraAmPm.timeZone = TimeZone.getTimeZone("UTC")

                    for (busy in busyDatesFromApi) {
                        try {
                            val inicioDate = sdfBackend.parse(busy.start)
                            val finDate = sdfBackend.parse(busy.end ?: busy.start)

                            if (inicioDate != null && finDate != null) {
                                val horaInicio = sdfSoloHoraAmPm.format(inicioDate)
                                val horaFin = sdfSoloHoraAmPm.format(finDate)

                                listaOcupados.add(
                                    RangoOcupado(
                                        inicioDate.time,
                                        finDate.time,
                                        horaInicio,
                                        horaFin
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    ocupados = listaOcupados
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun revisarSiHayChoque(usuarioInicioStr: String, usuarioFinStr: String): RangoOcupado? {
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            // 🔥 NUEVO: Añadimos esto para igualarlo a lo que lee del backend
            sdf.timeZone = TimeZone.getTimeZone("UTC")

            val usuarioInicioTime = sdf.parse(usuarioInicioStr)?.time ?: return null
            val usuarioFinTime = sdf.parse(usuarioFinStr)?.time ?: return null

            for (ocupado in ocupados) {
                if (usuarioInicioTime < ocupado.finMillis && usuarioFinTime > ocupado.inicioMillis) {
                    return ocupado
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun enviarSolicitud(payload: HiringRequestPayload, btn: MaterialButton) {
        btn.isEnabled = false
        btn.text = "Enviando..."

        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)
        lifecycleScope.launch {
            try {
                val response = api.createHiringRequest(payload)
                if (response.isSuccessful) {
                    Toast.makeText(context, "¡Solicitud enviada con éxito!", Toast.LENGTH_LONG).show()
                    dismiss()
                } else {
                    btn.isEnabled = true
                    btn.text = "Enviar Solicitud"
                    Toast.makeText(context, "Error al enviar solicitud", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                btn.isEnabled = true
                btn.text = "Enviar Solicitud"
                Toast.makeText(context, "Error de conexión", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

data class RangoOcupado(
    val inicioMillis: Long,
    val finMillis: Long,
    val horaInicio: String,
    val horaFin: String
)