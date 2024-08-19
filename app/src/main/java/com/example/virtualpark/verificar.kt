package com.example.virtualpark

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class verificar : AppCompatActivity() {

    private lateinit var etCodigo: EditText
    private lateinit var ivVerificado: ImageView
    private lateinit var btnVerificar: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnReenviarCodigo: Button
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var progressBar: ProgressBar
    private var verificationId: String? = null
    private var forceResendingToken: PhoneAuthProvider.ForceResendingToken? = null
    private var name: String? = null
    private var lastName: String? = null
    private var motherLastName: String? = null
    private var phone: String? = null
    private var password: String? = null  // Contraseña cifrada recibida desde la clase register
    private var salt: String? = null  // Salt recibido desde la clase register
    private val cooldownPeriod = 60000L // 60 segundos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_verificar)

        // Inicializar Firebase Auth y Firestore
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Obtener datos del intent
        verificationId = intent.getStringExtra("verificationId")
        name = intent.getStringExtra("name")
        lastName = intent.getStringExtra("lastName")
        motherLastName = intent.getStringExtra("motherLastName")
        phone = intent.getStringExtra("phone")
        password = intent.getStringExtra("password")
        salt = intent.getStringExtra("salt")
        forceResendingToken = intent.getParcelableExtra("forceResendingToken")

        // Inicializar vistas
        etCodigo = findViewById(R.id.et_codigo)
        ivVerificado = findViewById(R.id.iv_verificado)
        btnVerificar = findViewById(R.id.btn_verificar)
        btnBack = findViewById(R.id.btn_back)
        btnReenviarCodigo = findViewById(R.id.btn_reenviar_codigo)
        progressBar = findViewById(R.id.progress_bar_verificar)

        btnVerificar.setOnClickListener {
            val codigoIngresado = etCodigo.text.toString()
            if (codigoIngresado.length == 6) {
                progressBar.visibility = View.VISIBLE // Mostrar la animación de carga
                verificarCodigo(codigoIngresado)
            } else {
                Toast.makeText(
                    this,
                    "Por favor ingrese un código válido de 6 dígitos",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        btnReenviarCodigo.setOnClickListener {
            reenviarCodigo()
        }

        btnBack.setOnClickListener {
            onBackPressed()
        }
    }

    private fun verificarCodigo(codigo: String) {
        val credential = PhoneAuthProvider.getCredential(verificationId!!, codigo)
        signInWithPhoneAuthCredential(credential)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                progressBar.visibility = View.GONE // Ocultar la animación de carga
                if (task.isSuccessful) {
                    guardarUsuarioEnFirestore()
                } else {
                    val exception = task.exception
                    when {
                        exception is FirebaseAuthInvalidCredentialsException -> {
                            Toast.makeText(
                                this@verificar,
                                "El código de verificación es inválido. Verifica el código y vuelve a intentarlo.",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        exception is FirebaseException && exception.message?.contains("Too many attempts") == true -> {
                            Toast.makeText(
                                this@verificar,
                                "Demasiados intentos de verificación. Por favor, espera antes de intentarlo nuevamente.",
                                Toast.LENGTH_LONG
                            ).show()
                            Log.e(
                                "verificar",
                                "Demasiados intentos de verificación: ${exception.message}"
                            )
                            iniciarCooldown()
                        }

                        else -> {
                            Toast.makeText(
                                this@verificar,
                                "La verificación falló. Inténtalo de nuevo: ${exception?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            Log.e("verificar", "La verificación falló: ${exception?.message}")
                        }
                    }
                }
            }
    }

    private fun reenviarCodigo() {
        progressBar.visibility = View.VISIBLE // Mostrar la animación de carga
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phone!!)       // Número de teléfono a verificar
            .setTimeout(60L, TimeUnit.SECONDS) // Timeout y unidad
            .setActivity(this)                 // Activity (para callbacks de onVerificationCompleted)
            .setCallbacks(callbacks)          // OnVerificationStateChangedCallbacks
            .setForceResendingToken(forceResendingToken!!) // Token para reenviar
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            progressBar.visibility = View.GONE // Ocultar la animación de carga
            Toast.makeText(
                this@verificar,
                "Error al reenviar el código: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            this@verificar.verificationId = verificationId
            forceResendingToken = token
            progressBar.visibility = View.GONE // Ocultar la animación de carga
            Toast.makeText(this@verificar, "Código reenviado.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun iniciarCooldown() {
        btnVerificar.isEnabled = false
        btnReenviarCodigo.isEnabled = false

        object : CountDownTimer(cooldownPeriod, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val secondsRemaining = millisUntilFinished / 1000
                btnVerificar.text = "Verificar ($secondsRemaining)"
                btnReenviarCodigo.text = "Reenviar ($secondsRemaining)"
            }

            override fun onFinish() {
                btnVerificar.isEnabled = true
                btnReenviarCodigo.isEnabled = true
                btnVerificar.text = "Verificar"
                btnReenviarCodigo.text = "Reenviar código"
            }
        }.start()
    }

    private fun guardarUsuarioEnFirestore() {
        val user = hashMapOf(
            "name" to name,
            "lastName" to lastName,
            "motherLastName" to motherLastName,
            "phone" to phone,
            "password" to password, // Almacena la contraseña cifrada recibida desde register
            "salt" to salt // Almacena el salt recibido desde register
        )

        firestore.collection("users").document(auth.currentUser?.uid ?: "").set(user)
            .addOnSuccessListener {
                mostrarAnimacionYRedirigir()
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    this,
                    "Error al registrar el usuario: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun mostrarAnimacionYRedirigir() {
        // Mostrar animación de verificación exitosa
        ivVerificado.visibility = View.VISIBLE
        ivVerificado.setColorFilter(ContextCompat.getColor(this, android.R.color.holo_green_light))

        val scaleX = ObjectAnimator.ofFloat(ivVerificado, "scaleX", 0.5f, 1.2f, 1.0f)
        val scaleY = ObjectAnimator.ofFloat(ivVerificado, "scaleY", 0.5f, 1.2f, 1.0f)

        val animatorSet = AnimatorSet()
        animatorSet.playTogether(scaleX, scaleY)
        animatorSet.duration = 300
        animatorSet.start()

        // Esperar un segundo para mostrar la animación antes de navegar
        Handler().postDelayed({
            // Ir a la actividad de inicio de sesión después de la animación
            val intent = Intent(this, login::class.java)
            startActivity(intent)
            finish()
        }, 250)
    }
}