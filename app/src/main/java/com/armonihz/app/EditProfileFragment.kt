package com.armonihz.app

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.network.model.UpdateProfileRequest
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class EditProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var loader: View

    private var nombreOriginal = ""
    private var apellidoOriginal = ""
    private var telefonoOriginal = ""
    private var hayCambios = false
    private lateinit var btnSave: MaterialButton

    private lateinit var etName: TextInputEditText
    private lateinit var etLastName: TextInputEditText
    private lateinit var etPhone: TextInputEditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_edit_profile, container, false)

        auth = FirebaseAuth.getInstance()

        val btnBack = view.findViewById<ImageView>(R.id.btnBackProfile)
        etName = view.findViewById(R.id.etEditName)
        etLastName = view.findViewById(R.id.etEditLastName)
        etPhone = view.findViewById(R.id.etEditPhone)
        val tvEmail = view.findViewById<TextView>(R.id.tvDisplayEmail)
        val tvPhoneHint = view.findViewById<TextView>(R.id.tvPhoneHint)
        btnSave = view.findViewById(R.id.btnSaveProfile)
        loader = view.findViewById(R.id.loader)

        btnSave.isEnabled = false

        // 🔹 Filtros
        etName.filters = arrayOf(soloLetrasFiltro, InputFilter.LengthFilter(40))
        etLastName.filters = arrayOf(soloLetrasFiltro, InputFilter.LengthFilter(40))
        etPhone.filters = arrayOf(soloNumerosFiltro, InputFilter.LengthFilter(10))

        configurarNombreWatcher(etName)
        configurarNombreWatcher(etLastName)
        configurarTelefonoHint(etPhone, tvPhoneHint)

        val user = auth.currentUser

        if (user != null) {
            tvEmail.text = user.email
            cargarPerfilDesdeApi() // ✅ SOLO backend
        }

        btnBack.setOnClickListener { manejarSalida() }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    manejarSalida()
                }
            }
        )

        btnSave.setOnClickListener {
            guardarPerfilEnApi()
        }

        return view
    }

    // ✅ SOLO usa datos de Laravel
    private fun cargarPerfilDesdeApi() {
        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        showLoader()

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = api.getClientProfile()
                hideLoader()

                if (!isAdded) return@launch

                if (response.isSuccessful) {
                    val body = response.body()

                    // 🔥 EL TRUCO: Comprobamos si el apellido es nulo o está vacío
                    val esPerfilNuevo = body?.apellido.isNullOrEmpty()

                    if (esPerfilNuevo) {
                        // Si es nuevo (viene de Google y no ha guardado en la app), ignoramos la BD y dejamos en blanco
                        nombreOriginal = ""
                        apellidoOriginal = ""
                    } else {
                        // Si ya tiene apellido, significa que el usuario ya guardó su perfil antes. Cargamos sus datos.
                        nombreOriginal = body?.nombre ?: ""
                        apellidoOriginal = body?.apellido ?: ""
                    }

                    telefonoOriginal = body?.telefono ?: ""

                    // Asignamos a los EditText
                    etName.setText(nombreOriginal)
                    etLastName.setText(apellidoOriginal)
                    etPhone.setText(telefonoOriginal)

                    evaluarFormulario()

                } else {
                    Toast.makeText(requireContext(), "Error al cargar perfil", Toast.LENGTH_SHORT).show()
                }

            } catch (_: CancellationException) {
            } catch (e: Exception) {
                hideLoader()
                Toast.makeText(requireContext(), "Error de red", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun guardarPerfilEnApi() {
        val user = auth.currentUser ?: return

        val nuevoNombre = limpiarTexto(etName.text.toString())
        val nuevoApellido = limpiarTexto(etLastName.text.toString())
        val nuevoTelefono = etPhone.text.toString()

        if (nuevoNombre.length < 2) {
            etName.error = "Nombre muy corto"
            return
        }

        if (nuevoApellido.length < 2) {
            etLastName.error = "Apellido muy corto"
            return
        }

        if (!esTelefonoValido(nuevoTelefono)) {
            etPhone.error = "Teléfono inválido"
            return
        }

        btnSave.isEnabled = false
        showLoader()

        val request = UpdateProfileRequest(
            nombre = nuevoNombre,
            apellido = nuevoApellido,
            telefono = nuevoTelefono
        )

        // 🔥 AQUÍ ESTÁ EL LOG PARA ATRAPAR EL ERROR
        Log.d("API_ENVIO", "Enviando a Laravel -> Nombre: [${request.nombre}], Apellido: [${request.apellido}]")

        val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val response = api.updateProfile(request)

                if (!isAdded) return@launch

                if (response.isSuccessful) {
                    val nombreCompleto = "$nuevoNombre $nuevoApellido"
                    val profileUpdates = UserProfileChangeRequest.Builder()
                        .setDisplayName(nombreCompleto)
                        .build()

                    user.updateProfile(profileUpdates).addOnCompleteListener {
                        hideLoader()
                        Toast.makeText(requireContext(), "Perfil actualizado", Toast.LENGTH_SHORT).show()

                        // ✅ Si hay back stack, regresar. Si no, ir a HomeFragment
                        if (parentFragmentManager.backStackEntryCount > 0) {
                            parentFragmentManager.popBackStack()
                        } else {
                            parentFragmentManager.beginTransaction()
                                .replace(R.id.fragmentContainer, HomeFragment())
                                .commit()
                        }
                    }
                }

            } catch (_: CancellationException) {
            } catch (e: Exception) {
                hideLoader()
                btnSave.isEnabled = true
                Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 🔹 FILTROS

    private val soloLetrasFiltro = InputFilter { source, _, _, _, _, _ ->
        if (source.isEmpty()) return@InputFilter null
        val permitido = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyzÁÉÍÓÚáéíóúÑñ "
        val resultado = StringBuilder()
        for (char in source) if (permitido.contains(char)) resultado.append(char)
        if (resultado.isEmpty()) "" else resultado.toString()
    }

    private val soloNumerosFiltro = InputFilter { source, _, _, _, _, _ ->
        if (source.isEmpty()) return@InputFilter null
        val resultado = StringBuilder()
        for (char in source) if (char.isDigit()) resultado.append(char)
        if (resultado.isEmpty()) "" else resultado.toString()
    }

    // 🔹 WATCHERS

    private fun configurarNombreWatcher(editText: TextInputEditText) {
        editText.addTextChangedListener(object : TextWatcher {
            private var editando = false

            override fun afterTextChanged(s: Editable?) {
                if (editando) return
                editando = true

                val texto = s.toString()
                val limpio = texto.replace("\\s+".toRegex(), " ").trimStart()

                val capitalizado = limpio.split(" ").joinToString(" ") {
                    it.lowercase().replaceFirstChar { c -> c.uppercase() }
                }

                if (capitalizado != texto) {
                    editText.setText(capitalizado)
                    editText.setSelection(capitalizado.length)
                }

                editando = false
                evaluarFormulario()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun configurarTelefonoHint(editText: TextInputEditText, tvHint: TextView) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val telefono = s.toString()

                when {
                    telefono.isEmpty() -> tvHint.text = ""
                    telefono.length < 10 -> {
                        tvHint.text = "Faltan ${10 - telefono.length} dígitos"
                        tvHint.setTextColor(Color.RED)
                    }
                    telefono.length == 10 -> {
                        tvHint.text = "Número válido"
                        tvHint.setTextColor(Color.parseColor("#00897B"))
                    }
                }
                evaluarFormulario()
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun evaluarFormulario() {
        val nombre = limpiarTexto(etName.text.toString())
        val apellido = limpiarTexto(etLastName.text.toString())
        val telefono = etPhone.text.toString()

        val nombreValido = nombre.length >= 2
        val apellidoValido = apellido.length >= 2
        val telefonoValido = telefono.length == 10 || telefono.isEmpty()

        hayCambios = nombre != nombreOriginal ||
                apellido != apellidoOriginal ||
                telefono != telefonoOriginal

        btnSave.isEnabled = nombreValido && apellidoValido && telefonoValido && hayCambios
    }

    private fun manejarSalida() {
        if (hayCambios) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Salir sin guardar")
                .setMessage("Tienes cambios sin guardar. ¿Deseas salir?")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Salir") { _, _ ->
                    parentFragmentManager.popBackStack()
                }
                .show()
        } else {
            parentFragmentManager.popBackStack()
        }
    }

    private fun limpiarTexto(texto: String): String {
        return texto.trim().replace("\\s+".toRegex(), " ")
    }

    private fun esTelefonoValido(telefono: String): Boolean {
        return telefono.length == 10 || telefono.isEmpty()
    }

    private fun showLoader() {
        loader.visibility = View.VISIBLE
    }

    private fun hideLoader() {
        loader.visibility = View.GONE
    }
}