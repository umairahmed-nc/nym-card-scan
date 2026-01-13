package com.nymcard.cardsscan.compose

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nymcard.cardsscan.base.ScanBaseActivity
import com.nymcard.cardsscan.listener.OnScanListener
import com.nymcard.cardsscan.ml.MachineLearningThread
import com.nymcard.cardsscan.models.DetectedBox
import com.nymcard.cardsscan.models.Expiry
import com.nymcard.cardsscan.utils.DebitCardUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.Executors
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel(), OnScanListener {

    // ---- UI State ----
    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private val _cameraState = MutableStateFlow(CameraState())
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    private val _scanResult = MutableStateFlow<ScanResult?>(null)
    val scanResult: StateFlow<ScanResult?> = _scanResult.asStateFlow()

    // ---- CameraX / ML ----
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val machineLearningThread: MachineLearningThread by lazy {
        ScanBaseActivity.machineLearningThread!!
    }
    private val mlSemaphore = Semaphore(1)

    // ---- Internal scan state ----
    private var numberResults = HashMap<String?, Int?>()
    private var expiryResults = HashMap<Expiry?, Int?>()
    private var firstResultMs: Long = 0
    private var sentResponse = false
    private var isActive = true
    var roiCenterYRatio = 0.5f
    private val errorCorrectionDurationMs: Long = 2000

    init {
        machineLearningThread.warmUp(context)
        isActive = true
    }

    fun updateTexts(scanCardText: String?, positionCardText: String?) {
        _uiState.value = _uiState.value.copy(
            scanCardText = scanCardText ?: "Scan your card",
            positionCardText = positionCardText ?: "Position your card in the frame"
        )
    }

    fun setDebugMode(debug: Boolean) {
        _uiState.value = _uiState.value.copy(debugMode = debug)
    }

    fun setupCamera(
        provider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        preview: Preview,
        imageAnalysis: ImageAnalysis
    ) {
        cameraProvider = provider

        // Set single-thread analyzer
        imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
            processImageProxy(imageProxy)
        }

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )

            _cameraState.value = _cameraState.value.copy(
                isInitialized = true,
                hasError = false,
                errorMessage = null
            )
        } catch (e: Exception) {
            _cameraState.value = _cameraState.value.copy(
                hasError = true,
                errorMessage = "Failed to initialize camera: ${e.message}"
            )
        }
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        try {
            if (mlSemaphore.tryAcquire()) {
                val buffer = imageProxy.planes[0].buffer
                val data = ByteArray(buffer.remaining())
                buffer.get(data)

                machineLearningThread.post(
                    data,
                    imageProxy.width,
                    imageProxy.height,
                    imageProxy.format,
                    90,
                    this,
                    context,
                    roiCenterYRatio
                )

                mlSemaphore.release()
            }
        } catch (ex: Exception) {
            Log.e("ScanViewModelOptimized", "Error processing image: ${ex.message}")
            // Show "Failed to detect" dialog on any exception during image analysis
            showErrorDialog()
        } finally {
            imageProxy.close()
        }
    }

    private fun showErrorDialog() {
        if (!sentResponse && isActive) {
            _uiState.value = _uiState.value.copy(showErrorDialog = true)
        }
    }

    fun onErrorDialogRetry() {
        // Hide dialog and reset scan state
        _uiState.value = _uiState.value.copy(showErrorDialog = false)
        resetScanState()
    }

    fun onErrorDialogCancel() {
        // Hide dialog and set cancelled result
        _uiState.value = _uiState.value.copy(showErrorDialog = false)
        _scanResult.value = ScanResult.Cancelled
    }

    fun toggleFlashlight() {
        camera?.let { cam ->
            val current = cam.cameraInfo.torchState.value ?: TorchState.OFF
            val newState = current == TorchState.OFF
            cam.cameraControl.enableTorch(newState)

            _cameraState.value = _cameraState.value.copy(isFlashlightOn = newState)
        }
    }

    fun onBackPressed() {
        if (!sentResponse && isActive) {
            sentResponse = true
            _scanResult.value = ScanResult.Cancelled
        }
    }

    fun onCloseClicked() {
        _scanResult.value = ScanResult.Cancelled
    }

    fun onActivityPause() {
        isActive = false
    }

    fun onActivityResume() {
        resetScanState()
        isActive = true
    }

    fun resetScanState() {
        sentResponse = false
        firstResultMs = 0
        numberResults.clear()
        expiryResults.clear()
        _uiState.value = _uiState.value.copy(
            detectedCardNumber = null,
            detectedExpiry = null,
            showDetectedInfo = false
        )
        _scanResult.value = null
    }

    // ---- OnScanListener callbacks ----
    override fun onFatalError() {
        viewModelScope.launch {
            _scanResult.value = ScanResult.Error("Fatal error occurred during scanning")
        }
    }

    override fun onPrediction(
        number: String?, expiry: Expiry?, bitmap: Bitmap?,
        digitBoxes: MutableList<DetectedBox?>?, expiryBox: DetectedBox?
    ) {
        if (!sentResponse && isActive) {
            if (number != null && firstResultMs == 0L) firstResultMs = System.currentTimeMillis()
            if (number != null) incrementNumber(number)
            if (expiry != null) incrementExpiry(expiry)

            val duration = System.currentTimeMillis() - firstResultMs

            // Update UI without recomposition on every frame
            if (firstResultMs != 0L) {
                val currentNumber = getNumberResult()
                val currentExpiry = getExpiryResult()
                _uiState.value = _uiState.value.copy(
                    detectedCardNumber = currentNumber?.let { DebitCardUtils.format(it) },
                    detectedExpiry = currentExpiry?.format(),
                    showDetectedInfo = true
                )
            }

            // Send final result after errorCorrectionDurationMs
            if (firstResultMs != 0L && duration >= errorCorrectionDurationMs) {
                sentResponse = true
                val numberResult = getNumberResult()
                val expiryResult = getExpiryResult()
                _scanResult.value = ScanResult.Success(
                    cardNumber = numberResult,
                    expiryMonth = expiryResult?.month?.toString(),
                    expiryYear = expiryResult?.year?.toString()
                )
            }
            mlSemaphore.release()
        }
    }

    private fun incrementNumber(number: String?) {
        val current = numberResults[number] ?: 0
        numberResults[number] = current + 1
    }

    private fun incrementExpiry(expiry: Expiry?) {
        val current = expiryResults[expiry] ?: 0
        expiryResults[expiry] = current + 1
    }

    private fun getNumberResult(): String? {
        var result: String? = null
        var maxValue = 0
        for ((k, v) in numberResults) {
            if ((v ?: 0) > maxValue) {
                maxValue = v!!
                result = k
            }
        }
        return result
    }

    private fun getExpiryResult(): Expiry? {
        var result: Expiry? = null
        var maxValue = 0
        for ((k, v) in expiryResults) {
            if ((v ?: 0) > maxValue) {
                maxValue = v!!
                result = k
            }
        }
        return result
    }

    override fun onCleared() {
        super.onCleared()
        isActive = false
        cameraProvider = null
        camera = null
        analysisExecutor.shutdown()
    }
}
