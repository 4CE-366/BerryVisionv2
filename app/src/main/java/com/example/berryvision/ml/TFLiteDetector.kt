package com.example.berryvision.ml

import android.content.Context
import android.graphics.Bitmap
import com.example.berryvision.data.Detection
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class TFLiteDetector(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    private val modelInputSize = 640
    private val confidenceThreshold = 0.5f
    private val iouThreshold = 0.5f

    init {
        setupInterpreter()
    }

    private fun setupInterpreter() {
        try {
            val options = Interpreter.Options()
            try {
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
            } catch (t: Throwable) {
                gpuDelegate?.close()
                gpuDelegate = null
            }

            val model = try {
                loadModelFile("best_float16.tflite")
            } catch (t: Throwable) {
                null
            }

            if (model != null) {
                interpreter = Interpreter(model, options)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun loadModelFile(modelPath: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelPath)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun detect(bitmap: Bitmap): List<Detection> {
        val interp = interpreter ?: return emptyList()

        // 1. Preprocesamiento: Letterboxing para mantener la relación de aspecto
        val scale = minOf(modelInputSize.toFloat() / bitmap.width, modelInputSize.toFloat() / bitmap.height)
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        val paddedBitmap = Bitmap.createBitmap(modelInputSize, modelInputSize, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(paddedBitmap)
        // Color gris estándar para padding en YOLO
        canvas.drawColor(android.graphics.Color.rgb(114, 114, 114))
        
        val padX = (modelInputSize - newWidth) / 2f
        val padY = (modelInputSize - newHeight) / 2f
        canvas.drawBitmap(resizedBitmap, padX, padY, null)

        val inputBuffer = ByteBuffer.allocateDirect(1 * modelInputSize * modelInputSize * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(modelInputSize * modelInputSize)
        paddedBitmap.getPixels(intValues, 0, paddedBitmap.width, 0, 0, paddedBitmap.width, paddedBitmap.height)

        for (pixelValue in intValues) {
            inputBuffer.putFloat(((pixelValue shr 16 and 0xFF) / 255f))
            inputBuffer.putFloat(((pixelValue shr 8 and 0xFF) / 255f))
            inputBuffer.putFloat(((pixelValue and 0xFF) / 255f))
        }

        // 2. Preparar el arreglo de salida [1, 7, 8400] para tus 3 clases
        val outputArray = Array(1) { Array(7) { FloatArray(8400) } }

        // 3. Ejecutar Inferencia
        interp.run(inputBuffer, outputArray)

        // 4. Postprocesamiento
        return parseYOLOOutput(outputArray, bitmap.width.toFloat(), bitmap.height.toFloat(), scale, padX, padY)
    }

    private fun parseYOLOOutput(
        outputArray: Array<Array<FloatArray>>, 
        origWidth: Float, 
        origHeight: Float, 
        scale: Float, 
        padX: Float, 
        padY: Float
    ): List<Detection> {
        val candidateDetections = mutableListOf<Detection>()

        // Etiquetas extraídas del archivo metadata.yaml
        val numClasses = 3
        val labels = listOf("Madura", "Semimadura", "Inmadura")

        for (i in 0 until 8400) {
            var maxClassScore = 0f
            var classId = -1

            for (c in 0 until numClasses) {
                val score = outputArray[0][4 + c][i]
                if (score > maxClassScore) {
                    maxClassScore = score
                    classId = c
                }
            }

            if (maxClassScore > confidenceThreshold) {
                var cx = outputArray[0][0][i]
                var cy = outputArray[0][1][i]
                var w = outputArray[0][2][i]
                var h = outputArray[0][3][i]

                // Si el modelo saca normalizado (0-1), lo escalamos a 640x640
                if (cx <= 1.0f && cy <= 1.0f && w <= 1.0f && h <= 1.0f) {
                    cx *= modelInputSize
                    cy *= modelInputSize
                    w *= modelInputSize
                    h *= modelInputSize
                }

                // Remove padding
                val absCx = cx - padX
                val absCy = cy - padY
                
                // Unscale to original image dimensions
                val origCx = absCx / scale
                val origCy = absCy / scale
                val origW = w / scale
                val origH = h / scale

                // Convert to [0,1] normalized coordinates relative to the ORIGINAL image
                val xMin = (origCx - origW / 2f) / origWidth
                val yMin = (origCy - origH / 2f) / origHeight
                val xMax = (origCx + origW / 2f) / origWidth
                val yMax = (origCy + origH / 2f) / origHeight

                val labelName = if (classId in labels.indices) labels[classId] else "Desconocido"

                candidateDetections.add(
                    Detection(
                        label = labelName,
                        confidence = maxClassScore,
                        box = listOf(xMin, yMin, xMax, yMax)
                    )
                )
            }
        }

        return applyNMS(candidateDetections)
    }

    private fun applyNMS(detections: List<Detection>): List<Detection> {
        val sortedDetections = detections.sortedByDescending { it.confidence }.toMutableList()
        val result = mutableListOf<Detection>()

        while (sortedDetections.isNotEmpty()) {
            val best = sortedDetections.removeAt(0)
            result.add(best)
            val iterator = sortedDetections.iterator()
            while (iterator.hasNext()) {
                val current = iterator.next()
                if (calculateIoU(best.box, current.box) > iouThreshold) {
                    iterator.remove()
                }
            }
        }
        return result
    }

    private fun calculateIoU(box1: List<Float>, box2: List<Float>): Float {
        val x1 = maxOf(box1[0], box2[0])
        val y1 = maxOf(box1[1], box2[1])
        val x2 = minOf(box1[2], box2[2])
        val y2 = minOf(box1[3], box2[3])

        val intersectionArea = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val box1Area = (box1[2] - box1[0]) * (box1[3] - box1[1])
        val box2Area = (box2[2] - box2[0]) * (box2[3] - box2[1])

        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
        interpreter = null
        gpuDelegate = null
    }
}