package com.example.allinone.ui.drawing

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.SeekBar
import com.example.allinone.R
import org.maroxa.diario.custom.ColorPickerView

class ColorPickerDialog(
    context: Context,
    private val initialColor: Int = Color.BLACK,
    private val onColorSelected: (Int) -> Unit
) : Dialog(context) {

    private lateinit var colorPickerView: ColorPickerView
    private lateinit var brightnessSlider: SeekBar
    private var selectedColor: Int = initialColor

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null)
        setContentView(view)
        
        // Set up the ColorPickerView
        colorPickerView = view.findViewById(R.id.color_picker_view)
        colorPickerView.setColor(initialColor)
        colorPickerView.setOnColorChangedListener { 
            selectedColor = it
        }
        
        // Set up the brightness slider
        brightnessSlider = view.findViewById(R.id.brightness_slider)
        brightnessSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val brightness = progress / 100f
                    colorPickerView.setBrightness(brightness)
                    // The color will be updated via the OnColorChangedListener
                }
            }
            
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // Set up button clicks
        view.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            dismiss()
        }
        
        view.findViewById<Button>(R.id.btn_ok).setOnClickListener {
            onColorSelected(selectedColor)
            dismiss()
        }
        
        // Make dialog width match parent
        window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    
    companion object {
        /**
         * Shows a color picker dialog and returns the selected color to the callback
         */
        fun show(context: Context, initialColor: Int = Color.BLACK, onColorSelected: (Int) -> Unit) {
            ColorPickerDialog(context, initialColor, onColorSelected).show()
        }
    }
} 