# Track-D Implementation Plan - Liveness/Spoof Detection

## Overview
**Goal**: Add basic liveness detection to prevent spoofing attacks (photos, prints, etc.)

**Approach**: Multi-heuristic approach combining motion, texture, and consistency checks.

---

## Phase 1: Frame Collection (Foundation)
**Goal**: Collect multiple frames during preview for analysis.

### Step 1.1: Frame Buffer in CameraManager
- [ ] Add frame buffer to store last N frames (e.g., 5-10 frames)
- [ ] Collect frames during preview (from ImageAnalysis)
- [ ] Store frames with timestamps
- [ ] Limit buffer size (keep only recent frames)
- **Test**: Can collect and store frames during preview

### Step 1.2: Frame Collection Trigger
- [ ] Start collecting frames when finger is detected
- [ ] Collect for 1-2 seconds before capture
- [ ] Pass collected frames to liveness detector
- **Test**: Frames are collected when finger is detected

---

## Phase 2: Motion-Based Detection
**Goal**: Detect micro-movements (real fingers move slightly, photos don't).

### Step 2.1: Frame Difference Analysis
- [ ] Implement `checkMotion()` in `LivenessDetector`
- [ ] Compute frame-to-frame differences
- [ ] Calculate motion magnitude (optical flow or simple difference)
- [ ] Detect if there's sufficient micro-movement
- **Test**: Can detect motion between frames

### Step 2.2: Motion Score Calculation
- [ ] Normalize motion to 0-1 score
- [ ] Real finger: moderate motion (0.3-0.7)
- [ ] Photo/print: very low motion (<0.1) or no motion (0.0)
- **Test**: Motion scores are reasonable

---

## Phase 3: Texture-Based Detection
**Goal**: Detect spoof artifacts (print texture, photo artifacts, screen reflections).

### Step 3.1: Texture Analysis
- [ ] Implement `checkTexture()` in `LivenessDetector`
- [ ] Analyze texture patterns using:
  - Local Binary Patterns (LBP) or similar
  - Frequency domain analysis (FFT)
  - Edge pattern analysis
- [ ] Detect unnatural patterns (print artifacts, screen moiré)
- **Test**: Can detect texture differences

### Step 3.2: Texture Score Calculation
- [ ] Real finger: natural skin texture (high score)
- [ ] Print/photo: artificial texture patterns (low score)
- **Test**: Texture scores distinguish real vs fake

---

## Phase 4: Multi-Frame Consistency
**Goal**: Detect if frames are identical (photo) or too consistent (print).

### Step 4.1: Consistency Check
- [ ] Implement `checkConsistency()` in `LivenessDetector`
- [ ] Compare consecutive frames
- [ ] Compute similarity between frames
- [ ] Real finger: some variation between frames
- [ ] Photo/print: frames are too similar (identical or near-identical)
- **Test**: Can detect frame consistency

### Step 4.2: Consistency Score Calculation
- [ ] High consistency (very similar frames) = low score (likely spoof)
- [ ] Moderate variation = high score (likely live)
- **Test**: Consistency scores are reasonable

---

## Phase 5: Integration
**Goal**: Integrate liveness detection into capture flow.

### Step 5.1: Liveness Check During Capture
- [ ] Collect frames during preview (1-2 seconds)
- [ ] Run liveness detection before/after capture
- [ ] Show liveness result in UI
- **Test**: Liveness check runs during capture

### Step 5.2: UI Integration
- [ ] Add liveness indicator in CameraScreen
- [ ] Show liveness score/status
- [ ] Warn if spoof detected
- **Test**: Liveness status visible in UI

### Step 5.3: Block Capture if Spoof Detected
- [ ] Optional: Block capture if liveness fails
- [ ] Or: Show warning but allow capture
- [ ] Log liveness results
- **Test**: Spoof detection works

---

## Implementation Order (Step by Step)

**Week 1: Foundation**
1. Step 1.1 - Frame buffer in CameraManager
2. Step 1.2 - Frame collection trigger
3. Step 2.1 - Frame difference analysis

**Week 2: Detection Methods**
4. Step 2.2 - Motion score calculation
5. Step 3.1 - Texture analysis
6. Step 3.2 - Texture score calculation

**Week 3: Consistency & Integration**
7. Step 4.1 - Consistency check
8. Step 4.2 - Consistency score
9. Step 5.1 - Liveness check during capture

**Week 4: UI & Polish**
10. Step 5.2 - UI integration
11. Step 5.3 - Spoof blocking/warning
12. Testing & refinement

---

## Technical Approach

### Motion Detection
- **Method**: Frame-to-frame difference
- **Algorithm**: 
  - Convert frames to grayscale
  - Compute absolute difference
  - Sum differences (motion magnitude)
  - Normalize by frame size

### Texture Detection
- **Method**: Local Binary Patterns (LBP) or frequency analysis
- **Algorithm**:
  - Compute LBP histogram
  - Compare with expected skin texture patterns
  - Detect print artifacts (regular patterns, moiré)

### Consistency Check
- **Method**: Frame similarity
- **Algorithm**:
  - Compare consecutive frames
  - Compute correlation or difference
  - High similarity = likely photo/print

---

## Success Criteria
- [ ] Can collect frames during preview
- [ ] Motion detection distinguishes live vs static
- [ ] Texture detection identifies spoof artifacts
- [ ] Consistency check detects photos/prints
- [ ] Liveness results displayed in UI
- [ ] Works without significant performance impact

---

## Notes
- **Keep it simple**: Basic heuristics, not ML models
- **Performance**: Must not slow down capture significantly
- **False positives**: Better to warn than block (user experience)
- **Testing**: Test with real finger, photo, print, screen
