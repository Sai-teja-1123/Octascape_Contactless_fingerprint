package com.contactless.fingerprint.core

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat

/**
 * Core image processing utilities shared across tracks.
 *
 * Centralizing Bitmap <-> Mat conversions here keeps the enhancement
 * and matching code cleaner and makes it easy to tweak formats later.
 */
object ImageProcessor {

    /**
     * Convert an Android [Bitmap] to an OpenCV [Mat].
     *
     * The returned Mat is in 8-bit 4-channel format (CV_8UC4),
     * which is a good starting point for most pipelines.
     */
    fun bitmapToMat(bitmap: Bitmap): Mat {
        val mat = Mat(bitmap.height, bitmap.width, CvType.CV_8UC4)
        Utils.bitmapToMat(bitmap, mat)
        return mat
    }

    /**
     * Convert an OpenCV [Mat] back to a [Bitmap].
     *
     * The Mat is expected to be 8-bit single-channel or 4-channel.
     * The output bitmap will always be ARGB_8888.
     */
    fun matToBitmap(mat: Mat): Bitmap {
        val result = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, result)
        return result
    }
}
