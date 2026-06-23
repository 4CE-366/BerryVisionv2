package com.example.berryvision.data

import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface CloudApiService {
    @Multipart
    @POST("analyze")
    suspend fun analyzeImage(
        @Part image: MultipartBody.Part
    ): Response<AnalysisResponse>
}

data class AnalysisResponse(
    val detections: List<Detection>
)

data class Detection(
    val label: String,
    val confidence: Float,
    val box: List<Float> // Expected format: [x_min, y_min, x_max, y_max]
)
