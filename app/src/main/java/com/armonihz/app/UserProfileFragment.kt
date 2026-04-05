package com.armonihz.app

import android.content.Context
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
import com.bumptech.glide.signature.ObjectKey
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class UserProfileFragment : Fragment() {

    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var tvPhone: TextView
    private lateinit var profileImage: ShapeableImageView
    private lateinit var buttonSettings: MaterialButton
    private lateinit var buttonReviews: MaterialButton

    private val sharedViewModel: ProfileSharedViewModel by activityViewModels()

    // ✅ MEJORA 4: ApiService se crea una sola vez con lazy
    private val api by lazy {
        RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)
    }

    // ✅ MEJORA 1: Referencia a SharedPreferences para caché local
    private val profilePrefs by lazy {
        requireContext().getSharedPreferences("profile_cache", Context.MODE_PRIVATE)
    }

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
        buttonReviews = view.findViewById(R.id.btnMyReviews)

        val user = FirebaseAuth.getInstance().currentUser
        tvEmail.text = user?.email ?: "Sin correo"

        // ✅ MEJORA 1: Mostrar datos del caché local INMEDIATAMENTE (sin esperar la API)
        mostrarCacheLocal()

        // Foto de Google como fallback mientras llega la API
        if (profilePrefs.getString("photo_url", null).isNullOrEmpty()) {
            user?.photoUrl?.let {
                cargarFotoEnImageView(it.toString(), "google")
            }
        }

        observarFotoDesdeApi()
        loadProfileFromApi()
        configurarLogout(view)
        configurarNavegacion(view)

        return view
    }

    // ✅ MEJORA 1: Mostrar datos guardados localmente antes de llamar a la API
    private fun mostrarCacheLocal() {
        val nombreCache = profilePrefs.getString("nombre", null)
        val telefonoCache = profilePrefs.getString("telefono", null)
        val photoUrlCache = profilePrefs.getString("photo_url", null)

        if (!nombreCache.isNullOrEmpty()) tvName.text = nombreCache
        if (!telefonoCache.isNullOrEmpty()) tvPhone.text = telefonoCache else tvPhone.text = "Sin teléfono"
        if (!photoUrlCache.isNullOrEmpty()) sharedViewModel.updatePhoto(photoUrlCache)
    }

    // ✅ MEJORA 1: Guardar datos en caché local después de obtenerlos de la API
    private fun guardarCacheLocal(nombre: String, telefono: String?, photoUrl: String?) {
        profilePrefs.edit()
            .putString("nombre", nombre)
            .putString("telefono", telefono ?: "")
            .putString("photo_url", photoUrl ?: "")
            .apply()
    }

    // ✅ MEJORA 3: Usar Glide signature en lugar de bustCache con timestamp en URL
    // Esto respeta el caché de disco y solo re-descarga cuando la foto cambia realmente
    private fun cargarFotoEnImageView(url: String, versionKey: String) {
        if (!isAdded) return
        Glide.with(this)
            .load(url)
            .signature(ObjectKey(versionKey))    // ← reemplaza bustCache()
            .diskCacheStrategy(DiskCacheStrategy.ALL)
            .circleCrop()
            .placeholder(R.drawable.ic_user_placeholder)
            .into(profileImage)
    }

    private fun observarFotoDesdeApi() {
        sharedViewModel.profilePhotoUrl.observe(viewLifecycleOwner) { url ->
            val googleUrl = FirebaseAuth.getInstance().currentUser?.photoUrl

            when {
                !url.isNullOrEmpty() -> cargarFotoEnImageView(url, url)

                googleUrl != null -> cargarFotoEnImageView(googleUrl.toString(), googleUrl.toString())

                else -> {
                    // 🔥 SOLUCIÓN 2: Limpiar la memoria caché visual de Glide
                    Glide.with(this).clear(profileImage)
                    profileImage.setImageResource(R.drawable.ic_user_placeholder) // o ic_menu_camera
                }
            }
        }
    }

    private fun loadProfileFromApi() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = api.getProfile()

                if (!isAdded) return@launch

                if (response.isSuccessful) {
                    val body = response.body()

                    // 🔥 SOLUCIÓN 1: Actualizar SIEMPRE el ViewModel, incluso si es null
                    val photoUrl = body?.photoUrl
                    sharedViewModel.updatePhoto(photoUrl)

                    // Nombre
                    val nombre = body?.nombre ?: ""
                    val apellido = body?.apellido ?: ""
                    val nombreCompleto = "$nombre $apellido".trim()
                    if (nombreCompleto.isNotEmpty()) {
                        tvName.text = nombreCompleto
                    }

                    // Teléfono
                    val telefono = body?.telefono
                    tvPhone.text = if (!telefono.isNullOrEmpty()) telefono else "Sin teléfono"

                    // Guardar en caché local
                    guardarCacheLocal(nombreCompleto, telefono, photoUrl)
                }

            } catch (_: CancellationException) {
                // Ignorar cancelaciones normales
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

                    // ✅ Limpiar caché local al cerrar sesión
                    profilePrefs.edit().clear().apply()

                    googleSignInClient.signOut().addOnCompleteListener {
                        LoadingManager.hide()
                        val intent = Intent(requireContext(), LoginActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        requireActivity().finishAffinity()
                    }
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }

    private fun configurarNavegacion(view: View) {

        buttonSettings.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, SettingsFragment())
                .addToBackStack(null)
                .commit()
        }

        buttonReviews.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, MyReviewsFragment())
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