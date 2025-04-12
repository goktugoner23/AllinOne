package org.maroxa.diario.custom;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.SweepGradient;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

public class ColorPickerView extends View {
    private static final int[] COLORS = new int[]{
            Color.RED, Color.MAGENTA, Color.BLUE, Color.CYAN,
            Color.GREEN, Color.YELLOW, Color.RED
    };

    private Paint mPaint;
    private Paint mCenterPaint;
    private final int[] mColors;
    private boolean mTrackingCenter;
    private boolean mHighlightCenter;

    private int mCenterX;
    private int mCenterY;
    private int mCenterRadius = 32;
    private int mColorWheelRadius;
    private float brightness = 1.0f;

    private OnColorChangedListener mListener;

    public interface OnColorChangedListener {
        void colorChanged(int color);
    }

    public ColorPickerView(Context context) {
        this(context, null);
    }

    public ColorPickerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ColorPickerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mColors = COLORS;
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCenterPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mCenterPaint.setColor(Color.RED);
        mCenterPaint.setStrokeWidth(5);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        mPaint.setShader(new SweepGradient(mCenterX, mCenterY, mColors, null));

        canvas.drawCircle(mCenterX, mCenterY, mColorWheelRadius, mPaint);

        // Draw the center circle
        mCenterPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(mCenterX, mCenterY, mCenterRadius, mCenterPaint);

        // Draw circle outline/highlight if center is being pressed
        if (mTrackingCenter) {
            int c = mCenterPaint.getColor();
            mCenterPaint.setStyle(Paint.Style.STROKE);
            if (mHighlightCenter) {
                mCenterPaint.setAlpha(0xFF);
            } else {
                mCenterPaint.setAlpha(0x80);
            }
            canvas.drawCircle(mCenterX, mCenterY, mCenterRadius + 5, mCenterPaint);
            mCenterPaint.setStyle(Paint.Style.FILL);
            mCenterPaint.setColor(c);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mCenterX = w / 2;
        mCenterY = h / 2;
        mColorWheelRadius = Math.min(mCenterX, mCenterY) - 10;
    }

    private int ave(int s, int d, float p) {
        return s + Math.round(p * (d - s));
    }

    private int interpColor(int[] colors, float unit) {
        if (unit <= 0) {
            return colors[0];
        }
        if (unit >= 1) {
            return colors[colors.length - 1];
        }

        float p = unit * (colors.length - 1);
        int i = (int) p;
        p -= i;

        int c0 = colors[i];
        int c1 = colors[i + 1];
        int a = ave(Color.alpha(c0), Color.alpha(c1), p);
        int r = ave(Color.red(c0), Color.red(c1), p);
        int g = ave(Color.green(c0), Color.green(c1), p);
        int b = ave(Color.blue(c0), Color.blue(c1), p);

        return Color.argb(a, r, g, b);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX() - mCenterX;
        float y = event.getY() - mCenterY;
        boolean inCenter = Math.sqrt(x * x + y * y) <= mCenterRadius;

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mTrackingCenter = inCenter;
                if (inCenter) {
                    mHighlightCenter = true;
                    invalidate();
                    break;
                }
            case MotionEvent.ACTION_MOVE:
                if (mTrackingCenter) {
                    if (mHighlightCenter != inCenter) {
                        mHighlightCenter = inCenter;
                        invalidate();
                    }
                } else {
                    float angle = (float) Math.atan2(y, x);
                    // Need to turn angle [-PI ... PI] into unit [0....1]
                    float unit = angle / (2 * (float) Math.PI);
                    if (unit < 0) {
                        unit += 1;
                    }
                    int color = interpColor(mColors, unit);
                    // Apply brightness
                    float[] hsv = new float[3];
                    Color.colorToHSV(color, hsv);
                    hsv[2] = brightness;
                    color = Color.HSVToColor(hsv);
                    
                    mCenterPaint.setColor(color);
                    if (mListener != null) {
                        mListener.colorChanged(color);
                    }
                    invalidate();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (mTrackingCenter) {
                    if (inCenter) {
                        if (mListener != null) {
                            mListener.colorChanged(mCenterPaint.getColor());
                        }
                    }
                    mTrackingCenter = false;
                    invalidate();
                }
                break;
        }
        return true;
    }

    public void setOnColorChangedListener(OnColorChangedListener listener) {
        mListener = listener;
    }

    public void setColor(int color) {
        mCenterPaint.setColor(color);
        invalidate();
    }
    
    public int getColor() {
        return mCenterPaint.getColor();
    }
    
    public void setBrightness(float brightness) {
        this.brightness = brightness;
        int color = mCenterPaint.getColor();
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = brightness;
        mCenterPaint.setColor(Color.HSVToColor(hsv));
        invalidate();
        
        if (mListener != null) {
            mListener.colorChanged(mCenterPaint.getColor());
        }
    }
} 