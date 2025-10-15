package com.cinver.detection_face_app

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

class MyAdaptador(private val context: Context) : BaseAdapter() {

    override fun getCount(): Int {
        return MyGlobal.miscontactos.size
    }

    override fun getItem(position: Int): Any {
        return MyGlobal.miscontactos[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view: View
        val viewHolder: ViewHolder

        if (convertView == null) {
            view = LayoutInflater.from(context).inflate(R.layout.misfilas, parent, false)
            viewHolder = ViewHolder(view)
            view.tag = viewHolder
        } else {
            view = convertView
            viewHolder = view.tag as ViewHolder
        }

        val contacto = getItem(position) as Contacto
        viewHolder.lblminombre.text = contacto.nombre
        viewHolder.lblmialias.text = contacto.alias
        viewHolder.lblmiestado.text = contacto.estado

        // Mostrar la imagen que realmente seleccionó el usuario
        val imageResourceId = if (contacto.imagenSeleccionada != 0) {
            contacto.imagenSeleccionada // Usar la imagen que seleccionó el usuario
        } else {
            // Fallback: imagen basada en emoción (para compatibilidad con datos antiguos)
            when (contacto.estado.lowercase()) {
                "feliz" -> R.drawable.image_1
                "triste" -> R.drawable.image_3
                "enojado" -> R.drawable.image_2
                "miedo" -> R.drawable.image_5
                "disgusto" -> R.drawable.image_4
                else -> R.drawable.test_image
            }
        }
        viewHolder.imgmifoto.setImageResource(imageResourceId)


        return view
    }

    private class ViewHolder(view: View) {
        val lblminombre: TextView = view.findViewById(R.id.lblminombre)
        val lblmialias: TextView = view.findViewById(R.id.lblmialias)
        val lblmiestado: TextView = view.findViewById(R.id.lblmiestado)
        val imgmifoto: ImageView = view.findViewById(R.id.imgmifoto)
    }
}