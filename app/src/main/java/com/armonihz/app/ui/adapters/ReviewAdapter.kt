package com.armonihz.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.armonihz.app.R
import com.armonihz.app.network.model.ReviewItem
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class ReviewAdapter(private var reviews: List<ReviewItem>) : RecyclerView.Adapter<ReviewAdapter.ReviewViewHolder>() {

    class ReviewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivClientPhoto: CircleImageView = view.findViewById(R.id.ivClientPhoto)
        val tvClientName: TextView = view.findViewById(R.id.tvClientName)
        val tvReviewDate: TextView = view.findViewById(R.id.tvReviewDate)
        val rbReviewRating: RatingBar = view.findViewById(R.id.rbReviewRating)
        val tvReviewComment: TextView = view.findViewById(R.id.tvReviewComment)
        val layoutMusicianResponse: LinearLayout = view.findViewById(R.id.layoutMusicianResponse)
        val tvMusicianResponse: TextView = view.findViewById(R.id.tvMusicianResponse)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReviewViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_review, parent, false)
        return ReviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: ReviewViewHolder, position: Int) {
        val review = reviews[position]

        // Nombre
        holder.tvClientName.text = if (review.client != null) "${review.client.nombre} ${review.client.apellido}" else "Cliente Anónimo"

        // Calificación
        holder.rbReviewRating.rating = review.rating.toFloat()

        // Comentario
        if (!review.comment.isNullOrEmpty()) {
            holder.tvReviewComment.text = review.comment
            holder.tvReviewComment.visibility = View.VISIBLE
        } else {
            holder.tvReviewComment.visibility = View.GONE
        }

        // Respuesta del músico
        if (!review.response.isNullOrEmpty()) {
            holder.layoutMusicianResponse.visibility = View.VISIBLE
            holder.tvMusicianResponse.text = review.response
        } else {
            holder.layoutMusicianResponse.visibility = View.GONE
        }

        // Formato de Fecha (simplificado)
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val formatter = SimpleDateFormat("dd MMM, yyyy", Locale("es", "MX"))
            val date = parser.parse(review.created_at.substring(0, 19))
            holder.tvReviewDate.text = date?.let { formatter.format(it) } ?: review.created_at
        } catch (e: Exception) {
            holder.tvReviewDate.text = review.created_at.substring(0, 10)
        }

        val photoUrl = review.client?.photoUrl // 🔥 Asegúrate de que en tu modelo se llama photoUrl

        if (!photoUrl.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(photoUrl)
                .placeholder(R.drawable.ic_user_placeholder) // Muestra esto mientras carga
                .centerCrop()
                .into(holder.ivClientPhoto)
        } else {
            // Si el cliente no tiene foto, ponemos la imagen por defecto
            holder.ivClientPhoto.setImageResource(R.drawable.ic_user_placeholder)
        }
    }

    override fun getItemCount() = reviews.size

    fun updateData(newList: List<ReviewItem>) {
        reviews = newList
        notifyDataSetChanged()
    }
}