package com.izzy2lost.psx2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import androidx.core.content.ContextCompat;

public class JoystickView extends View {
    public interface OnMoveListener {
        void onMove(float nx, float ny, int action);
    }

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint knobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float centerX, centerY, radius, knobX, knobY, knobRadius;
    private boolean isDragging = false;
    private OnMoveListener listener;

    public JoystickView(Context ctx) { super(ctx); init(); }
    public JoystickView(Context ctx, AttributeSet attrs) { super(ctx, attrs); init(); }
    public JoystickView(Context ctx, AttributeSet attrs, int defStyle) { super(ctx, attrs, defStyle); init(); }

    private void init() {
        basePaint.setColor(0x22000000); // subtle fill
        basePaint.setStyle(Paint.Style.FILL);
        // Match Settings/Controls outline (brand primary blue)
        int brandBlue = ContextCompat.getColor(getContext(), R.color.brand_primary);
        ringPaint.setColor(brandBlue);
        ringPaint.setStyle(Paint.Style.STROKE);
        ringPaint.setStrokeWidth(dp(2));
        // Knob uses the same brand blue
        knobPaint.setColor(brandBlue);
        knobPaint.setStyle(Paint.Style.FILL);
        setClickable(true);
    }

    public void setOnMoveListener(OnMoveListener l) { this.listener = l; }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        centerX = w / 2f;
        centerY = h / 2f;
        // Make the visible base circle smaller relative to the view size
        radius = Math.min(w, h) * 0.32f;
        knobRadius = radius * 0.30f;
        resetKnob();
    }

    private void resetKnob() {
        knobX = centerX;
        knobY = centerY;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // Base circle
        canvas.drawCircle(centerX, centerY, radius, basePaint);
        // Outer thin ring
        canvas.drawCircle(centerX, centerY, radius, ringPaint);
        // Knob
        canvas.drawCircle(knobX, knobY, knobRadius, knobPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        final int action = event.getActionMasked();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                isDragging = true;
                // fallthrough to move
            case MotionEvent.ACTION_MOVE:
                if (isDragging) {
                    float dx = event.getX() - centerX;
                    float dy = event.getY() - centerY;
                    // Clamp to circle
                    float dist = (float)Math.hypot(dx, dy);
                    if (dist > radius) {
                        float scale = radius / dist;
                        dx *= scale;
                        dy *= scale;
                    }
                    knobX = centerX + dx;
                    knobY = centerY + dy;
                    invalidate();
                    if (listener != null) {
                        // Normalize to [-1,1], invert Y so up is negative value (screen y grows down)
                        float nx = dx / radius;
                        float ny = dy / radius;
                        listener.onMove(clamp(nx), clamp(ny), MotionEvent.ACTION_MOVE);
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                isDragging = false;
                resetKnob();
                if (listener != null) listener.onMove(0f, 0f, MotionEvent.ACTION_UP);
                return true;
        }
        return super.onTouchEvent(event);
    }

    private static float clamp(float v) { return Math.max(-1f, Math.min(1f, v)); }

    private float dp(float d) {
        return d * getResources().getDisplayMetrics().density;
    }
}
