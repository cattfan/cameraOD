package com.example.cameraod;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.DetectedObject;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "CameraOD";
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final float CONFIDENCE_THRESHOLD = 0.4f;

    // Views
    private PreviewView previewView;
    private GraphicOverlay graphicOverlay;
    private TextView fpsTextView;
    private TextView objectCountTextView;

    // Camera
    private ProcessCameraProvider cameraProvider;
    private ExecutorService cameraExecutor;

    // ML Kit Object Detector
    private ObjectDetector objectDetector;

    // FPS calculation
    private long lastFpsUpdateTime = 0;
    private int frameCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Handle system insets
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize views
        previewView = findViewById(R.id.previewView);
        graphicOverlay = findViewById(R.id.graphicOverlay);
        fpsTextView = findViewById(R.id.fpsTextView);
        objectCountTextView = findViewById(R.id.objectCountTextView);

        // Initialize camera executor
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Initialize ML Kit Object Detector
        initializeObjectDetector();

        // Check camera permission
        if (hasCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
    }

    /**
     * Initialize ML Kit ObjectDetector.
     */
    private void initializeObjectDetector() {
        ObjectDetectorOptions options = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableClassification()
                .enableMultipleObjects()
                .build();

        objectDetector = ObjectDetection.getClient(options);
        Log.d(TAG, "ML Kit ObjectDetector initialized");
    }

    /**
     * Check if camera permission is granted.
     */
    private boolean hasCameraPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Request camera permission from user.
     */
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST_CODE
        );
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    /**
     * Start CameraX with Preview and ImageAnalysis use cases.
     */
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture =
                ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "Failed to get camera provider", e);
                Toast.makeText(this, "Failed to initialize camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    /**
     * Bind Preview and ImageAnalysis use cases to camera lifecycle.
     */
    private void bindCameraUseCases() {
        if (cameraProvider == null) {
            Log.e(TAG, "Camera provider is null");
            return;
        }

        // Get screen rotation
        int rotation = previewView.getDisplay() != null ? 
                previewView.getDisplay().getRotation() : 0;

        // Unbind any existing use cases
        cameraProvider.unbindAll();

        // Camera selector - use back camera
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        // Preview use case
        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setTargetRotation(rotation)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Image analysis use case
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        try {
            // Bind use cases to camera
            cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalysis
            );
            Log.d(TAG, "Camera use cases bound successfully");
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind camera use cases", e);
            Toast.makeText(this, "Failed to start camera preview", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Analyze each camera frame for object detection.
     */
    @androidx.camera.core.ExperimentalGetImage
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        android.media.Image mediaImage = imageProxy.getImage();
        
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        // Get image dimensions and rotation
        int imageWidth = imageProxy.getWidth();
        int imageHeight = imageProxy.getHeight();
        int rotation = imageProxy.getImageInfo().getRotationDegrees();

        // Create InputImage from ImageProxy
        InputImage inputImage = InputImage.fromMediaImage(mediaImage, rotation);

        // Run object detection
        objectDetector.process(inputImage)
                .addOnSuccessListener(detectedObjects -> {
                    // Filter by confidence threshold
                    List<DetectedObject> filteredObjects = new ArrayList<>();
                    for (DetectedObject object : detectedObjects) {
                        boolean passesThreshold = false;
                        for (DetectedObject.Label label : object.getLabels()) {
                            if (label.getConfidence() >= CONFIDENCE_THRESHOLD) {
                                passesThreshold = true;
                                break;
                            }
                        }
                        if (passesThreshold || object.getLabels().isEmpty()) {
                            filteredObjects.add(object);
                        }
                    }

                    // Debug log
                    Log.d(TAG, "Detection: " + filteredObjects.size() + " objects");

                    // Update overlay on main thread
                    final int count = filteredObjects.size();
                    runOnUiThread(() -> {
                        graphicOverlay.setDetectionResults(filteredObjects, imageWidth, imageHeight, rotation);
                        objectCountTextView.setText(count + " vật thể");
                        updateFps();
                    });
                })
                .addOnFailureListener(e -> Log.e(TAG, "Detection failed", e))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    /**
     * Update FPS counter display.
     */
    private void updateFps() {
        frameCount++;
        long currentTime = System.currentTimeMillis();
        long elapsedTime = currentTime - lastFpsUpdateTime;

        if (elapsedTime >= 1000) {
            float fps = frameCount * 1000f / elapsedTime;
            fpsTextView.setText(String.format("FPS: %.1f", fps));
            frameCount = 0;
            lastFpsUpdateTime = currentTime;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }

        if (objectDetector != null) {
            objectDetector.close();
        }
    }
}