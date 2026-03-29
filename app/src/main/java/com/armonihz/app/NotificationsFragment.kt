package com.armonihz.app

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
// Asegúrate de importar ReviewRequest desde donde lo hayas creado
import com.armonihz.app.network.model.ReviewRequest
import com.armonihz.app.network.model.HiringRequestItem
import com.armonihz.app.ui.adapters.NotificationAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

class NotificationsFragment : Fragment() {

    private lateinit var rvNotifications: RecyclerView
    private lateinit var layoutEmptyState: LinearLayout
    private lateinit var adapter: NotificationAdapter

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_notifications, container, false)

        rvNotifications = view.findViewById(R.id.rvNotifications)
        layoutEmptyState = view.findViewById(R.id.layoutEmptyState)

        // Configurar el RecyclerView con AMBOS clics (Tarjeta y Botón de Reseña)
        adapter = NotificationAdapter(
            requestsList = emptyList(),
            onItemClick = { solicitudTocada ->
                // 🔥 AQUÍ ABRIMOS EL PANEL DE DETALLES
                val bottomSheet = RequestDetailBottomSheet(solicitudTocada)
                bottomSheet.show(parentFragmentManager, "RequestDetail")
            },
            onReviewClick = { solicitudParaResena ->
                // 🔥 AQUÍ ABRIMOS EL MODAL DE RESEÑA
                showReviewDialog(solicitudParaResena)
            }
        )

        rvNotifications.layoutManager = LinearLayoutManager(requireContext())
        rvNotifications.adapter = adapter

        // Configurar la navegación inferior
        val bottomNav = view.findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.nav_notifications
        bottomNav.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> { open(HomeFragment()); true }
                R.id.nav_events -> { open(MyEventsFragment()); true }
                R.id.nav_favorites -> { open(FavoritesFragment()); true }
                R.id.nav_notifications -> true
                R.id.nav_profile -> { open(UserProfileFragment()); true }
                else -> false
            }
        }

        // Llamar a Laravel
        cargarNotificaciones()

        return view
    }

    // NUEVO: Función para mostrar el diálogo de reseña
    private fun showReviewDialog(item: HiringRequestItem) {
        val dialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_leave_review, null)
        dialog.setContentView(view)

        val ratingBar = view.findViewById<RatingBar>(R.id.ratingBar)
        val etComment = view.findViewById<TextInputEditText>(R.id.etComment)
        val btnSubmitReview = view.findViewById<Button>(R.id.btnSubmitReview)

        btnSubmitReview.setOnClickListener {
            val rating = ratingBar.rating.toInt()
            val comment = etComment.text.toString().trim()

            if (rating == 0) {
                Toast.makeText(requireContext(), "Por favor selecciona una calificación", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSubmitReview.isEnabled = false
            btnSubmitReview.text = "Enviando..."

            val request = ReviewRequest(
                musician_profile_id = item.musician_profile!!.id,
                rating = rating,
                comment = if (comment.isEmpty()) null else comment,
                hiring_request_id = item.id
            )

            lifecycleScope.launch {
                try {
                    val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)
                    val response = api.createReview(request)

                    if (response.isSuccessful) {
                        Toast.makeText(requireContext(), "¡Reseña enviada con éxito!", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()

                        // Recargamos la lista para actualizar estados
                        cargarNotificaciones()
                    } else {
                        Toast.makeText(requireContext(), "Error al enviar reseña", Toast.LENGTH_SHORT).show()
                        btnSubmitReview.isEnabled = true
                        btnSubmitReview.text = "Enviar reseña"
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
                    btnSubmitReview.isEnabled = true
                    btnSubmitReview.text = "Enviar reseña"
                }
            }
        }

        dialog.show()
    }

    private fun cargarNotificaciones() {
        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        lifecycleScope.launch {
            try {
                val response = api.getMyHiringRequests()
                if (response.isSuccessful && response.body() != null) {
                    val solicitudes = response.body()!!.data

                    if (solicitudes.isEmpty()) {
                        rvNotifications.visibility = View.GONE
                        layoutEmptyState.visibility = View.VISIBLE
                    } else {
                        rvNotifications.visibility = View.VISIBLE
                        layoutEmptyState.visibility = View.GONE
                        adapter.updateData(solicitudes)
                    }
                } else {
                    context?.let { safeContext ->
                        Toast.makeText(
                            safeContext,
                            "No se pudieron cargar las solicitudes",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Notificaciones", "Error de red: ${e.message}")
                context?.let { safeContext ->
                    Toast.makeText(safeContext, "Error de conexión", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun open(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}