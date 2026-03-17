package com.armonihz.app.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.armonihz.app.R
import com.armonihz.app.network.model.EventResponse
import java.text.SimpleDateFormat
import java.util.*

class EventAdapter(
    private var eventsList: List<EventResponse>,
    private val onEventClick: (Int) -> Unit,
    private val onEditClick: (EventResponse) -> Unit,
    private val onDeleteClick: (EventResponse) -> Unit
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvEventTitle: TextView = view.findViewById(R.id.tvEventTitle)
        val tvEventDetails: TextView = view.findViewById(R.id.tvEventDetails)
        val tvProposals: TextView = view.findViewById(R.id.tvProposals)
        val tvEventStatus: TextView = view.findViewById(R.id.tvEventStatus)
        val tvEditEvent: TextView = view.findViewById(R.id.tvEditEvent)
        val tvEventWarning: TextView = view.findViewById(R.id.tvEventWarning) // NUEVO
        val tvDeleteEvent: TextView = itemView.findViewById(R.id.tvDeleteEvent)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = eventsList[position]

        holder.tvEventTitle.text = event.titulo
        holder.tvEventDetails.text = "Fecha: ${event.fecha} • ${event.ubicacion}"
        holder.tvProposals.text = "${event.propuestas} propuestas recibidas"

        val expired = isExpired(event.fecha)
        val daysLeft = daysUntil(event.fecha)

        // 🔴 EVENTO CADUCADO
        if (expired) {

            holder.tvEventStatus.text = "Estado: Caducado"
            holder.tvEventStatus.setTextColor(Color.parseColor("#DC3545"))

            holder.tvEventWarning.visibility = View.VISIBLE
            holder.tvEventWarning.text = "❌ Este evento ya caducó"

            holder.tvEditEvent.visibility = View.GONE

        }

        // 🟢 EVENTO DISPONIBLE
        else if (event.status == "open") {

            holder.tvEventStatus.text = "Estado: Disponible"
            holder.tvEventStatus.setTextColor(Color.parseColor("#28A745"))

            holder.tvEditEvent.visibility = View.VISIBLE

            // ⚠ EVENTO POR CADUCAR
            if (daysLeft in 0..2) {
                holder.tvEventWarning.visibility = View.VISIBLE
                holder.tvEventWarning.text = "⚠ Este evento está por caducar"
            } else {
                holder.tvEventWarning.visibility = View.GONE
            }

        }

        // ⚪ EVENTO CERRADO
        else {

            holder.tvEventStatus.text = "Estado: Aceptado/Cerrado"
            holder.tvEventStatus.setTextColor(Color.parseColor("#6C757D"))

            holder.tvEditEvent.visibility = View.GONE
            holder.tvEventWarning.visibility = View.GONE
        }

        // Click en tarjeta (abrir propuestas)
        holder.itemView.setOnClickListener {

            if (expired) {
                return@setOnClickListener
            }

            onEventClick(event.id)
        }

        // Click editar
        holder.tvEditEvent.setOnClickListener {

            if (!expired) {
                onEditClick(event)
            }

        }

        holder.tvDeleteEvent.setOnClickListener {
            onDeleteClick(event)
        }

        if (event.status != "open") {
            holder.tvDeleteEvent.visibility = View.GONE
        } else {
            holder.tvDeleteEvent.visibility = View.VISIBLE
        }
    }

    override fun getItemCount() = eventsList.size

    fun updateData(newEvents: List<EventResponse>) {
        eventsList = newEvents
        notifyDataSetChanged()
    }

    // Función para calcular días restantes
    private fun daysUntil(dateString: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val eventDate = format.parse(dateString) ?: return Long.MAX_VALUE
            val today = Date()

            val diff = eventDate.time - today.time
            diff / (1000 * 60 * 60 * 24)
        } catch (e: Exception) {
            Long.MAX_VALUE
        }
    }

    private fun isExpired(dateString: String): Boolean {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val eventDate = format.parse(dateString) ?: return false
            val today = Date()

            eventDate.before(today)
        } catch (e: Exception) {
            false
        }
    }
}