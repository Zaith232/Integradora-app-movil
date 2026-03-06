package com.armonihz.app.ui.adapters // Ajusta tu paquete si es necesario

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.armonihz.app.R
import com.armonihz.app.network.model.EventResponse // Asegúrate de importar tu modelo

class EventAdapter(private var eventsList: List<EventResponse>) :
    RecyclerView.Adapter<EventAdapter.EventViewHolder>() {

    class EventViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvEventTitle: TextView = view.findViewById(R.id.tvEventTitle)
        val tvEventDetails: TextView = view.findViewById(R.id.tvEventDetails)
        val tvProposals: TextView = view.findViewById(R.id.tvProposals)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_event, parent, false)
        return EventViewHolder(view)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        val event = eventsList[position]

        // "Buscando Mariachi", etc.
        holder.tvEventTitle.text = event.titulo

        // "Fecha: 15 de mayo • Tehuacán"
        holder.tvEventDetails.text = "Fecha: ${event.fecha} • ${event.ubicacion}"

        // "5 propuestas recibidas"
        holder.tvProposals.text = "${event.propuestas} propuestas recibidas"
    }

    override fun getItemCount() = eventsList.size

    fun updateData(newEvents: List<EventResponse>) {
        eventsList = newEvents
        notifyDataSetChanged()
    }
}