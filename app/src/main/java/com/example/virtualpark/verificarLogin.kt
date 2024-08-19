package com.example.virtualpark

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.os.DeadObjectException
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
import java.util.concurrent.TimeUnit

class verificarLogin : AppCompatActivity() {

    private lateinit var etCodigo: EditText
    private lateinit var ivVerificado: ImageView
    private lateinit var btnVerificar: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnReenviarCodigo: Button
    private lateinit var auth: FirebaseAuth
    private lateinit var progressBar: ProgressBar
    private var verificationId: String? = null
    private var forceResendingToken: PhoneAuthProvider.ForceResendingToken? = null
    private var phone: String? = null
    private val cooldownPeriod = 60000L
    private var isResendingCode = false
    private var handler: Handler? = Handler()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_verificar)

        // Inicializar Firebase Auth
        auth = FirebaseAuth.getInstance()

        // Obtener datos del intent
        verificationId = intent.getStringExtra("verificationId")
        phone = intent.getStringExtra("phone")
        forceResendingToken = intent.getParcelableExtra("forceResendingToken")

        Log.d("verificarLogin", "phone: $phone, forceResendingToken: $forceResendingToken")

        if (phone.isNullOrEmpty() || forceResendingToken == null) {
            Log.e("verificarLogin", "Los datos necesarios no se recibieron correctamente.")
            Toast.makeText(
                this,
                "No se puede proceder porque faltan datos necesarios.",
                Toast.LENGTH_LONG
            ).show()
            finish()
            return
        }

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

    override fun onDestroy() {
        super.onDestroy()
        // Cancelar cualquier tarea pendiente en el Handler para evitar posibles crashes
        handler?.removeCallbacksAndMessages(null)
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
                    mostrarAnimacionYRedirigir()
                } else {
                    val exception = task.exception
                    when {
                        exception is FirebaseAuthInvalidCredentialsException -> {
                            Toast.makeText(
                                this@verificarLogin,
                                "El código de verificación es inválido. Verifica el código y vuelve a intentarlo.",
                                Toast.LENGTH_LONG
                            ).show()
                        }

                        exception is FirebaseException && exception.message?.contains("Too many attempts") == true -> {
                            Toast.makeText(
                                this@verificarLogin,
                                "Demasiados intentos de verificación. Por favor, espera antes de intentarlo nuevamente.",
                                Toast.LENGTH_LONG
                            ).show()
                            Log.e(
                                "verificarLogin",
                                "Demasiados intentos de verificación: ${exception.message}"
                            )
                            iniciarCooldown()
                        }

                        else -> {
                            Toast.makeText(
                                this@verificarLogin,
                                "La verificación falló. Inténtalo de nuevo: ${exception?.message}",
                                Toast.LENGTH_LONG
                            ).show()
                            Log.e("verificarLogin", "La verificación falló: ${exception?.message}")
                        }
                    }
                }
            }
    }

    private fun reenviarCodigo() {
        Log.d("verificarLogin", "Iniciando reenvío de código")

        if (phone.isNullOrEmpty() || forceResendingToken == null) {
            // Manejar el caso en que phone o forceResendingToken son null
            Log.e(
                "verificarLogin",
                "No se puede reenviar el código porque phone o forceResendingToken son null"
            )
            Toast.makeText(
                this,
                "No se puede reenviar el código en este momento. Por favor, inténtalo más tarde.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        progressBar.visibility = View.VISIBLE // Mostrar la animación de carga
        isResendingCode = true  // Indicamos que estamos reenviando el código

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
            Log.d("verificarLogin", "onVerificationCompleted llamado")
            if (isResendingCode) {
                Log.d(
                    "verificarLogin",
                    "onVerificationCompleted ignorado debido al reenvío de código"
                )
                isResendingCode = false
            } else {
                Log.d(
                    "verificarLogin",
                    "onVerificationCompleted: Verificación automática exitosa, iniciando sesión"
                )
                signInWithPhoneAuthCredential(credential)
            }
        }

        override fun onVerificationFailed(e: FirebaseException) {
            progressBar.visibility = View.GONE // Ocultar la animación de carga
            Toast.makeText(
                this@verificarLogin,
                "Error al reenviar el código: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
            Log.e("verificarLogin", "onVerificationFailed: ${e.message}")
        }

        override fun onCodeSent(
            verificationId: String,
            token: PhoneAuthProvider.ForceResendingToken
        ) {
            Log.d("verificarLogin", "Código reenviado exitosamente")
            this@verificarLogin.verificationId = verificationId
            forceResendingToken = token
            progressBar.visibility = View.GONE // Ocultar la animación de carga
            Toast.makeText(this@verificarLogin, "Código reenviado.", Toast.LENGTH_SHORT).show()
            isResendingCode = false  // Resetear el flag después de reenviar el código
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

    private fun mostrarAnimacionYRedirigir() {
        // Guardar el estado de la sesión como iniciada
        val sharedPreferences = getSharedPreferences("UserSession", MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putBoolean("isLoggedIn", true)
        editor.apply()

        Log.d("verificarLogin", "Verificación exitosa, redirigiendo a Home")

        try {
            ivVerificado.visibility = View.VISIBLE
            ivVerificado.setColorFilter(
                ContextCompat.getColor(
                    this,
                    android.R.color.holo_green_light
                )
            )

            val scaleX = ObjectAnimator.ofFloat(ivVerificado, "scaleX", 0.5f, 1.2f, 1.0f)
            val scaleY = ObjectAnimator.ofFloat(ivVerificado, "scaleY", 0.5f, 1.2f, 1.0f)

            val animatorSet = AnimatorSet()
            animatorSet.playTogether(scaleX, scaleY)
            animatorSet.duration = 300
            animatorSet.start()

            handler?.postDelayed({
                if (!isFinishing && !isDestroyed) {
                    val intent = Intent(this, home::class.java)
                    startActivity(intent)
                    finish()
                }
            }, 250)
        } catch (e: DeadObjectException) {
            Log.e("verificarLogin", "DeadObjectException: ${e.message}")
        } catch (e: Exception) {
            Log.e("verificarLogin", "Error inesperado: ${e.message}")
        }
    }
}