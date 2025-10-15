package com.cinver.detection_face_app

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import com.cinver.detection_face_app.databinding.ActivityMainBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedImageUri: Uri? = null
    private var selectedImageDrawableId: Int = 0 // Nueva variable para guardar el ID del drawable

    // Referencia a la base de datos de Firebase
    private val myRef = FirebaseDatabase.getInstance("https://emotionsdb-adf68-default-rtdb.firebaseio.com/")
        .getReference("contactos")
    private var adapter: MyAdaptador? = null

    // Lista de imágenes de muestra para seleccionar
    private val imageList = listOf(
        Pair("Test", R.drawable.test_image),
        Pair("Feliz", R.drawable.image_1),
        Pair("Enojado", R.drawable.image_2),
        Pair("Triste", R.drawable.image_3),
        Pair("Disgusto", R.drawable.image_4),
        Pair("Miedo", R.drawable.image_5)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Inicializar el adaptador para el ListView
        adapter = MyAdaptador(this)
        binding.lvcontactos.adapter = adapter

        setupListeners()
        listenForFirebaseChanges()
    }

    private fun setupListeners() {
        // Botón para seleccionar una imagen
        binding.btnseleccionar.setOnClickListener {
            showImageSelectionDialog()
        }

        // Botón para guardar el contacto
        binding.btnguardar.setOnClickListener {
            val nombre = binding.txtnombre.text.toString().trim()
            val alias = binding.txtalias.text.toString().trim()

            if (nombre.isEmpty() || alias.isEmpty()) {
                Toast.makeText(this, "Nombre y alias son requeridos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedImageUri == null) {
                Toast.makeText(this, "Por favor, selecciona una imagen", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Deshabilitar el botón mientras se procesa
            binding.btnguardar.isEnabled = false
            binding.btnguardar.text = "Analizando y Guardando..."
            predictAndSave(nombre, alias)
        }
    }

    // Función que orquesta la predicción y el guardado
    private fun predictAndSave(nombre: String, alias: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val emocionResult = predecirEmocion(selectedImageUri!!)
            val emocion = emocionResult.split(" - ")[0] // Extrae solo la emoción, ej: "Feliz"

            if (emocion.contains("Error")) {
                Toast.makeText(this@MainActivity, emocionResult, Toast.LENGTH_LONG).show()
                resetSaveButton()
                return@launch
            }

            saveContactToFirebase(nombre, alias, emocion)
        }
    }

    // Guarda el nuevo contacto en Firebase Realtime Database
    @SuppressLint("HardwareIds")
    private fun saveContactToFirebase(nombre: String, alias: String, estado: String) {
        val key = myRef.push().key
        if (key == null) {
            Toast.makeText(this, "Error al generar clave para Firebase", Toast.LENGTH_SHORT).show()
            resetSaveButton()
            return
        }

        val codigo = Settings.Secure.getString(contentResolver, Settings.Secure.ANDROID_ID)
        val contacto = Contacto(nombre, alias, estado, 0, key, codigo, selectedImageDrawableId)

        myRef.child(key).setValue(contacto).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Toast.makeText(this, "Contacto guardado exitosamente", Toast.LENGTH_SHORT).show()
                // Limpiar la UI después de guardar
                binding.txtnombre.text.clear()
                binding.txtalias.text.clear()
                binding.imgFoto.setImageResource(android.R.color.transparent) // Restablecer imagen
                selectedImageUri = null
                selectedImageDrawableId = 0
            } else {
                Toast.makeText(this, "Error al guardar: ${task.exception?.message}", Toast.LENGTH_LONG).show()
            }
            resetSaveButton()
        }
    }

    // Escucha cambios en la base de datos para mantener la lista actualizada
    private fun listenForFirebaseChanges() {
        val valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                MyGlobal.miscontactos.clear()
                snapshot.children.forEach { dataSnapshot ->
                    val contacto = dataSnapshot.getValue(Contacto::class.java)
                    contacto?.let { MyGlobal.miscontactos.add(it) }
                }
                adapter?.notifyDataSetChanged()
                updateEmotionCount()
                Log.d("Firebase", "Datos cargados: ${MyGlobal.miscontactos.size} contactos.")
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w("Firebase", "loadPost:onCancelled", error.toException())
                Toast.makeText(baseContext, "Error al cargar datos de Firebase.", Toast.LENGTH_SHORT).show()
            }
        }
        myRef.addValueEventListener(valueEventListener)
    }

    // Actualiza el TextView con la cantidad de cada emoción
    @SuppressLint("SetTextI18n")
    private fun updateEmotionCount() {
        val counts = MyGlobal.miscontactos.groupingBy { it.estado.lowercase(Locale.ROOT) }.eachCount()
        val countText = counts.entries.joinToString(separator = ", ") { (emotion, count) ->
            "${emotion.replaceFirstChar { it.titlecase(Locale.ROOT) }}: $count"
        }
        binding.lblcantidad.text = countText.ifEmpty { "Aún no hay contactos guardados." }
    }

    // Llama a la API de Hugging Face para predecir la emoción
    private suspend fun predecirEmocion(uri: Uri): String {
        return withContext(Dispatchers.IO) {
            try {
                val API_URL = "https://alemesv-detect-emotions-alemesv.hf.space/predict"
                val inputStream = contentResolver.openInputStream(uri)
                val bytes = inputStream?.readBytes() ?: return@withContext "Error: No se pudo leer la imagen"
                inputStream.close()

                val requestBody = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("file", "image.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
                    .build()

                val client = OkHttpClient()
                val request = Request.Builder().url(API_URL).post(requestBody).build()
                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful && responseBody.contains("\"emotion\"")) {
                    val emotion = responseBody.substringAfter("\"emotion\":\"").substringBefore("\"")
                    val confidenceStr = responseBody.substringAfter("\"confidence\":").substringBefore("}")
                    val confidence = confidenceStr.trim().toDoubleOrNull()?.toInt() ?: 0

                    val emotionES = when(emotion.lowercase(Locale.ROOT)) {
                        "happy" -> "Feliz"
                        "sad" -> "Triste"
                        "angry" -> "Enojado"
                        "neutral" -> "Neutral"
                        "fear" -> "Miedo"
                        "surprise" -> "Sorpresa"
                        "disgust" -> "Disgusto"
                        else -> emotion.replaceFirstChar { it.titlecase(Locale.ROOT) }
                    }

                    "$emotionES - $confidence%"
                } else {
                    "Error al predecir: $responseBody"
                }
            } catch (e: Exception) {
                "Error de conexión: ${e.message}"
            }
        }
    }

    // Muestra un diálogo para seleccionar una de las imágenes de prueba
    private fun showImageSelectionDialog() {
        val imageNames = imageList.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Seleccionar Imagen de Prueba")
            .setItems(imageNames) { _, which ->
                val selectedImage = imageList[which]
                loadImageFromDrawable(selectedImage.second)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    // Carga la imagen seleccionada en el ImageView y guarda su URI
    private fun loadImageFromDrawable(drawableId: Int) {
        binding.imgFoto.setImageResource(drawableId)
        selectedImageUri = "android.resource://$packageName/$drawableId".toUri()
        selectedImageDrawableId = drawableId // Guardar el ID del drawable seleccionado
    }

    // Restablece el estado del botón de guardar
    private fun resetSaveButton() {
        binding.btnguardar.isEnabled = true
        binding.btnguardar.text = "Guardar"
    }
}