package com.armonihz.app

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.network.model.UpdateProfileRequest
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.launch
import java.io.InputStream

class CompleteProfileFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_complete_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val etNombre   = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etNombre)
        val etApellido = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.etApellido)
        val btnGuardar = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnGuardar)

        // Referencias a los componentes de términos
        val cbTerms = view.findViewById<MaterialCheckBox>(R.id.cbTermsProfile)
        val tvTerms = view.findViewById<TextView>(R.id.tvTermsTextProfile)

        // Precarga el nombre que trajo Google/Firebase
        val user = FirebaseAuth.getInstance().currentUser
        val displayName = user?.displayName ?: ""
        val partes = displayName.trim().split(" ")
        etNombre.setText(partes.firstOrNull() ?: "")

        // Configuramos el texto clickeable de los términos
        configurarTerminosYCondiciones(tvTerms, cbTerms)

        btnGuardar.setOnClickListener {
            // 1. Validar CheckBox
            if (!cbTerms.isChecked) {
                Toast.makeText(requireContext(), "Debes aceptar los Términos y Condiciones para continuar", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val nombre   = etNombre.text.toString().trim()
            val apellido = etApellido.text.toString().trim()

            // 2. Validar campos de texto
            if (nombre.isEmpty() || apellido.isEmpty()) {
                Toast.makeText(requireContext(), "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnGuardar.isEnabled = false
            btnGuardar.text = "Guardando..."

            lifecycleScope.launch {
                try {
                    val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

                    val request = UpdateProfileRequest(
                        nombre = nombre,
                        apellido = apellido,
                        telefono = ""
                    )

                    val response = api.updateProfile(request)

                    if (response.isSuccessful) {

                        // 🔥 LIBERACIÓN: Quitamos el candado de la memoria del teléfono
                        val prefs = requireContext().getSharedPreferences("ArmonihzPrefs", Context.MODE_PRIVATE)
                        prefs.edit().putBoolean("perfil_incompleto", false).apply()

                        // Actualizamos el nombre en Firebase Auth para que coincida
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName("$nombre $apellido")
                            .build()

                        user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                            if (isAdded) {
                                // Navegamos al Home
                                parentFragmentManager.beginTransaction()
                                    .replace(R.id.fragmentContainer, HomeFragment())
                                    .commit()
                            }
                        }
                    } else {
                        btnGuardar.isEnabled = true
                        btnGuardar.text = "Guardar"
                        Toast.makeText(requireContext(), "Error al guardar en la base de datos", Toast.LENGTH_SHORT).show()
                    }

                } catch (e: Exception) {
                    btnGuardar.isEnabled = true
                    btnGuardar.text = "Guardar"
                    Toast.makeText(requireContext(), "Error de conexión", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // ── Lógica de Términos y Condiciones ──────────────────────────────────────

    private fun configurarTerminosYCondiciones(tvTerms: TextView, cbTerms: MaterialCheckBox) {
        val textoCompleto = "Acepto los Términos y Condiciones"
        val spannableString = SpannableString(textoCompleto)

        val clickableSpan = object : ClickableSpan() {
            override fun onClick(widget: View) {
                mostrarPantallaDeTerminos(cbTerms)
            }

            override fun updateDrawState(ds: TextPaint) {
                super.updateDrawState(ds)
                ds.color = ContextCompat.getColor(requireContext(), R.color.md_primary)
                ds.isUnderlineText = false
                ds.isFakeBoldText = true
            }
        }

        val startIndex = textoCompleto.indexOf("Términos")
        val endIndex = textoCompleto.length

        spannableString.setSpan(clickableSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        tvTerms.text = spannableString
        tvTerms.movementMethod = LinkMovementMethod.getInstance()
        tvTerms.highlightColor = Color.TRANSPARENT
    }

    private fun leerTextoDesdeRaw(): String {
        return try {
            val inputStream: InputStream = resources.openRawResource(R.raw.terminos_condiciones)
            inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "Error al cargar los términos legales."
        }
    }

    private fun mostrarPantallaDeTerminos(cbTerms: MaterialCheckBox) {
        val textoLegal = leerTextoDesdeRaw()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Términos y Condiciones")
            .setMessage(textoLegal)
            .setPositiveButton("Aceptar") { dialog, _ ->
                cbTerms.isChecked = true
                dialog.dismiss()
            }
            .setNegativeButton("Cerrar") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
}