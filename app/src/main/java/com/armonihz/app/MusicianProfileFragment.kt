package com.armonihz.app

import android.app.Dialog
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.armonihz.app.databinding.FragmentMusicianProfileBinding
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.network.model.MultimediaItem
import com.armonihz.app.ui.adapters.MultimediaAdapter
import com.armonihz.app.ui.adapters.ReviewAdapter
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import kotlinx.coroutines.launch
import java.util.Locale

class MusicianProfileFragment : Fragment() {

    private var _binding: FragmentMusicianProfileBinding? = null
    private val binding get() = _binding!!

    private var musicianId: Int = -1

    // ⬅️ NUEVO: Variable para controlar el estado del favorito
    private var isFavorite: Boolean = false

    // Creamos dos listas separadas en memoria para fotos y videos
    private var photosList: List<MultimediaItem> = emptyList()
    private var videosList: List<MultimediaItem> = emptyList()

    private lateinit var reviewAdapter: ReviewAdapter

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
        reviewAdapter = ReviewAdapter(emptyList())
        binding.rvReviews.adapter = reviewAdapter

        setupTabs()
        setupListeners()

        if (musicianId != -1) {
            loadMusicianProfile()
            loadReviews()
        } else {
            Toast.makeText(context, "Error: Músico no encontrado", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupTabs() {
        // Pestañas de Información
        binding.btnTabDescription.setOnClickListener { switchTab(true) }
        binding.btnTabContact.setOnClickListener { switchTab(false) }

        // Pestañas de Multimedia
        binding.btnTabPhotos.setOnClickListener { switchMultimediaTab(true) }
        binding.btnTabVideos.setOnClickListener { switchMultimediaTab(false) }
    }

    private fun switchTab(showDescription: Boolean) {
        if (showDescription) {
            binding.layoutDescription.visibility = View.VISIBLE
            binding.layoutContactInfo.visibility = View.GONE

            binding.btnTabDescription.alpha = 1f
            binding.btnTabDescription.setTypeface(null, android.graphics.Typeface.BOLD)
            binding.btnTabContact.alpha = 0.5f
            binding.btnTabContact.setTypeface(null, android.graphics.Typeface.NORMAL)

            binding.indicatorDescription.visibility = View.VISIBLE
            binding.indicatorContact.visibility = View.INVISIBLE
        } else {
            binding.layoutDescription.visibility = View.GONE
            binding.layoutContactInfo.visibility = View.VISIBLE

            binding.btnTabContact.alpha = 1f
            binding.btnTabContact.setTypeface(null, android.graphics.Typeface.BOLD)
            binding.btnTabDescription.alpha = 0.5f
            binding.btnTabDescription.setTypeface(null, android.graphics.Typeface.NORMAL)

            binding.indicatorDescription.visibility = View.INVISIBLE
            binding.indicatorContact.visibility = View.VISIBLE
        }
    }

    private fun switchMultimediaTab(showPhotos: Boolean) {
        if (showPhotos) {
            binding.btnTabPhotos.alpha = 1f
            binding.btnTabPhotos.setTypeface(null, android.graphics.Typeface.BOLD)
            binding.btnTabVideos.alpha = 0.5f
            binding.btnTabVideos.setTypeface(null, android.graphics.Typeface.NORMAL)

            binding.indicatorPhotos.visibility = View.VISIBLE
            binding.indicatorVideos.visibility = View.INVISIBLE

            updateMultimediaAdapter(photosList)
        } else {
            binding.btnTabVideos.alpha = 1f
            binding.btnTabVideos.setTypeface(null, android.graphics.Typeface.BOLD)
            binding.btnTabPhotos.alpha = 0.5f
            binding.btnTabPhotos.setTypeface(null, android.graphics.Typeface.NORMAL)

            binding.indicatorPhotos.visibility = View.INVISIBLE
            binding.indicatorVideos.visibility = View.VISIBLE

            updateMultimediaAdapter(videosList)
        }
    }

    private fun updateMultimediaAdapter(list: List<MultimediaItem>) {
        if (list.isNotEmpty()) {
            binding.rvMultimedia.visibility = View.VISIBLE
            val adapter = MultimediaAdapter(list) { clickedMedia ->
                if (clickedMedia.type == "video") {
                    playVideoFullscreen(clickedMedia.file_path)
                } else {
                    showImageFullscreen(clickedMedia.file_path)
                }
            }
            binding.rvMultimedia.adapter = adapter
        } else {
            binding.rvMultimedia.visibility = View.GONE
        }
    }

    private fun loadMusicianProfile() {
        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        lifecycleScope.launch {
            try {
                val response = api.getMusicianProfile(musicianId)
                if (response.isSuccessful && response.body() != null) {

                    val musician = response.body()!!.data

                    // ⬅️ NUEVO: Leemos el estado del favorito desde la API y actualizamos la UI
                    isFavorite = musician.is_favorite == true
                    updateFavoriteIcon()

                    binding.artistName.text = musician.stage_name
                    binding.tvLocation.text = "📍 ${musician.location ?: "Ubicación no disponible"}"
                    binding.tvDescription.text = musician.bio ?: "Sin descripción disponible."

                    if (!musician.hourly_rate.isNullOrEmpty()) {
                        binding.tvHourlyRate.text = "$${musician.hourly_rate} MXN / hora"
                    } else {
                        binding.tvHourlyRate.text = "Tarifa a convenir"
                    }

                    binding.chipVerified.visibility = if (musician.is_verified == 1) View.VISIBLE else View.GONE

                    if (!musician.coverage_notes.isNullOrEmpty()) {
                        binding.tvCoverageNotes.text = "🚗 Cobertura: ${musician.coverage_notes}"
                        binding.tvCoverageNotes.visibility = View.VISIBLE
                    }

                    var hasContactInfo = false

                    if (!musician.phone.isNullOrEmpty()) {
                        binding.tvPhone.text = "Tel: ${musician.phone}"
                        binding.tvPhone.visibility = View.VISIBLE
                        hasContactInfo = true
                        binding.tvPhone.setOnClickListener {
                            val intent = android.content.Intent(android.content.Intent.ACTION_DIAL)
                            intent.data = Uri.parse("tel:${musician.phone}")
                            startActivity(intent)
                        }
                    }

                    if (!musician.instagram.isNullOrEmpty()) {
                        val igUser = musician.instagram.replace("@", "").trim()
                        binding.tvInstagram.text = "IG: @$igUser"
                        binding.tvInstagram.visibility = View.VISIBLE
                        hasContactInfo = true
                        binding.tvInstagram.setOnClickListener {
                            val url = if (igUser.startsWith("http")) igUser else "https://instagram.com/$igUser"
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                        }
                    }

                    if (!musician.facebook.isNullOrEmpty()) {
                        val fbUser = musician.facebook.trim()
                        binding.tvFacebook.text = "FB: $fbUser"
                        binding.tvFacebook.visibility = View.VISIBLE
                        hasContactInfo = true
                        binding.tvFacebook.setOnClickListener {
                            val url = if (fbUser.startsWith("http")) fbUser else "https://facebook.com/$fbUser"
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                        }
                    }

                    if (!musician.youtube.isNullOrEmpty()) {
                        val ytUser = musician.youtube.trim()
                        binding.tvYoutube.text = "YT: $ytUser"
                        binding.tvYoutube.visibility = View.VISIBLE
                        hasContactInfo = true
                        binding.tvYoutube.setOnClickListener {
                            val url = if (ytUser.startsWith("http")) ytUser else "https://youtube.com/$ytUser"
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                        }
                    }

                    if (!hasContactInfo) {
                        binding.tvNoContact.visibility = View.VISIBLE
                    } else {
                        binding.tvNoContact.visibility = View.GONE
                    }

                    if (musician.profile_picture.isNullOrEmpty()) {
                        Glide.with(this@MusicianProfileFragment).clear(binding.heroImage)
                        binding.heroImage.setImageDrawable(null)
                        binding.heroImage.setBackgroundColor(Color.parseColor("#E2E8F0"))
                    } else {
                        binding.heroImage.setBackgroundColor(Color.TRANSPARENT)

                        val fullImageUrl = if (musician.profile_picture.startsWith("http")) {
                            musician.profile_picture
                        } else {
                            val cleanPath = musician.profile_picture.removePrefix("/")
                            "https://armonihz-web-armonihz.lugsb1.easypanel.host/file/$cleanPath"
                        }

                        Glide.with(this@MusicianProfileFragment)
                            .load("https://images.unsplash.com/photo-1514525253161-7a46d19cd819?ixlib=rb-4.0.3&auto=format&fit=crop&w=1600&q=80")
                            .diskCacheStrategy(DiskCacheStrategy.ALL)
                            .skipMemoryCache(false)
                            .centerCrop()
                            .into(binding.heroImage)
                        val matrix = android.graphics.ColorMatrix()
                        matrix.setSaturation(0f)

                        val filter = android.graphics.ColorMatrixColorFilter(matrix)
                        binding.heroImage.colorFilter = filter

                        Glide.with(this@MusicianProfileFragment)
                            .load(fullImageUrl)
                            .centerCrop()
                            .into(binding.profileImage)
                    }

                    photosList = musician.media?.photos?.map {
                        MultimediaItem(id = it.id, type = "image", file_path = it.url)
                    } ?: emptyList()

                    videosList = musician.media?.videos?.map {
                        MultimediaItem(id = it.id, type = "video", file_path = it.url)
                    } ?: emptyList()

                    if (photosList.isNotEmpty() || videosList.isNotEmpty()) {
                        binding.layoutMultimediaTabs.visibility = View.VISIBLE

                        if (photosList.isNotEmpty()) {
                            switchMultimediaTab(true)
                        } else {
                            switchMultimediaTab(false)
                        }
                    } else {
                        binding.layoutMultimediaTabs.visibility = View.GONE
                        binding.rvMultimedia.visibility = View.GONE
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

    // ==========================================
    // LÓGICA DE FAVORITOS (NUEVO)
    // ==========================================
    private fun toggleFavorite() {
        if (musicianId == -1) return

        // 1. Cambio optimista para que la UI se sienta instantánea
        isFavorite = !isFavorite
        updateFavoriteIcon()

        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        // 2. Llamada a la API
        lifecycleScope.launch {
            try {
                val response = if (isFavorite) {
                    api.addFavorite(musicianId)
                } else {
                    api.removeFavorite(musicianId)
                }

                if (!response.isSuccessful) {
                    // Si el servidor falla, revertimos el cambio en la UI
                    isFavorite = !isFavorite
                    updateFavoriteIcon()
                    Toast.makeText(context, "Error al actualizar favoritos", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                // Si no hay internet, revertimos el cambio
                isFavorite = !isFavorite
                updateFavoriteIcon()
                Toast.makeText(context, "Sin conexión a internet", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateFavoriteIcon() {
        if (isFavorite) {
            binding.btnFav.setIconResource(R.drawable.ic_favorite)
        } else {
            binding.btnFav.setIconResource(R.drawable.ic_favorite_border)
        }
    }

    // ==========================================
    // VISOR DE IMÁGENES DENTRO DE LA APP
    // ==========================================
    private fun showImageFullscreen(imageUrl: String) {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

        val frameLayout = FrameLayout(requireContext())
        frameLayout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        frameLayout.setBackgroundColor(Color.parseColor("#D9000000"))

        val imageView = ImageView(requireContext())
        imageView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        imageView.scaleType = ImageView.ScaleType.FIT_CENTER
        Glide.with(this).load(imageUrl).into(imageView)

        val closeButton = ImageButton(requireContext())
        val btnParams = FrameLayout.LayoutParams(120, 120)
        btnParams.gravity = Gravity.TOP or Gravity.END
        btnParams.setMargins(0, 60, 40, 0)
        closeButton.layoutParams = btnParams
        closeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        closeButton.setBackgroundColor(Color.TRANSPARENT)
        closeButton.setColorFilter(Color.WHITE)
        closeButton.setOnClickListener { dialog.dismiss() }

        frameLayout.addView(imageView)
        frameLayout.addView(closeButton)

        dialog.setContentView(frameLayout)
        dialog.show()
    }

    // ==========================================
    // REPRODUCTOR DE VIDEO CON EXOPLAYER Y FONDO TRANSPARENTE
    // ==========================================
    @OptIn(UnstableApi::class)
    private fun playVideoFullscreen(videoUrl: String) {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen)

        val frameLayout = FrameLayout(requireContext())
        frameLayout.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        frameLayout.setBackgroundColor(Color.parseColor("#D9000000"))

        val playerView = PlayerView(requireContext())
        playerView.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        )

        playerView.setBackgroundColor(Color.TRANSPARENT)
        playerView.setShutterBackgroundColor(Color.TRANSPARENT)

        val exoPlayer = ExoPlayer.Builder(requireContext()).build()
        playerView.player = exoPlayer

        val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        val closeButton = ImageButton(requireContext())
        val btnParams = FrameLayout.LayoutParams(120, 120)
        btnParams.gravity = Gravity.TOP or Gravity.END
        btnParams.setMargins(0, 60, 40, 0)
        closeButton.layoutParams = btnParams
        closeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
        closeButton.setBackgroundColor(Color.TRANSPARENT)
        closeButton.setColorFilter(Color.WHITE)

        closeButton.setOnClickListener {
            exoPlayer.release()
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            exoPlayer.release()
        }

        frameLayout.addView(playerView)
        frameLayout.addView(closeButton)

        dialog.setContentView(frameLayout)
        dialog.show()
    }

    private fun setupListeners() {
        // ⬅️ NUEVO: Conectamos el botón con nuestra función
        binding.btnFav.setOnClickListener {
            toggleFavorite()
        }

        binding.bottomNavigation.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
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
                R.id.nav_notifications -> { open(NotificationsFragment()); true }
                R.id.nav_profile -> {
                    open(UserProfileFragment())
                    true
                }
                else -> false
            }
        }

        // Dentro de setupListeners()
        binding.btnContratar.setOnClickListener {
            if (musicianId != -1) {
                val bottomSheet = HiringBottomSheetFragment(musicianId)
                bottomSheet.show(parentFragmentManager, "HiringBottomSheet")
            }
        }
    }

    private fun loadReviews() {
        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        lifecycleScope.launch {
            try {
                val response = api.getMusicianReviews(musicianId)
                if (response.isSuccessful && response.body() != null) {
                    val reviews = response.body()!!.data

                    if (reviews.isNotEmpty()) {
                        binding.rvReviews.visibility = View.VISIBLE
                        binding.tvNoReviews.visibility = View.GONE
                        reviewAdapter.updateData(reviews)

                        // BONUS: ¡Actualizamos la cajita de valoración de arriba en tu Card!
                        val average = reviews.map { it.rating }.average()
                        binding.tvRating.text = String.format(Locale.US, "%.1f ⭐", average)
                    } else {
                        binding.rvReviews.visibility = View.GONE
                        binding.tvNoReviews.visibility = View.VISIBLE
                        binding.tvRating.text = "Nuevo" // Si no tiene reseñas
                    }
                }
            } catch (e: Exception) {
                Log.e("REVIEWS_ERROR", "Error al cargar reseñas: ${e.message}")
            }
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