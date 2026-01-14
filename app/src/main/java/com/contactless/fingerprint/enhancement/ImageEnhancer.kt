package com.contactless.fingerprint.enhancement

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.contactless.fingerprint.core.ImageProcessor
import com.contactless.fingerprint.camera.FingerDetector
import com.contactless.fingerprint.utils.Constants
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream

/**
 * Enhances finger images for fingerprint processing.
 *
 * Track B: Ridge-valley enhancement, noise reduction, contrast normalization.
 * The goal is not to be a perfect academic algorithm, but a robust,
 * fast pipeline that makes ridges clearer and more uniform for
 * both human inspection and later matching.
 */
class ImageEnhancer {

    /**
     * Full enhancement pipeline applied to the captured bitmap.
     *
     * Steps:
     * 1. Crop to finger box region (matching on-screen box exactly)
     * 2. Convert to grayscale
     * 3. Apply CLAHE (adaptive histogram equalization) for local contrast
     * 4. Light noise reduction (bilateral filter)
     * 5. Ridge emphasis via unsharp masking
     *
     * By cropping first, we focus all enhancement on the actual finger area,
     * resulting in much clearer ridge detail.
     */
    fun enhanceImage(bitmap: Bitmap, previewWidth: Int? = null, previewHeight: Int? = null): Bitmap {
        return try {
            // 1) Crop to match UI box exactly (using preview dimensions if available)
            val croppedBitmap = cropToFingerBox(bitmap, previewWidth, previewHeight)
            
            // 2) Convert to Mat
            val srcMat = ImageProcessor.bitmapToMat(croppedBitmap)

            // 3) Convert to grayscale
            val gray = Mat()
            Imgproc.cvtColor(srcMat, gray, Imgproc.COLOR_RGBA2GRAY)

            // 4) Contrast normalization using CLAHE
            //    Clip limit tuned so ridges pop without blowing out highlights.
            val clahe = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
            val claheMat = Mat()
            clahe.apply(gray, claheMat)

            // 5) Lighter noise reduction to preserve fine ridge details
            val denoised = Mat()
            Imgproc.bilateralFilter(claheMat, denoised, /*d=*/5, /*sigmaColor=*/20.0, /*sigmaSpace=*/20.0)

            // 6) Ridge emphasis via unsharp masking (reduced to avoid layering/ghosting)
            val blurred = Mat()
            Imgproc.GaussianBlur(denoised, blurred, Size(0.0, 0.0), 0.6)
            val sharpened = Mat()
            // Moderate sharpening to avoid layering artifacts while keeping ridges crisp
            Core.addWeighted(denoised, 1.6, blurred, -0.6, 0.0, sharpened)

            // 7) Convert back to 4-channel so UI can display it easily
            val resultRgba = Mat()
            Imgproc.cvtColor(sharpened, resultRgba, Imgproc.COLOR_GRAY2RGBA)

            // 8) Convert back to Bitmap
            ImageProcessor.matToBitmap(resultRgba)
        } catch (e: Exception) {
            Log.e("ImageEnhancer", "Error enhancing image: ${e.message}", e)
            // Fall back to original bitmap if anything goes wrong
            bitmap
        }
    }

    /**
     * Crops the image to match the UI box exactly.
     * 
     * The UI box is 300dp x 220dp, centered on the preview.
     * We calculate the exact box position in the captured image by:
     * 1. If preview dimensions are provided, scale the box from preview to captured image
     * 2. Otherwise, use a percentage-based approach with perfect centering
     */
    private fun cropToFingerBox(bitmap: Bitmap, previewWidth: Int?, previewHeight: Int?): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        
        // UI box dimensions: 300dp x 220dp (aspect ratio ≈ 1.36:1)
        val boxAspectRatio = 300f / 220f
        
        val cropWidth: Int
        val cropHeight: Int
        val startX: Int
        val startY: Int
        
        if (previewWidth != null && previewHeight != null && previewWidth > 0 && previewHeight > 0) {
            // Calculate exact box position based on preview dimensions
            // Box is centered, so calculate its pixel position in preview
            val scaleX = width.toFloat() / previewWidth
            val scaleY = height.toFloat() / previewHeight
            
            // Box is 300dp x 220dp, centered
            // Approximate: box is ~35-40% of preview width, ~25-30% of preview height
            // Use the larger scale to ensure we capture the full box
            val scale = if (scaleX > scaleY) scaleX else scaleY
            
            // Calculate box size in captured image pixels
            val boxWidthInPreview = previewWidth * 0.4f // Box is ~40% of preview width
            val boxHeightInPreview = boxWidthInPreview / boxAspectRatio
            
            cropWidth = (boxWidthInPreview * scale).toInt()
            cropHeight = (boxHeightInPreview * scale).toInt()
            
            // Perfect center
            startX = (width / 2 - cropWidth / 2).coerceIn(0, width - cropWidth)
            startY = (height / 2 - cropHeight / 2).coerceIn(0, height - cropHeight)
        } else {
            // Fallback: use percentage with perfect centering
            // Increased to 45% to better match box edges
            cropWidth = (width * 0.45f).toInt()
            cropHeight = (cropWidth / boxAspectRatio).toInt()
            
            // Perfect center (both X and Y)
            startX = (width / 2 - cropWidth / 2).coerceIn(0, width - cropWidth)
            startY = (height / 2 - cropHeight / 2).coerceIn(0, height - cropHeight)
        }
        
        // Ensure crop region is within bounds
        val safeStartX = startX.coerceIn(0, width - 1)
        val safeStartY = startY.coerceIn(0, height - 1)
        val safeCropWidth = cropWidth.coerceIn(1, width - safeStartX)
        val safeCropHeight = cropHeight.coerceIn(1, height - safeStartY)
        
        return try {
            Bitmap.createBitmap(bitmap, safeStartX, safeStartY, safeCropWidth, safeCropHeight)
        } catch (e: Exception) {
            Log.e("ImageEnhancer", "Error cropping image: ${e.message}", e)
            bitmap // Return original if crop fails
        }
    }

    /**
     * Exports enhanced image in ISO-like format (500 ppi equivalent, standard dimensions).
     * 
     * ISO/IEC 19794-4 standard for fingerprints typically requires:
     * - 500 ppi (pixels per inch) resolution
     * - Standard dimensions (500x500 pixels is common for fingerprint images)
     * - Grayscale format
     * - PNG format (lossless)
     * 
     * This function resizes the enhanced image to ISO-like dimensions while
     * maintaining aspect ratio, ensuring it's ready for downstream matching systems.
     * 
     * @param enhancedBitmap The enhanced fingerprint image
     * @param context Android context for file operations
     * @return File path to the exported ISO-like image, or null if export fails
     */
    fun exportToIsoFormat(enhancedBitmap: Bitmap, context: Context): String? {
        return try {
            // Convert to grayscale if not already (ISO format is grayscale)
            val grayscaleBitmap = if (enhancedBitmap.config == Bitmap.Config.ALPHA_8) {
                enhancedBitmap
            } else {
                // Convert to grayscale
                val gray = Mat()
                val srcMat = ImageProcessor.bitmapToMat(enhancedBitmap)
                Imgproc.cvtColor(srcMat, gray, Imgproc.COLOR_RGBA2GRAY)
                ImageProcessor.matToBitmap(gray)
            }
            
            // Resize to ISO-like dimensions (500x500 pixels, maintaining aspect ratio)
            // ISO standard is 500 ppi, and 500x500 is a common fingerprint image size
            val targetSize = Constants.ISO_RESOLUTION_WIDTH
            val currentWidth = grayscaleBitmap.width
            val currentHeight = grayscaleBitmap.height
            
            // Calculate new dimensions maintaining aspect ratio
            val aspectRatio = currentWidth.toFloat() / currentHeight.toFloat()
            val newWidth: Int
            val newHeight: Int
            
            if (currentWidth > currentHeight) {
                newWidth = targetSize
                newHeight = (targetSize / aspectRatio).toInt()
            } else {
                newHeight = targetSize
                newWidth = (targetSize * aspectRatio).toInt()
            }
            
            // Resize to ISO-like dimensions
            val isoBitmap = Bitmap.createScaledBitmap(
                grayscaleBitmap,
                newWidth,
                newHeight,
                true
            )
            
            val timestamp = System.currentTimeMillis()
            val fileName = "fingerprint_iso_${timestamp}.png"

            // Prefer saving to public Pictures/ContactlessFingerprint so it shows
            // up in Gallery / file managers without needing special access.
            val savedPath: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(
                            MediaStore.Images.Media.RELATIVE_PATH,
                            Environment.DIRECTORY_PICTURES + "/ContactlessFingerprint"
                        )
                    }
                    val resolver = context.contentResolver
                    val uri = resolver.insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        values
                    )
                    if (uri != null) {
                        resolver.openOutputStream(uri)?.use { out ->
                            isoBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                        }
                        Environment.DIRECTORY_PICTURES + "/ContactlessFingerprint/$fileName"
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    Log.e("ImageEnhancer", "MediaStore save failed: ${e.message}", e)
                    null
                }
            } else {
                // Legacy fallback: app-specific external files dir
                val outputDir = File(context.getExternalFilesDir(null), "iso_exports")
                if (!outputDir.exists()) {
                    outputDir.mkdirs()
                }
                val isoFile = File(outputDir, fileName)
                FileOutputStream(isoFile).use { out ->
                    isoBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                isoFile.absolutePath
            }

            // Clean up
            if (grayscaleBitmap != enhancedBitmap) {
                grayscaleBitmap.recycle()
            }
            isoBitmap.recycle()

            if (savedPath != null) {
                Log.d("ImageEnhancer", "ISO format export saved: $savedPath")
            } else {
                Log.e("ImageEnhancer", "ISO format export failed: savedPath is null")
            }
            savedPath
        } catch (e: Exception) {
            Log.e("ImageEnhancer", "Error exporting to ISO format: ${e.message}", e)
            null
        }
    }

}
