package com.armonihz.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.armonihz.app.R
import com.armonihz.app.network.model.MusicianProfileDetailResponse
import com.bumptech.glide.Glide
import com.google.android.material.imageview.ShapeableImageView

class FavoritesAdapter(
    private var favorites: MutableList<MusicianProfileDetailResponse>,
    private val onMusicianClick: (Int) -> Unit,
    private val onRemoveClick: (MusicianProfileDetailResponse, Int) -> Unit
) : RecyclerView.Adapter<FavoritesAdapter.FavoriteViewHolder>() {

    inner class FavoriteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        // Cambiado a ShapeableImageView para coincidir con el XML moderno
        val imgFav: ShapeableImageView = view.findViewById(R.id.imgFav)
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
            // Asegúrate de tener este ícono o cámbialo por el tuyo (ej. R.drawable.ic_launcher_background)
            holder.imgFav.setImageResource(R.drawable.ic_user_placeholder)
        }

        // Clic en la tarjeta completa
        holder.itemView.setOnClickListener { onMusicianClick(musician.id) }

        // 🚀 CORRECCIÓN CLAVE PARA PREVENIR CRASHES:
        // Usamos adapterPosition para obtener la posición actual, no la inicial
        holder.btnRemoveFav.setOnClickListener {
            val currentPosition = holder.adapterPosition
            if (currentPosition != RecyclerView.NO_POSITION) {
                onRemoveClick(musician, currentPosition)
            }
        }
    }

    override fun getItemCount() = favorites.size

    // 🚀 Método MEJORADO para borrar visualmente el elemento
    fun removeItem(position: Int) {
        if (position in 0 until favorites.size) {
            // 1. Lo borramos de la lista en memoria
            favorites.removeAt(position)

            // 2. Ejecutamos la animación de salida
            notifyItemRemoved(position)

            // 3. ¡IMPORTANTE! Actualizamos las posiciones de los elementos que quedaron abajo
            notifyItemRangeChanged(position, favorites.size)
        }
    }

    // Opcional: Para cuando recargues la lista completa desde la API
    fun updateData(newFavorites: List<MusicianProfileDetailResponse>) {
        favorites.clear()
        favorites.addAll(newFavorites)
        notifyDataSetChanged()
    }
}