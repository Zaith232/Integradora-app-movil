package com.armonihz.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.network.model.HiringRequestItem
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import com.google.android.material.card.MaterialCardView

class RequestDetailBottomSheet(
    private val requestItem: HiringRequestItem
) : BottomSheetDialogFragment() {
    private lateinit var layoutCounterOffer: MaterialCardView
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.layout_request_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvMusician = view.findViewById<TextView>(R.id.tvDetailMusician)
        val tvDate = view.findViewById<TextView>(R.id.tvDetailDate)
        val tvLocation = view.findViewById<TextView>(R.id.tvDetailLocation)
        layoutCounterOffer = view.findViewById<MaterialCardView>(R.id.layoutCounterOffer)
        val tvMessage = view.findViewById<TextView>(R.id.tvMusicianMessage)
        val tvNewPrice = view.findViewById<TextView>(R.id.tvNewPrice)

        val btnAccept = view.findViewById<MaterialButton>(R.id.btnAcceptOffer)
        val btnReject = view.findViewById<MaterialButton>(R.id.btnRejectOffer)

        // 1. Llenar datos básicos
        tvMusician.text = "Músico: ${requestItem.musician_profile?.stage_name ?: "Desconocido"}"
        tvLocation.text = "Ubicación: ${requestItem.event_location}"

        // 🌟 FIX FECHA BONITA 🌟
        try {
            val rawDateDetails = requestItem.event_date
            // Cortamos los microsegundos feos: "2026-03-31T20:00:00"
            val parsableDateDetails = rawDateDetails.substring(0, 19)

            val inputFormatDetails = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            inputFormatDetails.timeZone = TimeZone.getTimeZone("UTC")

            // Formato de salida: "31 de marzo, 2026 - 08:00 PM"
            val outputFormatDetails = SimpleDateFormat("d 'de' MMMM, yyyy - hh:mm a", Locale("es", "MX"))
            outputFormatDetails.timeZone = TimeZone.getDefault()

            val dateDetails = inputFormatDetails.parse(parsableDateDetails)

            if (dateDetails != null) {
                tvDate.text = "Fecha: ${outputFormatDetails.format(dateDetails)}"
            } else {
                tvDate.text = "Fecha: $rawDateDetails"
            }
        } catch (e: Exception) {
            e.printStackTrace()
            tvDate.text = "Fecha: ${requestItem.event_date}"
        }

        // 2. Si es Contraoferta, mostramos el cuadro con los botones
        if (requestItem.status == "counter_offer") {
            layoutCounterOffer.visibility = View.VISIBLE
            tvMessage.text = requestItem.musician_message ?: "Sin mensaje."
            tvNewPrice.text = "$${String.format(Locale.US, "%,.2f", requestItem.counter_offer ?: 0.0)} MXN"

            // Evento ACEPTAR
            btnAccept.setOnClickListener {
                responderContraoferta(requestItem.id, "accepted")
            }

            // Evento RECHAZAR
            btnReject.setOnClickListener {
                responderContraoferta(requestItem.id, "rejected")
            }
        } else {
            layoutCounterOffer.visibility = View.GONE
        }
    }

    private fun responderContraoferta(requestId: Int, status: String) {
        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        // Referencias a los botones
        val btnAccept = view?.findViewById<MaterialButton>(R.id.btnAcceptOffer)
        val btnReject = view?.findViewById<MaterialButton>(R.id.btnRejectOffer)

        // 👇 1. BLOQUEAMOS BOTONES Y CAMBIAMOS EL TEXTO AL INSTANTE
        btnAccept?.isEnabled = false
        btnReject?.isEnabled = false

        if (status == "accepted") {
            btnAccept?.text = "Aceptando..."
        } else {
            btnReject?.text = "Rechazando..."
        }

        lifecycleScope.launch {
            try {
                val response = api.respondToHiringRequest(requestId, mapOf("status" to status))

                if (response.isSuccessful) {
                    val mensaje = if (status == "accepted") "¡Evento Confirmado!" else "Contraoferta rechazada"
                    Toast.makeText(requireContext(), mensaje, Toast.LENGTH_SHORT).show()
                    dismiss()
                } else {
                    Toast.makeText(requireContext(), "Error al guardar respuesta", Toast.LENGTH_SHORT).show()
                    // Si hay error en el servidor, restauramos los botones
                    restaurarBotones(btnAccept, btnReject)
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Problema de conexión", Toast.LENGTH_SHORT).show()
                // Si se va el internet, restauramos los botones
                restaurarBotones(btnAccept, btnReject)
            }
        }
    }

    // 👇 2. FUNCIÓN AUXILIAR PARA RESTAURAR TODO SI FALLA
    private fun restaurarBotones(btnAccept: MaterialButton?, btnReject: MaterialButton?) {
        btnAccept?.isEnabled = true
        btnReject?.isEnabled = true
        btnAccept?.text = "Aceptar"
        btnReject?.text = "Rechazar"
    }
}