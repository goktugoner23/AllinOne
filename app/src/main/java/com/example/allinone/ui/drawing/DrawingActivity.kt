package com.example.allinone.ui.drawing

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.allinone.R
import com.example.allinone.databinding.ActivityDrawingBinding
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DrawingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDrawingBinding
    private var saveToGallery = false
    private var currentColor = Color.BLACK

    companion object {
        const val EXTRA_SAVE_TO_GALLERY = "save_to_gallery"
        const val RESULT_DRAWING_URI = "drawing_uri"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDrawingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if we should save to gallery
        saveToGallery = intent.getBooleanExtra(EXTRA_SAVE_TO_GALLERY, false)

        // Set up the color picker button
        setupColorPicker()

        // Set up brush size seekbar
        setupBrushSizeSeekBar()

        // Set up other buttons
        binding.clearButton.setOnClickListener { binding.drawingView.clear() }
        binding.saveButton.setOnClickListener { saveDrawing() }
    }

    private fun setupColorPicker() {
        // Set the initial color
        binding.currentColorView.setBackgroundColor(currentColor)
        
        // Set up the color picker button
        binding.colorPickerButton.setOnClickListener {
            ColorPickerDialog.show(this, currentColor) { color ->
                // Update the current color
                currentColor = color
                binding.currentColorView.setBackgroundColor(color)
                binding.drawingView.setColor(color)
            }
        }
    }

    private fun setupBrushSizeSeekBar() {
        binding.brushSizeSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.drawingView.setBrushSize(progress.toFloat())
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun saveDrawing() {
        val bitmap = binding.drawingView.getDrawingBitmap() ?: return

        try {
            // Create a file to save the drawing
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "Drawing_$timeStamp.png"
            val imageUri: Uri

            if (saveToGallery) {
                // Save to gallery
                imageUri = saveImageToGallery(bitmap, fileName)
                Toast.makeText(this, getString(R.string.drawing_saved_to_gallery), Toast.LENGTH_SHORT).show()
            } else {
                // Save to internal storage
                imageUri = saveImageToInternal(bitmap, fileName)
            }

            // Return the URI to the calling activity
            val resultIntent = Intent()
            resultIntent.putExtra(RESULT_DRAWING_URI, imageUri.toString())
            // Add flag to grant permission to the URI
            resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            resultIntent.data = imageUri
            setResult(RESULT_OK, resultIntent)
            finish()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, getString(R.string.error_saving_drawing, e.message), Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveImageToInternal(bitmap: Bitmap, fileName: String): Uri {
        val file = File(filesDir, fileName)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        
        // Ensure the file was created successfully
        if (!file.exists()) {
            throw IOException("Failed to create file in internal storage")
        }
        
        // Get URI using FileProvider
        val uri = FileProvider.getUriForFile(
            this,
            "${applicationContext.packageName}.provider",
            file
        )
        
        // Grant read permission to the URI
        grantUriPermission(
            packageName,
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        
        return uri
    }

    private fun saveImageToGallery(bitmap: Bitmap, fileName: String): Uri {
        val uri: Uri

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }

            val contentResolver = contentResolver
            uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
            val outputStream: OutputStream = contentResolver.openOutputStream(uri)!!
            
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.close()
        } else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, fileName)
            val outputStream = FileOutputStream(image)
            
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            outputStream.close()
            
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DATA, image.absolutePath)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            }
            
            uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
        }
        
        return uri
    }
} 