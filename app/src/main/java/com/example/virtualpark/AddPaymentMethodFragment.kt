package com.example.virtualpark

import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class AddPaymentMethodFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var etCardholderName: EditText
    private lateinit var etCardNumber: EditText
    private lateinit var etExpiryDate: EditText
    private lateinit var btnAddCard: Button
    private lateinit var btnBack: ImageButton
    private lateinit var ivCardLogo: ImageView
    private lateinit var tvInvalidCard: TextView
    private lateinit var tvInvalidExpiry: TextView
    private var cardDataChanged = false
    private lateinit var etCVC: EditText
    private lateinit var btnToggleCVCVisibility: ImageButton
    private var isCVCVisible: Boolean = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_add_payment_method, container, false)

        // Inicializar Firebase Auth y Firestore
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Inicializar vistas
        etCardholderName = view.findViewById(R.id.et_cardholder_name)
        etCardNumber = view.findViewById(R.id.et_card_number)
        etExpiryDate = view.findViewById(R.id.et_expiry_date)
        etCVC = view.findViewById(R.id.et_cvc)
        btnToggleCVCVisibility = view.findViewById(R.id.btn_toggle_cvc_visibility)
        btnAddCard = view.findViewById(R.id.btn_add_card)
        btnBack = view.findViewById(R.id.btn_back)
        ivCardLogo = view.findViewById(R.id.iv_card_logo)
        tvInvalidCard = view.findViewById(R.id.tv_invalid_card)
        tvInvalidExpiry = view.findViewById(R.id.tv_invalid_expiry)

        etCVC.filters = arrayOf(InputFilter.LengthFilter(3)) // Limitar a 3 dígitos

        // Configurar el botón de regreso
        btnBack.setOnClickListener {
            showBackConfirmationDialog()
        }

        // Configurar el filtro para el número de tarjeta (solo números y longitud máxima de 16)
        etCardNumber.filters = arrayOf(InputFilter.LengthFilter(16))
        etCardNumber.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                cardDataChanged = true
                validateCardNumber()
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        // Configurar el TextWatcher para la fecha de expiración
        etExpiryDate.addTextChangedListener(object : TextWatcher {
            private var isUpdating = false
            private var oldText = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                if (!isUpdating) {
                    oldText = s.toString()
                }
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (isUpdating) return

                var input = s.toString()

                if (input.length == 1 && oldText.isEmpty()) {
                    if (input.toIntOrNull() ?: 0 > 1) {
                        input = "0$input"
                    }
                }

                if (input.length == 2 && !input.contains("/")) {
                    val month = input.toIntOrNull() ?: 0
                    if (month in 1..12) {
                        input += "/"
                        tvInvalidExpiry.visibility = View.GONE
                    } else {
                        input = ""
                        tvInvalidExpiry.text = "Por favor, ingresa una fecha válida"
                        tvInvalidExpiry.visibility = View.VISIBLE
                    }
                    isUpdating = true
                    etExpiryDate.setText(input)
                    etExpiryDate.setSelection(input.length)
                } else if (input.length < oldText.length) {
                    isUpdating = true
                    etExpiryDate.setText("")
                    tvInvalidExpiry.text = "Por favor, ingresa una fecha válida"
                    tvInvalidExpiry.visibility = View.VISIBLE
                }
            }

            override fun afterTextChanged(s: Editable?) {
                isUpdating = false
            }
        })

        // Configurar el botón para mostrar/ocultar el CVC
        btnToggleCVCVisibility.setOnClickListener {
            toggleCVCVisibility()
        }

        // Listener para agregar la tarjeta
        btnAddCard.setOnClickListener {
            if (validateCardNumber() && validateExpiryDate()) {
                showAddCardConfirmationDialog()
            }
        }

        return view
    }


    private fun validateCardNumber(): Boolean {
        val cardNumber = etCardNumber.text.toString().trim()
        return when {
            cardNumber.startsWith("4") -> {
                ivCardLogo.setImageResource(R.drawable.ic_visa_logo)
                ivCardLogo.visibility = View.VISIBLE
                tvInvalidCard.visibility = View.GONE
                true
            }

            cardNumber.startsWith("5") -> {
                ivCardLogo.setImageResource(R.drawable.ic_mastercard_logo)
                ivCardLogo.visibility = View.VISIBLE
                tvInvalidCard.visibility = View.GONE
                true
            }

            cardNumber.startsWith("3") -> {
                ivCardLogo.setImageResource(R.drawable.ic_amex_logo)
                ivCardLogo.visibility = View.VISIBLE
                tvInvalidCard.visibility = View.GONE
                true
            }

            else -> {
                ivCardLogo.setImageResource(R.drawable.ic_default_card_logo)
                tvInvalidCard.visibility = View.VISIBLE
                false
            }
        }
    }

    private fun validateExpiryDate(): Boolean {
        val expiryDate = etExpiryDate.text.toString().trim()
        val sdf = SimpleDateFormat("MM/yy", Locale.US)
        sdf.isLenient = false

        return try {
            val expiry = sdf.parse(expiryDate)
            val currentDate = Calendar.getInstance().time

            // Verificar que la fecha es válida y es después de agosto de 2024
            val validDate = Calendar.getInstance().apply {
                set(2024, Calendar.AUGUST, 1)
            }.time

            if (expiry != null && expiry.after(currentDate) && expiry.after(validDate)) {
                tvInvalidExpiry.visibility = View.GONE
                true
            } else {
                tvInvalidExpiry.visibility = View.VISIBLE
                false
            }
        } catch (e: Exception) {
            tvInvalidExpiry.visibility = View.VISIBLE
            false
        }
    }

    @SuppressLint("DefaultLocale", "DiscouragedApi")
    private fun showDatePickerDialog() {
        val calendar = Calendar.getInstance()
        val year = calendar.get(Calendar.YEAR)
        val month = calendar.get(Calendar.MONTH)

        val datePickerDialog =
            DatePickerDialog(requireContext(), { _, selectedYear, selectedMonth, _ ->
                val formattedDate =
                    String.format("%02d/%02d", selectedMonth + 1, selectedYear % 100)
                etExpiryDate.setText(formattedDate)

                // Validar la fecha seleccionada
                validateExpiryDate()
            }, year, month, calendar.get(Calendar.DAY_OF_MONTH))

        // Establecer fecha mínima
        datePickerDialog.datePicker.minDate = getMinDate()
        datePickerDialog.datePicker.findViewById<View>(
            resources.getIdentifier("android:id/day", null, null)
        )?.visibility = View.GONE

        datePickerDialog.show()
    }

    private fun toggleCVCVisibility() {
        if (isCVCVisible) {
            // Ocultar CVC
            etCVC.inputType =
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            btnToggleCVCVisibility.setImageResource(R.drawable.ic_eye_off) // Cambiar ícono a ojo cerrado
        } else {
            // Mostrar CVC
            etCVC.inputType = InputType.TYPE_CLASS_NUMBER
            btnToggleCVCVisibility.setImageResource(R.drawable.ic_eye_on) // Cambiar ícono a ojo abierto
        }
        etCVC.setSelection(etCVC.text.length) // Mantener el cursor al final
        isCVCVisible = !isCVCVisible // Alternar estado
    }

    private fun getMinDate(): Long {
        val calendar = Calendar.getInstance()
        calendar.set(2024, Calendar.AUGUST, 1)
        return calendar.timeInMillis
    }

    private fun showAddCardConfirmationDialog() {
        AlertDialog.Builder(requireContext())
            .setMessage("¿Estás seguro de que quieres agregar esta tarjeta?")
            .setPositiveButton("Sí") { _, _ -> addCardToDatabase() }
            .setNegativeButton("No", null)
            .create()
            .show()
    }

    private fun addCardToDatabase() {
        val cardholderName = etCardholderName.text.toString().trim().uppercase(Locale.getDefault())
        val cardNumber = etCardNumber.text.toString().trim()
        val expiryDate = etExpiryDate.text.toString().trim()
        val cvc = etCVC.text.toString().trim()

        val userId = auth.currentUser?.uid ?: return

        val cardData = hashMapOf(
            "cardholderName" to cardholderName,
            "cardNumber" to cardNumber,
            "expiryDate" to expiryDate,
            "cvc" to cvc
        )

        firestore.collection("users").document(userId)
            .collection("tarjetas")
            .add(cardData)
            .addOnSuccessListener {
                Toast.makeText(context, "Tarjeta agregada exitosamente", Toast.LENGTH_SHORT).show()
                parentFragmentManager.popBackStack()
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error al agregar la tarjeta", Toast.LENGTH_SHORT).show()
            }
    }

    private fun showBackConfirmationDialog() {
        if (cardDataChanged) {
            AlertDialog.Builder(requireContext())
                .setMessage("Tienes cambios sin guardar. ¿Estás seguro de que quieres regresar?")
                .setPositiveButton("Sí") { _, _ -> parentFragmentManager.popBackStack() }
                .setNegativeButton("No", null)
                .create()
                .show()
        } else {
            parentFragmentManager.popBackStack()
        }
    }
}