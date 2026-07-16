package com.example.berryvision.ui.camera

import android.Manifest
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.ImageCaptureException
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.VideoCameraBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.berryvision.data.CloudApiService
import com.example.berryvision.ml.TFLiteDetector
import com.example.berryvision.ui.stats.DailyCountViewModel
import com.example.berryvision.util.ConnectivityObserver
import com.example.berryvision.util.scaleDown
import com.example.berryvision.util.toOrientedBitmap
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import androidx.core.content.ContextCompat

enum class AppMode {
    RealTime, Photo
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    detector: TFLiteDetector,
    cloudApiService: CloudApiService,
    connectivityObserver: ConnectivityObserver,
    viewModel: DetectionViewModel,
    dailyCountViewModel: DailyCountViewModel,
    initialMode: AppMode = AppMode.RealTime,
    onBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val detections by viewModel.detections.collectAsStateWithLifecycle()
    val connectivityStatus by viewModel.connectivityStatus.collectAsStateWithLifecycle()
    val capturedBitmap by viewModel.capturedBitmap.collectAsStateWithLifecycle()
    
    var currentMode by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initialMode) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    val analyzer = remember {
        StrawberryFrameAnalyzer(detector, cloudApiService, connectivityObserver) {
            viewModel.updateDetections(it)
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val bitmap = if (Build.VERSION.SDK_INT < 28) {
                MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            } else {
                val source = ImageDecoder.createSource(context.contentResolver, it)
                ImageDecoder.decodeBitmap(source).copy(Bitmap.Config.ARGB_8888, true)
            }
            val scaledBitmap = bitmap.scaleDown()
            viewModel.updateCapturedBitmap(scaledBitmap)
            scope.launch {
                analyzer.runInference(scaledBitmap, connectivityStatus, useCloud = true)
            }
        }
    }

    LaunchedEffect(Unit) {
        connectivityObserver.observe().collect { status ->
            viewModel.updateConnectivityStatus(status)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        if (capturedBitmap != null) "Análisis de Cosecha" else "BerryVision", 
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                },
                actions = {
                    if (currentMode == AppMode.Photo && capturedBitmap == null) {
                        IconButton(onClick = { photoPickerLauncher.launch("image/*") }) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = "Pick Photo",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f)
                )
            )
        },
        bottomBar = {
            if (capturedBitmap == null) {
                NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.VideoCameraBack, contentDescription = null) },
                    label = { Text("Tiempo Real") },
                    selected = currentMode == AppMode.RealTime,
                    onClick = { 
                        currentMode = AppMode.RealTime 
                        viewModel.updateCapturedBitmap(null)
                        viewModel.updateDetections(emptyList())
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.CameraAlt, contentDescription = null) },
                    label = { Text("Fotografía") },
                    selected = currentMode == AppMode.Photo,
                    onClick = { 
                        currentMode = AppMode.Photo 
                        viewModel.updateCapturedBitmap(null)
                        viewModel.updateDetections(emptyList())
                    }
                )
                }
            }
        }
    ) { innerPadding ->
        if (cameraPermissionState.status.isGranted) {
            Box(modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
            ) {
                if (capturedBitmap != null) {
                    AnalysisResultScreen(
                        bitmap = capturedBitmap!!,
                        detections = detections,
                        onNewCapture = {
                            viewModel.updateCapturedBitmap(null)
                            viewModel.updateDetections(emptyList())
                        },
                        onSaveCount = { count ->
                            dailyCountViewModel.addStrawberryCount(count)
                        }
                    )
                } else {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        detector = detector,
                        cloudApiService = cloudApiService,
                        connectivityObserver = connectivityObserver,
                        viewModel = viewModel,
                        imageCapture = imageCapture,
                        isRealTime = currentMode == AppMode.RealTime
                    )
                    
                    DetectionOverlay(
                        detections = detections,
                        modifier = Modifier.fillMaxSize()
                    )

                    if (currentMode == AppMode.Photo) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = 32.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            FloatingActionButton(
                                onClick = {
                                    imageCapture.takePicture(
                                        ContextCompat.getMainExecutor(context),
                                        object : ImageCapture.OnImageCapturedCallback() {
                                            override fun onCaptureSuccess(image: ImageProxy) {
                                                val bitmap = image.toOrientedBitmap().scaleDown()
                                                viewModel.updateCapturedBitmap(bitmap)
                                                scope.launch {
                                                    analyzer.runInference(bitmap, connectivityStatus)
                                                }
                                                image.close()
                                            }

                                            override fun onError(exception: ImageCaptureException) {
                                                exception.printStackTrace()
                                            }
                                        }
                                    )
                                },
                                containerColor = MaterialTheme.colorScheme.primary
                            ) {
                                Icon(Icons.Default.CameraAlt, contentDescription = "Take Photo")
                            }
                        }
                    }

                    StatusBadge(
                        status = connectivityStatus,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp)
                    )
                }
            }
        } else {
            PermissionRequestScreen(cameraPermissionState)
        }
    }
}

@Composable
fun StatusBadge(
    status: ConnectivityObserver.Status,
    modifier: Modifier = Modifier
) {
    val isOnline = status == ConnectivityObserver.Status.Available
    val backgroundColor = if (isOnline) Color(0xFF4CAF50) else Color(0xFFF44336)
    val text = if (isOnline) "Cloud Mode" else "Offline Mode"
    val icon = if (isOnline) Icons.Default.Cloud else Icons.Default.CloudOff

    Surface(
        modifier = modifier,
        color = backgroundColor.copy(alpha = 0.8f),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = text,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PermissionRequestScreen(permissionState: com.google.accompanist.permissions.PermissionState) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera Access Required",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "BerryVision needs the camera to detect strawberry ripeness in real-time.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = { permissionState.launchPermissionRequest() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Permission")
        }
    }
}
