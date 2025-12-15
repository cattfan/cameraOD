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
    private static final float CONFIDENCE_THRESHOLD = 0.3f; // Đã hạ xuống để bắt được nhiều phân loại hơn

    // Khai báo các biến View
    private PreviewView previewView;
    private GraphicOverlay graphicOverlay;
    private TextView fpsTextView;
    private TextView objectCountTextView;

    // Biến quản lý Camera
    private ProcessCameraProvider cameraProvider;
    private ExecutorService cameraExecutor;

    // Bộ nhận diện vật thể ML Kit
    private ObjectDetector objectDetector;

    // Biến tính toán FPS
    private long lastFpsUpdateTime = 0;
    private int frameCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        // Xử lý giao diện Edge-to-Edge (Tràn viền)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Ánh xạ Views
        previewView = findViewById(R.id.previewView);
        graphicOverlay = findViewById(R.id.graphicOverlay);
        fpsTextView = findViewById(R.id.fpsTextView);
        objectCountTextView = findViewById(R.id.objectCountTextView);

        // Khởi tạo luồng xử lý Camera (Background Thread)
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Khởi tạo ML Kit Object Detector
        initializeObjectDetector();

        // Kiểm tra quyền Camera tại thời điểm chạy (Runtime Permission)
        if (hasCameraPermission()) {
            startCamera();
        } else {
            requestCameraPermission();
        }
    }

    /**
     * Khởi tạo ML Kit ObjectDetector.
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
     * Khởi động CameraX với chế độ Preview (Xem trước) và ImageAnalysis (Phân tích ảnh).
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
     * Gắn kết các Use Case (Preview, Analysis) vào vòng đời Camera.
     */
    private void bindCameraUseCases() {
        if (cameraProvider == null) {
            Log.e(TAG, "Camera provider is null");
            return;
        }

        // Lấy góc xoay màn hình hiện tại
        int rotation = previewView.getDisplay() != null ? 
                previewView.getDisplay().getRotation() : 0;

        // Hủy liên kết các use case cũ trước khi gắn mới
        cameraProvider.unbindAll();

        // Bộ chọn Camera - Sử dụng Camera sau
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                .build();

        // Use case 1: Preview (Hiển thị hình ảnh lên màn hình)
        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setTargetRotation(rotation)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Use case 2: ImageAnalysis (Lấy dữ liệu ảnh để chạy AI)
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setTargetRotation(rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // Chỉ lấy frame mới nhất
                .build();

        imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

        try {
            // Gắn kết tất cả vào vòng đời Activity
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
     * Phân tích từng frame ảnh (Callback).
     */
    @androidx.camera.core.ExperimentalGetImage
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        android.media.Image mediaImage = imageProxy.getImage();
        
        if (mediaImage == null) {
            imageProxy.close();
            return;
        }

        // Lấy kích thước và góc xoay của ảnh gốc
        int imageWidth = imageProxy.getWidth();
        int imageHeight = imageProxy.getHeight();
        int rotation = imageProxy.getImageInfo().getRotationDegrees();

        // Tạo đối tượng InputImage từ ImageProxy để ML Kit xử lý
        InputImage inputImage = InputImage.fromMediaImage(mediaImage, rotation);

        // Chạy nhận diện đối tượng
        objectDetector.process(inputImage)
                .addOnSuccessListener(detectedObjects -> {
                    // Smart Filter: Hiển thị nếu ĐÃ PHÂN LOẠI hoặc (CHƯA PHÂN LOẠI nhưng KÍCH THƯỚC LỚN)
                    List<DetectedObject> filteredObjects = new ArrayList<>();
                    for (DetectedObject object : detectedObjects) {
                        boolean isClassified = false;
                        // Kiểm tra xem có nhãn nào đạt ngưỡng tin cậy không
                        for (DetectedObject.Label label : object.getLabels()) {
                            if (label.getConfidence() >= CONFIDENCE_THRESHOLD) {
                                isClassified = true;
                                break;
                            }
                        }
                        
                        // Kiểm tra kích thước (phải chiếm > 20% chiều rộng ảnh nếu chưa được phân loại)
                        boolean isLargeEnough = object.getBoundingBox().width() > (imageWidth * 0.2f);

                        // Điều kiện: Đã phân loại HOẶC Là vật thể lớn
                        if (isClassified || (object.getLabels().isEmpty() && isLargeEnough)) {
                            filteredObjects.add(object);
                        }
                    }

                    // Log kiểm tra
                    Log.d(TAG, "Detection: " + filteredObjects.size() + " objects");

                    // Cập nhật giao diện trên Luồng chính (Main Thread)
                    final int count = filteredObjects.size();
                    runOnUiThread(() -> {
                        graphicOverlay.setDetectionResults(filteredObjects, imageWidth, imageHeight, rotation);
                        objectCountTextView.setText(count + " vật thể");
                        updateFps();
                    });
                })
                .addOnFailureListener(e -> Log.e(TAG, "Detection failed", e))
                .addOnCompleteListener(task -> imageProxy.close()); // Quan trọng: Đóng frame ảnh để nhận frame tiếp theo
    }

    /**
     * Cập nhật bộ đếm FPS.
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