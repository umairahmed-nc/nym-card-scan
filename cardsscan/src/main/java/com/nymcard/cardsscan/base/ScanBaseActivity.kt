package com.nymcard.cardsscan.base

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.RectF
import android.hardware.Camera
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import com.nymcard.cardsscan.R
import com.nymcard.cardsscan.listener.OnCameraOpenListener
import com.nymcard.cardsscan.listener.OnObjectListener
import com.nymcard.cardsscan.listener.OnScanListener
import com.nymcard.cardsscan.ml.CameraThread
import com.nymcard.cardsscan.ml.MachineLearningThread
import com.nymcard.cardsscan.models.DetectedBox
import com.nymcard.cardsscan.models.Expiry
import com.nymcard.cardsscan.utils.DebitCardUtils
import com.nymcard.cardsscan.widget.Overlay
import java.io.File
import java.io.IOException
import java.util.concurrent.Semaphore

abstract class ScanBaseActivity : Activity(), Camera.PreviewCallback, View.OnClickListener,
    OnScanListener, OnObjectListener, OnCameraOpenListener {
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
    private var mCamera: Camera? = null
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
    private var mCameraThread: CameraThread? = null
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
        permissions: Array<String?>,
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

    override fun onCameraOpen(camera: Camera?) {
        if (camera == null) {
            val intent = Intent()
            intent.putExtra(RESULT_CAMERA_OPEN_ERROR, true)
            setResult(RESULT_CANCELED, intent)
            finish()
        } else if (!mIsActivityActive) {
            camera.release()
        } else {
            mCamera = camera
            setCameraDisplayOrientation(
                this, Camera.CameraInfo.CAMERA_FACING_BACK,
                mCamera!!
            )
            // Create our Preview view and set it as the content of our activity.
            val cameraPreview = CameraPreview(this, this)
            val preview = findViewById<FrameLayout>(mTextureId)
            preview.addView(cameraPreview)
            mCamera!!.setPreviewCallback(this)
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
                if (mCameraThread == null) {
                    mCameraThread = CameraThread()
                    mCameraThread!!.start()
                }

                mCameraThread!!.startCamera(this)
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

    override fun onPause() {
        super.onPause()
        if (mCamera != null) {
            mCamera!!.stopPreview()
            mCamera!!.setPreviewCallback(null)
            mCamera!!.release()
            mCamera = null
        }

        mOrientationEventListener!!.disable()
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
        var orientation = orientation
        if (orientation == OrientationEventListener.ORIENTATION_UNKNOWN) return
        val info =
            Camera.CameraInfo()
        Camera.getCameraInfo(Camera.CameraInfo.CAMERA_FACING_BACK, info)
        orientation = (orientation + 45) / 90 * 90
        val rotation: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            rotation = (info.orientation - orientation + 360) % 360
        } else {  // back-facing camera
            rotation = (info.orientation + orientation) % 360
        }

        if (mCamera != null) {
            try {
                val params = mCamera!!.getParameters()
                params.setRotation(rotation)
                mCamera!!.setParameters(params)
            } catch (e: Exception) {
                // This gets called often so we can just swallow it and wait for the next one
                e.printStackTrace()
            } catch (e: Error) {
                e.printStackTrace()
            }
        }
    }

    fun setCameraDisplayOrientation(
        activity: Activity,
        cameraId: Int, camera: Camera
    ) {
        val info =
            Camera.CameraInfo()
        Camera.getCameraInfo(cameraId, info)
        val rotation = activity.getWindowManager().getDefaultDisplay()
            .getRotation()
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }

        var result: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
            result = (360 - result) % 360 // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360
        }
        camera.setDisplayOrientation(result)
        mRotation = result
    }

    override fun onPreviewFrame(bytes: ByteArray?, camera: Camera) {
        if (mMachineLearningSemaphore.tryAcquire()) {
            val mlThread: MachineLearningThread = machineLearningThread!!

            val parameters = camera.getParameters()
            val width = parameters.getPreviewSize().width
            val height = parameters.getPreviewSize().height
            val format = parameters.getPreviewFormat()

            mPredictionStartMs = SystemClock.uptimeMillis()

            // Use the application context here because the machine learning thread's lifecycle
            // is connected to the application and not this activity
            if (mIsOcr) {
                mlThread.post(
                    bytes, width, height, format, mRotation, this,
                    this.getApplicationContext(), mRoiCenterYRatio
                )
            } else {
                mlThread.post(
                    bytes, width, height, format, mRotation, this,
                    this.getApplicationContext(), mRoiCenterYRatio, objectDetectFile
                )
            }
        }
    }

    override fun onClick(view: View) {
        if (mCamera != null && mFlashlightId == view.getId()) {
            val parameters = mCamera!!.getParameters()
            if (parameters.getFlashMode() == Camera.Parameters.FLASH_MODE_TORCH) {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF)
            } else {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH)
            }
            mCamera!!.setParameters(parameters)
            mCamera!!.startPreview()
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

    inner class CameraPreview(
        context: Context?,
        private val mPreviewCallback: Camera.PreviewCallback?
    ) : SurfaceView(context), Camera.AutoFocusCallback, SurfaceHolder.Callback {
        private val mHolder: SurfaceHolder

        init {
            mHolder = getHolder()
            mHolder.addCallback(this)
            mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS)

            val params = mCamera!!.getParameters()
            val focusModes = params.getSupportedFocusModes()
            if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)
            } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                params.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)
            }
            params.setRecordingHint(true)
            mCamera!!.setParameters(params)
        }

        override fun onAutoFocus(success: Boolean, camera: Camera?) {
        }

        override fun surfaceCreated(holder: SurfaceHolder) {
            try {
                if (mCamera == null) return
                mCamera!!.setPreviewDisplay(holder)
                mCamera!!.startPreview()
            } catch (e: IOException) {
                Log.d("CameraCaptureActivity", "Error setting camera preview: " + e.message)
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
        }

        override fun surfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            if (mHolder.getSurface() == null) {
                return
            }

            try {
                mCamera!!.stopPreview()
            } catch (e: Exception) {
            }

            try {
                mCamera!!.setPreviewDisplay(mHolder)
                mCamera!!.setPreviewCallback(mPreviewCallback)
                mCamera!!.startPreview()
            } catch (e: Exception) {
                Log.d("CameraCaptureActivity", "Error starting camera preview: " + e.message)
            }
        }
    }

    companion object {
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
