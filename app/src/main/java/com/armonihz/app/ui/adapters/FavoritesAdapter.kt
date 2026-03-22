package com.armonihz.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.armonihz.app.R
import com.armonihz.app.network.model.MusicianProfileDetailResponse
import com.bumptech.glide.Glide

class FavoritesAdapter(
    private var favorites: MutableList<MusicianProfileDetailResponse>,
    private val onMusicianClick: (Int) -> Unit,
    private val onRemoveClick: (MusicianProfileDetailResponse, Int) -> Unit
) : RecyclerView.Adapter<FavoritesAdapter.FavoriteViewHolder>() {

    inner class FavoriteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgFav: ImageView = view.findViewById(R.id.imgFav)
        val txtNameFav: TextView = view.findViewById(R.id.txtNameFav)
        val txtDetailsFav: TextView = view.findViewById(R.id.txtDetailsFav)
        val btnRemoveFav: ImageButton = view.findViewById(R.id.btnRemoveFav)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_favorite, parent, false)
        return FavoriteViewHolder(view)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        val musician = favorites[position]

        holder.txtNameFav.text = musician.stage_name

        // Mostrar género principal o ubicación
        val mainGenre = musician.genres?.firstOrNull()?.name ?: "Músico"
        holder.txtDetailsFav.text = "$mainGenre · ${musician.location ?: "Sin ubicación"}"

        // Cargar imagen
        if (!musician.profile_picture.isNullOrEmpty()) {
            val url = if (musician.profile_picture.startsWith("http")) musician.profile_picture
            else "https://armonihz-web-armonihz.lugsb1.easypanel.host/file/${musician.profile_picture.removePrefix("/")}"

            Glide.with(holder.itemView.context).load(url).centerCrop().into(holder.imgFav)
        } else {
            holder.imgFav.setImageResource(R.drawable.ic_user_placeholder) // Cambia esto por tu placeholder
        }

        // Clicks
        holder.itemView.setOnClickListener { onMusicianClick(musician.id) }
        holder.btnRemoveFav.setOnClickListener { onRemoveClick(musician, position) }
    }

    override fun getItemCount() = favorites.size

    // Método para borrar visualmente el elemento sin recargar toda la lista
    fun removeItem(position: Int) {
        favorites.removeAt(position)
        notifyItemRemoved(position)
    }
}