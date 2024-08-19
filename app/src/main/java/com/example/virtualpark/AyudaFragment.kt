package com.example.virtualpark

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment

class AyudaFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_ayuda, container, false)

        val facebookIcon: ImageView = view.findViewById(R.id.iv_facebook_icon)
        val facebookText: TextView = view.findViewById(R.id.tv_facebook)

        val instagramIcon: ImageView = view.findViewById(R.id.iv_instagram_icon)
        val instagramText: TextView = view.findViewById(R.id.tv_instagram)

        val emailIcon: ImageView = view.findViewById(R.id.iv_email_icon)
        val emailText: TextView = view.findViewById(R.id.tv_email)

        val xIcon: ImageView = view.findViewById(R.id.iv_x_icon)
        val xText: TextView = view.findViewById(R.id.tv_x)

        // Asignar listeners a los números de teléfono
        val phoneText1: TextView = view.findViewById(R.id.tv_phone1)
        val phoneText2: TextView = view.findViewById(R.id.tv_phone2)
        val phoneText3: TextView = view.findViewById(R.id.tv_phone3)

        // Listener para abrir la página de Facebook
        val openFacebook = View.OnClickListener {
            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.facebook.com/VirtualPark"))
            startActivity(intent)
        }
        facebookIcon.setOnClickListener(openFacebook)
        facebookText.setOnClickListener(openFacebook)

        // Listener para abrir la página de Instagram
        val openInstagram = View.OnClickListener {
            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse("https://www.instagram.com/VirtualPark"))
            startActivity(intent)
        }
        instagramIcon.setOnClickListener(openInstagram)
        instagramText.setOnClickListener(openInstagram)

        // Listener para abrir la app de email
        val sendEmail = View.OnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:soporte@ratech.vp.com")
            }
            startActivity(intent)
        }
        emailIcon.setOnClickListener(sendEmail)
        emailText.setOnClickListener(sendEmail)

        val openX = View.OnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.x.com/VirtualPark"))
            startActivity(intent)
        }
        xIcon.setOnClickListener(openX)
        xText.setOnClickListener(openX)

        val dialPhone1 = View.OnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+526692482176"))
            startActivity(intent)
        }
        phoneText1.setOnClickListener(dialPhone1)

        val dialPhone2 = View.OnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+525698548512"))
            startActivity(intent)
        }
        phoneText2.setOnClickListener(dialPhone2)

        val dialPhone3 = View.OnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+528967584296"))
            startActivity(intent)
        }
        phoneText3.setOnClickListener(dialPhone3)

        return view
    }
}