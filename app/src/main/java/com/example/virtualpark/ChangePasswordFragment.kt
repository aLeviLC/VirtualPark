package com.example.virtualpark

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

class ChangePasswordFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var etCurrentPassword: EditText
    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmNewPassword: EditText
    private lateinit var btnChangePassword: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnToggleCurrentPassword: ImageButton
    private lateinit var btnToggleNewPassword: ImageButton
    private lateinit var btnToggleConfirmPassword: ImageButton
    private var passwordDataChanged = false
    private var currentPasswordHash: String? = null
    private var currentSalt: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_change_password, container, false)

        // Inicializar Firebase Auth y Firestore
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Inicializar vistas
        etCurrentPassword = view.findViewById(R.id.et_current_password)
        etNewPassword = view.findViewById(R.id.et_new_password)
        etConfirmNewPassword = view.findViewById(R.id.et_confirm_new_password)
        btnChangePassword = view.findViewById(R.id.btn_change_password)
        btnBack = view.findViewById(R.id.btn_back)
        btnToggleCurrentPassword = view.findViewById(R.id.btn_toggle_current_password)
        btnToggleNewPassword = view.findViewById(R.id.btn_toggle_new_password)
        btnToggleConfirmPassword = view.findViewById(R.id.btn_toggle_confirm_password)

        setupPasswordToggle(btnToggleCurrentPassword, etCurrentPassword)
        setupPasswordToggle(btnToggleNewPassword, etNewPassword)
        setupPasswordToggle(btnToggleConfirmPassword, etConfirmNewPassword)

        // Configurar el botón de regreso
        btnBack.setOnClickListener {
            showBackConfirmationDialog()
        }

        // Listener para validar la contraseña actual
        etCurrentPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val input = s.toString()
                if (hashPassword(input, currentSalt ?: "") == currentPasswordHash) {
                    etNewPassword.isEnabled = true
                    etConfirmNewPassword.isEnabled = true
                    btnToggleNewPassword.isEnabled = true
                    btnToggleConfirmPassword.isEnabled = true
                } else {
                    etNewPassword.isEnabled = false
                    etConfirmNewPassword.isEnabled = false
                    btnToggleNewPassword.isEnabled = false
                    btnToggleConfirmPassword.isEnabled = false
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Listener para validar las nuevas contraseñas
        val newPasswordWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newPassword = etNewPassword.text.toString()
                val confirmNewPassword = etConfirmNewPassword.text.toString()
                val isValid = newPassword == confirmNewPassword && newPassword.isNotEmpty()
                val isDifferentFromOldPassword = newPassword != etCurrentPassword.text.toString()

                btnChangePassword.isEnabled = isValid && isDifferentFromOldPassword
                btnChangePassword.alpha = if (isValid && isDifferentFromOldPassword) 1.0f else 0.5f

                if (!isDifferentFromOldPassword && newPassword.isNotEmpty()) {
                    Toast.makeText(
                        context,
                        "La nueva contraseña no puede ser igual a la actual",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        }

        etNewPassword.addTextChangedListener(newPasswordWatcher)
        etConfirmNewPassword.addTextChangedListener(newPasswordWatcher)

        // Listener para cambiar la contraseña
        btnChangePassword.setOnClickListener {
            showChangePasswordConfirmationDialog()
        }

        // Cargar datos actuales del usuario
        loadUserData()

        return view
    }

    private fun setupPasswordToggle(button: ImageButton, editText: EditText) {
        button.setOnClickListener {
            if (editText.transformationMethod is PasswordTransformationMethod) {
                editText.transformationMethod = HideReturnsTransformationMethod.getInstance()
                button.setImageResource(R.drawable.ic_eye_on) // Cambia al ícono de "ojo abierto"
            } else {
                editText.transformationMethod = PasswordTransformationMethod.getInstance()
                button.setImageResource(R.drawable.ic_eye_off) // Cambia al ícono de "ojo cerrado"
            }
            // Mover el cursor al final del texto
            editText.setSelection(editText.text.length)
        }
    }

    private fun loadUserData() {
        val userId = auth.currentUser?.uid ?: return
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    currentPasswordHash = document.getString("password")
                    currentSalt = document.getString("salt")
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error al cargar los datos", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showChangePasswordConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setMessage("¿Estás seguro de que quieres cambiar tu contraseña?")
            .setPositiveButton("Sí") { _, _ -> changePassword() }
            .setNegativeButton("No", null)
            .create()
            .show()
    }

    private fun changePassword() {
        val newPassword = etNewPassword.text.toString()
        val newSalt = generateSalt()
        val newHashedPassword = hashPassword(newPassword, newSalt)

        val userId = auth.currentUser?.uid ?: return

        val updates = hashMapOf<String, Any>(
            "password" to newHashedPassword,
            "salt" to newSalt
        )

        firestore.collection("users").document(userId).update(updates)
            .addOnSuccessListener {
                Toast.makeText(context, "Contraseña cambiada exitosamente", Toast.LENGTH_SHORT)
                    .show()
                parentFragmentManager.popBackStack()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error al cambiar la contraseña", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showBackConfirmationDialog() {
        if (passwordDataChanged) {
            AlertDialog.Builder(requireContext())
                .setMessage("Tienes cambios sin guardar. ¿Estás seguro de que quieres regresar?")
                .setPositiveButton("Sí") { _, _ -> parentFragmentManager.popBackStack() }
                .setNegativeButton("No", null)
                .create()
                .show()
        } else {
            parentFragmentManager.popBackStack()
        }
    }

    private fun hashPassword(password: String, salt: String): String {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val saltedPassword = password + salt
        val hashBytes = messageDigest.digest(saltedPassword.toByteArray())
        return Base64.getEncoder().encodeToString(hashBytes)
    }

    private fun generateSalt(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }
}