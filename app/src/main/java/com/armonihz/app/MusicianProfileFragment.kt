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
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.MaterialDatePicker
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.TimeZone
import com.armonihz.app.network.model.BusyDate
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.applandeo.materialcalendarview.EventDay
import com.applandeo.materialcalendarview.listeners.OnDayClickListener
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.appcompat.widget.PopupMenu
import com.armonihz.app.network.model.ReportRequest

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
        setupRefresh()



        if (musicianId != -1) {
            registrarVistaSilenciosa(musicianId)
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
<<<<<<< HEAD
                    binding.tvLocation.text = musician.location ?: "Ubicación no disponible"
=======
                    binding.tvLocation.text = "📍 ${musician.location ?: "Ubicación no disponible"}"
>>>>>>> 412bd9d2ee7f7558048f9c9b723806b2448b9816
                    binding.tvDescription.text = musician.bio ?: "Sin descripción disponible."

                    if (!musician.hourly_rate.isNullOrEmpty()) {
                        binding.tvHourlyRate.text = "$${musician.hourly_rate} MXN / hora"
                    } else {
                        binding.tvHourlyRate.text = "Tarifa a convenir"
                    }

                    binding.chipVerified.visibility = if (musician.is_verified == 1) View.VISIBLE else View.GONE

                    if (!musician.coverage_notes.isNullOrEmpty()) {
<<<<<<< HEAD
                        binding.tvCoverageNotes.text = musician.coverage_notes
=======
                        binding.tvCoverageNotes.text = "🚗 Cobertura: ${musician.coverage_notes}"
>>>>>>> 412bd9d2ee7f7558048f9c9b723806b2448b9816
                        binding.tvCoverageNotes.visibility = View.VISIBLE
                    }

                    var hasContactInfo = false

                    if (!musician.phone.isNullOrEmpty()) {
<<<<<<< HEAD
                        binding.tvPhoneValue.text = musician.phone
=======
                        binding.tvPhone.text = "Tel: ${musician.phone}"
>>>>>>> 412bd9d2ee7f7558048f9c9b723806b2448b9816
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
<<<<<<< HEAD
                        val igHandle = if (igUser.startsWith("http")) igUser.substringAfterLast("/") else igUser
                        binding.tvInstagramValue.text = "@$igHandle"
=======
                        binding.tvInstagram.text = "IG: @$igUser"
>>>>>>> 412bd9d2ee7f7558048f9c9b723806b2448b9816
                        binding.tvInstagram.visibility = View.VISIBLE
                        hasContactInfo = true
                        binding.tvInstagram.setOnClickListener {
                            val url = if (igUser.startsWith("http")) igUser else "https://instagram.com/$igUser"
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                        }
                    }

                    if (!musician.facebook.isNullOrEmpty()) {
<<<<<<< HEAD
                        val fbRaw = musician.facebook.trim()
                        val fbLabel = fbRaw.substringAfterLast("/").ifEmpty { fbRaw }
                        binding.tvFacebookValue.text = fbLabel
                        binding.tvFacebook.visibility = View.VISIBLE
                        hasContactInfo = true
                        binding.tvFacebook.setOnClickListener {
                            val url = if (fbRaw.startsWith("http")) fbRaw else "https://facebook.com/$fbRaw"
=======
                        val fbUser = musician.facebook.trim()
                        binding.tvFacebook.text = "FB: $fbUser"
                        binding.tvFacebook.visibility = View.VISIBLE
                        hasContactInfo = true
                        binding.tvFacebook.setOnClickListener {
                            val url = if (fbUser.startsWith("http")) fbUser else "https://facebook.com/$fbUser"
>>>>>>> 412bd9d2ee7f7558048f9c9b723806b2448b9816
                            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
                            startActivity(intent)
                        }
                    }

                    if (!musician.youtube.isNullOrEmpty()) {
<<<<<<< HEAD
                        val ytRaw = musician.youtube.trim()
                        val ytLabel = ytRaw.substringAfterLast("/").removePrefix("@").ifEmpty { ytRaw }
                        binding.tvYoutubeValue.text = ytLabel
                        binding.tvYoutube.visibility = View.VISIBLE
                        hasContactInfo = true
                        binding.tvYoutube.setOnClickListener {
                            val url = if (ytRaw.startsWith("http")) ytRaw else "https://youtube.com/$ytRaw"
=======
                        val ytUser = musician.youtube.trim()
                        binding.tvYoutube.text = "YT: $ytUser"
                        binding.tvYoutube.visibility = View.VISIBLE
                        hasContactInfo = true
                        binding.tvYoutube.setOnClickListener {
                            val url = if (ytUser.startsWith("http")) ytUser else "https://youtube.com/$ytUser"
>>>>>>> 412bd9d2ee7f7558048f9c9b723806b2448b9816
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
            }finally {
                // ⬅️ NUEVO: Apagamos el indicador de refresco siempre al terminar
                binding.swipeRefresh.isRefreshing = false
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
        binding.btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        // ⬅️ NUEVO: Conectamos el botón con nuestra función
        binding.btnFav.setOnClickListener {
            toggleFavorite()
        }

        binding.btnMore.setOnClickListener { view ->
            showOptionsMenu(view)
        }



        // Dentro de setupListeners()
        binding.btnContratar.setOnClickListener {
            if (musicianId != -1) {
                val bottomSheet = HiringBottomSheetFragment(musicianId)
                bottomSheet.show(parentFragmentManager, "HiringBottomSheet")
            }
        }
        binding.chipVerCalendario.setOnClickListener {
            if (musicianId != -1) {
                fetchAndShowCalendar(musicianId)
            }
        }
    }

    private fun setupRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            if (musicianId != -1) {
                // Volvemos a pedir los datos a Laravel
                loadMusicianProfile()
                loadReviews()
            } else {
                // Si hay un error y no hay ID, apagamos la animación
                binding.swipeRefresh.isRefreshing = false
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

    private fun fetchAndShowCalendar(musicianId: Int) {
        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        lifecycleScope.launch {
            try {
                binding.chipVerCalendario.text = "Cargando..."
                binding.chipVerCalendario.isEnabled = false

                val response = api.getMusicianAvailability(musicianId)

                if (response.isSuccessful && response.body() != null) {
                    val busyDates = response.body()!!.data
                    showAvailabilityCalendar(busyDates)
                } else {
                    Toast.makeText(context, "Error al cargar disponibilidad", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Sin conexión a internet", Toast.LENGTH_SHORT).show()
            } finally {
                binding.chipVerCalendario.text = "Ver calendario →"
                binding.chipVerCalendario.isEnabled = true
            }
        }
    }

    private fun showAvailabilityCalendar(busyDates: List<BusyDate>) {
        // 1. Inflamos nuestro nuevo diseño en un BottomSheet
        val bottomSheetDialog = BottomSheetDialog(requireContext())
        val view = layoutInflater.inflate(R.layout.dialog_availability_calendar, null)
        bottomSheetDialog.setContentView(view)

        val calendarView = view.findViewById<com.applandeo.materialcalendarview.CalendarView>(R.id.calendarView)
        val tvEventTitle = view.findViewById<android.widget.TextView>(R.id.tvEventTitle)
        val tvEventDetails = view.findViewById<android.widget.TextView>(R.id.tvEventDetails)

        // 2. Preparamos los formatos (Usamos el formato con espacio, como lo tienes en Laravel)
        val apiFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        val eventsList = mutableListOf<EventDay>()
        val datesMap = mutableMapOf<String, MutableList<BusyDate>>()

        // 3. Procesamos las fechas ocupadas para agruparlas por día
        for (date in busyDates) {
            try {
                val startParsed = apiFormat.parse(date.start)
                val endParsed = apiFormat.parse(date.end)

                if (startParsed != null && endParsed != null) {
                    val startCal = Calendar.getInstance().apply { time = startParsed }
                    val endCal = Calendar.getInstance().apply { time = endParsed }

                    val startDayKey = dayKeyFormat.format(startCal.time)
                    val endDayKey = dayKeyFormat.format(endCal.time)

                    // --- AGREGAR AL DÍA DE INICIO ---
                    if (!datesMap.containsKey(startDayKey)) {
                        datesMap[startDayKey] = mutableListOf()
                    }
                    // Evitamos duplicados
                    if (datesMap[startDayKey]?.contains(date) == false) {
                        datesMap[startDayKey]?.add(date)
                        eventsList.add(EventDay(startCal.clone() as Calendar, R.drawable.ic_event_dot))
                    }

                    // --- AGREGAR AL DÍA DE FIN (Si el evento cruza la medianoche) ---
                    if (startDayKey != endDayKey) {
                        if (!datesMap.containsKey(endDayKey)) {
                            datesMap[endDayKey] = mutableListOf()
                        }
                        if (datesMap[endDayKey]?.contains(date) == false) {
                            datesMap[endDayKey]?.add(date)
                            // Le ponemos su propio puntito al día siguiente
                            eventsList.add(EventDay(endCal.clone() as Calendar, R.drawable.ic_event_dot))
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Le pasamos la lista de eventos al calendario para que pinte los puntitos
        calendarView.setEvents(eventsList)

        // Bloqueamos los días del pasado para que no puedan seleccionarlos
        val today = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        calendarView.setMinimumDate(today)
// 4. ¿Qué pasa cuando el usuario toca un día?
        calendarView.setOnDayClickListener(object : OnDayClickListener {
            override fun onDayClick(eventDay: EventDay) {
                val clickedCal = eventDay.calendar

                // Ignoramos el clic si es un día en el pasado
                if (clickedCal.before(today)) {
                    return
                }

                val dayKey = dayKeyFormat.format(clickedCal.time)

                // Ponemos el título (Ej: "1 de Abril")
                val prettyDate = SimpleDateFormat("d 'de' MMMM", Locale("es", "MX")).format(clickedCal.time)
                tvEventTitle.text = "Disponibilidad el $prettyDate"

                // Buscamos si hay eventos en ese día específico
                val eventosDelDia = datesMap[dayKey]

                if (eventosDelDia.isNullOrEmpty()) {
                    tvEventDetails.text = "✅ Todo el día disponible."
                } else {
                    val timeFormat = SimpleDateFormat("hh:mm a", Locale.getDefault()) // Formato 12 hrs
                    val exactTimeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault()) // Formato 24 hrs para validación

                    val sb = java.lang.StringBuilder("Horarios ocupados:\n\n")
                    var isFullyBlocked = false // ⬅️ Bandera para saber si todo el día está bloqueado

                    eventosDelDia.forEach {
                        try {
                            val startParsed = apiFormat.parse(it.start)
                            val endParsed = apiFormat.parse(it.end)

                            if (startParsed != null && endParsed != null) {

                                val startDayKey = dayKeyFormat.format(startParsed)
                                val endDayKey = dayKeyFormat.format(endParsed)

                                val horaInicio = timeFormat.format(startParsed)
                                val horaFin = timeFormat.format(endParsed)

                                // 🔥 NUEVO: Verificamos si empieza a las 00:00:00 y termina a las 23:59:59
                                val isAllDay = exactTimeFormat.format(startParsed) == "00:00:00" &&
                                        exactTimeFormat.format(endParsed) == "23:59:59"

                                if (isAllDay) {
                                    isFullyBlocked = true
                                } else if (startDayKey == dayKey && endDayKey == dayKey) {
                                    // Evento normal (empieza y termina el mismo día)
                                    sb.append("• De $horaInicio a $horaFin\n")
                                } else if (startDayKey == dayKey && endDayKey != dayKey) {
                                    // El evento empieza el día que tocamos, pero termina en la madrugada de mañana
                                    sb.append("• De $horaInicio a $horaFin del día siguiente\n")
                                } else if (startDayKey != dayKey && endDayKey == dayKey) {
                                    // El evento termina el día que tocamos, pero empezó ayer en la noche
                                    sb.append("• De 12:00 a.m. a $horaFin (continuación de evento)\n")
                                }
                            }
                        } catch (e: Exception) { }
                    }

                    // 🔥 NUEVO: Decidimos qué mensaje mostrar final
                    if (isFullyBlocked) {
                        tvEventDetails.text = "🔴 Todo el día ocupado."
                    } else {
                        sb.append("\nEl resto del día está disponible.")
                        tvEventDetails.text = sb.toString()
                    }
                }
            }
        })

        bottomSheetDialog.show()
    }

    // ==========================================
    // MENÚ DE OPCIONES Y REPORTE
    // ==========================================
    private fun showOptionsMenu(anchor: View) {
        val popupMenu = PopupMenu(requireContext(), anchor)
        // Agregamos la opción "Reportar músico" (groupId = 0, itemId = 1, order = 0)
        popupMenu.menu.add(0, 1, 0, "Reportar músico")

        popupMenu.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                1 -> {
                    showReportDialog()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun showReportDialog() {
        val reasons = arrayOf(
            "Contenido inapropiado",
            "Perfil falso o suplantación",
            "Estafa o fraude",
            "Comportamiento poco profesional",
            "Otro motivo"
        )

        // Función auxiliar para convertir dp a píxeles exactos según la pantalla
        val dpToPx = { dp: Int -> (dp * resources.displayMetrics.density).toInt() }

        // 1. Contenedor principal con padding estilo Material (24dp horizontal)
        val container = android.widget.LinearLayout(requireContext()).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dpToPx(24), dpToPx(8), dpToPx(24), 0)
        }

        // 2. Grupo de botones
        val radioGroup = android.widget.RadioGroup(requireContext())

        // 3. TextInputLayout (El contorno Material Design para el campo de texto)
        val textInputLayout = com.google.android.material.textfield.TextInputLayout(
            requireContext(),
            null,
            // Aplicamos el estilo por defecto de Material para campos de texto
            com.google.android.material.R.attr.textInputStyle
        ).apply {
            hint = "Detalla el motivo..."
            visibility = View.GONE // Oculto inicialmente
            boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(0, dpToPx(12), 0, dpToPx(8)) }
        }

        // El campo de texto en sí, dentro del Layout
        val editText = com.google.android.material.textfield.TextInputEditText(textInputLayout.context).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 2
            maxLines = 4
        }
        textInputLayout.addView(editText)

        // 4. Agregar MaterialRadioButtons con espaciado correcto
        reasons.forEachIndexed { index, reason ->
            val radioButton = com.google.android.material.radiobutton.MaterialRadioButton(requireContext()).apply {
                text = reason
                id = index
                textSize = 16f
                layoutParams = android.widget.RadioGroup.LayoutParams(
                    android.widget.RadioGroup.LayoutParams.MATCH_PARENT,
                    android.widget.RadioGroup.LayoutParams.WRAP_CONTENT
                )
                // Espaciado vertical para que los botones sean fáciles de tocar
                setPadding(dpToPx(8), dpToPx(12), dpToPx(8), dpToPx(12))
            }
            radioGroup.addView(radioButton)
        }

        // 5. Escuchar los cambios de selección
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == reasons.size - 1) {
                textInputLayout.visibility = View.VISIBLE
                editText.requestFocus()
            } else {
                textInputLayout.visibility = View.GONE
                editText.text?.clear()
                textInputLayout.error = null // Limpiamos el error visual si cambia de opción
            }
        }

        container.addView(radioGroup)
        container.addView(textInputLayout)

        // 6. Construir el diálogo
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Motivo del reporte")
            .setView(container)
            .setPositiveButton("Enviar", null)
            .setNegativeButton("Cancelar", null)
            .create()

        dialog.show()

        // 7. Configurar la lógica del botón "Enviar" con errores de Material
        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val checkedId = radioGroup.checkedRadioButtonId

            if (checkedId == -1) {
                Toast.makeText(context, "Por favor, selecciona un motivo", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            var finalReason = reasons[checkedId]

            // Si seleccionó "Otro", validamos usando el error nativo de TextInputLayout
            if (checkedId == reasons.size - 1) {
                val customReason = editText.text.toString().trim()
                if (customReason.isEmpty()) {
                    textInputLayout.error = "Este campo es obligatorio" // Muestra el error en rojo debajo del contorno
                    return@setOnClickListener
                }
                textInputLayout.error = null // Quitamos el error si escribió algo válido
                finalReason = customReason
            }

            // --- NUEVO: LLAMADA A LA API ---
            val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

            lifecycleScope.launch {
                try {
                    // Deshabilitar el botón temporalmente para evitar doble clic
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = false

                    val request = ReportRequest(reason = finalReason)
                    val response = api.reportMusician(musicianId, request)

                    if (response.isSuccessful) {
                        Toast.makeText(context, "Reporte enviado correctamente", Toast.LENGTH_LONG)
                            .show()
                        dialog.dismiss()
                    } else {
                        Toast.makeText(context, "Error al enviar el reporte", Toast.LENGTH_SHORT)
                            .show()
                        dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = true
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Sin conexión a internet", Toast.LENGTH_SHORT).show()
                    dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).isEnabled = true
                }
            }

            Toast.makeText(context, "Reporte enviado por: $finalReason", Toast.LENGTH_LONG).show()
            dialog.dismiss()
        }
    }

    private fun registrarVistaSilenciosa(musicianId: Int) {
        lifecycleScope.launch {
            try {
                val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)
                // Disparamos la petición a Laravel
                api.recordProfileView(musicianId)
            } catch (e: Exception) {
                // Falla silenciosa:
                // Si el internet falla, simplemente no se registra la vista,
                // pero no le mostramos ningún error molesto al usuario.
                Log.e("ProfileView", "No se pudo registrar la vista: ${e.message}")
            }
        }
    }

    // 3. Esta es la nueva función que muestra la lista de horas ocupadas


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