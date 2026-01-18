package com.contactless.fingerprint.matching

import android.graphics.Bitmap
import android.util.Log
import com.contactless.fingerprint.core.ImageProcessor
import com.contactless.fingerprint.enhancement.ImageEnhancer
import com.contactless.fingerprint.utils.Constants
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.*

/**
 * Extracts features from fingerprint images
 * Track C: Feature extraction (minutiae or surrogate features)
 */
class FeatureExtractor {
    
    private val imageEnhancer = ImageEnhancer()
    
    /**
     * Step 3.1: Preprocesses fingerprint image for feature extraction.
     * 
     * Ensures images are:
     * - Grayscale
     * - Normalized size (500x500, maintaining aspect ratio)
     * - Enhanced (reuses Track-B enhancement pipeline, but without cropping for gallery images)
     * 
     * @param bitmap Input fingerprint image (can be contact-based from gallery or contactless)
     * @param isFromGallery If true, skips cropping step (gallery images are assumed pre-cropped)
     * @return Preprocessed bitmap ready for feature extraction
     */
    fun preprocessForMatching(bitmap: Bitmap, isFromGallery: Boolean = false): Bitmap {
        return try {
            Log.d("FeatureExtractor", "Preprocessing ${if (isFromGallery) "gallery" else "contactless"} image: ${bitmap.width}x${bitmap.height}")
            
            // Step 1: Enhance image
            val enhanced = if (isFromGallery) {
                // Gallery images need enhancement (they're raw photos)
                enhanceWithoutCrop(bitmap)
            } else {
                // Contactless images are already enhanced from CameraScreen
                // Just ensure grayscale and resize, skip enhancement to avoid double-processing
                ensureGrayscale(bitmap)
            }
            
            // Step 2: Resize to normalized size (500x500, maintaining aspect ratio)
            val normalized = resizeToNormalizedSize(enhanced)
            
            // Step 3: Ensure grayscale (double-check for contactless)
            val grayscale = if (isFromGallery) {
                ensureGrayscale(normalized)
            } else {
                normalized // Already grayscale from above
            }
            
            Log.d("FeatureExtractor", "Preprocessed image size: ${grayscale.width}x${grayscale.height}")
            
            grayscale
        } catch (e: Exception) {
            Log.e("FeatureExtractor", "Error preprocessing image: ${e.message}", e)
            // Fallback: just resize to normalized size
            resizeToNormalizedSize(bitmap)
        }
    }
    
    /**
     * Enhances image without cropping (for gallery images that are already finger crops).
     * Reuses Track-B enhancement pipeline: CLAHE, bilateral filter, unsharp masking.
     */
    private fun enhanceWithoutCrop(bitmap: Bitmap): Bitmap {
        return try {
            val srcMat = ImageProcessor.bitmapToMat(bitmap)
            
            // Convert to grayscale
            val gray = Mat()
            Imgproc.cvtColor(srcMat, gray, Imgproc.COLOR_RGBA2GRAY)
            
            // CLAHE for contrast normalization
            val clahe = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
            val claheMat = Mat()
            clahe.apply(gray, claheMat)
            
            // Noise reduction
            val denoised = Mat()
            Imgproc.bilateralFilter(claheMat, denoised, 5, 20.0, 20.0)
            
            // Ridge emphasis via unsharp masking
            val blurred = Mat()
            Imgproc.GaussianBlur(denoised, blurred, Size(0.0, 0.0), 0.6)
            val sharpened = Mat()
            Core.addWeighted(denoised, 1.6, blurred, -0.6, 0.0, sharpened)
            
            // Convert back to RGBA for compatibility
            val resultRgba = Mat()
            Imgproc.cvtColor(sharpened, resultRgba, Imgproc.COLOR_GRAY2RGBA)
            
            ImageProcessor.matToBitmap(resultRgba)
        } catch (e: Exception) {
            Log.e("FeatureExtractor", "Error enhancing without crop: ${e.message}", e)
            bitmap
        }
    }
    
    /**
     * Resizes image to normalized size (500x500) while maintaining aspect ratio.
     * This ensures consistent feature extraction across different input sizes.
     */
    private fun resizeToNormalizedSize(bitmap: Bitmap): Bitmap {
        val targetSize = Constants.ISO_RESOLUTION_WIDTH // 500
        val currentWidth = bitmap.width
        val currentHeight = bitmap.height
        
        // If already close to target size, return as-is
        if (kotlin.math.abs(currentWidth - targetSize) < 10 && 
            kotlin.math.abs(currentHeight - targetSize) < 10) {
            return bitmap
        }
        
        // Calculate new dimensions maintaining aspect ratio
        val aspectRatio = currentWidth.toFloat() / currentHeight.toFloat()
        val newWidth: Int
        val newHeight: Int
        
        if (currentWidth > currentHeight) {
            // Landscape: fit width to target
            newWidth = targetSize
            newHeight = (targetSize / aspectRatio).toInt()
        } else {
            // Portrait or square: fit height to target
            newHeight = targetSize
            newWidth = (targetSize * aspectRatio).toInt()
        }
        
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
    
    /**
     * Ensures image is grayscale (converts if needed).
     */
    private fun ensureGrayscale(bitmap: Bitmap): Bitmap {
        // If already grayscale (ALPHA_8), return as-is
        if (bitmap.config == Bitmap.Config.ALPHA_8) {
            return bitmap
        }
        
        // Convert to grayscale using OpenCV
        return try {
            val srcMat = ImageProcessor.bitmapToMat(bitmap)
            val gray = Mat()
            Imgproc.cvtColor(srcMat, gray, Imgproc.COLOR_RGBA2GRAY)
            
            // Convert back to RGBA for compatibility
            val resultRgba = Mat()
            Imgproc.cvtColor(gray, resultRgba, Imgproc.COLOR_GRAY2RGBA)
            
            ImageProcessor.matToBitmap(resultRgba)
        } catch (e: Exception) {
            Log.e("FeatureExtractor", "Error converting to grayscale: ${e.message}", e)
            bitmap
        }
    }
    
    /**
     * Step 3.2: Extracts orientation field from fingerprint image.
     * 
     * Uses Sobel operators to compute gradients, then calculates orientation angles.
     * Creates a histogram of orientations (12 bins covering 0-180 degrees).
     * 
     * @param preprocessedBitmap Preprocessed grayscale fingerprint image
     * @return Orientation histogram as FloatArray (12 bins)
     */
    fun extractOrientationField(preprocessedBitmap: Bitmap): FloatArray {
        return try {
            // Convert to Mat (grayscale)
            val grayMat = ImageProcessor.bitmapToMat(preprocessedBitmap)
            val gray = Mat()
            // Ensure it's grayscale
            if (grayMat.channels() == 1) {
                grayMat.copyTo(gray)
            } else {
                Imgproc.cvtColor(grayMat, gray, Imgproc.COLOR_RGBA2GRAY)
            }
            
            // Step 1: Compute gradients using Sobel operators
            val gradX = Mat()
            val gradY = Mat()
            Imgproc.Sobel(gray, gradX, CvType.CV_32F, 1, 0, 3) // Gradient in X direction
            Imgproc.Sobel(gray, gradY, CvType.CV_32F, 0, 1, 3) // Gradient in Y direction
            
            // Step 2: Compute orientation angles from gradients
            // Orientation = atan2(gy, gx) / 2 (divide by 2 because ridges are symmetric)
            val rows = gray.rows()
            val cols = gray.cols()
            val orientationData = mutableListOf<Float>()
            
            // Iterate through pixels and compute orientation
            for (y in 0 until rows) {
                for (x in 0 until cols) {
                    val gxArray = gradX.get(y, x)
                    val gyArray = gradY.get(y, x)
                    val gx = gxArray[0].toFloat()
                    val gy = gyArray[0].toFloat()
                    
                    // Skip very small gradients (noise)
                    if (abs(gx) < 0.1f && abs(gy) < 0.1f) continue
                    
                    // Compute orientation angle (in radians)
                    // Divide by 2 because ridges are symmetric (0-180 degrees instead of 0-360)
                    var angle = atan2(gy.toDouble(), gx.toDouble()) / 2.0
                    // Normalize to 0-π range (0-180 degrees)
                    if (angle < 0) angle += PI
                    orientationData.add(angle.toFloat())
                }
            }
            
            // Step 3: Create orientation histogram (12 bins covering 0-180 degrees)
            val numBins = 12
            val histogram = FloatArray(numBins) { 0f }
            val binSize = PI / numBins // Each bin covers π/12 radians (15 degrees)
            
            // Count orientations into bins
            for (angle in orientationData) {
                if (angle.isFinite() && !angle.isNaN()) {
                    val binIndex = (angle / binSize).toInt().coerceIn(0, numBins - 1)
                    histogram[binIndex]++
                }
            }
            
            // Apply smoothing to histogram (makes it more robust to capture variations)
            // Simple 3-point moving average
            val smoothedHistogram = FloatArray(numBins)
            for (i in histogram.indices) {
                val prev = histogram[(i - 1 + numBins) % numBins]
                val curr = histogram[i]
                val next = histogram[(i + 1) % numBins]
                smoothedHistogram[i] = (prev * 0.2f + curr * 0.6f + next * 0.2f)
            }
            
            // Normalize histogram (convert counts to probabilities)
            val total = smoothedHistogram.sum()
            if (total > 0) {
                for (i in smoothedHistogram.indices) {
                    smoothedHistogram[i] /= total
                }
            }
            
            // Use smoothed histogram
            val finalHistogram = smoothedHistogram
            
            // Clean up
            grayMat.release()
            gray.release()
            gradX.release()
            gradY.release()
            
            finalHistogram
        } catch (e: Exception) {
            Log.e("FeatureExtractor", "Error extracting orientation field: ${e.message}", e)
            // Return uniform histogram on error
            FloatArray(12) { 1f / 12f }
        }
    }
    
    /**
     * Step 3.3: Extracts texture features from fingerprint image.
     * 
     * IMPROVED: Uses gradient magnitude instead of mean/std for better discrimination.
     * Gradient magnitude captures ridge patterns much better than simple statistics.
     * 
     * Divides image into a grid (8x8 patches) and computes gradient magnitude statistics
     * for each patch. This captures the actual ridge structure, not just brightness.
     * 
     * @param preprocessedBitmap Preprocessed grayscale fingerprint image
     * @return Texture feature vector (gradient magnitude features for each patch)
     */
    fun extractTextureFeatures(preprocessedBitmap: Bitmap): FloatArray {
        return try {
            // Convert to Mat (grayscale)
            val grayMat = ImageProcessor.bitmapToMat(preprocessedBitmap)
            val gray = Mat()
            // Ensure it's grayscale
            if (grayMat.channels() == 1) {
                grayMat.copyTo(gray)
            } else {
                Imgproc.cvtColor(grayMat, gray, Imgproc.COLOR_RGBA2GRAY)
            }
            
            // Compute gradient magnitude (more distinctive than mean/std)
            val gradX = Mat()
            val gradY = Mat()
            val gradientMagnitude = Mat()
            
            Imgproc.Sobel(gray, gradX, CvType.CV_32F, 1, 0, 3)
            Imgproc.Sobel(gray, gradY, CvType.CV_32F, 0, 1, 3)
            Core.magnitude(gradX, gradY, gradientMagnitude)
            
            // Grid size: 8x8 patches
            val gridRows = 8
            val gridCols = 8
            val imageRows = gray.rows()
            val imageCols = gray.cols()
            
            // Calculate patch size
            val patchHeight = imageRows / gridRows
            val patchWidth = imageCols / gridCols
            
            // Feature vector: gradient magnitude statistics for each patch
            val featureVector = mutableListOf<Float>()
            
            // Extract features from each patch
            for (row in 0 until gridRows) {
                for (col in 0 until gridCols) {
                    // Calculate patch boundaries
                    val startY = row * patchHeight
                    val startX = col * patchWidth
                    val endY = if (row == gridRows - 1) imageRows else (row + 1) * patchHeight
                    val endX = if (col == gridCols - 1) imageCols else (col + 1) * patchWidth
                    
                    val patchWidthActual = endX - startX
                    val patchHeightActual = endY - startY
                    
                    // Extract gradient magnitude patch
                    val patch = Mat(gradientMagnitude, Rect(startX, startY, patchWidthActual, patchHeightActual))
                    
                    // Compute statistics of gradient magnitude (more distinctive than image intensity)
                    val meanMat = MatOfDouble()
                    val stdMat = MatOfDouble()
                    Core.meanStdDev(patch, meanMat, stdMat)
                    
                    val meanGradient = meanMat.get(0, 0)[0].toFloat()
                    val stdGradient = stdMat.get(0, 0)[0].toFloat()
                    
                    // Also compute max gradient (captures strongest ridge edges)
                    val minMax = Core.minMaxLoc(patch)
                    val maxGradient = minMax.maxVal.toFloat()
                    
                    // Normalize: gradient magnitudes can vary, normalize to 0-1 range
                    // Typical gradient magnitude range: 0-500 for Sobel
                    val normalizedMean = (meanGradient / 500.0f).coerceIn(0f, 1f)
                    val normalizedStd = (stdGradient / 500.0f).coerceIn(0f, 1f)
                    val normalizedMax = (maxGradient / 500.0f).coerceIn(0f, 1f)
                    
                    featureVector.add(normalizedMean)
                    featureVector.add(normalizedStd)
                    featureVector.add(normalizedMax) // Added max for better discrimination
                    
                    patch.release()
                    meanMat.release()
                    stdMat.release()
                }
            }
            
            // Clean up
            grayMat.release()
            gray.release()
            gradX.release()
            gradY.release()
            gradientMagnitude.release()
            
            featureVector.toFloatArray()
        } catch (e: Exception) {
            Log.e("FeatureExtractor", "Error extracting texture features: ${e.message}", e)
            // Return zero vector on error (8x8 grid * 3 features = 192 features)
            FloatArray(192) { 0f }
        }
    }
    
    /**
     * Extracts ridge frequency features (spacing between ridges).
     * Different fingers have different ridge spacing patterns.
     */
    private fun extractRidgeFrequency(preprocessedBitmap: Bitmap): FloatArray {
        return try {
            val grayMat = ImageProcessor.bitmapToMat(preprocessedBitmap)
            val gray = Mat()
            if (grayMat.channels() == 1) {
                grayMat.copyTo(gray)
            } else {
                Imgproc.cvtColor(grayMat, gray, Imgproc.COLOR_RGBA2GRAY)
            }
            
            // Divide into 4x4 grid (coarser than texture to capture overall frequency)
            val gridRows = 4
            val gridCols = 4
            val imageRows = gray.rows()
            val imageCols = gray.cols()
            val patchHeight = imageRows / gridRows
            val patchWidth = imageCols / gridCols
            
            val frequencyFeatures = mutableListOf<Float>()
            
            for (row in 0 until gridRows) {
                for (col in 0 until gridCols) {
                    val startY = row * patchHeight
                    val startX = col * patchWidth
                    val endY = if (row == gridRows - 1) imageRows else (row + 1) * patchHeight
                    val endX = if (col == gridCols - 1) imageCols else (col + 1) * patchWidth
                    
                    val patch = Mat(gray, Rect(startX, startY, endX - startX, endY - startY))
                    
                    // Compute ridge frequency using autocorrelation
                    // Count ridge crossings in horizontal direction
                    var ridgeCrossings = 0
                    val threshold = 128.0 // Mid-gray threshold
                    val sampleRow = patch.rows() / 2
                    
                    if (sampleRow > 0 && sampleRow < patch.rows()) {
                        var prevValue = patch.get(sampleRow, 0)[0]
                        for (x in 1 until patch.cols()) {
                            val currValue = patch.get(sampleRow, x)[0]
                            // Count transitions across threshold (ridge crossings)
                            if ((prevValue < threshold && currValue >= threshold) ||
                                (prevValue >= threshold && currValue < threshold)) {
                                ridgeCrossings++
                            }
                            prevValue = currValue
                        }
                    }
                    
                    // Normalize by patch width (frequency = crossings / width)
                    val frequency = if (patch.cols() > 0) {
                        ridgeCrossings.toFloat() / patch.cols()
                    } else {
                        0f
                    }
                    
                    frequencyFeatures.add(frequency.coerceIn(0f, 1f))
                    patch.release()
                }
            }
            
            grayMat.release()
            gray.release()
            
            frequencyFeatures.toFloatArray()
        } catch (e: Exception) {
            Log.e("FeatureExtractor", "Error extracting ridge frequency: ${e.message}", e)
            FloatArray(16) { 0f } // 4x4 = 16 features
        }
    }
    
    fun extractFeatures(bitmap: Bitmap, isFromGallery: Boolean = false): FingerprintFeatures {
        // Preprocess first
        // Note: Contactless images are already enhanced from CameraScreen
        // Gallery images need enhancement without crop
        val preprocessed = preprocessForMatching(bitmap, isFromGallery = isFromGallery)
        
        // Extract minutiae only (removed surrogate features to avoid problems)
        val minutiaeExtractor = MinutiaeExtractor()
        val minutiae = minutiaeExtractor.extractMinutiae(preprocessed)
        
        Log.d("FeatureExtractor", "Extracted ${minutiae.size} minutiae from ${if (isFromGallery) "gallery" else "contactless"} image (size: ${bitmap.width}x${bitmap.height})")
        
        // Return only minutiae features (no surrogate features)
        return FingerprintFeatures(
            minutiae = minutiae,
            orientationHistogram = FloatArray(0), // Not used - minutiae only
            textureVector = FloatArray(0) // Not used - minutiae only
        )
    }
}

data class FingerprintFeatures(
    val orientationHistogram: FloatArray = FloatArray(0),
    val textureVector: FloatArray = FloatArray(0),
    // Legacy fields (kept for compatibility, but we're using surrogate features)
    val minutiae: List<Minutia> = emptyList(),
    val orientation: Float = 0f,
    val core: android.graphics.Point? = null,
    val delta: android.graphics.Point? = null
)

data class Minutia(
    val x: Int,
    val y: Int,
    val type: MinutiaType,
    val angle: Float
)

enum class MinutiaType {
    RIDGE_ENDING,
    BIFURCATION
}
