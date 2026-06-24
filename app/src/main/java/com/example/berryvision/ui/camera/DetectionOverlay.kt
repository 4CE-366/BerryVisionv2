package com.example.berryvision.ui.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.berryvision.data.Detection

@OptIn(ExperimentalTextApi::class)
@Composable
fun DetectionOverlay(
    detections: List<Detection>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val boxColor = Color.Red
    val labelStyle = TextStyle(
        color = Color.White,
        fontSize = 14.sp,
        background = Color.Red.copy(alpha = 0.7f)
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        detections.forEach { detection ->
            // Format: [x_min, y_min, x_max, y_max]
            val isAbsolute = detection.box.any { it > 1.0f }
            // In live camera, absolute coords would be relative to camera resolution, which is tricky to map without knowing it.
            // But TFLite now guarantees normalized [0,1]. We just handle the case if API is somehow used here.
            val box = if (isAbsolute) {
                // Approximate normalization assuming they might be scaled to some standard like 640
                listOf(detection.box[0]/640f, detection.box[1]/640f, detection.box[2]/640f, detection.box[3]/640f)
            } else {
                detection.box
            }

            val left = box[0] * width
            val top = box[1] * height
            val right = box[2] * width
            val bottom = box[3] * height

            drawRect(
                color = boxColor,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                style = Stroke(width = 3.dp.toPx())
            )

            val labelText = "${detection.label} ${(detection.confidence * 100).toInt()}%"
            val textLayoutResult = textMeasurer.measure(labelText, labelStyle)
            
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(left, top - textLayoutResult.size.height)
            )
        }
    }
}
