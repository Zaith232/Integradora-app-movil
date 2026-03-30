package com.armonihz.app.ui.adapters

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.armonihz.app.R
import com.armonihz.app.network.model.HiringRequestItem
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class NotificationAdapter(
    private var requestsList: List<HiringRequestItem>,
    private val onItemClick: (HiringRequestItem) -> Unit,
    // NUEVO: Callback para el botón de reseña
    private val onReviewClick: (HiringRequestItem) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    class NotificationViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvMusicianName: TextView = view.findViewById(R.id.tvMusicianName)
        val tvEventDate: TextView = view.findViewById(R.id.tvEventDate)
        val tvStatusBadge: TextView = view.findViewById(R.id.tvStatusBadge)
        val tvBudget: TextView = view.findViewById(R.id.tvBudget)
        // NUEVO: Instanciamos el botón
        val btnLeaveReview: Button = view.findViewById(R.id.btnLeaveReview)
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

        // 2. Presupuesto
        val precioAMostrar = if (item.status == "counter_offer" && item.counter_offer != null) {
            item.counter_offer
        } else {
            item.budget
        }
        holder.tvBudget.text = "$${String.format(Locale.US, "%,.2f", precioAMostrar)} MXN"

        // 3. Formatear la fecha
        try {
            val rawDate = item.event_date
            val parsableDate = rawDate.substring(0, 19) // "2026-03-31T01:00:00"
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            inputFormat.timeZone = TimeZone.getTimeZone("UTC")
            val outputFormat = SimpleDateFormat("yyyy-MM-dd - hh:mm a", Locale.US)
            outputFormat.timeZone = TimeZone.getDefault()

            val date = inputFormat.parse(parsableDate)
            if (date != null) {
                holder.tvEventDate.text = outputFormat.format(date)
            } else {
                holder.tvEventDate.text = item.event_date
            }
        } catch (e: Exception) {
            e.printStackTrace()
            holder.tvEventDate.text = item.event_date
        }

        // 4. Lógica de colores para la etiqueta de Estado (Badge)
        val badgeBackground = GradientDrawable()
        badgeBackground.cornerRadius = 16f

        // Por defecto, ocultamos el botón de reseña en cada recarga
        holder.btnLeaveReview.visibility = View.GONE

        when (item.status) {
            "pending" -> {
                holder.tvStatusBadge.text = "Pendiente"
                holder.tvStatusBadge.setTextColor(Color.parseColor("#B45309"))
                badgeBackground.setColor(Color.parseColor("#FEF3C7"))
            }
            "accepted" -> {
                holder.tvStatusBadge.text = "Confirmada"
                holder.tvStatusBadge.setTextColor(Color.parseColor("#15803D"))
                badgeBackground.setColor(Color.parseColor("#DCFCE7"))
            }
            "completed" -> {
                holder.tvStatusBadge.text = "Finalizado"
                holder.tvStatusBadge.setTextColor(Color.parseColor("#1D4ED8")) // Azul oscuro
                badgeBackground.setColor(Color.parseColor("#DBEAFE")) // Azul claro

                // 🔥 AQUÍ ESTÁ EL CAMBIO: Solo mostramos el botón si NO tiene reseña
                if (item.has_review == true) {
                    holder.btnLeaveReview.visibility = View.GONE
                } else {
                    holder.btnLeaveReview.visibility = View.VISIBLE
                }
            }
            "rejected" -> {
                holder.tvStatusBadge.text = "Rechazada"
                holder.tvStatusBadge.setTextColor(Color.parseColor("#B91C1C"))
                badgeBackground.setColor(Color.parseColor("#FEE2E2"))
            }
            "counter_offer" -> {
                holder.tvStatusBadge.text = "Contraoferta"
                holder.tvStatusBadge.setTextColor(Color.parseColor("#6D28D9"))
                badgeBackground.setColor(Color.parseColor("#EDE9FE"))
            }
            else -> {
                holder.tvStatusBadge.text = "Desconocida"
                holder.tvStatusBadge.setTextColor(Color.parseColor("#475569"))
                badgeBackground.setColor(Color.parseColor("#F1F5F9"))
            }
        }
        holder.tvStatusBadge.background = badgeBackground

        // Clic en la tarjeta completa (Abre tu BottomSheet de detalles)
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }

        // Clic específicamente en el botón de reseña
        holder.btnLeaveReview.setOnClickListener {
            onReviewClick(item)
        }
    }

    override fun getItemCount(): Int = requestsList.size

    fun updateData(newList: List<HiringRequestItem>) {
        requestsList = newList
        notifyDataSetChanged()
    }
}