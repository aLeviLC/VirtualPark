package com.example.virtualpark

import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Calendar

class EditCarFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var etCarPlates: EditText
    private lateinit var etCarBrand: EditText
    private lateinit var etCarModel: EditText
    private lateinit var spinnerCarYear: Spinner
    private lateinit var btnSaveCar: Button
    private var carDataChanged = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_edit_car, container, false)

        // Inicializar Firebase Auth y Firestore
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        // Inicializar vistas
        etCarPlates = view.findViewById(R.id.et_car_plates)
        etCarBrand = view.findViewById(R.id.et_car_brand)
        etCarModel = view.findViewById(R.id.et_car_model)
        spinnerCarYear = view.findViewById(R.id.spinner_car_year)
        btnSaveCar = view.findViewById(R.id.btn_save_car)

        configureYearSpinner()

        // Configurar el botón de regreso
        val btnBack: ImageButton = view.findViewById(R.id.btn_back)
        btnBack.setOnClickListener {
            showBackConfirmationDialog()
        }

        // Limitar la entrada de las placas a 7 caracteres y validar la estructura
        etCarPlates.filters = arrayOf(InputFilter.LengthFilter(7))
        etCarPlates.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                carDataChanged = true
                val input = s.toString().uppercase()
                if (input.length >= 3 && !input.substring(0, 3).matches(Regex("[A-Z]{3}"))) {
                    etCarPlates.error = "Las primeras 3 posiciones deben ser letras"
                }
            }

            override fun afterTextChanged(s: Editable?) {
                // Forzar que el texto se mantenga en mayúsculas
                s?.let {
                    val text = it.toString().uppercase()
                    if (text != it.toString()) {
                        it.replace(0, it.length, text)
                    }
                }
            }
        })

        btnSaveCar.setOnClickListener {
            saveCarData()
        }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    showBackConfirmationDialog()
                }
            })

        // Cargar datos si ya existen
        loadCarData()

        return view
    }

    private fun configureYearSpinner() {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val years = (1940..currentYear).toList().reversed()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, years)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerCarYear.adapter = adapter
    }

    private fun loadCarData() {
        val userPhone = auth.currentUser?.phoneNumber
        userPhone?.let {
            firestore.collection("users").whereEqualTo("phone", userPhone).get()
                .addOnSuccessListener { documents ->
                    val document = documents.firstOrNull()
                    if (document != null) {
                        etCarBrand.setText(document.getString("carBrand") ?: "")
                        etCarModel.setText(document.getString("carModel") ?: "")
                        etCarPlates.setText(document.getString("carPlates") ?: "")

                        val carYear = document.getString("carYear") ?: ""
                        val spinnerPosition =
                            (spinnerCarYear.adapter as ArrayAdapter<String>).getPosition(carYear)
                        spinnerCarYear.setSelection(spinnerPosition)
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(
                        requireContext(),
                        "Error al cargar los datos.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }

    private fun saveCarData() {
        val carBrand = etCarBrand.text.toString().trim().uppercase()
        val carModel = etCarModel.text.toString().trim().uppercase()
        val carYear = spinnerCarYear.selectedItem.toString()
        val carPlates = etCarPlates.text.toString().trim().uppercase()

        if (carBrand.isEmpty() || carModel.isEmpty() || carYear.isEmpty() || carPlates.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Por favor, completa todos los campos.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val userPhone = auth.currentUser?.phoneNumber
        if (userPhone != null) {
            val carData = hashMapOf(
                "carBrand" to carBrand,
                "carModel" to carModel,
                "carYear" to carYear,
                "carPlates" to carPlates
            )

            firestore.collection("users").whereEqualTo("phone", userPhone).get()
                .addOnSuccessListener { documents ->
                    val document = documents.firstOrNull()
                    if (document != null) {
                        // Si el documento ya existe, usar update
                        firestore.collection("users").document(document.id)
                            .update(carData as Map<String, Any>)
                            .addOnSuccessListener {
                                Toast.makeText(
                                    requireContext(),
                                    "Datos del auto actualizados exitosamente.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                carDataChanged = false
                                // Regresar al fragmento de usuario después de guardar
                                requireActivity().supportFragmentManager.popBackStack()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(
                                    requireContext(),
                                    "Error al actualizar los datos: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    } else {
                        // Si el documento no existe, usar set para agregar los campos
                        firestore.collection("users").document(userPhone)
                            .set(carData, SetOptions.merge())
                            .addOnSuccessListener {
                                Toast.makeText(
                                    requireContext(),
                                    "Datos del auto guardados exitosamente.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                carDataChanged = false
                                // Regresar al fragmento de usuario después de guardar
                                requireActivity().supportFragmentManager.popBackStack()
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(
                                    requireContext(),
                                    "Error al guardar los datos: ${e.message}",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                    }
                }
        } else {
            Toast.makeText(requireContext(), "Error: usuario no autenticado.", Toast.LENGTH_SHORT)
                .show()
        }
    }

    private fun showBackConfirmationDialog() {
        if (carDataChanged) {
            AlertDialog.Builder(requireContext())
                .setMessage("Tienes cambios sin guardar. ¿Estás seguro de que quieres regresar?")
                .setPositiveButton("Sí") { _, _ -> requireActivity().supportFragmentManager.popBackStack() }
                .setNegativeButton("No", null)
                .create()
                .show()
        } else {
            requireActivity().supportFragmentManager.popBackStack()
        }
    }
}