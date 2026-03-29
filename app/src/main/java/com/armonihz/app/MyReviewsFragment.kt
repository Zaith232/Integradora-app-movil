package com.armonihz.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.ui.adapters.MyReviewsAdapter
import kotlinx.coroutines.launch

class MyReviewsFragment : Fragment() {

    private lateinit var rvMyReviews: RecyclerView
    private lateinit var progressBar: ProgressBar
    private lateinit var tvEmptyState: TextView
    private lateinit var adapter: MyReviewsAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_my_reviews, container, false)

        rvMyReviews = view.findViewById(R.id.rvMyReviews)
        progressBar = view.findViewById(R.id.progressBar)
        tvEmptyState = view.findViewById(R.id.tvEmptyState)

        // Configurar botón regresar
        view.findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Inicializar Adapter
        rvMyReviews.layoutManager = LinearLayoutManager(requireContext())
        adapter = MyReviewsAdapter(emptyList())
        rvMyReviews.adapter = adapter

        cargarMisResenas()

        return view
    }

    private fun cargarMisResenas() {
        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        progressBar.visibility = View.VISIBLE
        rvMyReviews.visibility = View.GONE
        tvEmptyState.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = api.getMyReviews()
                progressBar.visibility = View.GONE

                if (response.isSuccessful && response.body() != null) {
                    val reviews = response.body()!!.data
                    if (reviews.isEmpty()) {
                        tvEmptyState.visibility = View.VISIBLE
                    } else {
                        rvMyReviews.visibility = View.VISIBLE
                        adapter.updateData(reviews)
                    }
                } else {
                    Toast.makeText(context, "Error al cargar reseñas", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                Toast.makeText(context, "Error de conexión", Toast.LENGTH_SHORT).show()
            }
        }
    }
}