package com.armonihz.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.armonihz.app.R
import com.armonihz.app.network.model.MultimediaItem
import com.bumptech.glide.Glide

class MultimediaAdapter(
    private val mediaList: List<MultimediaItem>,
    private val onMediaClick: (MultimediaItem) -> Unit
) : RecyclerView.Adapter<MultimediaAdapter.MediaViewHolder>() {

    inner class MediaViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivMedia: ImageView = itemView.findViewById(R.id.ivMedia)
        val ivPlayIcon: ImageView = itemView.findViewById(R.id.ivPlayIcon)

        fun bind(item: MultimediaItem) {
            // Mostrar ícono de play si es video
            if (item.type == "video") {
                ivPlayIcon.visibility = View.VISIBLE
            } else {
                ivPlayIcon.visibility = View.GONE
            }

            // Glide puede cargar imágenes y miniaturas de video desde URLs
            Glide.with(itemView.context)
                .load(item.file_path)
                .centerCrop()
                .into(ivMedia)

            itemView.setOnClickListener { onMediaClick(item) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MediaViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_multimedia, parent, false)
        return MediaViewHolder(view)
    }

    override fun onBindViewHolder(holder: MediaViewHolder, position: Int) {
        holder.bind(mediaList[position])
    }

    override fun getItemCount(): Int = mediaList.size
}
