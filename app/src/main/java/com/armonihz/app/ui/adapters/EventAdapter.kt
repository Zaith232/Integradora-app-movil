    package com.armonihz.app.ui.adapters

    import android.graphics.Color
    import android.util.Log
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
            val chipStatus: com.google.android.material.chip.Chip = view.findViewById(R.id.chipStatus) // antes tvEventStatus
            val tvEditEvent: TextView = view.findViewById(R.id.tvEditEvent)
            val tvEventWarning: TextView = view.findViewById(R.id.tvEventWarning)
            val tvDeleteEvent: TextView = view.findViewById(R.id.tvDeleteEvent)
            val viewStatusBar: View = view.findViewById(R.id.viewStatusBar) // barra lateral
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

            // 🔴 CADUCADO
            if (expired) {
                holder.viewStatusBar.setBackgroundColor(Color.parseColor("#9E9E9E"))
                holder.chipStatus.text = "Caducado"
                holder.chipStatus.setChipBackgroundColorResource(android.R.color.transparent)
                holder.chipStatus.chipBackgroundColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#F5F5F5"))
                holder.chipStatus.setTextColor(Color.parseColor("#616161"))

                holder.tvEventWarning.visibility = View.VISIBLE
                holder.tvEventWarning.text = "❌ Este evento ya caducó"
                holder.tvEditEvent.visibility = View.GONE
            }

            // 🟢 DISPONIBLE
            else if (event.status == "open") {
                holder.viewStatusBar.setBackgroundColor(Color.parseColor("#1565C0"))
                holder.chipStatus.text = "Disponible"
                holder.chipStatus.chipBackgroundColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#E8F5E9"))
                holder.chipStatus.setTextColor(Color.parseColor("#2E7D32"))

                holder.tvEditEvent.visibility = View.VISIBLE

                if (daysLeft in 0..2) {
                    holder.viewStatusBar.setBackgroundColor(Color.parseColor("#F9A825"))
                    holder.chipStatus.text = "Por caducar"
                    holder.chipStatus.chipBackgroundColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#FFF8E1"))
                    holder.chipStatus.setTextColor(Color.parseColor("#F57F17"))
                    holder.tvEventWarning.visibility = View.VISIBLE
                    holder.tvEventWarning.text = "Caduca en $daysLeft días"
                } else {
                    holder.tvEventWarning.visibility = View.GONE
                }
            }

            // ⚪ CERRADO
            else {
                holder.viewStatusBar.setBackgroundColor(Color.parseColor("#9E9E9E"))
                holder.chipStatus.text = "Cerrado"
                holder.chipStatus.chipBackgroundColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#F5F5F5"))
                holder.chipStatus.setTextColor(Color.parseColor("#616161"))

                holder.tvEditEvent.visibility = View.GONE
                holder.tvEventWarning.visibility = View.GONE
            }

            // Click en tarjeta (abrir propuestas)
            holder.itemView.setOnClickListener {
// Si está caducado O si no está abierto (está cerrado), no hacemos nada
                if (expired || event.status != "open") {
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

            holder.tvDeleteEvent.visibility = View.VISIBLE

            Log.d("EVENT_DEBUG", "Dias restantes: $daysLeft")
            Log.d("EVENT_DEBUG", "Fecha recibida: ${event.fecha}")

        }

        override fun getItemCount() = eventsList.size

        fun updateData(newEvents: List<EventResponse>) {
            eventsList = newEvents
            notifyDataSetChanged()
        }

        // Función para calcular días restantes
        private fun daysUntil(dateString: String): Long {

            return try {

                val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val eventDate = format.parse(dateString) ?: return Long.MAX_VALUE

                val calendarToday = Calendar.getInstance()
                calendarToday.set(Calendar.HOUR_OF_DAY, 0)
                calendarToday.set(Calendar.MINUTE, 0)
                calendarToday.set(Calendar.SECOND, 0)
                calendarToday.set(Calendar.MILLISECOND, 0)

                val today = calendarToday.time

                val diff = eventDate.time - today.time

                diff / (1000 * 60 * 60 * 24)

            } catch (e: Exception) {

                Log.e("DATE_ERROR", "Error parsing date: $dateString")

                Long.MAX_VALUE
            }
        }

        private fun isExpired(dateString: String): Boolean {

            return try {

                val format = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                val eventDate = format.parse(dateString) ?: return false

                val calendarToday = Calendar.getInstance()
                calendarToday.set(Calendar.HOUR_OF_DAY, 0)
                calendarToday.set(Calendar.MINUTE, 0)
                calendarToday.set(Calendar.SECOND, 0)
                calendarToday.set(Calendar.MILLISECOND, 0)

                val today = calendarToday.time

                eventDate.before(today)

            } catch (e: Exception) {

                false
            }
        }
    }