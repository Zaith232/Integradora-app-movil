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
import java.util.Locale

class RequestDetailBottomSheet(
    private val requestItem: HiringRequestItem
) : BottomSheetDialogFragment() {

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
        val layoutCounterOffer = view.findViewById<LinearLayout>(R.id.layoutCounterOffer)
        val tvMessage = view.findViewById<TextView>(R.id.tvMusicianMessage)
        val tvNewPrice = view.findViewById<TextView>(R.id.tvNewPrice)

        val btnAccept = view.findViewById<MaterialButton>(R.id.btnAcceptOffer)
        val btnReject = view.findViewById<MaterialButton>(R.id.btnRejectOffer)

        // 1. Llenar datos básicos
        tvMusician.text = "Músico: ${requestItem.musician_profile?.stage_name ?: "Desconocido"}"
        tvLocation.text = "Ubicación: ${requestItem.event_location}"

        // 🌟 FIX FECHA: Limpiamos el formato "2026-03-31T20:00:00.000000Z" que manda Laravel
        try {
            val fechaLimpia = requestItem.event_date.substringBefore("T")
            val horaLimpia = requestItem.event_date.substringAfter("T").substringBefore(".")
            tvDate.text = "Fecha: $fechaLimpia — $horaLimpia hrs"
        } catch (e: Exception) {
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

    // 🌟 FIX BOTONES: Usamos lifecycleScope.launch directamente
    private fun responderContraoferta(requestId: Int, status: String) {
        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        lifecycleScope.launch {
            try {
                // Deshabilitar botones para evitar doble clic
                view?.findViewById<MaterialButton>(R.id.btnAcceptOffer)?.isEnabled = false
                view?.findViewById<MaterialButton>(R.id.btnRejectOffer)?.isEnabled = false

                // Asegúrate de tener esta función en tu ApiService.kt
                val response = api.respondToHiringRequest(requestId, mapOf("status" to status))

                if (response.isSuccessful) {
                    val mensaje = if (status == "accepted") "¡Evento Confirmado!" else "Contraoferta rechazada"
                    Toast.makeText(requireContext(), mensaje, Toast.LENGTH_SHORT).show()
                    dismiss() // Cierra el panel
                } else {
                    Toast.makeText(requireContext(), "Error al guardar respuesta", Toast.LENGTH_SHORT).show()
                    view?.findViewById<MaterialButton>(R.id.btnAcceptOffer)?.isEnabled = true
                    view?.findViewById<MaterialButton>(R.id.btnRejectOffer)?.isEnabled = true
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Problema de conexión", Toast.LENGTH_SHORT).show()
                view?.findViewById<MaterialButton>(R.id.btnAcceptOffer)?.isEnabled = true
                view?.findViewById<MaterialButton>(R.id.btnRejectOffer)?.isEnabled = true
            }
        }
    }
}