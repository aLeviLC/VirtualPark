package com.example.virtualpark

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder

class BoletoFragment : Fragment() {

    private lateinit var ivTicket: ImageView
    private lateinit var tvTicketTitle: TextView
    private lateinit var tvTicketDate: TextView
    private lateinit var ivQRCode: ImageView
    private lateinit var btnPayTicket: Button
    private lateinit var firestore: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_boleto, container, false)

        ivTicket = view.findViewById(R.id.iv_ticket)
        tvTicketTitle = view.findViewById(R.id.tv_ticket_title)
        tvTicketDate = view.findViewById(R.id.tv_ticket_date)
        ivQRCode = view.findViewById(R.id.iv_qr_code)
        btnPayTicket = view.findViewById(R.id.btn_pay_ticket)
        firestore = FirebaseFirestore.getInstance()

        fetchUserTicket()

        btnPayTicket.setOnClickListener {
            navigateToPagarFragment()
        }

        return view
    }

    private fun navigateToPagarFragment() {
        val pagarFragment = pagar()
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, pagarFragment)
            .addToBackStack(null)
            .commit()
    }

    private fun fetchUserTicket() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            val userTicketRef = firestore.collection("users").document(userId).collection("boletos")

            userTicketRef.get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        val ticketDocument = documents.firstOrNull()
                        if (ticketDocument != null) {
                            val ticketId = ticketDocument.id
                            var dateGeneration = ticketDocument.getString("dateGeneration")

                            Log.d("BoletoFragment", "Fecha obtenida de Firestore: $dateGeneration")

                            // Formatear la fecha y hora antes de usarla
                            dateGeneration = formatDateTimeManually(dateGeneration)

                            Log.d("BoletoFragment", "Fecha después de formatear: $dateGeneration")

                            // Actualizar UI con los datos del boleto
                            tvTicketDate.text = "Fecha y hora:\n$dateGeneration"

                            Log.d(
                                "BoletoFragment",
                                "Fecha mostrada en el TextView: ${tvTicketDate.text}"
                            )

                            // Generar el código QR
                            generateQRCode(ticketId)

                            // Asegurarte de que la UI no se vuelva a actualizar
                            ivTicket.visibility = View.VISIBLE
                            tvTicketTitle.visibility = View.VISIBLE
                            ivQRCode.visibility = View.VISIBLE
                            btnPayTicket.visibility = View.VISIBLE
                        }
                    }
                }
                .addOnFailureListener {
                    Log.e("BoletoFragment", "Error al acceder a la base de datos: ${it.message}")
                }
        }
    }

    private fun formatDateTimeManually(dateTime: String?): String {
        return try {
            Log.d("BoletoFragment", "Formateando la fecha manualmente: $dateTime")

            // Remover la parte de la zona horaria
            val cleanDateTime = dateTime?.substringBeforeLast(" GMT") ?: ""

            val parts = cleanDateTime.split(", ")
            if (parts.size != 2) {
                return cleanDateTime // Devolver la cadena original si no tiene el formato esperado
            }

            // Remover los segundos de la hora
            val timeWithoutSeconds =
                parts[1].substringBeforeLast(":") + parts[1].substring(parts[1].lastIndexOf(' '))

            // Combinar la fecha con la hora sin segundos
            val formattedDate = "${parts[0]}\n$timeWithoutSeconds"

            Log.d("BoletoFragment", "Fecha formateada manualmente: $formattedDate")
            return formattedDate
        } catch (e: Exception) {
            Log.e("BoletoFragment", "Error al formatear la fecha manualmente: ${e.message}")
            dateTime ?: ""
        }
    }

    private fun generateQRCode(ticketId: String) {
        try {
            val barcodeEncoder = BarcodeEncoder()
            val bitmap = barcodeEncoder.encodeBitmap(ticketId, BarcodeFormat.QR_CODE, 200, 200)
            ivQRCode.setImageBitmap(bitmap)
        } catch (e: Exception) {
            Log.e("BoletoFragment", "Error al generar el código QR: ${e.message}")
        }
    }
}