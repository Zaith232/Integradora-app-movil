package com.armonihz.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.armonihz.app.auth.TokenManager
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.utils.LoadingManager
import com.armonihz.app.viewmodel.ProfileSharedViewModel
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class UserProfileFragment : Fragment() {

    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvPhone: TextView
    private lateinit var profileImage: ShapeableImageView
    private lateinit var buttonSettings: MaterialButton

    private val sharedViewModel: ProfileSharedViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_user_profile, container, false)

        tvName = view.findViewById(R.id.etuserName)
        tvEmail = view.findViewById(R.id.etuserEmail)
        tvPhone = view.findViewById(R.id.tvUserPhone)
        profileImage = view.findViewById(R.id.profileImage)
        buttonSettings = view.findViewById(R.id.btnSettings)

        val user = FirebaseAuth.getInstance().currentUser

        tvEmail.text = user?.email ?: "Sin correo"

        // 📸 FOTO DE FIREBASE INMEDIATA
        if (user?.photoUrl != null) {
            Glide.with(this)
                .load(user.photoUrl)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .circleCrop()
                .into(profileImage)
        }

        observarFotoDesdeApi()
        cargarDatosDesdeRealtime()
        loadProfileFromApi()

        configurarLogout(view)
        configurarNavegacion(view)

        return view
    }

    override fun onResume() {
        super.onResume()
        cargarDatosDesdeRealtime() // 🔄 se actualiza al volver de Editar Perfil
    }

    // 🔹 Cargar nombre y teléfono desde Realtime Database
    private fun cargarDatosDesdeRealtime() {

        val user = FirebaseAuth.getInstance().currentUser ?: return
        val database = com.google.firebase.database.FirebaseDatabase.getInstance().reference

        database.child("usuarios").child(user.uid).get()
            .addOnSuccessListener { snapshot ->

                if (!isAdded) return@addOnSuccessListener

                if (snapshot.exists()) {

                    val nombre = snapshot.child("nombre").value?.toString() ?: ""
                    val apellido = snapshot.child("apellido").value?.toString() ?: ""
                    val telefono = snapshot.child("telefono").value?.toString() ?: ""

                    tvName.text = "$nombre $apellido".trim()
                    tvPhone.text = if (telefono.isNotEmpty()) telefono else "Sin teléfono"

                } else {
                    tvName.text = user.displayName ?: "Sin nombre"
                    tvPhone.text = "Sin teléfono"
                }
            }
    }

    private fun observarFotoDesdeApi() {
        sharedViewModel.profilePhotoUrl.observe(viewLifecycleOwner) { url ->

            val googleUrl = FirebaseAuth.getInstance().currentUser?.photoUrl

            when {
                !url.isNullOrEmpty() -> {
                    Glide.with(this)
                        .load(url)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .circleCrop()
                        .into(profileImage)
                }

                googleUrl != null -> {
                    Glide.with(this)
                        .load(googleUrl)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .circleCrop()
                        .into(profileImage)
                }

                else -> {
                    profileImage.setImageResource(R.drawable.ic_user_placeholder)
                }
            }
        }
    }

    private fun loadProfileFromApi() {

        val user = FirebaseAuth.getInstance().currentUser ?: return

        user.getIdToken(false).addOnSuccessListener { result ->

            val firebaseToken = result.token ?: return@addOnSuccessListener
            val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

            viewLifecycleOwner.lifecycleScope.launch {
                try {

                    val response = api.getClientProfile("Bearer $firebaseToken")

                    if (!isAdded) return@launch

                    if (response.isSuccessful) {

                        val photoUrl = response.body()?.photoUrl

                        if (!photoUrl.isNullOrEmpty()) {
                            sharedViewModel.updatePhoto(photoUrl)
                        }
                    }

                } catch (_: CancellationException) {
                }
            }
        }
    }

    private fun configurarLogout(view: View) {

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .build()

        val googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)

        view.findViewById<Button>(R.id.btnLogout).setOnClickListener {

            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle("Cerrar sesión")
                .setMessage("¿Estás seguro de que deseas cerrar sesión?")
                .setPositiveButton("Sí, cerrar sesión") { _, _ ->

                    LoadingManager.show(requireActivity(), "Cerrando sesión...")

                    FirebaseAuth.getInstance().signOut()
                    TokenManager.clearToken(requireContext())

                    googleSignInClient.signOut().addOnCompleteListener {

                        LoadingManager.hide()

                        val intent = Intent(requireContext(), LoginActivity::class.java)
                        intent.flags =
                            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

                        startActivity(intent)
                        requireActivity().finishAffinity()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun configurarNavegacion(view: View) {

        view.findViewById<Button>(R.id.btnHome).setOnClickListener { open(HomeFragment()) }
        view.findViewById<Button>(R.id.btnFavorite).setOnClickListener { open(FavoritesFragment()) }
        view.findViewById<Button>(R.id.btnEvent).setOnClickListener { open(MyEventsFragment()) }

        buttonSettings.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, SettingsFragment())
                .addToBackStack(null)
                .commit()
        }

        view.findViewById<Button>(R.id.btnMyReviews).setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ReviewsFragment())
                .addToBackStack(null)
                .commit()
        }
    }

    private fun open(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}