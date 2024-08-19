package com.example.virtualpark

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.VideoView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class pagar : Fragment() {

    private lateinit var paymentMethodsContainer: LinearLayout
    private lateinit var btnAddPaymentMethod: Button
    private lateinit var btnWatchAd: Button
    private lateinit var btnPay: Button
    private lateinit var btnBack: ImageButton

    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    private var selectedPaymentMethodId: String? = null
    private var discountApplied: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_pagar, container, false)

        paymentMethodsContainer = view.findViewById(R.id.payment_methods_container)
        btnAddPaymentMethod = view.findViewById(R.id.btn_add_payment_method)
        btnWatchAd = view.findViewById(R.id.btn_watch_ad)
        btnPay = view.findViewById(R.id.btn_pay)
        btnBack = view.findViewById(R.id.btn_back)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        // Cargar métodos de pago desde Firestore
        loadPaymentMethods()

        // Configurar listeners de botones
        btnAddPaymentMethod.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AddPaymentMethodFragment())
                .addToBackStack(null)
                .commit()
        }

        btnWatchAd.setOnClickListener {
            watchAdAndApplyDiscount()
        }

        btnPay.setOnClickListener {
            processPayment()
        }

        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        return view
    }

    private fun loadPaymentMethods() {
        val userId = auth.currentUser?.uid ?: return
        paymentMethodsContainer.removeAllViews()

        firestore.collection("users").document(userId).collection("tarjetas")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val cardNumber = document.getString("cardNumber") ?: ""
                    val cardholderName = document.getString("cardholderName") ?: ""
                    val cardId = document.id

                    val cardType = when {
                        cardNumber.startsWith("4") -> R.drawable.ic_visa_logo
                        cardNumber.startsWith("5") -> R.drawable.ic_mastercard_logo
                        cardNumber.startsWith("3") -> R.drawable.ic_amex_logo
                        else -> R.drawable.ic_default_card_logo
                    }

                    val paymentMethodView = LayoutInflater.from(context)
                        .inflate(R.layout.item_payment_method, paymentMethodsContainer, false)
                    val cardLogoImageView =
                        paymentMethodView.findViewById<ImageView>(R.id.iv_card_logo)
                    val cardNumberTextView =
                        paymentMethodView.findViewById<TextView>(R.id.tv_card_number)
                    val cardholderNameTextView =
                        paymentMethodView.findViewById<TextView>(R.id.tv_cardholder_name)
                    val deleteButton =
                        paymentMethodView.findViewById<TextView>(R.id.btn_delete_card)

                    cardLogoImageView.setImageResource(cardType)
                    cardNumberTextView.text = "**** **** **** ${cardNumber.takeLast(4)}"
                    cardholderNameTextView.text = cardholderName

                    deleteButton.visibility = View.GONE

                    paymentMethodView.setOnClickListener {
                        selectedPaymentMethodId = cardId
                        highlightSelectedPaymentMethod(paymentMethodView)
                    }

                    paymentMethodsContainer.addView(paymentMethodView)
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    context,
                    "Error al cargar métodos de pago: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun highlightSelectedPaymentMethod(view: View) {
        for (i in 0 until paymentMethodsContainer.childCount) {
            val child = paymentMethodsContainer.getChildAt(i)
            child.setBackgroundResource(if (child == view) R.drawable.default_payment_method_background else R.drawable.selected_payment_method_background)
        }
    }

    @Suppress("DEPRECATION")
    private fun enterFullScreen() {
        activity?.window?.decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    @Suppress("DEPRECATION")
    private fun exitFullScreen() {
        activity?.window?.decorView?.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_VISIBLE
                )
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun watchAdAndApplyDiscount() {
        if (selectedPaymentMethodId == null) {
            Toast.makeText(context, "Por favor, selecciona un método de pago.", Toast.LENGTH_SHORT)
                .show()
            return
        }

        val videoView: VideoView = view?.findViewById(R.id.vv_ad_video) ?: return
        val btnWatchAd: Button = view?.findViewById(R.id.btn_watch_ad) ?: return
        val dimBackground: View = view?.findViewById(R.id.dim_background) ?: return

        // Configurar el video y hacerlo visible
        val videoUri = Uri.parse("android.resource://${requireContext().packageName}/raw/anuncio")
        videoView.setVideoURI(videoUri)
        videoView.visibility = View.VISIBLE
        dimBackground.visibility = View.VISIBLE
        enterFullScreen()
        videoView.start()

        btnWatchAd.isEnabled = false
        btnWatchAd.alpha = 0.5f

        // Evitar que el usuario pueda pausar o salir del video
        videoView.setOnTouchListener(null)

        videoView.setOnCompletionListener {
            videoView.visibility = View.GONE
            dimBackground.visibility = View.GONE
            exitFullScreen()
            Toast.makeText(context, "Aplicando descuento...", Toast.LENGTH_SHORT).show()
            discountApplied = true
            btnPay.text = "Pagar $6 MXN"
        }
    }

    private fun processPayment() {
        if (selectedPaymentMethodId == null) {
            Toast.makeText(context, "Por favor, selecciona un método de pago.", Toast.LENGTH_SHORT)
                .show()
            return
        }

        val userId = auth.currentUser?.uid ?: return

        // Buscar el boleto que no ha sido pagado (pay == false)
        firestore.collection("users").document(userId).collection("boletos")
            .whereEqualTo("pay", false)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    Toast.makeText(
                        context,
                        "No hay boletos pendientes de pago.",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addOnSuccessListener
                }

                val boletoDocument = documents.firstOrNull()
                if (boletoDocument != null) {
                    val boletoId = boletoDocument.id

                    // Agregar log para verificar el ID del boleto y el ID del usuario
                    Log.d("ProcessPayment", "Usuario ID: $userId, Boleto ID: $boletoId")

                    // Actualizar la base de datos del usuario para marcar el boleto como pagado y actualizar la fecha de expiración
                    val newExpirationDate = Calendar.getInstance().apply {
                        add(Calendar.MINUTE, 15) // Sumar 15 minutos a la hora actual
                    }.time

                    val formattedDate = SimpleDateFormat(
                        "d 'de' MMMM 'de' yyyy, h:mm:ss a z",
                        Locale.getDefault()
                    ).format(newExpirationDate)

                    // Agregar log para la fecha de expiración formateada
                    Log.d("ProcessPayment", "Fecha de expiración formateada: $formattedDate")

                    // Actualizar el boleto en la base de datos del usuario
                    firestore.collection("users").document(userId).collection("boletos")
                        .document(boletoId)
                        .update("pay", true, "dateExpiration", formattedDate)
                        .addOnSuccessListener {
                            // Log en caso de éxito
                            Log.d(
                                "ProcessPayment",
                                "Pago actualizado con éxito en Firestore (usuario)"
                            )

                            // También actualizar la fecha de expiración en la base de datos del kiosko
                            firestore.collection("kiosko").document(boletoId)
                                .update("dateExpiration", formattedDate)
                                .addOnSuccessListener {
                                    // Log en caso de éxito en la actualización del kiosko
                                    Log.d(
                                        "ProcessPayment",
                                        "Fecha de expiración actualizada en Firestore (kiosko)"
                                    )
                                    navigateToSalidaFragment(boletoId)
                                }
                                .addOnFailureListener { e ->
                                    // Log en caso de error al actualizar el kiosko
                                    Log.e(
                                        "ProcessPayment",
                                        "Error al actualizar la fecha en kiosko: ${e.message}"
                                    )
                                    Toast.makeText(
                                        context,
                                        "Error al actualizar la fecha en kiosko: ${e.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                        }
                        .addOnFailureListener { e ->
                            // Log en caso de error en la actualización del usuario
                            Log.e("ProcessPayment", "Error al procesar el pago: ${e.message}")
                            Toast.makeText(
                                context,
                                "Error al procesar el pago: ${e.message}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                } else {
                    Toast.makeText(
                        context,
                        "No se encontró un boleto pendiente de pago.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("ProcessPayment", "Error al buscar el boleto: ${e.message}")
                Toast.makeText(
                    context,
                    "Error al buscar el boleto: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun navigateToSalidaFragment(boletoId: String) {
        // Crear un Bundle para pasar el ID del boleto al fragmento de salida
        val bundle = Bundle().apply {
            putString("boletoId", boletoId)
        }

        // Navegar al fragmento de salida
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, Salida().apply { arguments = bundle })
            .addToBackStack(null)
            .commit()
    }

}