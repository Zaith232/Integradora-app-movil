package com.armonihz.app

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import androidx.activity.OnBackPressedCallback
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class EditProfileFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var loader: View

    private var nombreOriginal = ""
    private var apellidoOriginal = ""
    private var telefonoOriginal = ""
    private var hayCambios = false
    private lateinit var btnSave: MaterialButton

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_edit_profile, container, false)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        val btnBack = view.findViewById<ImageView>(R.id.btnBackProfile)
        val etName = view.findViewById<EditText>(R.id.etEditName)
        val etLastName = view.findViewById<EditText>(R.id.etEditLastName)
        val etPhone = view.findViewById<EditText>(R.id.etEditPhone)
        val tvEmail = view.findViewById<TextView>(R.id.tvDisplayEmail)
        val tvPhoneHint = view.findViewById<TextView>(R.id.tvPhoneHint)
        btnSave = view.findViewById(R.id.btnSaveProfile)
        btnSave.isEnabled = false
        loader = view.findViewById(R.id.loader)


        // 🔹 Filtros
        etName.filters = arrayOf(soloLetrasFiltro, InputFilter.LengthFilter(40))
        etLastName.filters = arrayOf(soloLetrasFiltro, InputFilter.LengthFilter(40))
        etPhone.filters = arrayOf(soloNumerosFiltro, InputFilter.LengthFilter(10))

        configurarNombreWatcher(etName)
        configurarNombreWatcher(etLastName)
        configurarTelefonoHint(etPhone, tvPhoneHint)

        val user = auth.currentUser

        // 🔹 Cargar datos actuales
        if (user != null) {
            tvEmail.text = user.email

            database.child("usuarios").child(user.uid).get()
                .addOnSuccessListener { snapshot ->

                    if (snapshot.exists()) {

                        nombreOriginal = snapshot.child("nombre").value?.toString() ?: ""
                        apellidoOriginal = snapshot.child("apellido").value?.toString() ?: ""
                        telefonoOriginal = snapshot.child("telefono").value?.toString() ?: ""

                        if (apellidoOriginal.isEmpty() && nombreOriginal.contains(" ")) {
                            val partes = nombreOriginal.split(" ", limit = 2)
                            etName.setText(partes[0])
                            etLastName.setText(partes[1])
                        } else {
                            etName.setText(nombreOriginal)
                            etLastName.setText(apellidoOriginal)
                        }

                        etPhone.setText(telefonoOriginal)
                        evaluarFormulario()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Error al cargar datos", Toast.LENGTH_SHORT).show()
                }
        }

        btnBack.setOnClickListener {
            manejarSalida()
        }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    manejarSalida()
                }
            }
        )

        btnSave.setOnClickListener {

            val nuevoNombre = limpiarTexto(etName.text.toString())
            val nuevoApellido = limpiarTexto(etLastName.text.toString())
            val nuevoTelefono = etPhone.text.toString()

            if (nuevoNombre.length < 2) {
                etName.error = "Nombre muy corto"
                return@setOnClickListener
            }

            if (nuevoApellido.length < 2) {
                etLastName.error = "Apellido muy corto"
                return@setOnClickListener
            }

            if (!esTelefonoValido(nuevoTelefono)) {
                etPhone.error = "Teléfono inválido (10 dígitos)"
                return@setOnClickListener
            }

            if (nuevoNombre == nombreOriginal &&
                nuevoApellido == apellidoOriginal &&
                nuevoTelefono == telefonoOriginal
            ) {
                Toast.makeText(requireContext(), "No hay cambios", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (user != null) {

                btnSave.isEnabled = false
                showLoader()

                val actualizaciones = mapOf<String, Any>(
                    "nombre" to nuevoNombre,
                    "apellido" to nuevoApellido,
                    "telefono" to nuevoTelefono
                )

                database.child("usuarios").child(user.uid)
                    .updateChildren(actualizaciones)
                    .addOnCompleteListener { taskDB ->

                        if (taskDB.isSuccessful) {

                            val nombreCompleto = "$nuevoNombre $nuevoApellido"

                            val profileUpdates = UserProfileChangeRequest.Builder()
                                .setDisplayName(nombreCompleto)
                                .build()

                            user.updateProfile(profileUpdates)
                                .addOnCompleteListener { taskAuth ->

                                    hideLoader()
                                    btnSave.isEnabled = true

                                    if (taskAuth.isSuccessful) {
                                        Toast.makeText(requireContext(), "Perfil actualizado", Toast.LENGTH_SHORT).show()
                                        parentFragmentManager.popBackStack()
                                    } else {
                                        Toast.makeText(requireContext(), "Actualizado en BD pero no en Auth", Toast.LENGTH_SHORT).show()
                                    }
                                }

                        } else {
                            hideLoader()
                            btnSave.isEnabled = true
                            Toast.makeText(requireContext(), "Error al actualizar BD", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }

        return view
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

    private fun configurarNombreWatcher(editText: EditText) {
        editText.addTextChangedListener(object : TextWatcher {
            private var editando = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

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

        })

    }

    private fun configurarTelefonoHint(editText: EditText, tvHint: TextView) {
        editText.addTextChangedListener(object : TextWatcher {

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {

                val telefono = s.toString()

                when {
                    telefono.isEmpty() -> {
                        tvHint.text = ""
                    }

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
        })
    }
    private fun evaluarFormulario() {

        val nombre = limpiarTexto(view?.findViewById<EditText>(R.id.etEditName)?.text.toString())
        val apellido = limpiarTexto(view?.findViewById<EditText>(R.id.etEditLastName)?.text.toString())
        val telefono = view?.findViewById<EditText>(R.id.etEditPhone)?.text.toString()

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

    // 🔹 VALIDACIONES

    private fun limpiarTexto(texto: String): String {
        return texto.trim().replace("\\s+".toRegex(), " ")
    }

    private fun esTelefonoValido(telefono: String): Boolean {
        return telefono.length == 10 || telefono.isEmpty()
    }

    // 🔹 LOADER

    private fun showLoader() {
        loader.visibility = View.VISIBLE
    }

    private fun hideLoader() {
        loader.visibility = View.GONE
    }
}