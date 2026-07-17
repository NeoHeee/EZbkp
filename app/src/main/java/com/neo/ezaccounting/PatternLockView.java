package com.neo.ezaccounting;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class PatternLockView extends View {
    public interface Listener { void onPatternComplete(String pattern); }

    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<Integer> selected = new ArrayList<>();
    private Listener listener;
    private float cellSize;
    private float radius;
    private float currentX;
    private float currentY;
    private boolean drawing;

    public PatternLockView(Context context) { super(context); init(); }
    public PatternLockView(Context context, AttributeSet attrs) { super(context, attrs); init(); }

    private void init() {
        setBackgroundColor(Color.TRANSPARENT);
        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(dp(2));
        circlePaint.setColor(Color.rgb(155, 170, 168));
        selectedPaint.setStyle(Paint.Style.FILL);
        selectedPaint.setColor(Color.rgb(13, 148, 136));
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dp(5));
        linePaint.setStrokeCap(Paint.Cap.ROUND);
        linePaint.setStrokeJoin(Paint.Join.ROUND);
        linePaint.setColor(Color.rgb(13, 148, 136));
        linePaint.setAlpha(180);
    }

    public void setListener(Listener listener) { this.listener = listener; }

    public void clearPattern() {
        selected.clear();
        drawing = false;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        int size = Math.min(width, height > 0 ? height : width);
        if (size <= 0) size = dp(280);
        setMeasuredDimension(size, size);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        cellSize = Math.min(w, h) / 3f;
        radius = Math.max(dp(14), cellSize * 0.13f);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!selected.isEmpty()) {
            Path path = new Path();
            for (int i = 0; i < selected.size(); i++) {
                float x = nodeX(selected.get(i));
                float y = nodeY(selected.get(i));
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            if (drawing) path.lineTo(currentX, currentY);
            canvas.drawPath(path, linePaint);
        }

        Paint inner = new Paint(Paint.ANTI_ALIAS_FLAG);
        inner.setColor(Color.WHITE);
        for (int i = 0; i < 9; i++) {
            float x = nodeX(i);
            float y = nodeY(i);
            if (selected.contains(i)) {
                canvas.drawCircle(x, y, radius, selectedPaint);
                canvas.drawCircle(x, y, radius * 0.33f, inner);
            } else {
                canvas.drawCircle(x, y, radius, circlePaint);
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        currentX = event.getX();
        currentY = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                selected.clear(); drawing = true; addTouchedNode(currentX, currentY); invalidate(); return true;
            case MotionEvent.ACTION_MOVE:
                addTouchedNode(currentX, currentY); invalidate(); return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                drawing = false; addTouchedNode(currentX, currentY); invalidate();
                if (!selected.isEmpty() && listener != null) listener.onPatternComplete(toPattern());
                return true;
            default:
                return true;
        }
    }

    private void addTouchedNode(float x, float y) {
        for (int i = 0; i < 9; i++) {
            float dx = x - nodeX(i);
            float dy = y - nodeY(i);
            float hitRadius = radius * 1.9f;
            if ((dx * dx + dy * dy) <= hitRadius * hitRadius && !selected.contains(i)) {
                addIntermediateNodeIfNeeded(i);
                selected.add(i);
                performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);
                return;
            }
        }
    }

    private void addIntermediateNodeIfNeeded(int next) {
        if (selected.isEmpty()) return;
        int previous = selected.get(selected.size() - 1);
        int rowSum = previous / 3 + next / 3;
        int colSum = previous % 3 + next % 3;
        if (rowSum % 2 == 0 && colSum % 2 == 0) {
            int middle = (rowSum / 2) * 3 + colSum / 2;
            if (middle != previous && middle != next && !selected.contains(middle)) selected.add(middle);
        }
    }

    private String toPattern() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < selected.size(); i++) {
            if (i > 0) builder.append('-');
            builder.append(selected.get(i));
        }
        return builder.toString();
    }

    private float nodeX(int index) { return (index % 3 + 0.5f) * cellSize; }
    private float nodeY(int index) { return (index / 3 + 0.5f) * cellSize; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
