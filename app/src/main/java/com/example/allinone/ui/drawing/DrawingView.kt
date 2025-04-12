package com.example.allinone.ui.drawing

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class DrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var mBitmap: Bitmap? = null
    private var mCanvas: Canvas? = null
    private val mPath: Path = Path()
    private val mPaint: Paint = Paint()
    private var mX = 0f
    private var mY = 0f
    private val TOUCH_TOLERANCE = 4f

    init {
        mPaint.apply {
            isAntiAlias = true
            isDither = true
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
            strokeWidth = 12f
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        mCanvas = Canvas(mBitmap!!)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        mBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        canvas.drawPath(mPath, mPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                touchStart(x, y)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                touchMove(x, y)
                invalidate()
            }
            MotionEvent.ACTION_UP -> {
                touchUp()
                invalidate()
            }
        }
        return true
    }

    private fun touchStart(x: Float, y: Float) {
        mPath.reset()
        mPath.moveTo(x, y)
        mX = x
        mY = y
    }

    private fun touchMove(x: Float, y: Float) {
        val dx = Math.abs(x - mX)
        val dy = Math.abs(y - mY)
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            mPath.quadTo(mX, mY, (x + mX) / 2, (y + mY) / 2)
            mX = x
            mY = y
        }
    }

    private fun touchUp() {
        mPath.lineTo(mX, mY)
        // Draw the path to our bitmap
        mCanvas?.drawPath(mPath, mPaint)
        mPath.reset()
    }

    fun setColor(newColor: Int) {
        mPaint.color = newColor
    }

    fun setBrushSize(newSize: Float) {
        mPaint.strokeWidth = newSize
    }

    fun clear() {
        mBitmap?.eraseColor(Color.TRANSPARENT)
        invalidate()
    }

    fun getDrawingBitmap(): Bitmap? {
        // Create a bitmap of the entire view with background
        val returnedBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        
        // Draw the background (usually white)
        canvas.drawColor(Color.WHITE)
        
        // Draw any existing content from our internal bitmap
        mBitmap?.let { canvas.drawBitmap(it, 0f, 0f, null) }
        
        // Draw any current path that might not have been committed yet
        canvas.drawPath(mPath, mPaint)
        
        return returnedBitmap
    }
} 