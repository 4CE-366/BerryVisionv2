package com.example.berryvision.data

import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import retrofit2.Response
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface CloudApiService {
    @Multipart
    @POST("detect")
    suspend fun analyzeImage(
        @Part file: MultipartBody.Part
    ): Response<AnalysisResponse>
}

data class AnalysisResponse(
    val detections: List<Detection>
)

data class Detection(
    @SerializedName("class") val label: String,
    val confidence: Float,
    @SerializedName("bbox") val box: List<Float> // Expected format: [x_min, y_min, x_max, y_max]
)
