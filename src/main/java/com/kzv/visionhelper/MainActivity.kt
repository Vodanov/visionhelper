package com.kzv.visionhelper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.kzv.visionhelper.databinding.ActivityMainBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    var DEBUG_MODE = true

    private var lastFrameAnalyzedTime = 0L
    private val minAnalysisIntervalMs = 500L // ~3 FPS max

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var tfliteHelper: Detector
    private lateinit var overlayView: OverlayView
    private lateinit var feedbackManager: FeedbackManager
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val isFrontCamera = false // Set true if you switch to front camera later

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewBinding = ActivityMainBinding.inflate(layoutInflater)

        // View
        overlayView = viewBinding.overlay
        setContentView(viewBinding.root)

        feedbackManager = FeedbackManager(this)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            activityResultLauncher.launch(REQUIRED_PERMISSIONS)
        }

        viewBinding.settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        tfliteHelper = Detector(
            applicationContext,
            object : Detector.DetectorListener {
                override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
                    if (DEBUG_MODE) {
                        Log.d("Detector", "Detected ${boundingBoxes.size} objects in ${inferenceTime}ms")
                        for (box in boundingBoxes) {
                            Log.d("Detector", "Label: ${box.clsName}, Confidence: ${box.cnf}")
                        }
                    }

                    if (boundingBoxes.isNotEmpty()) {
                        feedbackManager.playComboFeedback(boundingBoxes.map { it.clsName })
                    }

                    runOnUiThread {
                        overlayView.setResults(boundingBoxes)
                    }
                }

                override fun onEmptyDetect() {
                    overlayView.setResults(emptyList())
                    if (DEBUG_MODE) Log.d("Detector", "No objects detected.")
                }
            }
        )
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: throw IllegalStateException("Camera initialization failed.")

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
            .build()

        val rotation = viewBinding.viewFinder.display.rotation

        preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setTargetRotation(rotation)
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_4_3)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastFrameAnalyzedTime < minAnalysisIntervalMs) {
                    imageProxy.close()
                    return@setAnalyzer
                }
                lastFrameAnalyzedTime = currentTime
            val bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
            bitmapBuffer.copyPixelsFromBuffer(imageProxy.planes[0].buffer)

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                if (isFrontCamera) {
                    postScale(-1f, 1f)
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0,
                bitmapBuffer.width, bitmapBuffer.height,
                matrix, true
            )

            tfliteHelper.detect(rotatedBitmap)
            imageProxy.close()
        }

        try {
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(
                this, cameraSelector, preview, imageAnalyzer
            )
            preview?.setSurfaceProvider(viewBinding.viewFinder.surfaceProvider)
        } catch (e: Exception) {
            Log.e("MainActivity", "Use case binding failed", e)
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private val REQUIRED_PERMISSIONS = mutableListOf(
            Manifest.permission.CAMERA,
            Manifest.permission.VIBRATE
        ).apply {
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    override fun onResume() {
        super.onResume()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private val activityResultLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val permissionGranted = REQUIRED_PERMISSIONS.all {
                permissions[it] == true
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext, "Permission request denied", Toast.LENGTH_SHORT).show()
            } else {
                startCamera()
            }
        }
}