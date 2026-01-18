# Track-D Testing Guide - Liveness Detection

## Overview
This guide explains how to test the liveness detection features (Track-D) once the mobile device is connected.

---

## Prerequisites
- Android device connected via USB (with USB debugging enabled)
- APK installed on device (or build and install fresh APK)
- Test materials:
  - Real finger (your own)
  - Photo of finger on phone screen
  - Printed photo of finger (optional)
  - Screen showing finger image (optional)

---

## Testing Steps

### 1. Build and Install APK
```bash
# Navigate to project root
cd o:\Contactless_fingerprint

# Build debug APK
.\gradlew assembleDebug

# Install on connected device
adb install app\build\outputs\apk\debug\app-debug.apk
```

### 2. Test Frame Collection (Step 1.1 & 1.2)

**What to Test:**
- Frames are collected when finger is detected
- Collection period (1.5 seconds) before capture is enabled
- UI shows "Collecting frames..." with countdown

**Steps:**
1. Open the app
2. Navigate to "Capture Finger" screen
3. Place your finger in the box
4. **Observe:**
   - Status text should show "Collecting frames... (1s)" then "(0s)"
   - Capture button should be disabled during collection
   - After 1.5 seconds, button should enable and show "Capture"
   - Check Logcat for: `"Started frame collection for liveness detection"`
   - Check Logcat for: `"Frame added to buffer. Buffer size: X"`

**Expected Logcat Messages:**
```
CameraManager: Started frame collection for liveness detection
CameraManager: Frame added to buffer. Buffer size: 1
CameraManager: Frame added to buffer. Buffer size: 2
...
CameraScreen: Frame collection ready (1500ms elapsed)
```

---

### 3. Test Motion Detection (Step 2.1 & 2.2)

**What to Test:**
- Motion detection distinguishes live finger from static photo
- Motion scores are in expected ranges

**Test Case 1: Real Finger (Live)**
1. Place your real finger in the box
2. Keep finger steady but allow natural micro-movements
3. Wait for collection period (1.5s)
4. Capture the image
5. **Check Logcat for motion scores:**
   - Look for: `"Motion detection: avgMotion=X, variance=Y, score=Z"`
   - **Expected:** Score should be 0.3-0.7 (moderate motion)
   - **Expected:** avgMotion should be 40-100
   - **Expected:** variance should be > 2.0

**Test Case 2: Photo on Screen (Spoof)**
1. Display a photo of a finger on your phone/tablet screen
2. Place the screen showing the photo in the camera view
3. Wait for collection period
4. Capture the image
5. **Check Logcat for motion scores:**
   - **Expected:** Score should be < 0.1 (very low motion)
   - **Expected:** avgMotion should be < 20
   - **Expected:** variance should be < 2.0

**Test Case 3: Printed Photo (Spoof)**
1. Print a photo of a finger
2. Place the printed photo in the camera view
3. Wait for collection period
4. Capture the image
5. **Check Logcat for motion scores:**
   - **Expected:** Score should be < 0.1 (very low motion)
   - **Expected:** avgMotion should be < 20

**Expected Logcat Output (Real Finger):**
```
LivenessDetector: Motion detection: avgMotion=65.3, variance=8.2, score=0.52, pairs=8
```

**Expected Logcat Output (Photo/Spoof):**
```
LivenessDetector: Motion detection: avgMotion=5.1, variance=0.8, score=0.03, pairs=8
```

---

### 4. Test Texture Detection (Step 3.1 & 3.2)

**What to Test:**
- Texture analysis detects spoof artifacts
- Real finger has natural skin texture (high score)
- Print/photo has artificial patterns (low score)

**Test Cases:**
- Real finger → should have high texture score (> 0.6)
- Photo on screen → should have lower texture score (< 0.4)
- Printed photo → should have low texture score (< 0.3)

**Check Logcat for:**
```
LivenessDetector: Texture detection: score=X
```

---

### 5. Test Consistency Check (Step 4.1 & 4.2)

**What to Test:**
- Frame consistency distinguishes live vs static
- Real finger: some variation between frames (high score)
- Photo/print: frames too similar (low score)

**Test Cases:**
- Real finger → consistency score should be 0.5-0.8
- Photo/print → consistency score should be < 0.2

**Check Logcat for:**
```
LivenessDetector: Consistency check: score=X
```

---

### 6. Test Full Liveness Detection (Step 5.1 & 5.2)

**What to Test:**
- Complete liveness detection pipeline
- UI shows liveness result
- Combined scores from all methods

**Steps:**
1. Capture with real finger
2. Check UI for liveness indicator/score
3. Check Logcat for complete liveness result:
   ```
   LivenessDetector: Liveness result: isLive=true, confidence=0.75
   LivenessDetector: - Motion: 0.52
   LivenessDetector: - Texture: 0.68
   LivenessDetector: - Consistency: 0.61
   ```

**Test Cases:**
- Real finger → `isLive=true`, `confidence > 0.6`
- Photo/print → `isLive=false`, `confidence < 0.4`

---

## Logcat Filtering

**Filter for Camera/Liveness logs:**
```bash
adb logcat | grep -E "(CameraManager|LivenessDetector|CameraScreen)"
```

**Or use Android Studio Logcat filters:**
- Tag: `CameraManager`, `LivenessDetector`, `CameraScreen`
- Level: Debug and above

---

## Troubleshooting

### Issue: No frames collected
- **Check:** Is finger detection working? (green checkmark appears)
- **Check:** Logcat for "Started frame collection"
- **Fix:** Ensure finger is properly detected before collection starts

### Issue: Motion score always 0
- **Check:** Are frames being collected? (check buffer size in logs)
- **Check:** Are frames different? (photo might be too static)
- **Fix:** Ensure finger has some natural movement

### Issue: High motion score for photo
- **Check:** Is photo moving? (should be completely static)
- **Check:** Lighting changes? (might cause false motion)
- **Fix:** Ensure photo is completely still, good lighting

### Issue: Low motion score for real finger
- **Check:** Is finger too still? (try slight natural movements)
- **Check:** Collection period too short?
- **Fix:** Allow natural micro-movements, ensure 1.5s collection

---

## Success Criteria

✅ **Frame Collection:**
- Frames collected when finger detected
- Collection period (1.5s) works correctly
- Buffer maintains last 10 frames

✅ **Motion Detection:**
- Real finger: score 0.3-0.7
- Photo/print: score < 0.1
- Clear distinction between live and spoof

✅ **Texture Detection:**
- Real finger: score > 0.6
- Photo/print: score < 0.4

✅ **Consistency Check:**
- Real finger: score 0.5-0.8
- Photo/print: score < 0.2

✅ **Full Pipeline:**
- Combined liveness detection works
- UI shows results
- Accurate classification (live vs spoof)

---

## Notes

- **Performance:** Liveness detection should not significantly slow down capture
- **False Positives:** Better to warn than block (user experience)
- **Testing:** Test with various conditions (lighting, angles, materials)
- **Logging:** All detection steps log detailed information for debugging

---

## Quick Test Checklist

- [ ] Frame collection starts when finger detected
- [ ] Collection period (1.5s) completes before capture enabled
- [ ] Motion detection: Real finger → 0.3-0.7, Photo → < 0.1
- [ ] Texture detection: Real finger → > 0.6, Photo → < 0.4
- [ ] Consistency check: Real finger → 0.5-0.8, Photo → < 0.2
- [ ] Full liveness: Real finger → isLive=true, Photo → isLive=false
- [ ] UI shows liveness results
- [ ] No performance degradation during capture

---

**Last Updated:** After Step 2.2 completion
**Next Steps:** Continue with Step 3.1 (Texture Analysis)
