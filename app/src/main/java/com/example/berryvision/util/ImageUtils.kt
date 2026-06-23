package com.example.berryvision.util

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy

object ImageUtils {
    fun ImageProxy.toBitmap(): Bitmap {
        val bitmap = this.toBitmap() // This extension is available in recent CameraX versions (1.3+)
        
        // Handle rotation if needed
        val rotationDegrees = this.imageInfo.rotationDegrees
        return if (rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }
}
