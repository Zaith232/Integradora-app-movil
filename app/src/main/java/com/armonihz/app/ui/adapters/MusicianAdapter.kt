package com.armonihz.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.armonihz.app.R
import com.armonihz.app.network.model.MusicianProfileDetailResponse // Asegúrate de que esta ruta coincida con tu modelo
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy

class MusicianAdapter(
    private var musiciansList: List<MusicianProfileDetailResponse>,
    private val onMusicianClick: (Int) -> Unit
) : RecyclerView.Adapter<MusicianAdapter.MusicianViewHolder>() {

    class MusicianViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCoverPhoto: ImageView = view.findViewById(R.id.ivCoverPhoto)
        val tvStageName: TextView = view.findViewById(R.id.tvStageName)
        val tvLocationAndRate: TextView = view.findViewById(R.id.tvLocationAndRate)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicianViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_musician, parent, false)
        return MusicianViewHolder(view)
    }

    override fun onBindViewHolder(holder: MusicianViewHolder, position: Int) {
        val musician = musiciansList[position]

        holder.tvStageName.text = musician.stage_name

        // Construimos el texto de ubicación y tarifa
        val locationText = musician.location ?: "Ubicación desconocida"
        val rateText = if (!musician.hourly_rate.isNullOrEmpty()) " • $${musician.hourly_rate}/h" else ""
        holder.tvLocationAndRate.text = "📍 $locationText$rateText"

        // Lógica de foto con Glide (igual que en tu perfil)
        if (musician.profile_picture.isNullOrEmpty()) {
            Glide.with(holder.itemView.context).clear(holder.ivCoverPhoto)
            holder.ivCoverPhoto.setImageDrawable(null)
        } else {
            val fullImageUrl = if (musician.profile_picture.startsWith("http")) {
                musician.profile_picture
            } else {
                val cleanPath = musician.profile_picture.removePrefix("/")
                "https://armonihz-web-armonihz.lugsb1.easypanel.host/storage/$cleanPath"
            }

            Glide.with(holder.itemView.context)
                .load(fullImageUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .centerCrop()
                .into(holder.ivCoverPhoto)
        }

        // Clic en la tarjeta
        holder.itemView.setOnClickListener {
            onMusicianClick(musician.id)
        }
    }

    override fun getItemCount() = musiciansList.size

    fun updateData(newList: List<MusicianProfileDetailResponse>) {
        musiciansList = newList
        notifyDataSetChanged()
    }
}