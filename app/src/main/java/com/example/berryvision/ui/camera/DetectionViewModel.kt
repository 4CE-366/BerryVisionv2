package com.example.berryvision.ui.camera

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.berryvision.data.Detection
import com.example.berryvision.util.ConnectivityObserver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetectionViewModel : ViewModel() {

    private val _detections = MutableStateFlow<List<Detection>>(emptyList())
    val detections: StateFlow<List<Detection>> = _detections.asStateFlow()

    private val _connectivityStatus = MutableStateFlow(ConnectivityObserver.Status.Unavailable)
    val connectivityStatus: StateFlow<ConnectivityObserver.Status> = _connectivityStatus.asStateFlow()

    private val _capturedBitmap = MutableStateFlow<android.graphics.Bitmap?>(null)
    val capturedBitmap: StateFlow<android.graphics.Bitmap?> = _capturedBitmap.asStateFlow()

    fun updateDetections(newDetections: List<Detection>) {
        _detections.value = newDetections
    }

    fun updateConnectivityStatus(status: ConnectivityObserver.Status) {
        _connectivityStatus.value = status
    }

    fun updateCapturedBitmap(bitmap: android.graphics.Bitmap?) {
        _capturedBitmap.value = bitmap
    }
}
