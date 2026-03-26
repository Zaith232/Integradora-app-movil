package com.armonihz.app.ui.adapters

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.armonihz.app.R
import com.armonihz.app.network.model.HiringRequestItem
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
class NotificationAdapter(
    private var requestsList: List<HiringRequestItem>,
    private val onItemClick: (HiringRequestItem) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMusicianName: TextView = view.findViewById(R.id.tvMusicianName)
        val tvEventDate: TextView = view.findViewById(R.id.tvEventDate)
        val tvStatusBadge: TextView = view.findViewById(R.id.tvStatusBadge)
        val tvBudget: TextView = view.findViewById(R.id.tvBudget)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotificationViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val item = requestsList[position]


        // 1. Nombre del músico
        holder.tvMusicianName.text = item.musician_profile?.stage_name ?: "Músico sin nombre"

        // 2. Presupuesto (Si el estado es Contraoferta, mostramos el nuevo precio)
        val precioAMostrar = if (item.status == "counter_offer" && item.counter_offer != null) {
            item.counter_offer
        } else {
            item.budget
        }
        holder.tvBudget.text = "$${String.format(Locale.US, "%,.2f", precioAMostrar)} MXN"

        // 3. Formatear la fecha para que se lea natural (Ej: "31 Mar, 2026 - 20:00 hrs")
        try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd MMM, yyyy - HH:mm 'hrs'", Locale("es", "MX"))
            val date = inputFormat.parse(item.event_date)
            if (date != null) {
                holder.tvEventDate.text = outputFormat.format(date)
            } else {
                holder.tvEventDate.text = item.event_date
            }
        } catch (e: Exception) {
            holder.tvEventDate.text = item.event_date
        }

        // 4. Lógica de colores para la etiqueta de Estado (Badge)
        // Usamos GradientDrawable para no depender de archivos XML externos para los fondos
        val badgeBackground = GradientDrawable()
        badgeBackground.cornerRadius = 16f

        when (item.status) {
            "pending" -> {
                holder.tvStatusBadge.text = "Pendiente"
                holder.tvStatusBadge.setTextColor(Color.parseColor("#B45309")) // Naranja oscuro
                badgeBackground.setColor(Color.parseColor("#FEF3C7")) // Amarillo claro
            }
            "accepted" -> {
                holder.tvStatusBadge.text = "Confirmada"
                holder.tvStatusBadge.setTextColor(Color.parseColor("#15803D")) // Verde oscuro
                badgeBackground.setColor(Color.parseColor("#DCFCE7")) // Verde claro
            }
            "rejected" -> {
                holder.tvStatusBadge.text = "Rechazada"
                holder.tvStatusBadge.setTextColor(Color.parseColor("#B91C1C")) // Rojo oscuro
                badgeBackground.setColor(Color.parseColor("#FEE2E2")) // Rojo claro
            }
            "counter_offer" -> {
                holder.tvStatusBadge.text = "Contraoferta"
                holder.tvStatusBadge.setTextColor(Color.parseColor("#6D28D9")) // Morado oscuro
                badgeBackground.setColor(Color.parseColor("#EDE9FE")) // Morado claro
            }
            else -> {
                holder.tvStatusBadge.text = "Desconocida"
                holder.tvStatusBadge.setTextColor(Color.parseColor("#475569"))
                badgeBackground.setColor(Color.parseColor("#F1F5F9"))
            }
        }
        holder.tvStatusBadge.background = badgeBackground

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
        try {
            // La fecha cruda que viene de Laravel: "2026-03-31T01:00:00.000000Z"
            val rawDate = item.event_date

            // TRUCO MÁGICO: Usamos substring para recortar la parte fea de los microsegundos
            // y la 'Z', quedándonos solo con "yyyy-MM-dd'T'HH:mm:ss".
            // Esto hace que el parseo sea robusto en cualquier versión de Android.
            val parsableDate = rawDate.substring(0, 19) // "2026-03-31T01:00:00"

            // 1. Definimos el formato de entrada (cómo leemos la cadena limpia)
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            // Laravel envía la hora en UTC (Z), así que le avisamos al parser
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")

            // 2. Definimos el formato de salida deseado (cómo queremos que lo vea el cliente)
            // Formato elegido para la lista: "AAAA-MM-DD - HH:MM AM/PM"
            val outputFormat = SimpleDateFormat("yyyy-MM-dd - hh:mm a", Locale.US)
            // Le pedimos que muestre la hora local del celular del cliente
            outputFormat.timeZone = TimeZone.getDefault()

            // Hacemos la conversión
            val date = inputFormat.parse(parsableDate)

            if (date != null) {
                // Ponemos la fecha bonita en el TextView
                holder.tvEventDate.text = outputFormat.format(date)
            } else {
                // Fallback: si algo sale mal, mostramos la fecha cruda para no dejar en blanco
                holder.tvEventDate.text = item.event_date
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback en caso de error
            holder.tvEventDate.text = item.event_date
        }
    }

    override fun getItemCount(): Int = requestsList.size

    fun updateData(newList: List<HiringRequestItem>) {
        requestsList = newList
        notifyDataSetChanged()
    }
}