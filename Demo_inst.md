Below are the **assignment tracks/options** related to SITAA **contactless fingerprint challenge**. A Startup can choose minimum one (or more) track as per convenience. Based on these tracks, Start-ups will have to submit an **Android APK** and share it through a **GitHub URL, by 20<sup>th</sup> Jan 2026. The start-ups must clearly mention the chosen tracks in their submission. Few additional pointers**

- The APK should be installable on a standard android smartphone.
- A short technical note should be added, briefing about APK and its implementation approach and design choice.
- A demo video can be shared, but not mandatory, showing the APK use in real device.
- The use of open-source libraries is allowed but **must be clearly disclosed.**

**Track A - Contactless Finger Capture & Quality Assessment (CV-heavy)**

**Scope**

Build an Android APK that:

- Captures **finger images using the mobile camera**
- Guides the user to position the finger correctly
- Performs **basic quality checks**

**Minimum Functional Requirements**

- Live camera preview
- Finger detection / segmentation (rule-based or ML)
- Capture of at least one finger image
- At least **3 quality indicators**, such as:
  - Focus / blur score
  - Illumination check
  - Finger coverage / orientation
- Display a **quality score or pass/fail indicator**

**What This Track Evaluates**

- Computer vision fundamentals
- Image processing pipeline
- Mobile CV optimization
- UX understanding for biometric capture

**Track B - Finger Image Enhancement & Template Readiness (AI-heavy)**

**Scope**

Build an Android APK that:

- Takes a captured finger image (live or gallery)
- Performs **image enhancement suitable for fingerprint processing**

**Minimum Functional Requirements**

- Finger region isolation
- Ridge-valley enhancement (classical or ML-based)
- Noise reduction / contrast normalization
- Output an enhanced image
- (Optional but strong) Export image in **ISO-like resolution & format**

**What This Track Evaluates**

- Fingerprint domain understanding
- Image enhancement techniques
- Readiness for downstream matching
- ML vs classical trade-off clarity

**Track C - Contactless-to-Contact Matching Demo (Advanced)**

**Scope**

Build an Android APK that demonstrates:

- Matching between:
  - One **contactless finger image** (captured or gallery)
  - One or more **contact-based fingerprint images** (gallery)

**Minimum Functional Requirements**

- Import contact-based fingerprint images
- Capture or import contactless finger image
- Extract basic features (minutiae or surrogate features)
- Produce a **similarity score**
- Display **match / no-match decision**

**Note:** Accuracy is NOT the primary criterion here-**pipeline clarity and correctness are**.

**What This Track Evaluates**

- End-to-end biometric thinking
- Feature extraction & similarity modelling
- Practical constraints awareness

**Track D - Liveness / Spoof Heuristic (Optional Differentiator)**

**Scope**

Add a **basic liveness or spoof-resistance heuristic**, such as:

- Motion-based cue (micro movement)
- Texture-based check
- Multi-frame consistency check

**What This Track Evaluates**

- Security mindset
- Anti-spoof awareness
- Practical biometric risk thinking