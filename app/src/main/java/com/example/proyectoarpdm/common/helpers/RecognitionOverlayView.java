package com.example.proyectoarpdm.common.helpers;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;

import androidx.annotation.Nullable;

public class RecognitionOverlayView extends View {
    private Paint paint;
    private RectF box;
    private boolean isRecognized = false;
    private float pulseScale = 1.0f;
    private ValueAnimator animator;

    public RecognitionOverlayView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(8f);
        box = new RectF();
        
        setupAnimation();
    }

    private void setupAnimation() {
        animator = ValueAnimator.ofFloat(0.95f, 1.05f);
        animator.setDuration(1000);
        animator.setRepeatCount(ValueAnimator.INFINITE);
        animator.setRepeatMode(ValueAnimator.REVERSE);
        animator.setInterpolator(new AccelerateDecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            pulseScale = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float width = getWidth();
        float height = getHeight();
        float boxSize = Math.min(width, height) * 0.6f * pulseScale;

        float left = (width - boxSize) / 2;
        float top = (height - boxSize) / 2;
        float right = left + boxSize;
        float bottom = top + boxSize;

        box.set(left, top, right, bottom);

        if (isRecognized) {
            paint.setColor(Color.GREEN);
            paint.setStrokeWidth(12f);
        } else {
            paint.setColor(Color.WHITE);
            paint.setStrokeWidth(8f);
        }

        canvas.drawRect(box, paint);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) {
            animator.cancel();
        }
    }

    public void setRecognized(boolean recognized) {
        this.isRecognized = recognized;
        invalidate();
    }

    public RectF getTargetBox() {
        return box;
    }
}
