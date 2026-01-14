# How to Find Your APK File

## Step 1: Build the APK in Android Studio

1. **In Android Studio**, go to the top menu:
   - **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**
   
2. Wait for the build to complete (you'll see a notification)

3. When build completes, click **"locate"** in the notification popup

## Step 2: APK Location

The APK will be located at:
```
o:\Contactless_fingerprint\app\build\outputs\apk\debug\app-debug.apk
```

## Step 3: Access via Android Studio

### Method A: Through Build Notification
- After building, a notification appears at the bottom-right
- Click **"locate"** to open the folder in Windows Explorer

### Method B: Through Project View
1. In Android Studio, switch to **Project** view (not Android view)
2. Navigate to: `app` → `build` → `outputs` → `apk` → `debug`
3. You should see `app-debug.apk`

### Method C: Direct File Explorer
1. Open Windows File Explorer
2. Navigate to: `O:\Contactless_fingerprint\app\build\outputs\apk\debug\`
3. Look for `app-debug.apk`

## If APK Doesn't Exist

If the folder `app/build/outputs/apk/debug/` doesn't exist or is empty:

1. **Clean the project first:**
   - **Build** → **Clean Project**
   
2. **Then rebuild:**
   - **Build** → **Rebuild Project**
   
3. **Then build APK:**
   - **Build** → **Build Bundle(s) / APK(s)** → **Build APK(s)**

## Quick Build Command

Alternatively, you can use the terminal in Android Studio:
1. Open **Terminal** tab at the bottom
2. Run: `.\gradlew assembleDebug` (or `gradlew.bat assembleDebug` on Windows)
3. APK will be at: `app\build\outputs\apk\debug\app-debug.apk`
