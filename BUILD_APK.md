# Building APK for Distribution

## Method 1: Build Debug APK (Easiest - Recommended for Sharing)

### Steps in Android Studio:

1. **Open the project** in Android Studio
2. **Build Menu** → **Build Bundle(s) / APK(s)** → **Build APK(s)**
3. Wait for the build to complete
4. When done, click **locate** in the notification, or find the APK at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

### Alternative: Using Build Variants

1. **View** → **Tool Windows** → **Build Variants**
2. Select **debug** variant
3. **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**

## Method 2: Build Release APK (Optimized)

### Steps:

1. **Build** → **Generate Signed Bundle / APK**
2. Select **APK** → **Next**
3. **Create new keystore** (or use existing)
   - **Key store path**: Choose a location and name (e.g., `release.keystore`)
   - **Password**: Create a strong password
   - **Key alias**: `release-key`
   - **Key password**: Same or different password
   - Fill in certificate information
4. Select **release** build variant
5. Click **Finish**
6. APK will be at: `app/build/outputs/apk/release/app-release.apk`

## Method 3: Command Line (If Gradle is installed)

```bash
# Build Debug APK
cd o:\Contactless_fingerprint
gradlew assembleDebug

# Build Release APK
gradlew assembleRelease
```

APK location: `app/build/outputs/apk/debug/app-debug.apk` or `app/build/outputs/apk/release/app-release.apk`

## Sharing the APK

### Option 1: GitHub Releases (Recommended)
1. Go to your GitHub repository
2. Click **Releases** → **Create a new release**
3. Tag: `v1.0.0`
4. Title: `Initial Release - Contactless Fingerprint App`
5. Upload the APK file
6. Click **Publish release**

### Option 2: Direct Share
- Share the APK file directly via email, cloud storage, etc.
- Recipients need to enable "Install from unknown sources" on their Android device

## Important Notes

- **Debug APK**: Larger file size, includes debug symbols, fine for testing/sharing
- **Release APK**: Optimized, smaller, production-ready (requires signing)
- **Installation**: Users may need to enable "Install from unknown sources" in Android settings
