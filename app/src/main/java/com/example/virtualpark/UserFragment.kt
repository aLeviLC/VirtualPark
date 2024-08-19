package com.example.virtualpark

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import de.hdodenhof.circleimageview.CircleImageView
import java.io.ByteArrayOutputStream

@Suppress("DEPRECATION")
class UserFragment : Fragment() {

    private lateinit var viewHistoryButton: TextView
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore
    private lateinit var storage: FirebaseStorage
    private lateinit var userProfileImageView: CircleImageView
    private lateinit var userNameTextView: TextView
    private lateinit var userIdTextView: TextView
    private lateinit var userPhoneTextView: TextView
    private lateinit var carPlatesTextView: TextView
    private lateinit var carBrandTextView: TextView
    private lateinit var carModelTextView: TextView
    private lateinit var carYearTextView: TextView
    private lateinit var editCarButton: TextView
    private lateinit var editUser: TextView
    private lateinit var changePasswordButton: TextView
    private lateinit var addPaymentMethodButton: TextView
    private lateinit var logoutButton: Button
    private lateinit var paymentMethodsContainer: LinearLayout
    private var currentImageUrl: String? = null

    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            bitmap?.let {
                userProfileImageView.setImageBitmap(it)
                uploadProfileImage(bitmap)
            }
        }

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                userProfileImageView.setImageURI(uri)
                val bitmap = (userProfileImageView.drawable as BitmapDrawable).bitmap
                uploadProfileImage(bitmap)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_user, container, false)

        // Initialize Firebase Auth, Firestore, and Storage
        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        // Initialize views
        userProfileImageView = view.findViewById(R.id.iv_user_profile)
        userNameTextView = view.findViewById(R.id.tv_user_name)
        userIdTextView = view.findViewById(R.id.tv_user_id)
        userPhoneTextView = view.findViewById(R.id.tv_phone)
        carPlatesTextView = view.findViewById(R.id.tv_car_plates)
        carBrandTextView = view.findViewById(R.id.tv_car_brand)
        carModelTextView = view.findViewById(R.id.tv_car_model)
        carYearTextView = view.findViewById(R.id.tv_car_year)
        editCarButton = view.findViewById(R.id.btn_edit_car)
        editUser = view.findViewById(R.id.btn_edit_profile_image)
        changePasswordButton = view.findViewById(R.id.btn_change_password)
        addPaymentMethodButton = view.findViewById(R.id.btn_add_payment_method)
        logoutButton = view.findViewById(R.id.btn_logout)
        paymentMethodsContainer = view.findViewById(R.id.payment_methods_container)
        viewHistoryButton = view.findViewById(R.id.btn_view_ticket_history)

        viewHistoryButton.setOnClickListener {
            // Lógica para navegar al fragmento de historial de boletos
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, TicketHistoryFragment())
                .addToBackStack(null)
                .commit()
        }

        // Subrayar textviews
        editCarButton.paintFlags = editCarButton.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        changePasswordButton.paintFlags =
            changePasswordButton.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        addPaymentMethodButton.paintFlags =
            addPaymentMethodButton.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        editUser.paintFlags = editUser.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        viewHistoryButton.paintFlags = viewHistoryButton.paintFlags or Paint.UNDERLINE_TEXT_FLAG

        view.findViewById<TextView>(R.id.btn_edit_profile_image).setOnClickListener {
            showImagePickerOptions()
        }

        logoutButton.setOnClickListener {
            // Eliminar el estado de la sesión
            val sharedPreferences =
                activity?.getSharedPreferences("UserSession", Context.MODE_PRIVATE)
            val editor = sharedPreferences?.edit()
            editor?.clear()
            editor?.apply()

            auth.signOut()

            val intent = Intent(activity, login::class.java)
            startActivity(intent)
            activity?.finish()
        }

        editCarButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, EditCarFragment())
                .addToBackStack(null)
                .commit()
        }

        changePasswordButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ChangePasswordFragment())
                .addToBackStack(null)
                .commit()
        }

        addPaymentMethodButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, AddPaymentMethodFragment())
                .addToBackStack(null)
                .commit()
        }

        userProfileImageView.setOnClickListener {
            showLargeImage()
        }

        return view
    }

    private fun showLargeImage() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_image)

        val imageView = dialog.findViewById<CircleImageView>(R.id.iv_large_image)

        // Cargar la imagen actual en el ImageView del diálogo
        Glide.with(this)
            .load(currentImageUrl) // Usar la URL de la imagen actual
            .placeholder(R.drawable.ic_user_placeholder)
            .into(imageView)

        // Mostrar el diálogo
        dialog.show()

        // Cerrar el diálogo al hacer clic en la imagen
        imageView.setOnClickListener {
            dialog.dismiss()
        }
    }


    private fun showImagePickerOptions() {
        val options = arrayOf("Take a Photo", "Choose from Gallery")
        AlertDialog.Builder(requireContext())
            .setTitle("Change Profile Picture")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> takePicture()
                    1 -> pickImageFromGallery()
                }
            }
            .show()
    }

    private fun takePicture() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            takePicture.launch(null)
        } else {
            ActivityCompat.requestPermissions(
                requireActivity(),
                arrayOf(Manifest.permission.CAMERA),
                102
            )
        }
    }


    private fun checkPermissionAndOpenGallery() {
        Log.d("UserFragment", "Checking storage permission")

        // Check for Android version to request appropriate permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_MEDIA_IMAGES
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("UserFragment", "Permission granted, opening gallery")
                pickImage.launch("image/*")
            } else {
                Log.d("UserFragment", "Permission not granted, requesting permission")
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.READ_MEDIA_IMAGES),
                    101
                )
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("UserFragment", "Permission granted, opening gallery")
                pickImage.launch("image/*")
            } else {
                Log.d("UserFragment", "Permission not granted, requesting permission")
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE),
                    101
                )
            }
        }
    }

    private fun pickImageFromGallery() {
        checkPermissionAndOpenGallery()
    }

    private fun uploadProfileImage(bitmap: Bitmap) {
        Log.d("UserFragment", "Uploading image")
        val userId = auth.currentUser?.uid ?: return
        val storageRef = storage.reference.child("profile_images/$userId.jpg")

        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos)
        val data = baos.toByteArray()

        storageRef.putBytes(data)
            .addOnSuccessListener {
                Log.d("UserFragment", "Image upload successful")
                storageRef.downloadUrl.addOnSuccessListener { uri ->
                    // Guarda la URL en Firestore
                    firestore.collection("users").document(userId)
                        .update("profileImageUrl", uri.toString())
                        .addOnSuccessListener {
                            Log.d("UserFragment", "Profile image URL saved to Firestore")
                            loadImageIntoImageView(uri.toString())
                        }
                        .addOnFailureListener { e ->
                            Log.e("UserFragment", "Error saving profile image URL", e)
                        }
                }
            }
            .addOnFailureListener { e ->
                Log.e("UserFragment", "Image upload failed", e)
                Toast.makeText(context, "Error al subir la imagen", Toast.LENGTH_SHORT).show()
            }
    }

    private fun loadImageIntoImageView(url: String) {
        Glide.with(this)
            .load(url)
            .placeholder(R.drawable.ic_user_placeholder)
            .error(R.drawable.ic_user_placeholder)
            .into(userProfileImageView)
    }

    // Manejar la respuesta de la solicitud de permisos
    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 101 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d("UserFragment", "Permission granted, opening gallery")
            pickImageFromGallery()
        } else if (requestCode == 102 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d("UserFragment", "Permission granted, opening camera")
            takePicture.launch(null)
        } else {
            Log.d("UserFragment", "Permission denied")
            Toast.makeText(context, "Permiso denegado", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onResume() {
        super.onResume()

        // Cargar los datos del usuario si está autenticado
        loadUserData()
    }

    private fun loadUserData() {
        Log.d("UserFragment", "Calling loadUserData()")

        val userId = auth.currentUser?.uid

        if (userId == null) {
            Log.e("UserFragment", "UID is null. Redirecting to login.")
            val intent = Intent(activity, login::class.java)
            startActivity(intent)
            activity?.finish()
            return
        }

        // Mostrar el UID y el número de teléfono del usuario
        userIdTextView.text = "UID: $userId"
        userPhoneTextView.text = "Phone: ${auth.currentUser?.phoneNumber}"

        // Obtener datos del usuario
        firestore.collection("users").document(userId).get()
            .addOnSuccessListener { document ->
                if (document != null) {
                    val name = document.getString("name") ?: ""
                    val lastName = document.getString("lastName") ?: ""
                    val carPlates = document.getString("carPlates") ?: "No registrado"
                    val carBrand = document.getString("carBrand") ?: "No registrado"
                    val carModel = document.getString("carModel") ?: "No registrado"
                    val carYear = document.getString("carYear") ?: "No registrado"
                    val profileImageUrl = document.getString("profileImageUrl") ?: ""

                    userNameTextView.text = "$name $lastName"
                    userNameTextView.visibility = View.VISIBLE
                    userIdTextView.visibility = View.VISIBLE
                    userPhoneTextView.visibility = View.VISIBLE

                    carPlatesTextView.text = "Placas: $carPlates"
                    carBrandTextView.text = "Marca: $carBrand"
                    carModelTextView.text = "Modelo: $carModel"
                    carYearTextView.text = "Año: $carYear"

                    carPlatesTextView.visibility = View.VISIBLE
                    carBrandTextView.visibility = View.VISIBLE
                    carModelTextView.visibility = View.VISIBLE
                    carYearTextView.visibility = View.VISIBLE

                    if (profileImageUrl.isNotEmpty()) {
                        loadImageIntoImageView(profileImageUrl)
                        currentImageUrl = profileImageUrl
                    }

                    // Cargar métodos de pago
                    loadPaymentMethods(userId)
                }
            }
            .addOnFailureListener { e ->
                Log.e("UserFragment", "Error fetching user data", e)
            }
    }

    private fun loadPaymentMethods(userId: String) {
        paymentMethodsContainer.removeAllViews()

        firestore.collection("users").document(userId).collection("tarjetas")
            .get()
            .addOnSuccessListener { documents ->
                for (document in documents) {
                    val cardNumber = document.getString("cardNumber") ?: ""
                    val cardholderName = document.getString("cardholderName") ?: ""
                    val cardId = document.id  // Obtener el ID del documento para la eliminación

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

                    deleteButton.setOnClickListener {
                        showDeleteConfirmationDialog(userId, cardId, paymentMethodView)
                    }

                    paymentMethodsContainer.addView(paymentMethodView)
                }
            }
            .addOnFailureListener { e ->
                Log.e("UserFragment", "Error fetching payment methods", e)
            }
    }

    private fun showDeleteConfirmationDialog(userId: String, cardId: String, view: View) {
        AlertDialog.Builder(requireContext())
            .setMessage("¿Estás seguro de que quieres eliminar esta tarjeta?")
            .setPositiveButton("Sí") { _, _ -> deleteCard(userId, cardId, view) }
            .setNegativeButton("No", null)
            .create()
            .show()
    }

    private fun deleteCard(userId: String, cardId: String, view: View) {
        firestore.collection("users").document(userId).collection("tarjetas").document(cardId)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(context, "Tarjeta eliminada", Toast.LENGTH_SHORT).show()
                paymentMethodsContainer.removeView(view)
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error al eliminar la tarjeta", Toast.LENGTH_SHORT).show()
            }
    }
}