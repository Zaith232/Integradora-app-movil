package com.armonihz.app

import android.os.Bundle
import android.os.Parcel
import android.os.Parcelable
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
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HiringBottomSheetFragment(private val musicianId: Int) : BottomSheetDialogFragment() {

    private var selectedDate: String = ""
    private var selectedStartTime: String = ""
    private var selectedEndTime: String = ""
    private var disabledDatesUtc = LongArray(0)

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
            val constraints = CalendarConstraints.Builder()
                .setValidator(BusyDateValidator(disabledDatesUtc))
                .build()

            val datePicker = MaterialDatePicker.Builder.datePicker()
                .setTitleText("Selecciona la fecha")
                .setCalendarConstraints(constraints)
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
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setTitleText("Hora de inicio")
                .build()

            timePicker.addOnPositiveButtonClickListener {
                selectedStartTime = String.format("%02d:%02d:00", timePicker.hour, timePicker.minute)
                btnStartTime.text = String.format("Inicio: %02d:%02d", timePicker.hour, timePicker.minute)
            }
            timePicker.show(parentFragmentManager, "START_TIME")
        }

        btnEndTime.setOnClickListener {
            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setTitleText("Hora de fin")
                .build()

            timePicker.addOnPositiveButtonClickListener {
                selectedEndTime = String.format("%02d:%02d:00", timePicker.hour, timePicker.minute)
                btnEndTime.text = String.format("Fin: %02d:%02d", timePicker.hour, timePicker.minute)
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

            // 🔥 SOLUCIÓN A LA MEDIANOCHE:
            // Si la hora de fin es menor a la de inicio (ej. 01:00 < 20:00), sumamos 1 día a la fecha final.
            var endDateForDatabase = selectedDate
            if (selectedEndTime < selectedStartTime) {
                try {
                    val sdfDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val parsedDate = sdfDate.parse(selectedDate)
                    if (parsedDate != null) {
                        val cal = java.util.Calendar.getInstance()
                        cal.time = parsedDate
                        cal.add(java.util.Calendar.DAY_OF_YEAR, 1) // Sumamos 1 día
                        endDateForDatabase = sdfDate.format(cal.time)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val horaInicioLimpia = selectedStartTime.dropLast(3)
            val horaFinLimpia = selectedEndTime.dropLast(3)
            val finalDescription = "⏰ Horario: $horaInicioLimpia a $horaFinLimpia hrs.\n\n" + etLocation.text.toString() + "\n\n" + etDescription.text.toString()

            val payload = HiringRequestPayload(
                musician_profile_id = musicianId,
                event_date = "$selectedDate $selectedStartTime",
                end_time = "$endDateForDatabase $selectedEndTime", // Usamos la fecha calculada
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
                    val busyDates = response.body()!!.data
                    val timestamps = mutableListOf<Long>()

                    val sdfBackend = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val sdfUtc = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    sdfUtc.timeZone = TimeZone.getTimeZone("UTC")

                    for (busy in busyDates) {
                        try {
                            val dateObj = sdfBackend.parse(busy.start)
                            if (dateObj != null) {
                                val dayString = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(dateObj)
                                val utcDate = sdfUtc.parse(dayString)
                                if (utcDate != null) {
                                    timestamps.add(utcDate.time)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                    disabledDatesUtc = timestamps.toLongArray()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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

class BusyDateValidator(private val disabledDates: LongArray) : CalendarConstraints.DateValidator {
    override fun isValid(date: Long): Boolean {
        if (date < MaterialDatePicker.todayInUtcMilliseconds()) {
            return false
        }
        return !disabledDates.contains(date)
    }

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeLongArray(disabledDates)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<BusyDateValidator> {
        override fun createFromParcel(parcel: Parcel): BusyDateValidator {
            return BusyDateValidator(parcel.createLongArray() ?: LongArray(0))
        }
        override fun newArray(size: Int): Array<BusyDateValidator?> {
            return arrayOfNulls(size)
        }
    }
}