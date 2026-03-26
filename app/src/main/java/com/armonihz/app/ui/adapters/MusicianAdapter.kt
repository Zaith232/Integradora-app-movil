package com.armonihz.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.armonihz.app.R
import com.armonihz.app.network.model.MusicianProfileDetailResponse
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

class MusicianAdapter(
    private var musiciansList: List<MusicianProfileDetailResponse>,
    private val onMusicianClick: (Int) -> Unit
) : RecyclerView.Adapter<MusicianAdapter.MusicianViewHolder>() {

    class MusicianViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivCoverPhoto: ImageView = view.findViewById(R.id.ivCoverPhoto)
        val tvStageName: TextView = view.findViewById(R.id.tvStageName)
        val tvLocation: TextView = view.findViewById(R.id.tvLocation)
        val tvPriceHint: TextView = view.findViewById(R.id.tvPriceHint)
        val tvRating: TextView = view.findViewById(R.id.tvRating)
        val chipGroupGenres: ChipGroup = view.findViewById(R.id.chipGroupGenres)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MusicianViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_musician, parent, false)
        return MusicianViewHolder(view)
    }

    override fun onBindViewHolder(holder: MusicianViewHolder, position: Int) {
        val musician = musiciansList[position]

        // Nombre artístico
        holder.tvStageName.text = musician.stage_name

        // Ubicación
        val locationText = musician.location ?: "Ubicación desconocida"
        holder.tvLocation.text = "📍 $locationText"

        // Precio
        if (!musician.hourly_rate.isNullOrEmpty()) {
            holder.tvPriceHint.text = "Desde $${musician.hourly_rate}"
        } else {
            holder.tvPriceHint.text = "A convenir"
        }

        // Rating (puedes reemplazar "4.8" por un campo real del modelo si lo tienes)
        holder.tvRating.text = "4.8"

        // Chips de géneros — limpiar primero para evitar duplicados al reciclar
        holder.chipGroupGenres.removeAllViews()
        musician.genres?.take(2)?.forEach { genre ->
            val chip = Chip(holder.itemView.context).apply {
                text = genre.name
                isClickable = false
                isCheckable = false
                textSize = 10f
                chipMinHeight = 28f
                chipStartPadding = 6f
                chipEndPadding = 6f
            }
            holder.chipGroupGenres.addView(chip)
        }

        // Foto con Glide
        if (musician.profile_picture.isNullOrEmpty()) {
            Glide.with(holder.itemView.context).clear(holder.ivCoverPhoto)
            holder.ivCoverPhoto.setImageDrawable(null)
        } else {
            val fullImageUrl = if (musician.profile_picture.startsWith("http")) {
                musician.profile_picture
            } else {
                val cleanPath = musician.profile_picture.removePrefix("/")
                "https://armonihz-web-armonihz.lugsb1.easypanel.host/file/$cleanPath"
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