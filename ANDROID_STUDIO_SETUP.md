# Android Studio Setup Guide

## Essential Settings & Configurations

### 1. **SDK Manager Setup** (Tools → SDK Manager)
Install the following:
- **Android SDK Platform**: API 24 (Android 7.0) minimum, API 33+ recommended
- **Android SDK Build-Tools**: Latest version
- **Android SDK Platform-Tools**: Latest version
- **Android Emulator**: Latest version
- **Android SDK Command-line Tools**: Latest version

### 2. **Gradle Settings** (File → Settings → Build, Execution, Deployment → Gradle)
- **Gradle JDK**: Use JDK 17 or JDK 11 (recommended: JDK 17)
- **Gradle Version**: Use Gradle wrapper (will be auto-configured)
- **Build and run using**: Gradle (default)
- **Run tests using**: Gradle (default)

### 3. **Project Structure Settings** (File → Project Structure)
- **Compile SDK Version**: 34 (Android 14)
- **Min SDK Version**: 24 (Android 7.0) - for broader device compatibility
- **Target SDK Version**: 34 (Android 14)
- **Source Compatibility**: Java 17
- **Target Compatibility**: Java 17

### 4. **Plugins to Install** (File → Settings → Plugins)
- **Kotlin**: Should be included by default
- **Android NDK**: For native code (if needed for OpenCV)
- **Git Integration**: Should be enabled by default

### 5. **Emulator Setup** (Tools → Device Manager)
- Create a virtual device with:
  - **API Level**: 30+ (Android 11+)
  - **System Image**: Google Play (recommended) or Google APIs
  - **Device**: Pixel 5 or similar (good camera support)

### 6. **Memory Settings** (Help → Edit Custom VM Options)
If you have 16GB+ RAM:
```
-Xms2048m
-Xmx4096m
-XX:ReservedCodeCacheSize=1024m
```

### 7. **Code Style** (File → Settings → Editor → Code Style → Kotlin)
- Use default Kotlin style (recommended)
- Set indentation: 4 spaces (standard)

### 8. **Import Settings** (File → Settings → Editor → General → Auto Import)
- ✅ Add unambiguous imports on the fly
- ✅ Optimize imports on the fly

## After Installation Checklist

1. ✅ Open SDK Manager and install required SDKs
2. ✅ Set up at least one Android Virtual Device (AVD)
3. ✅ Configure Gradle JDK to JDK 17
4. ✅ Verify Kotlin plugin is installed
5. ✅ Test by creating a sample project

## Important Notes

- **Internet Connection**: Required for first-time Gradle sync (downloads dependencies)
- **Storage**: Ensure at least 10GB free space for SDK, emulator, and dependencies
- **Hardware Acceleration**: Enable HAXM or Hyper-V for faster emulator performance
