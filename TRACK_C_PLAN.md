# Track-C Implementation Plan - Step by Step

## Overview
**Goal**: Match contactless captured finger against enrolled contact-based fingerprints (by ID).

**Approach**: Surrogate features (orientation, texture) - simple, robust, fast.

---

## Phase 1: Basic UI & Data Structure (Foundation)
**Goal**: Create matching screen and simple data storage.

### Step 1.1: Add Matching Screen to Navigation
- [ ] Add `Screen.Matching` to `AppNavigation.kt`
- [ ] Create basic `MatchingScreen.kt` (just placeholder UI for now)
- [ ] Add "Matching" button in `HomeScreen.kt`
- **Test**: Can navigate to Matching screen

### Step 1.2: Create Simple Data Models
- [ ] Create `FingerprintEnrollment.kt` data class:
  ```kotlin
  data class FingerprintEnrollment(
      val id: String,
      val name: String,
      val imageUris: List<Uri>,  // Contact-based images
      val enrolledDate: Long
  )
  ```
- [ ] Create simple in-memory `EnrollmentRepository.kt`:
  - Store list of enrollments
  - Add/remove/get functions
- **Test**: Can create and store enrollments in memory

---

## Phase 2: Enrollment UI (Add Contact-Based Prints)
**Goal**: User can enroll fingerprints with an ID.

### Step 2.1: Gallery Picker Integration
- [ ] Add gallery picker button in `MatchingScreen`
- [ ] Use `ActivityResultContracts.PickMultipleVisualMedia()` for image selection
- [ ] Request `READ_MEDIA_IMAGES` permission if needed
- **Test**: Can pick images from gallery

### Step 2.2: Enrollment Dialog/Form
- [ ] Create enrollment dialog:
  - Text field for ID (e.g., "P001")
  - Text field for name (optional, e.g., "Sai - Right Index")
  - Display selected images as thumbnails
  - "Enroll" button
- [ ] Save enrollment to repository
- **Test**: Can enroll a fingerprint with ID and images

### Step 2.3: Display Enrolled IDs
- [ ] Show list of enrolled IDs in `MatchingScreen`
- [ ] Allow selecting an enrolled ID
- **Test**: Can see and select enrolled IDs

---

## Phase 3: Feature Extraction (Core Logic)
**Goal**: Extract simple features from fingerprint images.

### Step 3.1: Image Preprocessing
- [ ] Ensure images are:
  - Grayscale
  - Normalized size (e.g., 500x500)
  - Enhanced (reuse Track-B enhancement)
- [ ] Create helper function `preprocessForMatching(bitmap: Bitmap): Bitmap`
- **Test**: Preprocessing produces consistent output

### Step 3.2: Orientation Field Extraction
- [ ] Implement in `FeatureExtractor.kt`:
  - Use OpenCV `Sobel` to get gradients
  - Compute orientation from gradients
  - Create orientation histogram (8-16 bins)
- [ ] Return `OrientationFeatures` (histogram vector)
- **Test**: Can extract orientation from test image

### Step 3.3: Texture Features
- [ ] Implement simple texture descriptor:
  - Divide image into grid (e.g., 8x8 patches)
  - Compute mean/std for each patch
  - Concatenate into feature vector
- [ ] Return `TextureFeatures` (vector)
- **Test**: Can extract texture features

### Step 3.4: Combine Features
- [ ] Create `FingerprintFeatures` data class:
  ```kotlin
  data class FingerprintFeatures(
      val orientationHistogram: FloatArray,
      val textureVector: FloatArray
  )
  ```
- [ ] `FeatureExtractor.extractFeatures()` returns combined features
- **Test**: Full feature extraction pipeline works

---

## Phase 4: Matching Algorithm
**Goal**: Compare features and compute similarity.

### Step 4.1: Feature Normalization
- [ ] Normalize feature vectors (L2 normalization)
- [ ] Handle edge cases (empty features, all zeros)
- **Test**: Normalization produces valid vectors

### Step 4.2: Similarity Computation
- [ ] Implement in `Matcher.kt`:
  - Cosine similarity for orientation histogram
  - Cosine similarity for texture vector
  - Weighted average (e.g., 60% orientation, 40% texture)
- [ ] Return similarity score (0.0 to 1.0)
- **Test**: Similarity works on identical images (should be ~1.0)

### Step 4.3: Match Decision
- [ ] Apply threshold (e.g., 0.7 from `Constants.MATCH_THRESHOLD`)
- [ ] Return `MatchResult` with:
  - `similarityScore: Float`
  - `isMatch: Boolean`
  - `confidence: Float` (same as similarity)
- **Test**: Match decision logic works correctly

---

## Phase 5: Integration (Connect Everything)
**Goal**: Full end-to-end matching flow.

### Step 5.1: Match Contactless to Enrolled ID
- [ ] In `MatchingScreen`:
  - User selects enrolled ID
  - User captures contactless finger (reuse CameraScreen) OR uses already captured
  - Click "Match" button
- [ ] Extract features from:
  - Contactless image (enhanced)
  - All contact-based images for selected ID
- [ ] Compute best match (highest similarity)
- **Test**: Can match contactless to enrolled ID

### Step 5.2: Display Results
- [ ] Show in `MatchingScreen`:
  - Selected enrolled ID
  - Contactless image thumbnail
  - Similarity score (0-100%)
  - Match/No Match status (color-coded)
  - Best matching contact-based image thumbnail
- **Test**: Results display correctly

### Step 5.3: Background Processing
- [ ] Run feature extraction and matching on `Dispatchers.Default`
- [ ] Show loading indicator during processing
- [ ] Handle errors gracefully
- **Test**: UI doesn't freeze, errors are handled

---

## Phase 6: Polish & Testing
**Goal**: Make it production-ready.

### Step 6.1: Error Handling
- [ ] Handle: no enrolled IDs, image load failures, feature extraction failures
- [ ] Show user-friendly error messages
- **Test**: All error cases handled

### Step 6.2: UI Improvements
- [ ] Better layout and spacing
- [ ] Icons and visual feedback
- [ ] Smooth animations
- **Test**: UI looks professional

### Step 6.3: Testing
- [ ] Test with different images
- [ ] Test edge cases (very different fingers, same finger different angles)
- [ ] Verify similarity scores are reasonable
- **Test**: Everything works end-to-end

---

## Implementation Order (What We'll Do First)

**Week 1: Foundation**
1. Step 1.1 - Add Matching Screen
2. Step 1.2 - Data Models
3. Step 2.1 - Gallery Picker
4. Step 2.2 - Enrollment Dialog

**Week 2: Core Logic**
5. Step 3.1 - Preprocessing
6. Step 3.2 - Orientation Features
7. Step 3.3 - Texture Features
8. Step 3.4 - Combine Features

**Week 3: Matching**
9. Step 4.1 - Normalization
10. Step 4.2 - Similarity
11. Step 4.3 - Match Decision
12. Step 5.1 - Integration

**Week 4: Polish**
13. Step 5.2 - Results Display
14. Step 5.3 - Background Processing
15. Step 6.1-6.3 - Polish & Testing

---

## Success Criteria
- [ ] Can enroll fingerprints with ID
- [ ] Can match contactless capture to enrolled ID
- [ ] Shows similarity score and match/no-match
- [ ] Pipeline is clear and documented
- [ ] Works without crashes

---

## Notes
- **Keep it simple**: We're using surrogate features, not minutiae
- **Test each step**: Don't move forward until current step works
- **Reuse existing code**: Track-B enhancement, CameraScreen, etc.
- **Focus on clarity**: SITAA values pipeline clarity over accuracy
