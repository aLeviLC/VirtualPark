package com.example.virtualpark

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@Suppress("DEPRECATION")
class home : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var bottomNavigation: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_home)

        // Inicializar Firebase Auth y Firestore
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Verificar si el usuario está autenticado
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.d("HomeActivity", "Usuario no autenticado, redirigiendo a login")
            val intent = Intent(this, login::class.java)
            startActivity(intent)
            finish()
            return
        } else {
            Log.d("HomeActivity", "Usuario autenticado: ${currentUser.phoneNumber}")
        }

        bottomNavigation = findViewById(R.id.bottom_navigation)

        // Cargar el nombre del usuario y actualizar la barra de navegación
        updateUserNavigationTitle(currentUser.phoneNumber)

        // Verificar si hay boletos no pagados al iniciar la aplicación
        checkUnpaidTicketAndNavigate()

        bottomNavigation.setOnNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_scan -> checkUnpaidTicketAndNavigate()
                R.id.navigation_user -> loadFragment(UserFragment())
                R.id.navigation_help -> loadFragment(AyudaFragment())
            }
            true
        }

        // Seleccionar el botón de escaneo como predeterminado
        if (savedInstanceState == null) {
            bottomNavigation.selectedItemId = R.id.navigation_scan
            checkUnpaidTicketAndNavigate() // Llamar a la función para verificar los boletos y cargar el fragmento adecuado
        }
    }

    private fun checkUnpaidTicketAndNavigate() {
        val userId = auth.currentUser?.uid

        if (userId != null) {
            val userTicketRef = firestore.collection("users").document(userId).collection("boletos")

            userTicketRef.get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        val unpaidTicket = documents.firstOrNull { it.getBoolean("pay") == false }
                        if (unpaidTicket != null) {
                            // Si hay un boleto no pagado, mostrar el fragmento del boleto
                            loadFragment(BoletoFragment())
                        } else {
                            // Si no hay boletos no pagados, mostrar el fragmento de escaneo
                            loadFragment(ScanFragment())
                        }
                    } else {
                        // Si no hay boletos, mostrar el fragmento de escaneo
                        loadFragment(ScanFragment())
                    }
                }
                .addOnFailureListener {
                    loadFragment(ScanFragment())
                }
        } else {
            // Si no hay usuario autenticado, redirigir al inicio de sesión o mostrar un mensaje
            loadFragment(ScanFragment())
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }

    private fun updateUserNavigationTitle(phoneNumber: String?) {
        if (phoneNumber == null) return

        firestore.collection("users").whereEqualTo("phone", phoneNumber).get()
            .addOnSuccessListener { documents ->
                val document = documents.firstOrNull()
                if (document != null) {
                    val name = document.getString("name") ?: "User"
                    Log.d("Firestore", "User name retrieved: $name")

                    // Actualizar el título de nav bar user
                    bottomNavigation.menu.findItem(R.id.navigation_user).title = name
                    bottomNavigation.invalidate()
                } else {
                    Log.e("Firestore", "No document found for phone: $phoneNumber")
                }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error fetching user data", e)
            }
    }

    override fun onBackPressed() {
        val builder = AlertDialog.Builder(this)
        builder.setMessage("¿Deseas salir de VirtualPark?")
            .setPositiveButton("Sí") { _, _ ->
                finishAffinity() // Cierra la aplicación
            }
            .setNegativeButton("No") { dialog, _ ->
                dialog.dismiss() // Cierra el diálogo y permanece en la misma pantalla
            }
            .create()
            .show()
    }
}