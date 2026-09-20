# PixelGallery

PixelGallery is a local Android gallery designed for viewing pixel art clearly without smoothing.

## Features

- Reads local images through Android MediaStore
- Opens a selected folder through Android's Storage Access Framework
- Pixel-perfect nearest-neighbor rendering
- Original-resolution image loading
- Pinch zoom and drag in the full-screen viewer
- GIF animation playback
- Size filters: 32, 64, 128, 256, 512, 1024, and 2048 pixels
- Pinch gesture to change gallery grid density
- Android back button and edge-back gesture return to the gallery
- Dark Material 3 interface

## Requirements

- Android 8.0 or later
- Android Studio with JDK 17
- Android SDK 35

## Build

1. Clone the repository.
2. Open the project in Android Studio.
3. Let Gradle synchronize dependencies.
4. Run the `app` configuration, or execute:

```bash
./gradlew assembleDebug
```

On Windows:

```bat
gradlew.bat assembleDebug
```

The APK is generated under:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Privacy

PixelGallery reads images selected or authorized by the user. The application does not upload images to a server.

## Technology

- Kotlin
- Jetpack Compose
- Material 3
- Coil 3

## License

Licensed under the Apache License 2.0. See [LICENSE](LICENSE).
