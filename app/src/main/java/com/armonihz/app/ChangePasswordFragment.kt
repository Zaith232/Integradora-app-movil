package com.armonihz.app

import android.graphics.Color
import android.os.Bundle
import android.text.Editable
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
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth

class ChangePasswordFragment : Fragment() {

    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        val view = inflater.inflate(R.layout.fragment_change_password, container, false)

        auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        val btnBack = view.findViewById<ImageView>(R.id.btnBackPassword)
        val etCurrentPassword = view.findViewById<EditText>(R.id.etCurrentPassword)
        val etNewPassword = view.findViewById<EditText>(R.id.etNewPassword)
        val etConfirmPassword = view.findViewById<EditText>(R.id.etConfirmPassword)
        val btnUpdate = view.findViewById<MaterialButton>(R.id.btnUpdatePassword)

        val tvPasswordHint = view.findViewById<TextView>(R.id.tvPasswordHint)
        val tvConfirmPasswordHint = view.findViewById<TextView>(R.id.tvConfirmPasswordHint)

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // 🔎 VALIDACIÓN EN TIEMPO REAL
        etNewPassword.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {

                val password = s.toString()

                val hasMinLength = password.length >= 8
                val hasUppercase = password.any { it.isUpperCase() }
                val hasDigit = password.any { it.isDigit() }

                when {
                    password.isEmpty() -> {
                        tvPasswordHint.text = "Mínimo 8 caracteres, 1 mayúscula y 1 número"
                        tvPasswordHint.setTextColor(Color.GRAY)
                    }
                    !hasMinLength -> {
                        tvPasswordHint.text = "Faltan caracteres (mínimo 8)"
                        tvPasswordHint.setTextColor(Color.RED)
                    }
                    !hasUppercase -> {
                        tvPasswordHint.text = "Falta agregar una mayúscula"
                        tvPasswordHint.setTextColor(Color.RED)
                    }
                    !hasDigit -> {
                        tvPasswordHint.text = "Falta agregar un número"
                        tvPasswordHint.setTextColor(Color.RED)
                    }
                    else -> {
                        tvPasswordHint.text = "¡Contraseña segura!"
                        tvPasswordHint.setTextColor(Color.parseColor("#00897B"))
                    }
                }

                val confirmPass = etConfirmPassword.text.toString()
                if (confirmPass.isNotEmpty()) {
                    if (password == confirmPass) {
                        tvConfirmPasswordHint.text = "Las contraseñas coinciden"
                        tvConfirmPasswordHint.setTextColor(Color.parseColor("#00897B"))
                    } else {
                        tvConfirmPasswordHint.text = "Las contraseñas no coinciden"
                        tvConfirmPasswordHint.setTextColor(Color.RED)
                    }
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val confirmPassword = s.toString()
                val originalPassword = etNewPassword.text.toString()

                when {
                    confirmPassword.isEmpty() -> {
                        tvConfirmPasswordHint.text = ""
                    }
                    confirmPassword == originalPassword -> {
                        tvConfirmPasswordHint.text = "Las contraseñas coinciden"
                        tvConfirmPasswordHint.setTextColor(Color.parseColor("#00897B"))
                    }
                    else -> {
                        tvConfirmPasswordHint.text = "Las contraseñas no coinciden"
                        tvConfirmPasswordHint.setTextColor(Color.RED)
                    }
                }
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        // 💾 ACTUALIZAR CONTRASEÑA
        btnUpdate.setOnClickListener {

            val currentPass = etCurrentPassword.text.toString().trim()
            val newPass = etNewPassword.text.toString().trim()
            val confirmPass = etConfirmPassword.text.toString().trim()

            if (currentPass.isEmpty()) {
                etCurrentPassword.error = "Ingresa tu contraseña actual"
                return@setOnClickListener
            }

            if (newPass.length < 8) {
                etNewPassword.error = "Mínimo 8 caracteres"
                return@setOnClickListener
            }

            if (!newPass.any { it.isDigit() } || !newPass.any { it.isUpperCase() }) {
                etNewPassword.error = "Debe tener 1 mayúscula y 1 número"
                return@setOnClickListener
            }

            if (newPass != confirmPass) {
                etConfirmPassword.error = "Las contraseñas no coinciden"
                return@setOnClickListener
            }

            if (user != null && user.email != null) {

                val providerData = user.providerData
                for (userInfo in providerData) {
                    if (userInfo.providerId == "google.com" || userInfo.providerId == "facebook.com") {
                        Toast.makeText(requireContext(), "Tu cuenta usa Google/Facebook", Toast.LENGTH_LONG).show()
                        return@setOnClickListener
                    }
                }

                btnUpdate.isEnabled = false
                btnUpdate.text = "Actualizando..."

                val credential = EmailAuthProvider.getCredential(user.email!!, currentPass)

                user.reauthenticate(credential).addOnCompleteListener { reauthTask ->
                    if (reauthTask.isSuccessful) {

                        user.updatePassword(newPass).addOnCompleteListener { updateTask ->
                            if (updateTask.isSuccessful) {
                                Toast.makeText(requireContext(), "Contraseña actualizada", Toast.LENGTH_SHORT).show()
                                parentFragmentManager.popBackStack()
                            } else {
                                Toast.makeText(requireContext(), "Error: ${updateTask.exception?.message}", Toast.LENGTH_LONG).show()
                                btnUpdate.isEnabled = true
                                btnUpdate.text = "Actualizar contraseña"
                            }
                        }

                    } else {
                        etCurrentPassword.error = "Contraseña actual incorrecta"
                        btnUpdate.isEnabled = true
                        btnUpdate.text = "Actualizar contraseña"
                    }
                }
            }
        }

        return view
    }
}