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

        // 1. Preprocesamiento: Redimensionar y Normalizar
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, modelInputSize, modelInputSize, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * modelInputSize * modelInputSize * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        val intValues = IntArray(modelInputSize * modelInputSize)
        resizedBitmap.getPixels(intValues, 0, resizedBitmap.width, 0, 0, resizedBitmap.width, resizedBitmap.height)

        for (pixelValue in intValues) {
            inputBuffer.putFloat(((pixelValue shr 16 and 0xFF) / 255f))
            inputBuffer.putFloat(((pixelValue shr 8 and 0xFF) / 255f))
            inputBuffer.putFloat(((pixelValue and 0xFF) / 255f))
        }

        // 2. Preparar el arreglo de salida [1, 7, 8400] para tus 3 clases
        // 4 coordenadas + 3 clases de madurez = 7
        val outputArray = Array(1) { Array(7) { FloatArray(8400) } }

        // 3. Ejecutar Inferencia directamente sobre el arreglo (evita crasheos por bytes)
        interp.run(inputBuffer, outputArray)

        // 4. Postprocesamiento
        return parseYOLOOutput(outputArray)
    }

    private fun parseYOLOOutput(outputArray: Array<Array<FloatArray>>): List<Detection> {
        val candidateDetections = mutableListOf<Detection>()

        // Ajusta estas etiquetas según cómo etiquetaste tu dataset de fresas
        val numClasses = 3
        val labels = listOf("Verde", "Madura", "Pasada")

        for (i in 0 until 8400) {
            var maxClassScore = 0f
            var classId = -1

            // Iterar sobre las 3 clases para encontrar la de mayor probabilidad en este anchor
            for (c in 0 until numClasses) {
                val score = outputArray[0][4 + c][i]
                if (score > maxClassScore) {
                    maxClassScore = score
                    classId = c
                }
            }

            if (maxClassScore > confidenceThreshold) {
                // Sintaxis correcta para extraer de un arreglo 3D
                val cx = outputArray[0][0][i]
                val cy = outputArray[0][1][i]
                val w = outputArray[0][2][i]
                val h = outputArray[0][3][i]

                // Convertir centro [cx, cy, w, h] a esquinas [xMin, yMin, xMax, yMax]
                val xMin = (cx - w / 2f) / modelInputSize
                val yMin = (cy - h / 2f) / modelInputSize
                val xMax = (cx + w / 2f) / modelInputSize
                val yMax = (cy + h / 2f) / modelInputSize

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