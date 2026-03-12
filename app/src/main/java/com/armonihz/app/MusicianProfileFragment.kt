package com.armonihz.app

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.armonihz.app.databinding.FragmentMusicianProfileBinding
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.launch

class MusicianProfileFragment : Fragment() {

    private var _binding: FragmentMusicianProfileBinding? = null
    private val binding get() = _binding!!

    private var musicianId: Int = -1

    companion object {
        private const val ARG_MUSICIAN_ID = "musician_id"

        fun newInstance(musicianId: Int): MusicianProfileFragment {
            val fragment = MusicianProfileFragment()
            val args = Bundle()
            args.putInt(ARG_MUSICIAN_ID, musicianId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            musicianId = it.getInt(ARG_MUSICIAN_ID, -1)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMusicianProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupTabs() // ⬅️ Inicializamos el comportamiento de las pestañas
        setupListeners()

        if (musicianId != -1) {
            loadMusicianProfile()
        } else {
            Toast.makeText(context, "Error: Músico no encontrado", Toast.LENGTH_SHORT).show()
        }
    }

    // ==========================================
    // LÓGICA DE PESTAÑAS (TABS)
    // ==========================================
    private fun setupTabs() {
        binding.btnTabDescription.setOnClickListener { switchTab(true) }
        binding.btnTabContact.setOnClickListener { switchTab(false) }
    }

    private fun switchTab(showDescription: Boolean) {
        if (showDescription) {
            // Mostrar Descripción, ocultar Contacto
            binding.layoutDescription.visibility = View.VISIBLE
            binding.layoutContactInfo.visibility = View.GONE

            // Resaltar pestaña Descripción
            binding.btnTabDescription.alpha = 1f
            binding.btnTabDescription.setTypeface(null, android.graphics.Typeface.BOLD)
            binding.btnTabContact.alpha = 0.5f
            binding.btnTabContact.setTypeface(null, android.graphics.Typeface.NORMAL)
        } else {
            // Mostrar Contacto, ocultar Descripción
            binding.layoutDescription.visibility = View.GONE
            binding.layoutContactInfo.visibility = View.VISIBLE

            // Resaltar pestaña Contacto
            binding.btnTabContact.alpha = 1f
            binding.btnTabContact.setTypeface(null, android.graphics.Typeface.BOLD)
            binding.btnTabDescription.alpha = 0.5f
            binding.btnTabDescription.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
    }

    // ==========================================
    // CARGAR DATOS DEL MÚSICO
    // ==========================================
    private fun loadMusicianProfile() {
        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        lifecycleScope.launch {
            try {
                val response = api.getMusicianProfile(musicianId)
                if (response.isSuccessful && response.body() != null) {

                    val musician = response.body()!!.data

                    // Asignación de textos básicos
                    binding.artistName.text = musician.stage_name
                    binding.tvLocation.text = "📍 ${musician.location ?: "Ubicación no disponible"}"
                    binding.tvDescription.text = musician.bio ?: "Sin descripción disponible."

                    if (!musician.hourly_rate.isNullOrEmpty()) {
                        binding.tvHourlyRate.text = "$${musician.hourly_rate} MXN / hora"
                    } else {
                        binding.tvHourlyRate.text = "Tarifa a convenir"
                    }

                    binding.tvVerified.visibility = if (musician.is_verified == 1) View.VISIBLE else View.GONE

                    if (!musician.coverage_notes.isNullOrEmpty()) {
                        binding.tvCoverageNotes.text = "🚗 Cobertura: ${musician.coverage_notes}"
                        binding.tvCoverageNotes.visibility = View.VISIBLE
                    }

                    // ⬅️ LÓGICA DE REDES SOCIALES E INTENTS
                    var hasContactInfo = false

                    // Teléfono
                    if (!musician.phone.isNullOrEmpty()) {
                        binding.tvPhone.text = "Tel: ${musician.phone}"
                        binding.tvPhone.visibility = View.VISIBLE
                        hasContactInfo = true

                        binding.tvPhone.setOnClickListener {
                            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL)
                            intent.data = android.net.Uri.parse("tel:${musician.phone}")
                            startActivity(intent)
                        }
                    }

                    // Instagram
                    if (!musician.instagram.isNullOrEmpty()) {
                        val igUser = musician.instagram.replace("@", "").trim()
                        binding.tvInstagram.text = "IG: @$igUser"
                        binding.tvInstagram.visibility = View.VISIBLE
                        hasContactInfo = true

                        binding.tvInstagram.setOnClickListener {
                            val url = if (igUser.startsWith("http")) igUser else "https://instagram.com/$igUser"
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            startActivity(intent)
                        }
                    }

                    // Facebook
                    if (!musician.facebook.isNullOrEmpty()) {
                        val fbUser = musician.facebook.trim()
                        binding.tvFacebook.text = "FB: $fbUser"
                        binding.tvFacebook.visibility = View.VISIBLE
                        hasContactInfo = true

                        binding.tvFacebook.setOnClickListener {
                            val url = if (fbUser.startsWith("http")) fbUser else "https://facebook.com/$fbUser"
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            startActivity(intent)
                        }
                    }

                    // YouTube
                    if (!musician.youtube.isNullOrEmpty()) {
                        val ytUser = musician.youtube.trim()
                        binding.tvYoutube.text = "YT: $ytUser"
                        binding.tvYoutube.visibility = View.VISIBLE
                        hasContactInfo = true

                        binding.tvYoutube.setOnClickListener {
                            val url = if (ytUser.startsWith("http")) ytUser else "https://youtube.com/$ytUser"
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            startActivity(intent)
                        }
                    }

                    // Si no tiene ningún dato de contacto, mostramos el mensaje vacío
                    if (!hasContactInfo) {
                        binding.tvNoContact.visibility = View.VISIBLE
                    } else {
                        binding.tvNoContact.visibility = View.GONE
                    }

                    // Lógica de la Foto con Glide
                    if (musician.profile_picture.isNullOrEmpty()) {
                        com.bumptech.glide.Glide.with(this@MusicianProfileFragment).clear(binding.heroImage)
                        binding.heroImage.setImageDrawable(null)
                        binding.heroImage.setBackgroundColor(android.graphics.Color.parseColor("#E2E8F0"))
                    } else {
                        binding.heroImage.setBackgroundColor(android.graphics.Color.TRANSPARENT)

                        val fullImageUrl = if (musician.profile_picture.startsWith("http")) {
                            musician.profile_picture
                        } else {
                            val cleanPath = musician.profile_picture.removePrefix("/")
                            "https://armonihz-web-armonihz.lugsb1.easypanel.host/storage/$cleanPath"
                        }

                        com.bumptech.glide.Glide.with(this@MusicianProfileFragment)
                            .load(fullImageUrl)
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                            .skipMemoryCache(false)
                            .centerCrop()
                            .into(binding.heroImage)
                    }

                } else {
                    Toast.makeText(context, "Error al cargar datos del músico", Toast.LENGTH_SHORT).show()
                    Log.e("API_ERROR", "Código: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("API_ERROR", "Excepción: ${e.message}")
                Toast.makeText(context, "Error de conexión", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupListeners() {
        binding.btnHome.setOnClickListener { open(HomeFragment()) }
        binding.btnEvent.setOnClickListener { open(MyEventsFragment()) }
        binding.btnFavorite.setOnClickListener { open(FavoritesFragment()) }
        binding.btnProfile.setOnClickListener { open(UserProfileFragment()) }

        binding.btnFav.setOnClickListener {
            Toast.makeText(context, "Añadido a favoritos ❤️", Toast.LENGTH_SHORT).show()
        }
    }

    private fun open(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}