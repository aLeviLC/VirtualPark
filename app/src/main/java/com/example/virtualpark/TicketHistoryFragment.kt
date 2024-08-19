package com.example.virtualpark

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.text.SimpleDateFormat
import java.util.Locale

class TicketHistoryAdapter(
    private val tickets: List<Ticket>
) : RecyclerView.Adapter<TicketHistoryAdapter.TicketViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TicketViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ticket, parent, false)
        return TicketViewHolder(view)
    }

    override fun onBindViewHolder(holder: TicketViewHolder, position: Int) {
        val ticket = tickets[position]
        holder.bind(ticket)

        // Aquí actualizamos el TextView con la fecha del boleto
        holder.ticketDateTextView.text = ticket.date

        // Aquí se asegura de que los detalles del ticket estén en el estado correcto (expandido o reducido)
        val isExpanded = holder.ticketDetails.visibility == View.VISIBLE
        holder.expandIcon.setImageResource(
            if (isExpanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more
        )

        holder.expandIcon.setOnClickListener {
            val wasExpanded = holder.ticketDetails.visibility == View.VISIBLE
            holder.ticketDetails.visibility = if (wasExpanded) View.GONE else View.VISIBLE
            holder.expandIcon.setImageResource(
                if (wasExpanded) R.drawable.ic_expand_more else R.drawable.ic_expand_less
            )
        }
    }

    override fun getItemCount(): Int = tickets.size

    inner class TicketViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ticketDateTextView: TextView = itemView.findViewById(R.id.tv_ticket_date)
        private val qrCodeImageView: ImageView = itemView.findViewById(R.id.iv_qr_code)
        private val ticketIdTextView: TextView = itemView.findViewById(R.id.tv_ticket_id)
        val ticketDetails: View = itemView.findViewById(R.id.ll_ticket_details)
        val expandIcon: ImageView = itemView.findViewById(R.id.iv_expand_icon)

        fun bind(ticket: Ticket) {
            ticketIdTextView.text = "ID: ${ticket.id}"

            // Generar el código QR
            val qrCodeBitmap = generateQRCode(ticket.id)
            qrCodeImageView.setImageBitmap(qrCodeBitmap)
        }

        private fun generateQRCode(ticketId: String): Bitmap {
            val writer = QRCodeWriter()
            val bitMatrix = writer.encode(ticketId, BarcodeFormat.QR_CODE, 200, 200)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
            for (x in 0 until width) {
                for (y in 0 until height) {
                    bmp.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
                }
            }
            return bmp
        }
    }
}

data class Ticket(
    val id: String,
    val date: String
)

class TicketHistoryFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TicketHistoryAdapter
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ticket_history, container, false)

        recyclerView = view.findViewById(R.id.recyclerViewTickets)
        recyclerView.layoutManager = LinearLayoutManager(context)

        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()

        loadTickets()

        val btnBack: ImageButton = view.findViewById(R.id.btn_back)
        btnBack.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, UserFragment())
                .addToBackStack(null)
                .commit()
        }

        return view
    }

    private fun formatDateTime(dateTime: String): String {
        return try {
            val originalFormat =
                SimpleDateFormat("d 'de' MMMM 'de' yyyy, h:mm:ss a z", Locale.getDefault())
            val date = originalFormat.parse(dateTime)
            val targetFormat =
                SimpleDateFormat("d 'de' MMMM 'de' yyyy, h:mm:ss a", Locale.getDefault())
            targetFormat.format(date ?: "")
        } catch (e: Exception) {
            dateTime
        }
    }

    private fun loadTickets() {
        val userId = auth.currentUser?.uid ?: return

        firestore.collection("users").document(userId).collection("boletos")
            .whereEqualTo("pay", true) // Filtrar solo los boletos pagados
            .get()
            .addOnSuccessListener { documents ->
                val tickets = documents.map { document ->
                    Ticket(
                        id = document.id,
                        date = formatDateTime(
                            document.getString("dateExpiration") ?: "Fecha no disponible"
                        )
                    )
                }
                adapter = TicketHistoryAdapter(tickets)
                recyclerView.adapter = adapter
            }
            .addOnFailureListener {
            }
    }
}