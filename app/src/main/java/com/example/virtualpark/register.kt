package com.example.virtualpark

import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.hbb20.CountryCodePicker
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.TimeUnit

class register : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etMotherLastName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var btnRegister: Button
    private lateinit var btnBack: ImageButton
    private lateinit var ccp: CountryCodePicker
    private lateinit var auth: FirebaseAuth
    private lateinit var progressBar: ProgressBar
    private lateinit var firestore: FirebaseFirestore
    private var verificationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        // Inicializar Firebase Auth y Firestore
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        etName = findViewById(R.id.et_name)
        etLastName = findViewById(R.id.et_lastname)
        etMotherLastName = findViewById(R.id.et_mother_lastname)
        etPhone = findViewById(R.id.et_phone)
        etPassword = findViewById(R.id.et_password)
        etConfirmPassword = findViewById(R.id.et_confirm_password)
        btnRegister = findViewById(R.id.btn_register)
        btnBack = findViewById(R.id.btn_back)
        ccp = findViewById(R.id.ccp)
        progressBar = findViewById(R.id.progress_bar)

        // Vincular el `CountryCodePicker` con el campo de teléfono
        ccp.registerCarrierNumberEditText(etPhone)

        btnRegister.isEnabled = false
        btnRegister.alpha = 0.5f

        val btnTogglePassword: ImageButton = findViewById(R.id.btn_toggle_password)

        // Listener para habilitar el botón de registro solo si los campos están llenos, la contraseña tiene al menos 8 caracteres y las contraseñas coinciden
        val textWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val name = etName.text.toString()
                val lastName = etLastName.text.toString()
                val motherLastName = etMotherLastName.text.toString()
                val phone = etPhone.text.toString()
                val password = etPassword.text.toString()
                val confirmPassword = etConfirmPassword.text.toString()

                // Habilitar el botón si todos los campos están llenos, la contraseña tiene al menos 8 caracteres y las contraseñas coinciden
                val isEnabled =
                    name.isNotEmpty() && lastName.isNotEmpty() && motherLastName.isNotEmpty()
                            && phone.isNotEmpty() && password.isNotEmpty() && confirmPassword.isNotEmpty()
                            && password.length >= 8 && password == confirmPassword

                btnRegister.isEnabled = isEnabled
                btnRegister.alpha = if (isEnabled) 1.0f else 0.5f
            }

            override fun afterTextChanged(s: Editable?) {}
        }


        btnTogglePassword.setOnClickListener {
            if (etPassword.transformationMethod is PasswordTransformationMethod) {
                etPassword.transformationMethod = HideReturnsTransformationMethod.getInstance()
                btnTogglePassword.setImageResource(R.drawable.ic_eye_on) // Cambia al ícono de "ojo abierto"
            } else {
                etPassword.transformationMethod = PasswordTransformationMethod.getInstance()
                btnTogglePassword.setImageResource(R.drawable.ic_eye_off) // Cambia al ícono de "ojo cerrado"
            }
            // Mover el cursor al final del texto
            etPassword.setSelection(etPassword.text.length)
        }

        val uppercaseTextWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                etName.removeTextChangedListener(this)
                etLastName.removeTextChangedListener(this)
                etMotherLastName.removeTextChangedListener(this)

                etName.setText(etName.text.toString().uppercase())
                etLastName.setText(etLastName.text.toString().uppercase())
                etMotherLastName.setText(etMotherLastName.text.toString().uppercase())

                etName.setSelection(etName.text.length)
                etLastName.setSelection(etLastName.text.length)
                etMotherLastName.setSelection(etMotherLastName.text.length)

                etName.addTextChangedListener(this)
                etLastName.addTextChangedListener(this)
                etMotherLastName.addTextChangedListener(this)
            }

            override fun afterTextChanged(s: Editable?) {}
        }

        etName.addTextChangedListener(uppercaseTextWatcher)
        etLastName.addTextChangedListener(uppercaseTextWatcher)
        etMotherLastName.addTextChangedListener(uppercaseTextWatcher)
        etPhone.addTextChangedListener(textWatcher)
        etPassword.addTextChangedListener(textWatcher)
        etConfirmPassword.addTextChangedListener(textWatcher)

        btnRegister.setOnClickListener {
            val password = etPassword.text.toString()
            val confirmPassword = etConfirmPassword.text.toString()

            if (password.length >= 8 && password == confirmPassword) {
                btnRegister.isEnabled = true
                progressBar.visibility = View.VISIBLE // Mostrar la animación de carga
                val fullPhoneNumber = ccp.fullNumberWithPlus
                checkIfPhoneNumberExists(fullPhoneNumber) // Verificar si el número ya está registrado
            } else {
                btnRegister.isEnabled = false
                Toast.makeText(
                    this@register,
                    "La contraseña debe tener al menos 8 caracteres y coincidir",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        btnBack.setOnClickListener {
            showExitConfirmationDialog()
        }
    }

    override fun onBackPressed() {
        showExitConfirmationDialog()
    }

    private fun showExitConfirmationDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("¿Estás seguro que quieres salir sin guardar cambios?")
            .setPositiveButton("Sí") { dialogInterface: DialogInterface, i: Int ->
                // Si el usuario confirma, cancelar el proceso de registro y volver atrás
                super.onBackPressed()
            }
            .setNegativeButton("No", null) // Si el usuario cancela, no hacer nada
            .create()
            .show()
    }

    private fun checkIfPhoneNumberExists(phoneNumber: String) {
        // Verifica si el número de teléfono ya está registrado en Firebase Firestore
        firestore.collection("users")
            .whereEqualTo("phone", phoneNumber)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this,
                        "El número de teléfono ya está registrado.",
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    startPhoneNumberVerification(phoneNumber) // Inicia la verificación si el número no está registrado
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(
                    this,
                    "Error al verificar el número: ${e.message}",
                    Toast.LENGTH_LONG
                ).show()
                Log.e("RegisterActivity", "Error al verificar el número: ${e.message}")
            }
    }

    private fun startPhoneNumberVerification(phoneNumber: String) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Si la verificación automática se completa
                    signInWithPhoneAuthCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(
                        this@register,
                        "Verificación fallida: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("RegisterActivity", "Verificación fallida: ${e.message}")
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    this@register.verificationId = verificationId
                    progressBar.visibility = View.GONE

                    val salt = generateSalt()
                    val encryptedPassword = hashPassword(etPassword.text.toString(), salt)
                    goToVerificationScreen(encryptedPassword, salt)
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                progressBar.visibility = View.GONE
                if (task.isSuccessful) {
                    // Sign-in successful, ahora puedes guardar en Firestore!!!
                    guardarUsuarioEnFirestore()
                } else {
                    Toast.makeText(
                        this@register,
                        "La verificación falló. Inténtalo de nuevo.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    // Función para cifrar una contraseña con SHA-256 y un salt
    private fun hashPassword(password: String, salt: String): String {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        val saltedPassword = password + salt
        val hashBytes = messageDigest.digest(saltedPassword.toByteArray())
        return Base64.getEncoder().encodeToString(hashBytes)
    }

    // Función para generar un salt aleatorio
    private fun generateSalt(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return Base64.getEncoder().encodeToString(salt)
    }

    private fun guardarUsuarioEnFirestore() {
        val salt = generateSalt()
        val encryptedPassword = hashPassword(etPassword.text.toString(), salt)

        val user = hashMapOf(
            "name" to etName.text.toString().uppercase(),
            "lastName" to etLastName.text.toString().uppercase(),
            "motherLastName" to etMotherLastName.text.toString().uppercase(),
            "phone" to ccp.fullNumberWithPlus.uppercase(),
            "password" to encryptedPassword,  // Almacena la contraseña cifrada
            "salt" to salt // Almacena el salt para usarlo al verificar la contraseña
        )

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            firestore.collection("users").document(uid).set(user)
                .addOnSuccessListener {
                    Toast.makeText(this, "Registro exitoso", Toast.LENGTH_LONG).show()
                    goToVerificationScreen(encryptedPassword, salt)
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        this,
                        "Error al registrar el usuario: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                    Log.e("RegisterActivity", "Error al registrar el usuario: ${e.message}")
                }
        } else {
            Toast.makeText(this, "Error al obtener UID del usuario", Toast.LENGTH_LONG).show()
        }
    }

    private fun goToVerificationScreen(encryptedPassword: String, salt: String) {
        val intent = Intent(this, verificar::class.java)
        intent.putExtra("verificationId", verificationId)
        intent.putExtra("name", etName.text.toString().uppercase())
        intent.putExtra("lastName", etLastName.text.toString().uppercase())
        intent.putExtra("motherLastName", etMotherLastName.text.toString().uppercase())
        intent.putExtra("phone", ccp.fullNumberWithPlus.uppercase())
        intent.putExtra("password", encryptedPassword)  // Añadido en register
        intent.putExtra("salt", salt)  // Añadido en register
        startActivity(intent)
    }

}