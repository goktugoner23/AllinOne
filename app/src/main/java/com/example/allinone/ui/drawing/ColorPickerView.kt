package com.example.allinone.ui.drawing

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sqrt

class ColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val colorWheelRect = RectF()
    private var radius = 0f
    private var centerX = 0f
    private var centerY = 0f
    
    // Default center color is white
    private var centerColor = Color.WHITE
    
    private var onColorChangedListener: ((Int) -> Unit)? = null
    
    // Colors for the color wheel
    private val colors = intArrayOf(
        Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN,
        Color.GREEN, Color.YELLOW, Color.RED
    )

    init {
        centerPaint.color = centerColor
        centerPaint.style = Paint.Style.FILL
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        
        // Calculate the size of the color wheel
        val minDimension = minOf(w, h)
        radius = (minDimension / 2f) * 0.9f
        centerX = w / 2f
        centerY = h / 2f
        
        colorWheelRect.set(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius
        )
        
        // Create the sweep gradient for the color wheel
        val shader = SweepGradient(centerX, centerY, colors, null)
        paint.shader = shader
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = radius / 4
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Draw the color wheel
        canvas.drawCircle(centerX, centerY, radius - paint.strokeWidth / 2, paint)
        
        // Draw the center circle with the selected color
        canvas.drawCircle(centerX, centerY, radius / 2, centerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // Calculate the distance from center
                val dx = x - centerX
                val dy = y - centerY
                val distance = sqrt(dx.pow(2) + dy.pow(2))
                
                // Check if the touch is within the color wheel
                if (distance > radius / 2 && distance < radius) {
                    // Calculate the angle
                    val angle = (Math.toDegrees(atan2(dy, dx).toDouble()) + 360) % 360
                    
                    // Get the color at that angle
                    val hue = (angle / 360 * 360).toFloat()
                    val newColor = Color.HSVToColor(floatArrayOf(hue, 1f, 1f))
                    
                    // Update the center color
                    centerColor = newColor
                    centerPaint.color = centerColor
                    
                    // Notify the listener
                    onColorChangedListener?.invoke(centerColor)
                    
                    invalidate()
                    return true
                }
            }
        }
        
        return super.onTouchEvent(event)
    }
    
    fun setColor(color: Int) {
        centerColor = color
        centerPaint.color = color
        invalidate()
    }
    
    fun getColor(): Int {
        return centerColor
    }
    
    fun setOnColorChangedListener(listener: (Int) -> Unit) {
        onColorChangedListener = listener
    }
} 