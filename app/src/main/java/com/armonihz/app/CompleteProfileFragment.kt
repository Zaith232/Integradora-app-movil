package com.armonihz.app

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.armonihz.app.network.ApiService
import com.armonihz.app.network.RetrofitClient
import com.armonihz.app.network.model.UpdateProfileRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import kotlinx.coroutines.launch

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

        // Precarga el nombre que trajo Google
        val user = FirebaseAuth.getInstance().currentUser
        val displayName = user?.displayName ?: ""
        val partes = displayName.trim().split(" ")
        etNombre.setText(partes.firstOrNull() ?: "")

        btnGuardar.setOnClickListener {
            val nombre   = etNombre.text.toString().trim()
            val apellido = etApellido.text.toString().trim()

            if (nombre.isEmpty() || apellido.isEmpty()) {
                Toast.makeText(requireContext(), "Completa todos los campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnGuardar.isEnabled = false
            btnGuardar.text = "Guardando..."

            lifecycleScope.launch {
                try {
                    val api = RetrofitClient.getInstance(requireContext()).create(ApiService::class.java)

                    // 1. Usamos tu UpdateProfileRequest (mandamos teléfono vacío)
                    val request = UpdateProfileRequest(
                        nombre = nombre,
                        apellido = apellido,
                        telefono = ""
                    )

                    // 2. Llamamos al endpoint de actualización de perfil
                    val response = api.updateProfile(request)

                    if (response.isSuccessful) {
                        // 3. Si el backend guardó bien, actualizamos el nombre en Firebase Auth
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName("$nombre $apellido")
                            .build()

                        user?.updateProfile(profileUpdates)?.addOnCompleteListener {
                            if (isAdded) {
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
}