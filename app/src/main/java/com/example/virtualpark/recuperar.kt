@file:Suppress("DEPRECATION")

package com.example.virtualpark

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class recuperar : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var ccp: com.hbb20.CountryCodePicker
    private lateinit var etPhone: EditText
    private lateinit var etVerificationCode: EditText
    private lateinit var btnSendCode: Button
    private lateinit var btnVerifyCode: Button
    private lateinit var tvRecoveredPassword: TextView
    private lateinit var btnLogin: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvTimer: TextView
    private lateinit var verificationId: String
    private var timer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_recuperar)

        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        ccp = findViewById(R.id.ccp)
        etPhone = findViewById(R.id.et_phone)
        etVerificationCode = findViewById(R.id.et_verification_code)
        btnSendCode = findViewById(R.id.btn_send_code)
        btnVerifyCode = findViewById(R.id.btn_verify_code)
        tvRecoveredPassword = findViewById(R.id.tv_recovered_password)
        btnLogin = findViewById(R.id.btn_login)
        progressBar = findViewById(R.id.progress_bar_verificar)
        tvTimer = findViewById(R.id.tv_timer)

        ccp.registerCarrierNumberEditText(etPhone)

        // Configurar el botón de regreso
        findViewById<ImageButton>(R.id.btn_back).setOnClickListener {
            onBackPressed()
        }

        // Listener para enviar el código
        btnSendCode.setOnClickListener {
            val phoneNumber = ccp.fullNumberWithPlus
            if (phoneNumber.isNotEmpty()) {
                // Consultar Firestore para verificar si el número está registrado
                firestore.collection("users")
                    .whereEqualTo("phone", phoneNumber)
                    .get()
                    .addOnSuccessListener { documents ->
                        if (!documents.isEmpty) {
                            // El número está registrado, proceder con el envío del código
                            sendVerificationCode(phoneNumber)
                            ccp.visibility = View.GONE  // Ocultar el CCP
                            etPhone.visibility = View.GONE  // Ocultar el número de teléfono
                            btnSendCode.visibility = View.GONE  // Ocultar el botón de enviar código
                            etVerificationCode.visibility =
                                View.VISIBLE  // Mostrar el campo de verificación
                            etVerificationCode.isEnabled =
                                true  // Habilitar el campo de verificación
                            btnVerifyCode.visibility =
                                View.VISIBLE  // Mostrar el botón de verificación
                        } else {
                            // El número no está registrado
                            Toast.makeText(
                                this,
                                "Número de teléfono no registrado.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(
                            this,
                            "Error al verificar el número: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            } else {
                Toast.makeText(
                    this,
                    "Por favor ingresa un número de teléfono válido",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnVerifyCode.setOnClickListener {
            val code = etVerificationCode.text.toString().trim()
            if (code.isNotEmpty()) {
                verifyCode(code)
            } else {
                Toast.makeText(
                    this,
                    "Por favor ingresa el código de verificación",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnLogin.setOnClickListener {
            val intent = Intent(this, home::class.java)
            startActivity(intent)
            finish()
        }
    }

    private fun sendVerificationCode(phoneNumber: String) {
        progressBar.visibility = View.VISIBLE
        btnSendCode.isEnabled = false

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithPhoneAuthCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    progressBar.visibility = View.GONE
                    btnSendCode.isEnabled = true
                    Toast.makeText(
                        this@recuperar,
                        "Error de verificación: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    this@recuperar.verificationId = verificationId
                    progressBar.visibility = View.GONE
                    Toast.makeText(this@recuperar, "Código enviado", Toast.LENGTH_SHORT).show()
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun verifyCode(code: String) {
        progressBar.visibility = View.VISIBLE
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        signInWithPhoneAuthCredential(credential)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Guardar el estado de inicio de sesión
                    val sharedPreferences = getSharedPreferences("VirtualParkPrefs", MODE_PRIVATE)
                    val editor = sharedPreferences.edit()
                    editor.putBoolean("isLoggedIn", true)
                    editor.putString("userId", auth.currentUser?.uid)
                    editor.apply()

                    retrieveUserId()
                } else {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this,
                        "Error de autenticación: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    @SuppressLint("SetTextI18n")
    private fun retrieveUserId() {
        val userId = auth.currentUser?.uid
        progressBar.visibility = View.GONE

        if (userId != null) {
            tvRecoveredPassword.text =
                "Este es tu UID: $userId\n\nContacta a soporte y proporciona tu ID para cambiar tu contraseña."
            tvRecoveredPassword.visibility = View.VISIBLE
            tvTimer.visibility = View.VISIBLE
            startTimer()
            btnLogin.visibility = View.VISIBLE
            etVerificationCode.visibility = View.GONE
            btnVerifyCode.visibility = View.GONE
        } else {
            Toast.makeText(this, "Error al recuperar el UID del usuario", Toast.LENGTH_LONG).show()
        }
    }

    private fun startTimer() {
        timer = object : CountDownTimer(30000, 1000) {
            @SuppressLint("SetTextI18n")
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                tvTimer.text =
                    "00:${if (secondsRemaining < 10) "0$secondsRemaining" else secondsRemaining}"
            }

            override fun onFinish() {
                val intent = Intent(this@recuperar, home::class.java)
                startActivity(intent)
                finish()
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
    }
}