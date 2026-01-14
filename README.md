# Contactless Fingerprint Challenge

Android application for contactless fingerprint capture, quality assessment, enhancement, and matching.

## Project Overview

This project implements multiple tracks for the SITAA contactless fingerprint challenge:

- **Track A**: Contactless Finger Capture & Quality Assessment (CV-heavy)
- **Track B**: Finger Image Enhancement & Template Readiness (AI-heavy)
- **Track C**: Contactless-to-Contact Matching Demo (Advanced)
- **Track D**: Liveness / Spoof Heuristic (Optional)

## Technology Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Camera**: CameraX
- **Image Processing**: OpenCV (to be integrated)
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)

## Project Structure

```
app/
├── src/main/
│   ├── java/com/contactless/fingerprint/
│   │   ├── camera/          # Track A: Camera capture & finger detection
│   │   ├── quality/          # Track A: Quality assessment
│   │   ├── enhancement/     # Track B: Image enhancement
│   │   ├── matching/         # Track C: Feature extraction & matching
│   │   ├── liveness/         # Track D: Liveness detection
│   │   ├── core/             # Core utilities
│   │   ├── utils/            # Helper utilities
│   │   └── ui/               # UI components
│   └── res/                  # Resources (strings, colors, themes)
└── build.gradle.kts          # App-level dependencies
```

## Features

### Track A - Capture & Quality Assessment
- Live camera preview
- Finger detection/segmentation
- Quality indicators:
  - Blur/focus score
  - Illumination check
  - Finger coverage/orientation
- Quality score display

### Track B - Image Enhancement
- Finger region isolation
- Ridge-valley enhancement
- Noise reduction
- Contrast normalization
- ISO-like format export (optional)

### Track C - Matching
- Feature extraction (minutiae or surrogate)
- Similarity computation
- Match/no-match decision

### Track D - Liveness Detection
- Motion-based detection
- Texture-based spoof check
- Multi-frame consistency

## Setup Instructions

1. Install Android Studio (see `ANDROID_STUDIO_SETUP.md`)
2. Open the project in Android Studio
3. Sync Gradle dependencies
4. Connect an Android device or start an emulator
5. Run the app

## Dependencies

### Open Source Libraries Used
- **AndroidX CameraX**: Camera operations
- **Jetpack Compose**: Modern UI framework
- **OpenCV**: Computer vision and image processing (to be integrated)

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug

# Run tests
./gradlew test
```

## Technical Notes

(To be added after implementation)

## License

[To be determined]

## Submission

- **APK**: Built and ready for submission
- **GitHub URL**: [To be added]
- **Technical Note**: [To be added]
- **Demo Video**: [Optional]
