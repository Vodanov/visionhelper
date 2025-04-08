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
import androidx.appcompat.app.AppCompatDelegate
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.kzv.visionhelper.databinding.ActivityMainBinding
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    var DEBUG_MODE = true

    private var lastFrameAnalyzedTime = 0L
    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var tfliteHelper: Detector
    private lateinit var overlayView: OverlayView
    private lateinit var feedbackManager: FeedbackManager
    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private val prefs by lazy { getSharedPreferences("vision_settings", MODE_PRIVATE) }

    private val minAnalysisIntervalMs: Long
        get() {
            val fps = prefs.getInt("fps_limit", 2).coerceAtLeast(1)
            return 1000L / fps
        }




    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

        val prefs = getSharedPreferences("vision_settings", MODE_PRIVATE)

        // Set default language once on first boot
        if (!prefs.contains("language")) {
            val systemLang = Locale.getDefault().language
            val russianFamily = setOf("ru", "uk", "be", "kk", "uz", "ky", "tg", "tk", "az", "hy", "mo")
            val defaultLang = if (systemLang in russianFamily) "ru" else "en"
            prefs.edit().putString("language", defaultLang).apply()
        }

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        overlayView = viewBinding.overlay
        setContentView(viewBinding.root)

        val modelType = getSharedPreferences("vision_settings", MODE_PRIVATE)
            .getString("model_type", "float16")
        val modelName = if (modelType == "float32") "modelHQ.tflite" else "model.tflite"

        feedbackManager = FeedbackManager(this)

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            activityResultLauncher.launch(REQUIRED_PERMISSIONS)
        }
        val settingsLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val shouldReload = result.data?.getBooleanExtra("reload_model", false) ?: false
                if (shouldReload) {
                    reloadDetector()
                }
            }
        }
        viewBinding.settingsButton.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            settingsLauncher.launch(intent)
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
            },
            modelName
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

    private fun reloadDetector() {
        val prefs = getSharedPreferences("vision_settings", MODE_PRIVATE)
        val modelType = prefs.getString("model_type", "float16")
        val modelName = if (modelType == "float32") "modelHQ.tflite" else "model.tflite"

        tfliteHelper.close()

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
            },
            modelName
        )
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