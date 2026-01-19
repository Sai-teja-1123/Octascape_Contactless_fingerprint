# Octascape_Contactless_fingerprint

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
- **Image Processing**: OpenCV 4.9.0
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
- Real-time finger detection
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

## Open Source Libraries Disclosure

This application uses the following open-source libraries. All libraries are properly licensed and their use is disclosed as required:

### Core Android Libraries (Apache License 2.0)
- **AndroidX Core KTX** (v1.13.1) - Kotlin extensions for Android core functionality
  - License: Apache 2.0
  - Purpose: Core Android utilities with Kotlin support
  
- **AndroidX Lifecycle Runtime KTX** (v2.7.0) - Lifecycle-aware components
  - License: Apache 2.0
  - Purpose: Lifecycle management for Android components

- **AndroidX Activity Compose** (v1.9.2) - Compose integration for Activities
  - License: Apache 2.0
  - Purpose: Activity integration with Jetpack Compose

### UI Framework (Apache License 2.0)
- **Jetpack Compose BOM** (2024.02.00) - Bill of Materials for Compose libraries
  - License: Apache 2.0
  - Purpose: Version management for Compose libraries

- **Jetpack Compose UI** - Modern declarative UI framework
  - License: Apache 2.0
  - Purpose: Building native Android UI

- **Jetpack Compose UI Graphics** - Graphics and drawing utilities
  - License: Apache 2.0
  - Purpose: Graphics rendering in Compose

- **Jetpack Compose Material3** - Material Design 3 components
  - License: Apache 2.0
  - Purpose: Material Design 3 UI components

- **Google Material Components** (v1.12.0) - Material Design components
  - License: Apache 2.0
  - Purpose: Additional Material Design components

- **Jetpack Navigation Compose** (v2.7.6) - Navigation for Compose
  - License: Apache 2.0
  - Purpose: Navigation between screens

### Camera & Media (Apache License 2.0)
- **AndroidX CameraX Core** (v1.3.3) - Camera abstraction library
  - License: Apache 2.0
  - Purpose: Camera operations and preview

- **AndroidX CameraX Camera2** (v1.3.3) - Camera2 API integration
  - License: Apache 2.0
  - Purpose: Low-level camera control

- **AndroidX CameraX Lifecycle** (v1.3.3) - Lifecycle-aware camera
  - License: Apache 2.0
  - Purpose: Camera lifecycle management

- **AndroidX CameraX View** (v1.3.3) - Camera preview view
  - License: Apache 2.0
  - Purpose: Camera preview UI component

### Image Processing (Apache License 2.0 / BSD License)
- **OpenCV** (v4.9.0) - Open Source Computer Vision Library
  - License: Apache 2.0 / BSD 3-Clause
  - Purpose: Image processing, enhancement, feature extraction
  - Used for:
    - Image enhancement (CLAHE, bilateral filter, Gaussian blur)
    - Quality assessment (blur detection, edge detection)
    - Feature extraction (minutiae detection, skeletonization)
    - Liveness detection (texture analysis, motion detection)

### Data Persistence (Apache License 2.0)
- **Gson** (v2.10.1) - JSON serialization/deserialization
  - License: Apache 2.0
  - Purpose: JSON serialization for persistent storage of enrollments

### Testing Libraries (Apache License 2.0 / EPL 1.0)
- **JUnit** (v4.13.2) - Unit testing framework
  - License: EPL 1.0
  - Purpose: Unit testing (development only)

- **AndroidX Test Ext JUnit** (v1.1.5) - Android JUnit extensions
  - License: Apache 2.0
  - Purpose: Android-specific unit testing (development only)

- **Espresso Core** (v3.5.1) - UI testing framework
  - License: Apache 2.0
  - Purpose: UI automation testing (development only)

- **Jetpack Compose UI Test JUnit4** - Compose UI testing
  - License: Apache 2.0
  - Purpose: Compose UI testing (development only)

### License Summary
All production dependencies use **Apache License 2.0** or compatible licenses (BSD 3-Clause for OpenCV). Testing libraries use EPL 1.0 (JUnit) and Apache 2.0, but are not included in the production APK.

### Library Usage by Track
- **Track A (Capture & Quality)**: CameraX, OpenCV
- **Track B (Enhancement)**: OpenCV
- **Track C (Matching)**: OpenCV, Gson (for persistence)
- **Track D (Liveness)**: OpenCV
- **UI (All Tracks)**: Jetpack Compose, Material Design 3

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

### Implementation Approach

This application implements all four tracks (A, B, C, D) of the SITAA contactless fingerprint challenge using a modular, track-based architecture.

#### Track A - Capture & Quality Assessment
- **Camera Integration**: Uses CameraX for robust camera operations across Android versions
- **Finger Detection**: Rule-based detection using HSV color space, edge density, and shape analysis
- **Quality Metrics**: 
  - Blur detection using Laplacian variance
  - Illumination analysis using histogram statistics
  - Coverage analysis using finger detection bounding box
  - Orientation analysis using gradient direction
- **User Guidance**: Visual finger placement box with corner indicators and real-time confidence feedback

#### Track B - Image Enhancement
- **Pipeline**: Multi-stage enhancement optimized for fingerprint ridge clarity
- **Techniques**:
  - Finger region isolation via precise cropping
  - CLAHE (Contrast Limited Adaptive Histogram Equalization) for local contrast
  - Bilateral filtering for noise reduction while preserving edges
  - Unsharp masking for ridge emphasis
- **ISO Export**: Optional 500x500 pixel export at 500 ppi equivalent resolution

#### Track C - Matching
- **Feature Extraction**: Minutiae-based approach using:
  - Zhang-Suen skeletonization algorithm
  - Minutiae detection (ridge endings and bifurcations)
  - Local descriptor construction for rotation/translation tolerance
- **Matching Algorithm**: Descriptor-based matching with strict quality thresholds
- **Storage**: Persistent enrollment storage using SharedPreferences and internal file storage
- **Unique IDs**: Each enrollment requires a unique identifier

#### Track D - Liveness Detection
- **Multi-Method Approach**:
  - Motion-based detection (frame-to-frame analysis)
  - Texture-based spoof detection (compression artifacts, screen patterns)
  - Multi-frame consistency checking
  - Texture variation analysis
- **Integration**: Runs during frame collection before capture, providing pre-capture feedback

### Design Choices

1. **Minutiae over Surrogate Features**: Chose minutiae-based matching for Track C as it's more robust and standard in fingerprint recognition, despite higher computational cost.

2. **Rule-Based Detection**: Used rule-based finger detection (Track A) for reliability and no ML dependencies, suitable for the challenge timeframe.

3. **Classical Image Processing**: Track B uses classical OpenCV techniques (CLAHE, bilateral filter) rather than ML models for transparency and reproducibility.

4. **Persistent Storage**: Implemented file-based storage with SharedPreferences for metadata, avoiding database dependencies while ensuring data persistence.

5. **iOS-Inspired UI**: Applied modern design principles with smooth animations and professional aesthetics for better user experience.

### Performance Optimizations

- Image downscaling before skeletonization (300x300 target)
- Early termination in Zhang-Suen algorithm
- Optimized neighbor access patterns
- Background processing for heavy operations
- Frame collection limited to 1.5 seconds for liveness detection

## License

[To be determined]

## Submission

### Chosen Tracks
This submission implements **all four tracks**:
- ✅ **Track A**: Contactless Finger Capture & Quality Assessment
- ✅ **Track B**: Finger Image Enhancement & Template Readiness
- ✅ **Track C**: Contactless-to-Contact Matching Demo
- ✅ **Track D**: Liveness / Spoof Heuristic (Optional)

### Submission Details
- **APK**: Available in GitHub Releases
- **GitHub URL**: https://github.com/Sai-teja-1123/Octascape_Contactless_fingerprint
- **Technical Note**: See "Technical Notes" section above
- **Demo Video**: Optional (not included)

### Installation Instructions
1. Download the APK from GitHub Releases
2. Enable "Install from unknown sources" on your Android device
3. Install the APK
4. Grant camera and storage permissions when prompted
5. Follow on-screen instructions to capture and match fingerprints

### System Requirements
- Android 7.0 (API 24) or higher
- Camera with autofocus
- Minimum 2GB RAM recommended
- Storage permission for gallery access and enrollment storage
