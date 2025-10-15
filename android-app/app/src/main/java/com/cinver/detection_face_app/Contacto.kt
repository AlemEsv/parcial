package com.cinver.detection_face_app

data class Contacto(
    var nombre: String = "",
    var alias: String = "",
    var estado: String = "", // Campo para el estado de ánimo
    var idcontacto: Int = 0,
    var key: String = "",
    var codigo: String = "",
    var imagenSeleccionada: Int = 0 // ID de la imagen drawable seleccionada
)