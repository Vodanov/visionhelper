package com.kzv.visionhelper

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

data class BoundingBox(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val cnf: Float,
    val cls: Int,
    val clsName: String
)
class Detector(
    private val context: Context,
    private val detectorListener: DetectorListener,
    private var modelPath: String,
    private val labels: List<String>,
    private val useGpu: Boolean = false
){
    private var interpreter: Interpreter
    private var tensorWidth = 0
    private var tensorHeight = 0
    private var numChannel = 0
    private var numElements = 0

    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()

    init {
        val options = if (useGpu) {
            val compatList = CompatibilityList()
            Interpreter.Options().apply {
                if (compatList.isDelegateSupportedOnThisDevice) {
                    val delegateOptions = compatList.bestOptionsForThisDevice
                    addDelegate(GpuDelegate(delegateOptions))
                } else {
                    setNumThreads(4)
                }
            }
        } else {
            Interpreter.Options().apply {
                setNumThreads(4)
            }
        }

        val model = FileUtil.loadMappedFile(context, modelPath)
        interpreter = Interpreter(model, Interpreter.Options().apply {
            setNumThreads(4)
        })

        val inputShape = interpreter.getInputTensor(0)?.shape()
        val outputShape = interpreter.getOutputTensor(0)?.shape()

        if (inputShape != null) {
            tensorWidth = inputShape[1]
            tensorHeight = inputShape[2]

            if (inputShape[1] == 3) {
                tensorWidth = inputShape[2]
                tensorHeight = inputShape[3]
            }
        }

        if (outputShape != null) {
            numElements = outputShape[1]
            numChannel = outputShape[2]
        }
    }
    fun reloadModel(newModelPath: String, useGpu: Boolean = false) {
        interpreter.close()

        val options = if (useGpu) {
            val compatList = CompatibilityList()
            Interpreter.Options().apply {
                if (compatList.isDelegateSupportedOnThisDevice) {
                    val delegateOptions = compatList.bestOptionsForThisDevice
                    addDelegate(GpuDelegate(delegateOptions))
                } else {
                    setNumThreads(4)
                }
            }
        } else {
            Interpreter.Options().apply {
                setNumThreads(4)
            }
        }

        val model = FileUtil.loadMappedFile(context, newModelPath)
        interpreter = Interpreter(model, options)
    }

    fun restart(isGpu: Boolean) {
        interpreter.close()

        val options = if (isGpu) {
            val compatList = CompatibilityList()
            Interpreter.Options().apply{
                if(compatList.isDelegateSupportedOnThisDevice){
                    val delegateOptions = compatList.bestOptionsForThisDevice
                    this.addDelegate(GpuDelegate(delegateOptions))
                } else {
                    this.setNumThreads(4)
                }
            }
        } else {
            Interpreter.Options().apply{
                this.setNumThreads(4)
            }
        }

        val model = FileUtil.loadMappedFile(context, modelPath)
        interpreter = Interpreter(model, options)
    }

    fun close() {
        interpreter.close()
    }
    fun tensor_empty(): Boolean {
        return tensorHeight != 0 && tensorWidth != 0 && numChannel != 0 && numElements != 0
    }
    fun detect(frame: Bitmap) {
        if (tensor_empty()) return
        var inferenceTime = SystemClock.uptimeMillis()
        val resizedBitmap = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false)

        val tensorImage = TensorImage(INPUT_IMAGE_TYPE)
        tensorImage.load(resizedBitmap)
        val processedImage = imageProcessor.process(tensorImage)
        val imageBuffer = processedImage.buffer

        val output = TensorBuffer.createFixedSize(intArrayOf(1, numChannel, numElements), OUTPUT_IMAGE_TYPE)
        interpreter.run(imageBuffer, output.buffer)

        val bestBoxes = bestBox(output.floatArray)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime

        if (bestBoxes.isEmpty()) {
            detectorListener.onEmptyDetect()
            return
        }

        detectorListener.onDetect(bestBoxes, inferenceTime)
    }

    private fun bestBox(array: FloatArray) : List<BoundingBox> {
        val boundingBoxes = mutableListOf<BoundingBox>()
        for (r in 0 until numElements) {
            val cnf = array[r * numChannel + 4]
            if (cnf > CONFIDENCE_THRESHOLD) {
                val x1 = array[r * numChannel]
                val y1 = array[r * numChannel + 1]
                val x2 = array[r * numChannel + 2]
                val y2 = array[r * numChannel + 3]
                val cls = array[r * numChannel + 5].toInt()
                val clsName = labels[cls]
                boundingBoxes.add(
                    BoundingBox(
                        x1 = x1, y1 = y1, x2 = x2, y2 = y2,
                        cnf = cnf, cls = cls, clsName = clsName
                    )
                )
            }
        }
        return boundingBoxes
    }

    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
    }

    companion object {
        private const val INPUT_MEAN = 0f
        private const val INPUT_STANDARD_DEVIATION = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.3F
    }
}