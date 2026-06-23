package com.example.berryvision.ui.camera

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.berryvision.data.CloudApiService
import com.example.berryvision.ml.TFLiteDetector
import com.example.berryvision.util.ConnectivityObserver
import com.example.berryvision.util.ImageUtils.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

class StrawberryFrameAnalyzer(
    private val detector: TFLiteDetector,
    private val cloudApiService: CloudApiService,
    private val connectivityObserver: ConnectivityObserver,
    private val onDetectionsUpdated: (List<com.example.berryvision.data.Detection>) -> Unit
) : ImageAnalysis.Analyzer {

    private val scope = CoroutineScope(Dispatchers.Default)

    override fun analyze(image: ImageProxy) {
        scope.launch {
            val status = connectivityObserver.observe().first()
            val bitmap = image.toBitmap()
            
            runInference(bitmap, status)
            
            image.close()
        }
    }

    suspend fun runInference(bitmap: Bitmap, status: ConnectivityObserver.Status) {
        if (status == ConnectivityObserver.Status.Available) {
            analyzeWithCloud(bitmap)
        } else {
            val detections = detector.detect(bitmap)
            withContext(Dispatchers.Main) {
                onDetectionsUpdated(detections)
            }
        }
    }

    private suspend fun analyzeWithCloud(bitmap: Bitmap) {
        try {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream)
            val byteArray = stream.toByteArray()
            
            val requestFile = byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull())
            val body = MultipartBody.Part.createFormData("image", "frame.jpg", requestFile)
            
            val response = cloudApiService.analyzeImage(body)
            if (response.isSuccessful) {
                response.body()?.let { analysisResponse ->
                    withContext(Dispatchers.Main) {
                        onDetectionsUpdated(analysisResponse.detections)
                    }
                }
            } else {
                // Fallback to local if cloud fails
                val detections = detector.detect(bitmap)
                withContext(Dispatchers.Main) {
                    onDetectionsUpdated(detections)
                }
            }
        } catch (e: Exception) {
            // Fallback to local on error
            val detections = detector.detect(bitmap)
            withContext(Dispatchers.Main) {
                onDetectionsUpdated(detections)
            }
        }
    }
}
