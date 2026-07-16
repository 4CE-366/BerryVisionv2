package com.example.berryvision.util

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageProxy
import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun ImageProxy.toOrientedBitmap(): Bitmap {
    val bitmap = this.toBitmap() // Extension available in CameraX 1.3+
    
    val rotationDegrees = this.imageInfo.rotationDegrees
    return if (rotationDegrees != 0) {
        val matrix = Matrix()
        matrix.postRotate(rotationDegrees.toFloat())
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    } else {
        bitmap
    }
}

fun Bitmap.scaleDown(maxDimension: Int = 1080): Bitmap {
    val largestDimension = maxOf(width, height)
    if (largestDimension <= maxDimension) return this
    val scale = maxDimension.toFloat() / largestDimension
    return Bitmap.createScaledBitmap(this, (width * scale).toInt(), (height * scale).toInt(), true)
}

fun saveBitmapToInternalStorage(context: Context, bitmap: Bitmap, count: Int): String? {
    val directory = context.getDir("berryvision_photos", Context.MODE_PRIVATE)
    if (!directory.exists()) {
        directory.mkdirs()
    }
    
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val fileName = "IMG_${timestamp}_C${count}.jpg"
    val file = File(directory, fileName)
    
    return try {
        val outputStream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        outputStream.flush()
        outputStream.close()
        file.absolutePath
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
