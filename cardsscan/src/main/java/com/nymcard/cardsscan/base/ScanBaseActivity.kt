package com.nymcard.cardsscan.base

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import android.view.View
import android.view.ViewTreeObserver
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.nymcard.cardsscan.R
import com.nymcard.cardsscan.listener.OnObjectListener
import com.nymcard.cardsscan.listener.OnScanListener
import com.nymcard.cardsscan.ml.MachineLearningThread
import com.nymcard.cardsscan.models.DetectedBox
import com.nymcard.cardsscan.models.Expiry
import com.nymcard.cardsscan.utils.DebitCardUtils
import com.nymcard.cardsscan.widget.Overlay
import java.io.File
import java.util.concurrent.Semaphore

abstract class ScanBaseActivity : AppCompatActivity(), View.OnClickListener,
    OnScanListener, OnObjectListener {
    var wasPermissionDenied: Boolean = false
    var denyPermissionTitle: String? = null
    var denyPermissionMessage: String? = null
    var denyPermissionButton: String? = null
    var mPredictionStartMs: Long = 0
    @JvmField
    var mIsPermissionCheckDone: Boolean = false
    var errorCorrectionDurationMs: Long = 0
    protected var mShowNumberAndExpiryAsScanning: Boolean = true
    protected var objectDetectFile: File? = null
    
    // CameraX components
    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var previewView: PreviewView? = null
    
    private var mOrientationEventListener: OrientationEventListener? = null
    private val mMachineLearningSemaphore = Semaphore(1)
    private var mRotation = 0
    private var mSentResponse = false
    private var mIsActivityActive = false
    private var numberResults = HashMap<String?, Int?>()
    private var expiryResults = HashMap<Expiry?, Int?>()
    private var firstResultMs: Long = 0
    private var mFlashlightId = 0
    private var mCardNumberId = 0
    private var mExpiryId = 0
    private var mTextureId = 0
    private var mRoiCenterYRatio = 0f
    private var mIsOcr = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        denyPermissionTitle = getString(R.string.deny_permission_title)
        denyPermissionMessage = getString(R.string.deny_permission_message)
        denyPermissionButton = getString(R.string.deny_permission_button)

        mIsOcr = getIntent().getBooleanExtra(IS_OCR, true)

        mOrientationEventListener = object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                orientationChanged(orientation)
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            mIsPermissionCheckDone = true
        } else {
            wasPermissionDenied = true
            val builder = AlertDialog.Builder(this)
            builder.setMessage(this.denyPermissionMessage)
                .setTitle(this.denyPermissionTitle)
            builder.setPositiveButton(
                this.denyPermissionButton,
                object : DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, id: Int) {
                        // just let the user click on the back button manually
                    }
                })
            val dialog = builder.create()
            dialog.show()
        }
    }

    protected fun startCamera() {
        numberResults = HashMap<String?, Int?>()
        expiryResults = HashMap<Expiry?, Int?>()
        firstResultMs = 0
        if (mOrientationEventListener!!.canDetectOrientation()) {
            mOrientationEventListener!!.enable()
        }

        try {
            if (mIsPermissionCheckDone) {
                initializeCameraX()
            }
        } catch (e: Exception) {
            val builder = AlertDialog.Builder(this)
            builder.setMessage(R.string.busy_camera)
                .setTitle(R.string.busy_camera_title)
            builder.setPositiveButton(
                R.string.deny_permission_button,
                object : DialogInterface.OnClickListener {
                    override fun onClick(dialog: DialogInterface?, id: Int) {
                        finish()
                    }
                })
            val dialog = builder.create()
            dialog.show()
        }
    }

    private fun initializeCameraX() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                setupCamera()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize CameraX", e)
                showCameraError()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setupCamera() {
        if (cameraProvider == null) return

        // Find PreviewView in the layout
        previewView = findViewById<PreviewView>(mTextureId)
        if (previewView == null) {
            Log.e(TAG, "PreviewView not found in layout")
            return
        }

        // Preview use case
        preview = Preview.Builder()
            .setTargetRotation(Surface.ROTATION_0)
            .build()
        
        preview?.setSurfaceProvider(previewView!!.surfaceProvider)

        // Image analysis use case for ML processing
        imageAnalysis = ImageAnalysis.Builder()
            .setTargetRotation(Surface.ROTATION_0)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalysis?.setAnalyzer(ContextCompat.getMainExecutor(this)) { imageProxy ->
            processImageProxy(imageProxy)
        }

        // Camera selector
        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            // Unbind all use cases before rebinding
            cameraProvider?.unbindAll()

            // Bind use cases to camera
            camera = cameraProvider?.bindToLifecycle(
                this as LifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis
            )

        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind camera use cases", e)
            showCameraError()
        }
    }

    private fun processImageProxy(imageProxy: ImageProxy) {
        if (mMachineLearningSemaphore.tryAcquire()) {
            val mlThread: MachineLearningThread = machineLearningThread!!
            
            // Convert ImageProxy to byte array (keeping original approach)
            val buffer = imageProxy.planes[0].buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)

            if (mIsOcr) {
                mlThread.post(
                    data,
                    imageProxy.width,
                    imageProxy.height,
                    imageProxy.format,
                    90, // Sensor orientation
                    this,
                    this,
                    mRoiCenterYRatio
                )
            } else {
                mlThread.post(
                    data,
                    imageProxy.width,
                    imageProxy.height,
                    imageProxy.format,
                    90, // Sensor orientation
                    this,
                    this,
                    mRoiCenterYRatio,
                    objectDetectFile
                )
            }
            
            mMachineLearningSemaphore.release()
        }
        
        imageProxy.close()
    }

    private fun showCameraError() {
        val intent = Intent()
        intent.putExtra(RESULT_CAMERA_OPEN_ERROR, true)
        setResult(RESULT_CANCELED, intent)
        finish()
    }

    override fun onPause() {
        super.onPause()
        cameraProvider?.unbindAll()
        mOrientationEventListener?.disable()
        mIsActivityActive = false
    }

    override fun onResume() {
        super.onResume()

        mIsActivityActive = true
        firstResultMs = 0
        numberResults = HashMap<String?, Int?>()
        expiryResults = HashMap<Expiry?, Int?>()
        mSentResponse = false

        if (findViewById<View?>(mCardNumberId) != null) {
            findViewById<View?>(mCardNumberId).setVisibility(View.INVISIBLE)
        }
        if (findViewById<View?>(mExpiryId) != null) {
            findViewById<View?>(mExpiryId).setVisibility(View.INVISIBLE)
        }

        startCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    fun setViewIds(
        flashlightId: Int, cardRectangleId: Int, overlayId: Int, textureId: Int,
        cardNumberId: Int, expiryId: Int
    ) {
        mFlashlightId = flashlightId
        mTextureId = textureId
        mCardNumberId = cardNumberId
        mExpiryId = expiryId
        val flashlight = findViewById<View?>(flashlightId)
        if (flashlight != null) {
            flashlight.setOnClickListener(this)
        }
        findViewById<View?>(cardRectangleId).getViewTreeObserver()
            .addOnGlobalLayoutListener(MyGlobalListenerClass(cardRectangleId, overlayId))
    }

    fun orientationChanged(orientation: Int) {
        // CameraX handles orientation automatically
        // This method is kept for compatibility but doesn't need to do anything
        mRotation = orientation
    }

    override fun onClick(view: View) {
        if (camera != null && mFlashlightId == view.getId()) {
            val currentTorchState = camera?.cameraInfo?.torchState?.value ?: TorchState.OFF
            val newTorchState = currentTorchState == TorchState.OFF
            camera?.cameraControl?.enableTorch(newTorchState)
        }
    }

    override fun onBackPressed() {
        if (!mSentResponse && mIsActivityActive) {
            mSentResponse = true
            val intent = Intent()
            setResult(RESULT_CANCELED, intent)
            finish()
        }
    }

    @VisibleForTesting
    fun incrementNumber(number: String?) {
        var currentValue = numberResults.get(number)
        if (currentValue == null) {
            currentValue = 0
        }

        numberResults.put(number, currentValue + 1)
    }

    @VisibleForTesting
    fun incrementExpiry(expiry: Expiry?) {
        var currentValue = expiryResults.get(expiry)
        if (currentValue == null) {
            currentValue = 0
        }

        expiryResults.put(expiry, currentValue + 1)
    }

    @get:VisibleForTesting
    val numberResult: String?
        get() {
            // Ugg there has to be a better way
            var result: String? = null
            var maxValue = 0

            for (number in numberResults.keys) {
                var value = 0
                val count = numberResults.get(number)
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

    @get:VisibleForTesting
    val expiryResult: Expiry?
        get() {
            var result: Expiry? = null
            var maxValue = 0

            for (expiry in expiryResults.keys) {
                var value = 0
                val count = expiryResults.get(expiry)
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

    private fun setValueAnimated(textView: TextView, value: String?) {
        if (textView.getVisibility() != View.VISIBLE) {
            textView.setVisibility(View.VISIBLE)
            textView.setAlpha(0.0f)
            //			textView.animate().setDuration(errorCorrectionDurationMs / 2).alpha(1.0f);
        }
        textView.setText(value)
    }

    protected abstract fun onCardScanned(numberResult: String?, month: String?, year: String?)

    protected fun setNumberAndExpiryAnimated(duration: Long) {
        val numberResult = this.numberResult
        val expiryResult = this.expiryResult
        var textView = findViewById<TextView>(mCardNumberId)
        setValueAnimated(textView, DebitCardUtils.format(numberResult!!))

        if (expiryResult != null && duration >= (errorCorrectionDurationMs / 2)) {
            textView = findViewById<TextView>(mExpiryId)
            setValueAnimated(textView, expiryResult.format())
        }
    }

    override fun onFatalError() {
        val intent = Intent()
        intent.putExtra(RESULT_FATAL_ERROR, true)
        setResult(RESULT_CANCELED, intent)
        finish()
    }

    override fun onPrediction(
        number: String?, expiry: Expiry?, bitmap: Bitmap?,
        digitBoxes: MutableList<DetectedBox?>?, expiryBox: DetectedBox?
    ) {
        if (!mSentResponse && mIsActivityActive) {
            if (number != null && firstResultMs == 0L) {
                firstResultMs = SystemClock.uptimeMillis()
            }

            if (number != null) {
                incrementNumber(number)
            }
            if (expiry != null) {
                incrementExpiry(expiry)
            }

            val duration = SystemClock.uptimeMillis() - firstResultMs
            if (firstResultMs != 0L && mShowNumberAndExpiryAsScanning) {
                setNumberAndExpiryAnimated(duration)
            }

            if (firstResultMs != 0L && duration >= errorCorrectionDurationMs) {
                mSentResponse = true
                val numberResult = this.numberResult
                val expiryResult = this.expiryResult
                var month: String? = null
                var year: String? = null
                if (expiryResult != null) {
                    month = expiryResult.month.toString()
                    year = expiryResult.year.toString()
                }

                onCardScanned(numberResult, month, year)
            }
        }

        mMachineLearningSemaphore.release()
    }

    override fun onObjectFatalError() {
        Log.d("ScanBaseActivity", "onObjectFatalError for object detection")
    }

    override fun onPrediction(bm: Bitmap?, imageWidth: Int, imageHeight: Int) {
        if (!mSentResponse && mIsActivityActive) {
            // do something with the prediction
        }
        mMachineLearningSemaphore.release()
    }

    internal inner class MyGlobalListenerClass(
        private val cardRectangleId: Int,
        private val overlayId: Int
    ) : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            val xy = IntArray(2)
            val view = findViewById<View>(cardRectangleId)
            view.getLocationInWindow(xy)

            // convert from DP to pixels
            val radius = (11 * Resources.getSystem().getDisplayMetrics().density).toInt()
            val rect = RectF(
                xy[0].toFloat(), xy[1].toFloat(),
                (xy[0] + view.getWidth()).toFloat(),
                (xy[1] + view.getHeight()).toFloat()
            )
            val overlay = findViewById<Overlay>(overlayId)
            overlay.setCircle(rect, radius)

            this@ScanBaseActivity.mRoiCenterYRatio =
                (xy[1] + view.getHeight() * 0.5f) / overlay.getHeight()
        }
    }

    companion object {
        private const val TAG = "ScanBaseActivity"
        const val IS_OCR: String = "is_ocr"
        const val RESULT_FATAL_ERROR: String = "result_fatal_error"
        const val RESULT_CAMERA_OPEN_ERROR: String = "result_camera_open_error"
        var machineLearningThread: MachineLearningThread? = null
            get() {
                if (field == null) {
                    field = MachineLearningThread()
                    Thread(field).start()
                }

                return field
            }
            private set

        @JvmStatic
        fun warmUp(context: Context) {
            machineLearningThread!!.warmUp(context)
        }
    }
}
