package com.nymcard.cardsscan.compose

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
import java.util.concurrent.Semaphore
import javax.inject.Inject

@HiltViewModel
class ScanViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel(), OnScanListener {

    // UI State
    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    // Camera state
    private val _cameraState = MutableStateFlow(CameraState())
    val cameraState: StateFlow<CameraState> = _cameraState.asStateFlow()

    // Scan results
    private val _scanResult = MutableStateFlow<ScanResult?>(null)
    val scanResult: StateFlow<ScanResult?> = _scanResult.asStateFlow()

    // Internal state
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val machineLearningThread: MachineLearningThread by lazy {
        ScanBaseActivity.machineLearningThread!!
    }
    private val mMachineLearningSemaphore = Semaphore(1)
    
    // Scan tracking
    private var numberResults = HashMap<String?, Int?>()
    private var expiryResults = HashMap<Expiry?, Int?>()
    private var firstResultMs: Long = 0
    private var mSentResponse = false
    private var mIsActivityActive = true
    private val errorCorrectionDurationMs: Long = 2000 // 2 seconds

    init {
        // Warm up ML thread
        machineLearningThread.warmUp(context)
    }

    fun updateTexts(scanCardText: String?, positionCardText: String?) {
        _uiState.value = _uiState.value.copy(
            scanCardText = scanCardText ?: "Scan your card",
            positionCardText = positionCardText ?: "Position your card in the frame"
        )
    }

    fun setDebugMode(debugMode: Boolean) {
        _uiState.value = _uiState.value.copy(debugMode = debugMode)
    }

    fun setupCamera(
        cameraProvider: ProcessCameraProvider,
        lifecycleOwner: LifecycleOwner,
        preview: Preview,
        imageAnalysis: ImageAnalysis
    ) {
        this.cameraProvider = cameraProvider
        
        // Setup image analysis
        imageAnalysis.setAnalyzer(
            androidx.core.content.ContextCompat.getMainExecutor(context)
        ) { imageProxy ->
            processImageProxy(imageProxy)
        }

        try {
            // Unbind all use cases before rebinding
            cameraProvider.unbindAll()

            // Bind use cases to camera
            camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageAnalysis
            )

            _cameraState.value = _cameraState.value.copy(
                isInitialized = true,
                hasError = false
            )

        } catch (e: Exception) {
            _cameraState.value = _cameraState.value.copy(
                hasError = true,
                errorMessage = "Failed to initialize camera: ${e.message}"
            )
        }
    }

    private fun processImageProxy(imageProxy: androidx.camera.core.ImageProxy) {
        if (mMachineLearningSemaphore.tryAcquire()) {
            // Convert ImageProxy to byte array (keeping original approach)
            val buffer = imageProxy.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)

            machineLearningThread.post(
                data,
                imageProxy.width,
                imageProxy.height,
                imageProxy.format,
                90, // Sensor orientation
                this,
                context,
                0.5f // ROI center Y ratio
            )
            
            mMachineLearningSemaphore.release()
        }
        
        imageProxy.close()
    }

    fun toggleFlashlight() {
        camera?.let { cam ->
            val currentTorchState = cam.cameraInfo.torchState.value ?: TorchState.OFF
            val newTorchState = currentTorchState == TorchState.OFF
            cam.cameraControl.enableTorch(newTorchState)
            
            _cameraState.value = _cameraState.value.copy(
                isFlashlightOn = newTorchState
            )
        }
    }

    fun onCloseClicked() {
        _scanResult.value = ScanResult.Cancelled
    }

    fun onBackPressed() {
        if (!mSentResponse && mIsActivityActive) {
            mSentResponse = true
            _scanResult.value = ScanResult.Cancelled
        }
    }

    // OnScanListener implementation
    override fun onFatalError() {
        _scanResult.value = ScanResult.Error("Fatal error occurred during scanning")
    }

    override fun onPrediction(
        number: String?,
        expiry: Expiry?,
        bitmap: Bitmap?,
        digitBoxes: MutableList<DetectedBox?>?,
        expiryBox: DetectedBox?
    ) {
        if (!mSentResponse && mIsActivityActive) {
            if (number != null && firstResultMs == 0L) {
                firstResultMs = System.currentTimeMillis()
            }

            if (number != null) {
                incrementNumber(number)
            }
            if (expiry != null) {
                incrementExpiry(expiry)
            }

            val duration = System.currentTimeMillis() - firstResultMs
            
            // Update UI with current predictions
            if (firstResultMs != 0L) {
                val currentNumber = getNumberResult()
                val currentExpiry = getExpiryResult()
                
                _uiState.value = _uiState.value.copy(
                    detectedCardNumber = currentNumber?.let { DebitCardUtils.format(it) },
                    detectedExpiry = currentExpiry?.format(),
                    showDetectedInfo = true
                )
            }

            // Check if we have enough confidence to return result
            if (firstResultMs != 0L && duration >= errorCorrectionDurationMs) {
                mSentResponse = true
                val numberResult = getNumberResult()
                val expiryResult = getExpiryResult()
                
                _scanResult.value = ScanResult.Success(
                    cardNumber = numberResult,
                    expiryMonth = expiryResult?.month?.toString(),
                    expiryYear = expiryResult?.year?.toString()
                )
            }
        }

        mMachineLearningSemaphore.release()
    }

    private fun incrementNumber(number: String?) {
        var currentValue = numberResults[number]
        if (currentValue == null) {
            currentValue = 0
        }
        numberResults[number] = currentValue + 1
    }

    private fun incrementExpiry(expiry: Expiry?) {
        var currentValue = expiryResults[expiry]
        if (currentValue == null) {
            currentValue = 0
        }
        expiryResults[expiry] = currentValue + 1
    }

    private fun getNumberResult(): String? {
        var result: String? = null
        var maxValue = 0

        for (number in numberResults.keys) {
            var value = 0
            val count = numberResults[number]
            if (count != null) {
                value = count
            }
            if (value > maxValue) {
                result = number
                maxValue = value
            }
        }

        return result
    }

    private fun getExpiryResult(): Expiry? {
        var result: Expiry? = null
        var maxValue = 0

        for (expiry in expiryResults.keys) {
            var value = 0
            val count = expiryResults[expiry]
            if (count != null) {
                value = count
            }
            if (value > maxValue) {
                result = expiry
                maxValue = value
            }
        }

        return result
    }

    fun resetScanState() {
        mSentResponse = false
        mIsActivityActive = true
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

    fun setActivityActive(active: Boolean) {
        mIsActivityActive = active
    }

    override fun onCleared() {
        super.onCleared()
        cameraProvider?.unbindAll()
    }
}

// Data classes for state management
data class ScanUiState(
    val scanCardText: String = "Scan your card",
    val positionCardText: String = "Position your card in the frame",
    val detectedCardNumber: String? = null,
    val detectedExpiry: String? = null,
    val showDetectedInfo: Boolean = false,
    val debugMode: Boolean = false
)

data class CameraState(
    val isInitialized: Boolean = false,
    val isFlashlightOn: Boolean = false,
    val hasError: Boolean = false,
    val errorMessage: String? = null
)

sealed class ScanResult {
    data class Success(
        val cardNumber: String?,
        val expiryMonth: String?,
        val expiryYear: String?
    ) : ScanResult()
    
    object Cancelled : ScanResult()
    
    data class Error(val message: String) : ScanResult()
}