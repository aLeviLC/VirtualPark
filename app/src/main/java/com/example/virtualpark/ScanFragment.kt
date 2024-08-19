package com.example.virtualpark

import android.graphics.ImageFormat
import android.media.Image
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.TranslateAnimation
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ScanFragment : Fragment() {

    private lateinit var previewView: PreviewView
    private lateinit var scanLine: ImageView
    private lateinit var firestore: FirebaseFirestore

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_scan, container, false)

        previewView = view.findViewById(R.id.previewView)
        scanLine = view.findViewById(R.id.scan_line)
        firestore = FirebaseFirestore.getInstance()

        startScanAnimation(scanLine)
        startCamera()

        return view
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())

        cameraProviderFuture.addListener(Runnable {
            if (!isAdded) return@Runnable // Asegúrate de que el fragmento esté adjunto

            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().also {
                    it.setAnalyzer(ContextCompat.getMainExecutor(requireContext())) { imageProxy ->
                        QRCodeAnalyzer { qrCode ->
                            handleQRCodeScanned(qrCode)
                        }.analyze(imageProxy)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis)
            } catch (_: Exception) {
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }

    private fun startScanAnimation(scanLine: ImageView) {
        val animation = TranslateAnimation(
            Animation.RELATIVE_TO_PARENT,
            0f,
            Animation.RELATIVE_TO_PARENT,
            0f,
            Animation.RELATIVE_TO_PARENT,
            0f,
            Animation.RELATIVE_TO_PARENT,
            0.29f
        )
        animation.duration = 1500 // Duración de la animación
        animation.repeatCount = Animation.INFINITE
        animation.repeatMode = Animation.REVERSE
        scanLine.startAnimation(animation)
    }

    private fun handleQRCodeScanned(qrCode: String) {
        if (qrCode.startsWith("VT")) {
            verifyOrCreateTicketInFirestore(qrCode)
        }
    }

    private fun verifyOrCreateTicketInFirestore(ticketId: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        firestore.collection("kiosko").document(ticketId).get().addOnSuccessListener { document ->
            if (document.exists()) {
                // Verificar si el boleto ya está pagado en la base de datos del usuario
                firestore.collection("users").document(userId).collection("boletos")
                    .document(ticketId).get().addOnSuccessListener { userDoc ->
                        val isPaid = userDoc.getBoolean("pay") ?: false
                        if (isPaid) {
                            Toast.makeText(
                                context, "Este boleto ya ha sido pagado.", Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            // Si no está pagado, guardar en la colección de usuario
                            saveTicketToUser(ticketId, document.data ?: emptyMap())
                        }
                    }.addOnFailureListener { e ->
                        Toast.makeText(
                            context,
                            "Error al verificar el boleto: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            } else {
                // Crear nuevo ticket en kiosko
                val newTicketData = generateNewTicketData(userId)
                firestore.collection("kiosko").document(ticketId).set(newTicketData)
                    .addOnSuccessListener {
                        saveTicketToUser(ticketId, newTicketData)
                    }.addOnFailureListener { e ->
                        Toast.makeText(
                            context,
                            "Error al crear el boleto en el kiosko: ${e.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
            }
        }.addOnFailureListener {
            Toast.makeText(
                context, "Error al buscar el boleto en el kiosko.", Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun generateNewTicketData(userId: String): Map<String, Any> {
        val dateGeneration = Date()
        val calendar = Calendar.getInstance()
        calendar.time = dateGeneration
        calendar.add(Calendar.MINUTE, 15) // Añadir 15 minutos para la expiración

        val dateExpiration = calendar.time

        val sdf = SimpleDateFormat("d 'de' MMMM 'de' yyyy, h:mm:ss a z", Locale.getDefault())

        return mapOf(
            "dateGeneration" to sdf.format(dateGeneration),
            "dateExpiration" to sdf.format(dateExpiration),
            "uid" to userId
        )
    }

    private fun navigateToBoletoFragment() {
        val transaction = parentFragmentManager.beginTransaction()
        transaction.replace(R.id.fragment_container, BoletoFragment())
        transaction.addToBackStack(null)
        transaction.commit()
    }

    private fun saveTicketToUser(ticketId: String, ticketData: Map<String, Any>) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return

        val userTicketRef = firestore.collection("users").document(userId).collection("boletos")
            .document(ticketId)
        val updatedData = ticketData.toMutableMap()
        updatedData["pay"] = false // Campo "pay" inicializado en false

        userTicketRef.set(updatedData).addOnSuccessListener {
            navigateToBoletoFragment() // Navegar al fragmento del boleto
        }.addOnFailureListener {
            Toast.makeText(context, "Error al guardar el boleto en el usuario.", Toast.LENGTH_SHORT)
                .show()
        }
    }

}

class QRCodeAnalyzer(private val onQRCodeFound: (String) -> Unit) : ImageAnalysis.Analyzer {

    private val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient()

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage: Image? = imageProxy.image
        if (mediaImage != null && imageProxy.format == ImageFormat.YUV_420_888) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            barcodeScanner.process(image).addOnSuccessListener { barcodes ->
                for (barcode in barcodes) {
                    if (barcode.valueType == Barcode.TYPE_TEXT || barcode.valueType == Barcode.TYPE_URL) {
                        onQRCodeFound(barcode.rawValue ?: "")
                        break
                    }
                }
            }.addOnFailureListener {
                // Handle the error
            }.addOnCompleteListener {
                imageProxy.close()
            }
        } else {
            imageProxy.close()
        }
    }
}