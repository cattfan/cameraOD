# 📷 CameraOD - Nhận diện vật thể

Ứng dụng nhận diện vật thể real-time sử dụng CameraX và Google ML Kit Object Detection.

## ✨ Tính năng

- 🎯 **Nhận diện vật thể real-time** - Phát hiện 5 loại: Thực phẩm, Đồ gia dụng, Thời trang, Địa điểm, Cây cối
- 🎨 **Giao diện đẹp** - Gradient borders, glow effects, corner accents
- 🎬 **Animation mượt mà** - Smooth lerp interpolation, fade in/out
- 🇻🇳 **Giao diện tiếng Việt** - Tất cả labels và UI đều bằng tiếng Việt
- ⚡ **Tối ưu hiệu suất** - Hardware acceleration, object pooling
- 📊 **Hiển thị FPS** - Theo dõi performance real-time
- 🔢 **Đếm vật thể** - Hiển thị số lượng vật thể phát hiện được

## 📱 Screenshots

| Camera Preview | Detection |
|----------------|-----------|
| Khung gradient với góc nhấn mạnh | Label tiếng Việt với % confidence |

## 🛠️ Công nghệ

- **CameraX** 1.4.2 - Camera API hiện đại
- **ML Kit Object Detection** 17.0.1 - AI nhận diện vật thể
- **Material Design 3** - UI components
- **Java 11** - Ngôn ngữ lập trình

## 📦 Cài đặt

1. Clone repository
2. Mở bằng Android Studio
3. Sync Gradle
4. Build và chạy trên thiết bị thật

> ⚠️ **Lưu ý:** Camera không hoạt động trên emulator

## 📋 Yêu cầu

- Android 7.0+ (API 24)
- Camera permission
- Thiết bị thật (không phải emulator)

## 📁 Cấu trúc

```
app/src/main/
├── java/com/example/cameraod/
│   ├── MainActivity.java      # Camera + ML Kit
│   └── GraphicOverlay.java    # Animation + Drawing
└── res/
    ├── layout/activity_main.xml
    ├── drawable/              # Gradients, badges
    └── values/                # Colors, themes, strings
```

## 🎨 Màu sắc

| Loại | Màu |
|------|-----|
| Thực phẩm | 🟡 Vàng → Cam |
| Đồ gia dụng | 🔴 Coral → Hồng |
| Thời trang | 🟢 Mint → Xanh lá |
| Địa điểm | 🟣 Tím → Purple |
| Cây cối | 🔵 Teal → Xanh dương |

## � Cấu hình

Có thể điều chỉnh trong code:

```java
// MainActivity.java
CONFIDENCE_THRESHOLD = 0.4f  // Ngưỡng tin cậy (0.0 - 1.0)

// GraphicOverlay.java  
LERP_FACTOR = 0.3f   // Tốc độ animation (0.1 = chậm, 0.5 = nhanh)
FADE_SPEED = 0.15f   // Tốc độ fade in/out
```

## � License

MIT License

---

Made with ❤️ using Android Studio
