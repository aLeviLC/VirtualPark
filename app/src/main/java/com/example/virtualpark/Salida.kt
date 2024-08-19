package com.example.virtualpark

import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

class Salida : Fragment() {

    private lateinit var ivQRCode: ImageView
    private lateinit var tvTimer: TextView
    private lateinit var btnContinue: Button
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private var ticketId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_salida, container, false)

        ivQRCode = view.findViewById(R.id.iv_qr_code)
        tvTimer = view.findViewById(R.id.tv_timer)
        btnContinue = view.findViewById(R.id.btn_continue)
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        getTicketIdAndGenerateQRCode()

        startTimer()

        btnContinue.setOnClickListener {
            // Navegar al ScanFragment
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ScanFragment())
                .addToBackStack(null)
                .commit()
        }
        return view
    }

    private fun getTicketIdAndGenerateQRCode() {
        val userId = auth.currentUser?.uid ?: return

        // Consulta a Firestore para obtener el ID del boleto
        firestore.collection("users").document(userId).collection("boletos").get()
            .addOnSuccessListener { documents ->
                val ticketDocument = documents.firstOrNull()
                if (ticketDocument != null) {
                    ticketId = ticketDocument.id
                    generateQRCode(ticketId!!)
                } else {
                    Toast.makeText(context, "No se encontró el boleto", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(
                    context,
                    "Error al obtener el boleto: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun generateQRCode(ticketId: String) {
        try {
            val barcodeEncoder = BarcodeEncoder()
            val bitmap = barcodeEncoder.encodeBitmap(ticketId, BarcodeFormat.QR_CODE, 200, 200)
            ivQRCode.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Toast.makeText(
                context,
                "Error al generar el código QR: ${e.message}",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun startTimer() {
        val timerDuration = 15 * 60 * 1000L // 15 minutos en milisegundos

        object : CountDownTimer(timerDuration, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val minutes = TimeUnit.MILLISECONDS.toMinutes(millisUntilFinished)
                val seconds = TimeUnit.MILLISECONDS.toSeconds(millisUntilFinished) % 60
                tvTimer.text = String.format(
                    Locale.getDefault(),
                    "Tienes %02d:%02d min para salir",
                    minutes,
                    seconds
                )
            }

            override fun onFinish() {
                tvTimer.text = "El tiempo ha expirado"
                // Navegar al ScanFragment
                parentFragmentManager.beginTransaction()
                    .replace(R.id.fragment_container, ScanFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }.start()
    }
}