package com.example.cameraod;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import com.google.mlkit.vision.objects.DetectedObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * High-performance GraphicOverlay with smooth animations.
 * Features:
 * - Smooth interpolation for bounding box positions
 * - Fade in/out animations
 * - Object tracking with smooth transitions
 * - Optimized drawing for better FPS
 */
public class GraphicOverlay extends View {

    // Animation constants
    private static final float LERP_FACTOR = 0.3f;  // Smooth interpolation speed
    private static final float FADE_SPEED = 0.15f;  // Alpha fade speed
    private static final long FADE_OUT_DELAY = 200; // ms before starting fade out
    
    // Modern color palette with gradients
    private static final int[][] GRADIENT_COLORS = {
        {0xFF00D9FF, 0xFF0066FF},  // Cyan to Blue
        {0xFFFF6B6B, 0xFFFF3366},  // Coral to Pink
        {0xFF4FFFB0, 0xFF00CC66},  // Mint to Green
        {0xFFFFD93D, 0xFFFF9500},  // Yellow to Orange
        {0xFFB388FF, 0xFF8E24AA},  // Lavender to Purple
        {0xFF64FFDA, 0xFF00BFA5},  // Teal Light to Teal
    };

    // Category to color mapping
    private static final Map<Integer, Integer> CATEGORY_COLOR_INDEX = new HashMap<>();
    static {
        CATEGORY_COLOR_INDEX.put(0, 0);
        CATEGORY_COLOR_INDEX.put(1, 1);
        CATEGORY_COLOR_INDEX.put(2, 2);
        CATEGORY_COLOR_INDEX.put(3, 3);
        CATEGORY_COLOR_INDEX.put(4, 4);
        CATEGORY_COLOR_INDEX.put(5, 5);
    }

    // Vietnamese translations
    private static final Map<String, String> VIETNAMESE_LABELS = new HashMap<>();
    static {
        VIETNAMESE_LABELS.put("Food", "Thực phẩm");
        VIETNAMESE_LABELS.put("Home good", "Đồ gia dụng");
        VIETNAMESE_LABELS.put("Home goods", "Đồ gia dụng");
        VIETNAMESE_LABELS.put("Fashion good", "Thời trang");
        VIETNAMESE_LABELS.put("Fashion goods", "Thời trang");
        VIETNAMESE_LABELS.put("Place", "Địa điểm");
        VIETNAMESE_LABELS.put("Plant", "Cây cối");
        VIETNAMESE_LABELS.put("Animal", "Động vật");
        VIETNAMESE_LABELS.put("Object", "Vật thể");
        VIETNAMESE_LABELS.put("Person", "Người");
        VIETNAMESE_LABELS.put("Car", "Xe hơi");
        VIETNAMESE_LABELS.put("Chair", "Ghế");
        VIETNAMESE_LABELS.put("Table", "Bàn");
        VIETNAMESE_LABELS.put("Phone", "Điện thoại");
        VIETNAMESE_LABELS.put("Laptop", "Máy tính");
        VIETNAMESE_LABELS.put("Book", "Sách");
        VIETNAMESE_LABELS.put("Bottle", "Chai");
        VIETNAMESE_LABELS.put("Cup", "Cốc");
        VIETNAMESE_LABELS.put("Keyboard", "Bàn phím");
        VIETNAMESE_LABELS.put("Mouse", "Chuột");
    }

    // Animated box class for smooth transitions
    private static class AnimatedBox {
        int trackingId;
        RectF currentRect = new RectF();
        RectF targetRect = new RectF();
        float alpha = 0f;
        float targetAlpha = 1f;
        String label;
        float confidence;
        int colorIndex;
        long lastUpdateTime;
        boolean isActive = true;

        AnimatedBox(int id) {
            this.trackingId = id;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        void updateTarget(RectF newTarget, String label, float confidence, int colorIndex) {
            this.targetRect.set(newTarget);
            this.label = label;
            this.confidence = confidence;
            this.colorIndex = colorIndex;
            this.targetAlpha = 1f;
            this.isActive = true;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        void animate() {
            // Smooth position interpolation
            currentRect.left = lerp(currentRect.left, targetRect.left, LERP_FACTOR);
            currentRect.top = lerp(currentRect.top, targetRect.top, LERP_FACTOR);
            currentRect.right = lerp(currentRect.right, targetRect.right, LERP_FACTOR);
            currentRect.bottom = lerp(currentRect.bottom, targetRect.bottom, LERP_FACTOR);

            // Smooth alpha interpolation
            alpha = lerp(alpha, targetAlpha, FADE_SPEED);
        }

        void startFadeOut() {
            targetAlpha = 0f;
            isActive = false;
        }

        boolean shouldRemove() {
            return !isActive && alpha < 0.01f;
        }

        private float lerp(float start, float end, float factor) {
            return start + (end - start) * factor;
        }
    }

    // Cached paints for performance
    private final Paint boxPaint;
    private final Paint glowPaint;
    private final Paint textPaint;
    private final Paint textBgPaint;
    private final Paint cornerPaint;
    
    // Reusable objects to reduce allocations
    private final RectF tempRect = new RectF();
    private final Rect textBounds = new Rect();
    private final Path cornerPath = new Path();

    // Animated boxes map (trackingId -> AnimatedBox)
    private final Map<Integer, AnimatedBox> animatedBoxes = new HashMap<>();
    private int nextTempId = -1; // For objects without tracking ID

    // Image dimensions
    private int imageWidth = 0;
    private int imageHeight = 0;
    private int imageRotation = 0;

    // Transformation values
    private float scaleX = 1f;
    private float scaleY = 1f;
    private float offsetX = 0f;
    private float offsetY = 0f;

    // Animation timing
    private long lastFrameTime = 0;
    private boolean needsAnimation = false;

    public GraphicOverlay(Context context) {
        this(context, null);
    }

    public GraphicOverlay(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GraphicOverlay(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        // Enable hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null);

        // Pre-create paints for performance
        boxPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        boxPaint.setStyle(Paint.Style.STROKE);
        boxPaint.setStrokeWidth(4f);
        boxPaint.setStrokeCap(Paint.Cap.ROUND);

        glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeWidth(12f);

        cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeWidth(6f);
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(36f);
        textPaint.setFakeBoldText(true);

        textBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textBgPaint.setStyle(Paint.Style.FILL);
    }

    public void setDetectionResults(List<DetectedObject> objects, int imgWidth, int imgHeight, int rotation) {
        // Update dimensions if changed
        if (imageWidth != imgWidth || imageHeight != imgHeight || imageRotation != rotation) {
            imageWidth = imgWidth;
            imageHeight = imgHeight;
            imageRotation = rotation;
            updateTransformationValues();
        }

        // Mark all existing boxes as inactive
        for (AnimatedBox box : animatedBoxes.values()) {
            box.isActive = false;
        }

        // Update or create animated boxes
        if (objects != null) {
            for (int i = 0; i < objects.size(); i++) {
                DetectedObject obj = objects.get(i);
                Integer trackingId = obj.getTrackingId();
                
                // Use temp ID if no tracking ID
                int id = trackingId != null ? trackingId : (nextTempId--);

                // Get or create animated box
                AnimatedBox animBox = animatedBoxes.get(id);
                if (animBox == null) {
                    animBox = new AnimatedBox(id);
                    // Initialize position at target for new boxes
                    transformRect(obj.getBoundingBox(), animBox.currentRect);
                    animatedBoxes.put(id, animBox);
                }

                // Transform and update target
                transformRect(obj.getBoundingBox(), tempRect);
                
                // Get label and color
                String label = "Vật thể";
                float confidence = 0f;
                int colorIndex = i % GRADIENT_COLORS.length;

                if (!obj.getLabels().isEmpty()) {
                    DetectedObject.Label topLabel = obj.getLabels().get(0);
                    String englishLabel = topLabel.getText();
                    label = VIETNAMESE_LABELS.getOrDefault(englishLabel, englishLabel);
                    confidence = topLabel.getConfidence();
                    colorIndex = CATEGORY_COLOR_INDEX.getOrDefault(topLabel.getIndex(), 0);
                }

                animBox.updateTarget(tempRect, label, confidence, colorIndex);
            }
        }

        // Start fade out for inactive boxes
        long now = System.currentTimeMillis();
        for (AnimatedBox box : animatedBoxes.values()) {
            if (!box.isActive && box.targetAlpha > 0 && 
                (now - box.lastUpdateTime) > FADE_OUT_DELAY) {
                box.startFadeOut();
            }
        }

        needsAnimation = true;
        postInvalidate();
    }

    public void clear() {
        for (AnimatedBox box : animatedBoxes.values()) {
            box.startFadeOut();
        }
        postInvalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateTransformationValues();
    }

    private void updateTransformationValues() {
        if (imageWidth == 0 || imageHeight == 0 || getWidth() == 0 || getHeight() == 0) {
            return;
        }

        boolean isRotated = imageRotation == 90 || imageRotation == 270;
        int mlKitWidth = isRotated ? imageHeight : imageWidth;
        int mlKitHeight = isRotated ? imageWidth : imageHeight;

        float viewAspect = (float) getWidth() / getHeight();
        float imageAspect = (float) mlKitWidth / mlKitHeight;

        if (viewAspect > imageAspect) {
            scaleX = (float) getWidth() / mlKitWidth;
            scaleY = scaleX;
        } else {
            scaleY = (float) getHeight() / mlKitHeight;
            scaleX = scaleY;
        }

        offsetX = (getWidth() - mlKitWidth * scaleX) / 2f;
        offsetY = (getHeight() - mlKitHeight * scaleY) / 2f;
    }

    private void transformRect(Rect imageRect, RectF viewRect) {
        viewRect.left = imageRect.left * scaleX + offsetX;
        viewRect.top = imageRect.top * scaleY + offsetY;
        viewRect.right = imageRect.right * scaleX + offsetX;
        viewRect.bottom = imageRect.bottom * scaleY + offsetY;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (animatedBoxes.isEmpty()) {
            return;
        }

        // Animate all boxes
        boolean stillAnimating = false;
        Iterator<Map.Entry<Integer, AnimatedBox>> iterator = animatedBoxes.entrySet().iterator();
        
        while (iterator.hasNext()) {
            AnimatedBox box = iterator.next().getValue();
            box.animate();

            // Remove fully faded boxes
            if (box.shouldRemove()) {
                iterator.remove();
                continue;
            }

            // Check if still animating
            if (Math.abs(box.alpha - box.targetAlpha) > 0.01f) {
                stillAnimating = true;
            }

            // Draw if visible
            if (box.alpha > 0.01f && box.currentRect.width() > 10 && box.currentRect.height() > 10) {
                drawAnimatedBox(canvas, box);
            }
        }

        // Continue animation if needed
        if (stillAnimating || needsAnimation) {
            needsAnimation = false;
            postInvalidateOnAnimation();
        }
    }

    private void drawAnimatedBox(Canvas canvas, AnimatedBox box) {
        int[] colors = GRADIENT_COLORS[box.colorIndex];
        int primaryColor = applyAlpha(colors[0], box.alpha);
        int secondaryColor = applyAlpha(colors[1], box.alpha);

        RectF rect = box.currentRect;

        // Draw glow effect
        glowPaint.setColor(primaryColor);
        glowPaint.setAlpha((int)(30 * box.alpha));
        canvas.drawRoundRect(rect, 16f, 16f, glowPaint);

        // Draw main box with gradient
        LinearGradient gradient = new LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            primaryColor, secondaryColor, Shader.TileMode.CLAMP
        );
        boxPaint.setShader(gradient);
        boxPaint.setAlpha((int)(255 * box.alpha));
        canvas.drawRoundRect(rect, 12f, 12f, boxPaint);
        boxPaint.setShader(null);

        // Draw corner accents
        drawCornerAccents(canvas, rect, primaryColor, box.alpha);

        // Draw label
        drawLabel(canvas, box.label, box.confidence, rect, primaryColor, secondaryColor, box.alpha);
    }

    private int applyAlpha(int color, float alpha) {
        int a = (int)(Color.alpha(color) * alpha);
        return Color.argb(a, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void drawCornerAccents(Canvas canvas, RectF rect, int color, float alpha) {
        float cornerSize = 24f;
        cornerPaint.setColor(color);
        cornerPaint.setAlpha((int)(255 * alpha));

        // Top-left
        cornerPath.reset();
        cornerPath.moveTo(rect.left, rect.top + cornerSize);
        cornerPath.lineTo(rect.left, rect.top);
        cornerPath.lineTo(rect.left + cornerSize, rect.top);
        canvas.drawPath(cornerPath, cornerPaint);

        // Top-right
        cornerPath.reset();
        cornerPath.moveTo(rect.right - cornerSize, rect.top);
        cornerPath.lineTo(rect.right, rect.top);
        cornerPath.lineTo(rect.right, rect.top + cornerSize);
        canvas.drawPath(cornerPath, cornerPaint);

        // Bottom-left
        cornerPath.reset();
        cornerPath.moveTo(rect.left, rect.bottom - cornerSize);
        cornerPath.lineTo(rect.left, rect.bottom);
        cornerPath.lineTo(rect.left + cornerSize, rect.bottom);
        canvas.drawPath(cornerPath, cornerPaint);

        // Bottom-right
        cornerPath.reset();
        cornerPath.moveTo(rect.right - cornerSize, rect.bottom);
        cornerPath.lineTo(rect.right, rect.bottom);
        cornerPath.lineTo(rect.right, rect.bottom - cornerSize);
        canvas.drawPath(cornerPath, cornerPaint);
    }

    private void drawLabel(Canvas canvas, String label, float confidence, RectF rect,
                           int primaryColor, int secondaryColor, float alpha) {
        if (alpha < 0.1f) return;

        String displayLabel = label;
        if (confidence > 0) {
            displayLabel += String.format(" • %.0f%%", confidence * 100);
        }

        textPaint.getTextBounds(displayLabel, 0, displayLabel.length(), textBounds);
        float textWidth = textBounds.width();
        float textHeight = textBounds.height();
        float padding = 12f;

        float bgLeft = rect.left;
        float bgTop = rect.top - textHeight - padding * 2 - 8;
        float bgRight = rect.left + textWidth + padding * 2;
        float bgBottom = rect.top - 8;

        if (bgTop < 0) {
            bgTop = rect.bottom + 8;
            bgBottom = rect.bottom + textHeight + padding * 2 + 8;
        }

        // Draw background
        LinearGradient bgGradient = new LinearGradient(
            bgLeft, bgTop, bgRight, bgBottom,
            primaryColor, secondaryColor, Shader.TileMode.CLAMP
        );
        textBgPaint.setShader(bgGradient);
        textBgPaint.setAlpha((int)(255 * alpha));
        
        tempRect.set(bgLeft, bgTop, bgRight, bgBottom);
        canvas.drawRoundRect(tempRect, 8f, 8f, textBgPaint);
        textBgPaint.setShader(null);

        // Draw text
        textPaint.setAlpha((int)(255 * alpha));
        canvas.drawText(displayLabel, bgLeft + padding, bgBottom - padding - 2, textPaint);
    }
}
