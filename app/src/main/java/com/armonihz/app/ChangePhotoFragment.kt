package com.armonihz.app

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.viewmodel.ProfileSharedViewModel
import com.google.android.material.button.MaterialButton
import com.google.android.material.imageview.ShapeableImageView
import com.yalantis.ucrop.UCrop
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.google.firebase.auth.FirebaseAuth

class ChangePhotoFragment : Fragment() {

    private lateinit var ivProfilePicture: ShapeableImageView
    private lateinit var loader: View
    private lateinit var tvDeletePhoto: TextView

    private var finalImageUri: Uri? = null
    private var isDeletingPhoto = false
    private var hasLaravelPhoto = false

    private val sharedViewModel: ProfileSharedViewModel by activityViewModels()

    // 📷 SELECT IMAGE
    private val pickImageLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let { if (isValidImage(it)) startCrop(it) }
        }

    // ✂️ CROP IMAGE
    private val cropImageLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val resultUri = UCrop.getOutput(result.data!!)
                resultUri?.let {
                    finalImageUri = it
                    isDeletingPhoto = false
                    ivProfilePicture.setImageURI(it)
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_change_photo, container, false)

        loader = view.findViewById(R.id.loader)
        val btnBack = view.findViewById<ImageView>(R.id.btnBack)
        ivProfilePicture = view.findViewById(R.id.ivProfilePicture)
        val tvUploadPhoto = view.findViewById<TextView>(R.id.tvUploadPhoto)
        tvDeletePhoto = view.findViewById(R.id.tvDeletePhoto)
        val btnCancel = view.findViewById<MaterialButton>(R.id.btnCancel)
        val btnSaveChanges = view.findViewById<MaterialButton>(R.id.btnSaveChanges)

        val goBack = { requireActivity().supportFragmentManager.popBackStack() }
        btnBack.setOnClickListener { goBack() }
        btnCancel.setOnClickListener { goBack() }

        tvUploadPhoto.setOnClickListener { pickImageLauncher.launch("image/*") }

        tvDeletePhoto.setOnClickListener {
            if (!hasLaravelPhoto) {
                Toast.makeText(requireContext(), "No puedes eliminar la foto de Google", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            ivProfilePicture.setImageResource(android.R.drawable.ic_menu_camera)
            finalImageUri = null
            isDeletingPhoto = true
            sharedViewModel.updatePhoto(null)
            Toast.makeText(requireContext(), "Imagen removida. Guarda para aplicar.", Toast.LENGTH_SHORT).show()
        }

        btnSaveChanges.setOnClickListener {
            btnSaveChanges.isEnabled = false

            when {
                isDeletingPhoto -> deletePhotoFromApi()
                finalImageUri != null -> uploadPhotoToApi(finalImageUri!!)
                else -> {
                    Toast.makeText(requireContext(), "No has realizado cambios", Toast.LENGTH_SHORT).show()
                    btnSaveChanges.isEnabled = true
                }
            }
        }

        observeSharedPhoto()
        loadInitialPhoto()

        return view
    }

    // 👀 OBSERVAR FOTO EN TIEMPO REAL
    private fun observeSharedPhoto() {
        sharedViewModel.profilePhotoUrl.observe(viewLifecycleOwner) { url ->
            if (!url.isNullOrEmpty()) {
                hasLaravelPhoto = true
                enableDeleteButton(true)

                Glide.with(this)
                    .load(url)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .skipMemoryCache(false)
                    .circleCrop()
                    .into(ivProfilePicture)
            }
        }
    }

    // ⚡ CARGA INICIAL INSTANTÁNEA
    private fun loadInitialPhoto() {

        val sharedUrl = sharedViewModel.profilePhotoUrl.value
        val user = FirebaseAuth.getInstance().currentUser

        when {
            !sharedUrl.isNullOrEmpty() -> {
                hasLaravelPhoto = true
                enableDeleteButton(true)

                Glide.with(this)
                    .load(sharedUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .skipMemoryCache(false)
                    .circleCrop()
                    .into(ivProfilePicture)
            }

            user?.photoUrl != null -> {
                hasLaravelPhoto = false
                enableDeleteButton(false)

                Glide.with(this)
                    .load(user.photoUrl)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .skipMemoryCache(false)
                    .circleCrop()
                    .into(ivProfilePicture)
            }

            else -> {
                enableDeleteButton(false)
                ivProfilePicture.setImageResource(android.R.drawable.ic_menu_camera)
                loadCurrentPhoto()
            }
        }
    }

    // 🌐 CARGAR DESDE API SOLO SI NO HAY NADA
    private fun loadCurrentPhoto() {

        val user = FirebaseAuth.getInstance().currentUser ?: return

        user.getIdToken(false).addOnSuccessListener { result ->

            val firebaseToken = result.token ?: return@addOnSuccessListener
            val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val response = api.getClientProfile("Bearer $firebaseToken")

                    if (!isAdded) return@launch

                    val photoUrlLaravel = response.body()?.photoUrl

                    if (!photoUrlLaravel.isNullOrEmpty()) {
                        sharedViewModel.updatePhoto(photoUrlLaravel)
                    }

                } catch (e: Exception) {
                    if (e is CancellationException) return@launch
                }
            }
        }
    }

    private fun uploadPhotoToApi(imageUri: Uri) {

        showLoader()

        val file = File(requireContext().cacheDir, "upload.jpg")
        val inputStream = requireContext().contentResolver.openInputStream(imageUri)
        file.outputStream().use { inputStream?.copyTo(it) }

        val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
        val photoPart = MultipartBody.Part.createFormData("foto", file.name, requestFile)

        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = api.uploadProfilePhoto(photoPart)

                if (!isAdded) return@launch

                if (response.isSuccessful) {
                    val newPhotoUrl = response.body()?.photoUrl
                    sharedViewModel.updatePhoto(newPhotoUrl)
                    Toast.makeText(requireContext(), "Foto actualizada", Toast.LENGTH_SHORT).show()
                    requireActivity().supportFragmentManager.popBackStack()
                } else {
                    Toast.makeText(requireContext(), "Error ${response.code()}", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                if (e is CancellationException) return@launch
            } finally {
                hideLoader()
            }
        }
    }

    private fun deletePhotoFromApi() {

        showLoader()

        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = api.deleteProfilePhoto()

                if (!isAdded) return@launch

                if (response.isSuccessful) {
                    sharedViewModel.updatePhoto(null)
                    hasLaravelPhoto = false
                    enableDeleteButton(false)

                    ivProfilePicture.setImageResource(android.R.drawable.ic_menu_camera)

                    Toast.makeText(requireContext(), "Foto eliminada", Toast.LENGTH_SHORT).show()
                    requireActivity().supportFragmentManager.popBackStack()
                }

            } catch (e: Exception) {
                if (e is CancellationException) return@launch
            } finally {
                hideLoader()
            }
        }
    }

    private fun enableDeleteButton(enable: Boolean) {
        tvDeletePhoto.isEnabled = enable
        tvDeletePhoto.alpha = if (enable) 1f else 0.4f
    }

    private fun startCrop(sourceUri: Uri) {
        val destinationUri = Uri.fromFile(
            File(requireContext().cacheDir, "cropped_${System.currentTimeMillis()}.jpg")
        )

        val options = UCrop.Options().apply {
            setCompressionFormat(android.graphics.Bitmap.CompressFormat.JPEG)
            setCompressionQuality(90)
            setToolbarTitle("Ajustar Foto")
            setCircleDimmedLayer(true)
        }

        val uCropIntent = UCrop.of(sourceUri, destinationUri)
            .withAspectRatio(1f, 1f)
            .withMaxResultSize(800, 800)
            .withOptions(options)
            .getIntent(requireContext())

        cropImageLauncher.launch(uCropIntent)
    }

    private fun isValidImage(uri: Uri): Boolean {
        val contentResolver = requireContext().contentResolver
        val mimeType = contentResolver.getType(uri)

        if (mimeType != "image/jpeg" && mimeType != "image/png") {
            Toast.makeText(requireContext(), "Solo JPG y PNG", Toast.LENGTH_SHORT).show()
            return false
        }

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && sizeIndex != -1) {
                if (cursor.getLong(sizeIndex) > 5 * 1024 * 1024) {
                    Toast.makeText(requireContext(), "Máximo 5MB", Toast.LENGTH_SHORT).show()
                    return false
                }
            }
        }

        return true
    }

    private fun showLoader() {
        loader.visibility = View.VISIBLE
    }

    private fun hideLoader() {
        loader.visibility = View.GONE
    }
}