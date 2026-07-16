package com.example.berryvision

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.berryvision.data.CloudApiService
import com.example.berryvision.ml.TFLiteDetector
import com.example.berryvision.ui.camera.DetectionViewModel
import com.example.berryvision.ui.camera.MainScreen
import com.example.berryvision.ui.gallery.GalleryScreen
import com.example.berryvision.ui.home.HomeScreen
import com.example.berryvision.ui.stats.DailyCountScreen
import com.example.berryvision.ui.stats.DailyCountViewModel
import com.example.berryvision.ui.theme.BerryvisionTheme
import com.example.berryvision.util.NetworkConnectivityObserver
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MainActivity : ComponentActivity() {
    
    private val detectionViewModel: DetectionViewModel by viewModels()
    private val dailyCountViewModel: DailyCountViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()

        // Initialize dependencies (In a real app, use Hilt or Koin)
        val connectivityObserver = NetworkConnectivityObserver(applicationContext)
        val tfliteDetector = TFLiteDetector(applicationContext)
        
        // Setup Retrofit with a placeholder URL
        val retrofit = Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8000/") // Connected to local FastAPI instance
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val cloudApiService = retrofit.create(CloudApiService::class.java)

        setContent {
            BerryvisionTheme {
                var currentScreen by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("home") }

                if (currentScreen == "home") {
                    HomeScreen(
                        onAnalyzeClick = { currentScreen = "camera_realtime" },
                        onGalleryClick = { currentScreen = "gallery" },
                        onStatsClick = { currentScreen = "stats" }
                    )
                } else if (currentScreen == "gallery") {
                    GalleryScreen(
                        onBack = { currentScreen = "home" },
                        onOpenCamera = { currentScreen = "camera_photo" }
                    )
                } else if (currentScreen == "stats") {
                    DailyCountScreen(
                        viewModel = dailyCountViewModel,
                        onBack = { currentScreen = "home" }
                    )
                } else {
                    MainScreen(
                        detector = tfliteDetector,
                        cloudApiService = cloudApiService,
                        connectivityObserver = connectivityObserver,
                        viewModel = detectionViewModel,
                        dailyCountViewModel = dailyCountViewModel,
                        initialMode = if (currentScreen == "camera_realtime") com.example.berryvision.ui.camera.AppMode.RealTime else com.example.berryvision.ui.camera.AppMode.Photo,
                        onBack = { currentScreen = "home" }
                    )
                }
            }
        }
    }
}
