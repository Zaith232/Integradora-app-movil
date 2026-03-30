package com.armonihz.app.ui.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.armonihz.app.R
import com.armonihz.app.network.model.MyReviewItem
import com.bumptech.glide.Glide
import de.hdodenhof.circleimageview.CircleImageView
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class MyReviewsAdapter(private var reviewsList: List<MyReviewItem>, private val onItemClick: (Int) -> Unit)
    : RecyclerView.Adapter<MyReviewsAdapter.MyReviewViewHolder>() {

    class MyReviewViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivMusicianPhoto: CircleImageView = view.findViewById(R.id.ivMusicianPhoto)
        val tvMusicianName: TextView = view.findViewById(R.id.tvMusicianName)
        val tvReviewDate: TextView = view.findViewById(R.id.tvReviewDate)
        val rbMyRating: RatingBar = view.findViewById(R.id.rbMyRating)
        val tvMyComment: TextView = view.findViewById(R.id.tvMyComment)
        val layoutMusicianResponse: LinearLayout = view.findViewById(R.id.layoutMusicianResponse)
        val tvMusicianResponse: TextView = view.findViewById(R.id.tvMusicianResponse)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyReviewViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_my_review, parent, false)
        return MyReviewViewHolder(view)
    }

    override fun onBindViewHolder(holder: MyReviewViewHolder, position: Int) {
        val review = reviewsList[position]

        holder.tvMusicianName.text = review.musician.stage_name
        holder.rbMyRating.rating = review.rating.toFloat()

        if (!review.comment.isNullOrEmpty()) {
            holder.tvMyComment.text = review.comment
            holder.tvMyComment.visibility = View.VISIBLE
        } else {
            holder.tvMyComment.visibility = View.GONE
        }

        if (!review.response.isNullOrEmpty()) {
            holder.layoutMusicianResponse.visibility = View.VISIBLE
            holder.tvMusicianResponse.text = review.response
        } else {
            holder.layoutMusicianResponse.visibility = View.GONE
        }

        try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            parser.timeZone = TimeZone.getTimeZone("UTC")
            val formatter = SimpleDateFormat("dd MMM, yyyy", Locale("es", "MX"))
            val date = parser.parse(review.created_at.substring(0, 19))
            holder.tvReviewDate.text = date?.let { formatter.format(it) } ?: review.created_at
        } catch (e: Exception) {
            holder.tvReviewDate.text = review.created_at.substring(0, 10)
        }

        if (!review.musician.profile_picture.isNullOrEmpty()) {
            val url = if (review.musician.profile_picture.startsWith("http")) review.musician.profile_picture
            else "https://armonihz-web-armonihz.lugsb1.easypanel.host/file/${review.musician.profile_picture.removePrefix("/")}"

            Glide.with(holder.itemView.context).load(url).centerCrop().into(holder.ivMusicianPhoto)
        } else {
            holder.ivMusicianPhoto.setImageResource(R.drawable.ic_user_placeholder)
        }

        holder.itemView.setOnClickListener {
            onItemClick(review.musician.id)
        }
    }

    override fun getItemCount() = reviewsList.size

    fun updateData(newList: List<MyReviewItem>) {
        reviewsList = newList
        notifyDataSetChanged()
    }
}