package com.armonihz.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        val view = inflater.inflate(R.layout.fragment_user_profile, container, false)

        tvName = view.findViewById(R.id.etuserName)
        tvEmail = view.findViewById(R.id.etuserEmail)
        tvPhone = view.findViewById(R.id.tvUserPhone)
        profileImage = view.findViewById(R.id.profileImage)
        buttonSettings = view.findViewById(R.id.btnSettings)

        val user = FirebaseAuth.getInstance().currentUser

        tvEmail.text = user?.email ?: "Sin correo"

        // 🔵 Cargar foto de Google inmediatamente
        user?.photoUrl?.let {
            Glide.with(this)
                .load(it)
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .circleCrop()
                .into(profileImage)
        }

        observarFotoDesdeApi()
        loadProfileFromApi()

        configurarLogout(view)
        configurarNavegacion(view)

        return view
    }

    private fun bustCache(url: String): String {
        val cleanUrl = url.substringBefore("?")
        return "$cleanUrl?t=${System.currentTimeMillis()}"
    }

    private fun observarFotoDesdeApi() {

        sharedViewModel.profilePhotoUrl.observe(viewLifecycleOwner) { url ->

            val googleUrl = FirebaseAuth.getInstance().currentUser?.photoUrl

            when {

                // 🔵 Foto desde Laravel
                !url.isNullOrEmpty() -> {

                    Glide.with(this)
                        .load(bustCache(url))
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .circleCrop()
                        .into(profileImage)
                }

                // 🔵 Foto de Google
                googleUrl != null -> {

                    Glide.with(this)
                        .load(bustCache(googleUrl.toString()))
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

        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        viewLifecycleOwner.lifecycleScope.launch {

            try {
                // 🔵 Asegúrate de que este método en tu ApiService esté retornando Response<ProfileResponse>
                val response = api.getProfile()

                if (!isAdded) return@launch

                if (response.isSuccessful) {
                    val body = response.body()

                    // 1. Cargar la foto
                    val photoUrl = body?.photoUrl
                    if (!photoUrl.isNullOrEmpty()) {
                        sharedViewModel.updatePhoto(photoUrl)
                    }

                    // 2. Cargar el nombre
                    val nombre   = body?.nombre   ?: ""
                    val apellido = body?.apellido ?: ""
                    val nombreCompleto = "$nombre $apellido".trim()

                    if (nombreCompleto.isNotEmpty()) {
                        tvName.text = nombreCompleto
                    }

                    // 3. Cargar el teléfono (NUEVO)
                    val telefonoApi = body?.telefono
                    if (!telefonoApi.isNullOrEmpty()) {
                        tvPhone.text = telefonoApi
                    } else {
                        tvPhone.text = "Sin teléfono"
                    }
                }

            } catch (_: CancellationException) {
            } catch (e: Exception) {
                e.printStackTrace()
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

        val bottomNavigation = view.findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(R.id.bottomNavigation)

        bottomNavigation.selectedItemId = R.id.nav_profile

        bottomNavigation.setOnItemSelectedListener {

            when (it.itemId) {

                R.id.nav_home -> {
                    open(HomeFragment())
                    true
                }

                R.id.nav_events -> {
                    open(MyEventsFragment())
                    true
                }

                R.id.nav_favorites -> {
                    open(FavoritesFragment())
                    true
                }

                else -> false
            }
        }

        buttonSettings.setOnClickListener {

            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, SettingsFragment())
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