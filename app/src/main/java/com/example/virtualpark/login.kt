package com.example.virtualpark

import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.text.InputType
import android.util.Base64
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.hbb20.CountryCodePicker
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class login : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var ccp: CountryCodePicker
    private lateinit var passwordEditText: EditText
    private lateinit var togglePasswordButton: ImageButton
    private var isPasswordVisible = false
    private var verificationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_login)

        Log.d("LoginActivity", "onCreate: Iniciando LoginActivity")

        // Inicializar Firebase Auth y Firestore
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        auth.signOut()

        // Inicializar CountryCodePicker
        ccp = findViewById(R.id.ccp)

        val backButton: ImageButton = findViewById(R.id.btn_back)
        backButton.setOnClickListener {
            onBackPressed()  // Regresa a la actividad anterior
            Log.d("LoginActivity", "onCreate: Botón de retroceso presionado")
        }

        val registerTextView: TextView = findViewById(R.id.tv_register)
        val forgotPasswordTextView: TextView = findViewById(R.id.tv_forgot_password)

        val phoneNumberEditText: EditText = findViewById(R.id.et_phone_number)
        passwordEditText = findViewById(R.id.et_password)

        // Vincular el CountryCodePicker con el campo de teléfono
        ccp.registerCarrierNumberEditText(phoneNumberEditText)

        val loginButton: Button = findViewById(R.id.btn_login)

        togglePasswordButton = findViewById(R.id.btn_toggle_password)
        togglePasswordButton.setOnClickListener {
            togglePasswordVisibility()
            Log.d(
                "LoginActivity",
                "onCreate: Botón de alternar visibilidad de contraseña presionado"
            )
        }

        // Agregar subrayado a los TextView
        registerTextView.paintFlags = registerTextView.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        forgotPasswordTextView.paintFlags =
            forgotPasswordTextView.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        // Configurar los listeners para mostrar un Toast
        registerTextView.setOnClickListener {
            val intent = Intent(this, register::class.java)
            startActivity(intent)
            Log.d("LoginActivity", "onCreate: Navegando a RegisterActivity")
        }

        forgotPasswordTextView.setOnClickListener {
            val intent = Intent(this, recuperar::class.java)
            startActivity(intent)
            Log.d("LoginActivity", "onCreate: Forgot password clicked")
        }

        // Listener para el botón de inicio de sesión
        loginButton.setOnClickListener {
            val inputPhoneNumber = ccp.fullNumberWithPlus.trim()
            val inputPassword = passwordEditText.text.toString().trim()

            Log.d("LoginActivity", "Botón de inicio de sesión presionado")
            Log.d("LoginActivity", "Número de teléfono ingresado: $inputPhoneNumber")

            if (inputPhoneNumber.isNotEmpty() && inputPassword.isNotEmpty()) {
                loginUser(inputPhoneNumber, inputPassword)
            } else {
                mostrarToast("Por favor ingresa todos los campos.")
                Log.d("LoginActivity", "Faltan campos por completar")
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun togglePasswordVisibility() {
        if (isPasswordVisible) {
            // Ocultar contraseña
            passwordEditText.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            togglePasswordButton.setImageResource(R.drawable.ic_eye_off)
            Log.d("LoginActivity", "togglePasswordVisibility: Contraseña oculta")
        } else {
            // Mostrar contraseña
            passwordEditText.inputType =
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            togglePasswordButton.setImageResource(R.drawable.ic_eye_on)
            Log.d("LoginActivity", "togglePasswordVisibility: Contraseña visible")
        }
        isPasswordVisible = !isPasswordVisible
        passwordEditText.setSelection(passwordEditText.text.length)  // Mantener el cursor al final del texto
    }

    private fun loginUser(phoneNumber: String, password: String) {
        Log.d("LoginActivity", "Iniciando loginUser con teléfono: $phoneNumber")

        val loginButton: Button = findViewById(R.id.btn_login)
        loginButton.isEnabled = false
        loginButton.alpha = 0.5f

        firestore.collection("users")
            .whereEqualTo("phone", phoneNumber)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    mostrarToast("Número de teléfono no registrado.")
                    Log.d("LoginActivity", "Número de teléfono no registrado")

                    loginButton.isEnabled = true
                    loginButton.alpha = 1.0f
                    return@addOnSuccessListener
                }

                val userDocument = documents.firstOrNull()
                val storedPassword = userDocument?.getString("password")
                val storedSalt = userDocument?.getString("salt")

                Log.d("LoginActivity", "Documento del usuario obtenido: $userDocument")

                if (storedPassword != null && storedSalt != null) {
                    Log.d(
                        "LoginActivity",
                        "Contraseña almacenada: $storedPassword, Salt almacenado: $storedSalt"
                    )
                    val inputPasswordHashed = hashPassword(password, storedSalt)
                    Log.d("LoginActivity", "Contraseña ingresada hasheada: $inputPasswordHashed")
                    if (inputPasswordHashed == storedPassword) {
                        Log.d(
                            "LoginActivity",
                            "Contraseña correcta, enviando código de verificación"
                        )

                        // Enviar código de verificación
                        val options = PhoneAuthOptions.newBuilder(auth)
                            .setPhoneNumber(phoneNumber)
                            .setTimeout(60L, TimeUnit.SECONDS)
                            .setActivity(this)
                            .setCallbacks(object :
                                PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                                    Log.d(
                                        "LoginActivity",
                                        "onVerificationCompleted: Verificación completada"
                                    )
                                    signInWithPhoneAuthCredential(credential)
                                }

                                override fun onVerificationFailed(e: FirebaseException) {
                                    Log.d(
                                        "LoginActivity",
                                        "onVerificationFailed: Verificación fallida",
                                        e
                                    )
                                    mostrarToast("Error de verificación: ${e.message}")

                                    loginButton.isEnabled = true
                                    loginButton.alpha = 1.0f
                                }

                                override fun onCodeSent(
                                    verificationId: String,
                                    token: PhoneAuthProvider.ForceResendingToken
                                ) {
                                    Log.d(
                                        "LoginActivity",
                                        "onCodeSent: Código enviado, ID: $verificationId"
                                    )
                                    this@login.verificationId = verificationId

                                    // Redirigir a la actividad de verificación y pasar el token de reenvío también
                                    val intent =
                                        Intent(this@login, verificarLogin::class.java).apply {
                                            putExtra("verificationId", verificationId)
                                            putExtra("phone", phoneNumber)
                                            putExtra("forceResendingToken", token)
                                            putExtra("isRegistering", false)
                                        }
                                    startActivity(intent)
                                    finish()
                                }
                            })
                            .build()
                        PhoneAuthProvider.verifyPhoneNumber(options)
                    } else {
                        mostrarToast("Contraseña incorrecta.")
                        Log.d("LoginActivity", "Contraseña incorrecta")

                        // Volver a habilitar el botón y restaurar su opacidad si ocurre un error
                        loginButton.isEnabled = true
                        loginButton.alpha = 1.0f
                    }
                } else {
                    mostrarToast("Error en los datos del usuario.")
                    Log.d("LoginActivity", "Error en los datos del usuario")

                    // Volver a habilitar el botón y restaurar su opacidad si ocurre un error
                    loginButton.isEnabled = true
                    loginButton.alpha = 1.0f
                }
            }
            .addOnFailureListener { e ->
                mostrarToast("Error al iniciar sesión: ${e.message}")
                Log.d("LoginActivity", "Error al iniciar sesión: ${e.message}")

                // Volver a habilitar el botón y restaurar su opacidad si ocurre un error
                loginButton.isEnabled = true
                loginButton.alpha = 1.0f
            }
    }


    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d(
                        "LoginActivity",
                        "signInWithPhoneAuthCredential: Inicio de sesión exitoso"
                    )
                    val intent = Intent(this, home::class.java)
                    startActivity(intent)
                    Log.d("LoginActivity", "HomeActivity iniciado exitosamente")
                    finish()
                } else {
                    Log.d(
                        "LoginActivity",
                        "signInWithPhoneAuthCredential: Error de autenticación",
                        task.exception
                    )
                    mostrarToast("Error de autenticación: ${task.exception?.message}")
                }
            }
    }

    // Función para cifrar una contraseña con SHA-256 y un salt
    private fun hashPassword(password: String, salt: String): String {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val saltedPassword = password + salt
        val hashBytes = messageDigest.digest(saltedPassword.toByteArray())
        return Base64.encodeToString(hashBytes, Base64.NO_WRAP)
    }

    private fun mostrarToast(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_SHORT).show()
    }
}