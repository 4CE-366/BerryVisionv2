package com.example.berryvision.ui.camera

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.berryvision.data.Detection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisResultScreen(
    bitmap: Bitmap,
    detections: List<Detection>,
    onNewCapture: () -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val sheetState = rememberModalBottomSheetState()
    var showBottomSheet by remember { mutableStateOf(true) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Resultados del Análisis") },
                actions = {
                    IconButton(onClick = onNewCapture) {
                        Icon(Icons.Default.Close, contentDescription = "Cerrar")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale *= zoom
                        offset += pan
                    }
                }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = maxOf(1f, minOf(5f, scale)),
                        scaleY = maxOf(1f, minOf(5f, scale)),
                        translationX = offset.x,
                        translationY = offset.y
                    )
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Overlay detections on the static image
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // We need to match the Fit contentScale to correctly place boxes
                    val imageWidth = bitmap.width.toFloat()
                    val imageHeight = bitmap.height.toFloat()
                    val canvasWidth = size.width
                    val canvasHeight = size.height

                    val scaleFactor = minOf(canvasWidth / imageWidth, canvasHeight / imageHeight)
                    val dx = (canvasWidth - imageWidth * scaleFactor) / 2
                    val dy = (canvasHeight - imageHeight * scaleFactor) / 2

                    detections.forEach { detection ->
                        val left = detection.box[0] * imageWidth * scaleFactor + dx
                        val top = detection.box[1] * imageHeight * scaleFactor + dy
                        val right = detection.box[2] * imageWidth * scaleFactor + dx
                        val bottom = detection.box[3] * imageHeight * scaleFactor + dy

                        drawRect(
                            color = when (detection.label.lowercase()) {
                                "madura" -> Color.Red
                                "verde" -> Color.Green
                                else -> Color.Yellow
                            },
                            topLeft = Offset(left, top),
                            size = Size(right - left, bottom - top),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }

            if (showBottomSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showBottomSheet = false },
                    sheetState = sheetState
                ) {
                    AnalysisSummary(detections)
                }
            }
        }
    }
}

@Composable
fun AnalysisSummary(detections: List<Detection>) {
    val total = detections.size
    val avgConfidence = if (detections.isNotEmpty()) {
        detections.map { it.confidence }.average()
    } else 0.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .padding(bottom = 32.dp)
    ) {
        Text(
            text = "Resumen de Detección",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            InfoCard("Total Fresas", total.toString())
            InfoCard("Confianza Media", "${(avgConfidence * 100).toInt()}%")
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        val counts = detections.groupingBy { it.label }.eachCount()
        counts.forEach { (label, count) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(label, fontWeight = FontWeight.Medium)
                Text(count.toString(), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun InfoCard(label: String, value: String) {
    Card(
        modifier = Modifier
            .width(150.dp)
            .padding(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = label, style = MaterialTheme.typography.labelMedium)
            Text(text = value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}
