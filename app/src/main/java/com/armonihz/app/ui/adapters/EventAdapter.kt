package com.armonihz.app.ui.adapters

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.armonihz.app.R
import com.armonihz.app.network.model.EventResponse

class EventAdapter(
    private var eventsList: List<EventResponse>,
    private val onEventClick: (Int) -> Unit,
    private val onEditClick: (EventResponse) -> Unit // ⬅️ Nuevo listener para el botón Editar
) : RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvEventTitle: TextView = view.findViewById(R.id.tvEventTitle)
        val tvEventDetails: TextView = view.findViewById(R.id.tvEventDetails)
        val tvProposals: TextView = view.findViewById(R.id.tvProposals)
        val tvEventStatus: TextView = view.findViewById(R.id.tvEventStatus) // ⬅️ Referencia al Estado
        val tvEditEvent: TextView = view.findViewById(R.id.tvEditEvent)     // ⬅️ Referencia a Editar
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

        // ⬅️ Lógica para el Estado
        if (event.status == "open") {
            holder.tvEventStatus.text = "Estado: Disponible"
            holder.tvEventStatus.setTextColor(Color.parseColor("#28A745")) // Verde
            holder.tvEditEvent.visibility = View.VISIBLE // Solo se puede editar si está abierto
        } else {
            holder.tvEventStatus.text = "Estado: Aceptado/Cerrado"
            holder.tvEventStatus.setTextColor(Color.parseColor("#6C757D")) // Gris
            holder.tvEditEvent.visibility = View.GONE // Ocultamos el botón editar si ya aceptaron a alguien
        }

        // ⬅️ Clic para ver propuestas (en toda la tarjeta menos en el botón editar)
        holder.itemView.setOnClickListener {
            onEventClick(event.id)
        }

        // ⬅️ Clic específico para Editar
        holder.tvEditEvent.setOnClickListener {
            onEditClick(event)
        }
    }

    override fun getItemCount() = eventsList.size

    fun updateData(newEvents: List<EventResponse>) {
        eventsList = newEvents
        notifyDataSetChanged()
    }
}