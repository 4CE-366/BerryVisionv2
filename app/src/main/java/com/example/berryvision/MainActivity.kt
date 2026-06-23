package com.example.berryvision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.remember
import com.example.berryvision.data.CloudApiService
import com.example.berryvision.ml.TFLiteDetector
import com.example.berryvision.ui.camera.DetectionViewModel
import com.example.berryvision.ui.camera.MainScreen
import com.example.berryvision.ui.theme.BerryvisionTheme
import com.example.berryvision.util.NetworkConnectivityObserver
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {
    
    private val detectionViewModel: DetectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()

        // Initialize dependencies (In a real app, use Hilt or Koin)
        val connectivityObserver = NetworkConnectivityObserver(applicationContext)
        val tfliteDetector = TFLiteDetector(applicationContext)
        
        // Setup Retrofit with a placeholder URL
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.berryvision.example.com/") // Placeholder
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val cloudApiService = retrofit.create(CloudApiService::class.java)

        setContent {
            BerryvisionTheme {
                MainScreen(
                    detector = tfliteDetector,
                    cloudApiService = cloudApiService,
                    connectivityObserver = connectivityObserver,
                    viewModel = detectionViewModel
                )
            }
        }
    }
}
