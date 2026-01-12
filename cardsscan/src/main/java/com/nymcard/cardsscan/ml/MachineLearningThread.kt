package com.nymcard.cardsscan.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.renderscript.Type
import com.nymcard.cardsscan.listener.OnObjectListener
import com.nymcard.cardsscan.listener.OnScanListener
import java.io.File
import java.util.LinkedList

class MachineLearningThread : Runnable {
    private val queue = LinkedList<RunArguments>()

    @Synchronized
    fun warmUp(context: Context) {
        if (OCR.Companion.isInit || !queue.isEmpty()) {
            return
        }
        val args = RunArguments(
            null, 0, 0, 0,
            90, null, context, 0.5f
        )
        queue.push(args)
        (this as Object).notify()
    }

    @Synchronized
    fun post(
        bytes: ByteArray?, width: Int, height: Int, format: Int, sensorOrientation: Int,
        scanListener: OnScanListener?, context: Context, roiCenterYRatio: Float
    ) {
        val args = RunArguments(
            bytes, width, height, format, sensorOrientation,
            scanListener, context, roiCenterYRatio
        )
        queue.push(args)
        (this as Object).notify()
    }

    @Synchronized
    fun post(
        bytes: ByteArray?, width: Int, height: Int, format: Int, sensorOrientation: Int,
        objectListener: OnObjectListener?, context: Context, roiCenterYRatio: Float,
        objectDetectFile: File?
    ) {
        val args = RunArguments(
            bytes, width, height, format, sensorOrientation,
            objectListener, context, roiCenterYRatio, objectDetectFile
        )
        queue.push(args)
        (this as Object).notify()
    }

    private fun YUV_toRGB(yuvByteArray: ByteArray, W: Int, H: Int, ctx: Context?): Bitmap {
        // Check if RenderScript is safe to use (avoid on newer Android versions where it crashes)
        val androidVersion = android.os.Build.VERSION.SDK_INT
        val useRenderScript = androidVersion < 31 && isRenderScriptSafe() // RenderScript deprecated in API 31+
        
        if (useRenderScript) {
            try {
                val rs = RenderScript.create(ctx)
                val yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(
                    rs,
                    Element.U8_4(rs)
                )

                val yuvType = Type.Builder(rs, Element.U8(rs)).setX(yuvByteArray.size)
                val `in` = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT)

                val rgbaType = Type.Builder(rs, Element.RGBA_8888(rs)).setX(W).setY(H)
                val out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT)

                `in`.copyFrom(yuvByteArray)

                yuvToRgbIntrinsic.setInput(`in`)
                yuvToRgbIntrinsic.forEach(out)
                val bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
                out.copyTo(bmp)

                yuvToRgbIntrinsic.destroy()
                rs.destroy()
                `in`.destroy()
                out.destroy()
                return bmp
            } catch (e: Exception) {
                // Fallback to manual conversion if RenderScript fails
                return fallbackYUVtoRGB(yuvByteArray, W, H)
            }
        } else {
            // Use manual conversion on newer Android versions
            return fallbackYUVtoRGB(yuvByteArray, W, H)
        }
    }

    private fun isRenderScriptSafe(): Boolean {
        return try {
            // Additional safety check - avoid RenderScript on problematic devices/versions
            val manufacturer = android.os.Build.MANUFACTURER.lowercase()
            val model = android.os.Build.MODEL.lowercase()
            
            // Known problematic devices/manufacturers can be blacklisted here
            when {
                manufacturer.contains("samsung") && android.os.Build.VERSION.SDK_INT >= 30 -> false
                manufacturer.contains("google") && android.os.Build.VERSION.SDK_INT >= 31 -> false
                else -> true
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun fallbackYUVtoRGB(yuvByteArray: ByteArray, W: Int, H: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        
        try {
            // Improved YUV420 to RGB conversion for camera data
            val pixels = IntArray(W * H)
            val frameSize = W * H
            val uvPixelStride = 1
            
            for (j in 0 until H) {
                for (i in 0 until W) {
                    val pixelIndex = j * W + i
                    if (pixelIndex >= frameSize || pixelIndex >= yuvByteArray.size) continue
                    
                    // Y component
                    val y = (0xff and yuvByteArray[pixelIndex].toInt())
                    
                    // U and V components (subsampled)
                    val uvIndex = frameSize + (j / 2) * (W / 2) + (i / 2)
                    val u: Int
                    val v: Int
                    
                    if (uvIndex < yuvByteArray.size - 1) {
                        // NV21 format: YYYYYYYY VUVU
                        v = (0xff and yuvByteArray[uvIndex].toInt()) - 128
                        u = (0xff and yuvByteArray[uvIndex + 1].toInt()) - 128
                    } else {
                        u = 0
                        v = 0
                    }
                    
                    // YUV to RGB conversion
                    val yAdjusted = Math.max(0, y - 16)
                    var r = (1.164f * yAdjusted + 1.596f * v).toInt()
                    var g = (1.164f * yAdjusted - 0.813f * v - 0.391f * u).toInt()
                    var b = (1.164f * yAdjusted + 2.018f * u).toInt()
                    
                    // Clamp values
                    r = Math.max(0, Math.min(255, r))
                    g = Math.max(0, Math.min(255, g))
                    b = Math.max(0, Math.min(255, b))
                    
                    pixels[pixelIndex] = (0xff shl 24) or (r shl 16) or (g shl 8) or b
                }
            }
            
            bitmap.setPixels(pixels, 0, W, 0, 0, W, H)
        } catch (e: Exception) {
            // Final fallback: create a gray bitmap with some pattern
            val canvas = Canvas(bitmap)
            val paint = Paint()
            paint.color = Color.GRAY
            canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), paint)
            
            // Add a simple pattern so we know fallback was used
            paint.color = Color.LTGRAY
            for (i in 0 until W step 50) {
                canvas.drawLine(i.toFloat(), 0f, i.toFloat(), H.toFloat(), paint)
            }
            for (j in 0 until H step 50) {
                canvas.drawLine(0f, j.toFloat(), W.toFloat(), j.toFloat(), paint)
            }
        }
        
        return bitmap
    }

    private fun getBitmap(
        bytes: ByteArray, width: Int, height: Int, format: Int, sensorOrientation: Int,
        roiCenterYRatio: Float, ctx: Context?, isOcr: Boolean
    ): Bitmap {
        var sensorOrientation = sensorOrientation
        val bitmap = YUV_toRGB(bytes, width, height, ctx)

        sensorOrientation = sensorOrientation % 360

        val h: Double
        val w: Double
        var x: Int
        var y: Int

        if (sensorOrientation == 0) {
            w = bitmap.getWidth().toDouble()
            h = if (isOcr) w * 302.0 / 480.0 else w
            x = 0
            y = Math.round((bitmap.getHeight().toDouble()) * roiCenterYRatio - h * 0.5).toInt()
        } else if (sensorOrientation == 90) {
            h = bitmap.getHeight().toDouble()
            w = if (isOcr) h * 302.0 / 480.0 else h
            y = 0
            x = Math.round((bitmap.getWidth().toDouble()) * roiCenterYRatio - w * 0.5).toInt()
        } else if (sensorOrientation == 180) {
            w = bitmap.getWidth().toDouble()
            h = if (isOcr) w * 302.0 / 480.0 else w
            x = 0
            y = Math.round((bitmap.getHeight().toDouble()) * (1.0 - roiCenterYRatio) - h * 0.5)
                .toInt()
        } else {
            h = bitmap.getHeight().toDouble()
            w = if (isOcr) h * 302.0 / 480.0 else h
            x = Math.round((bitmap.getWidth().toDouble()) * (1.0 - roiCenterYRatio) - w * 0.5)
                .toInt()
            y = 0
        }

        if (x < 0) {
            x = 0
        }
        if (y < 0) {
            y = 0
        }
        if ((x + w) > bitmap.getWidth()) {
            x = bitmap.getWidth() - w.toInt()
        }
        if ((y + h) > bitmap.getHeight()) {
            y = bitmap.getHeight() - h.toInt()
        }

        val croppedBitmap = Bitmap.createBitmap(bitmap, x, y, w.toInt(), h.toInt())

        val matrix = Matrix()
        matrix.postRotate(sensorOrientation.toFloat())
        val bm = Bitmap.createBitmap(
            croppedBitmap, 0, 0, croppedBitmap.getWidth(), croppedBitmap.getHeight(),
            matrix, true
        )

        croppedBitmap.recycle()
        bitmap.recycle()

        return bm
    }

    @get:Synchronized
    private val nextImage: RunArguments
        get() {
            while (queue.size == 0) {
                try {
                    (this as Object).wait()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }

            return queue.pop()
        }

    private fun runObjectModel(bitmap: Bitmap, args: RunArguments) {
        if (args.mObjectDetectFile == null) {
            val handler = Handler(Looper.getMainLooper())
            handler.post(object : Runnable {
                override fun run() {
                    if (args.mObjectListener != null) {
                        args.mObjectListener.onPrediction(
                            bitmap,
                            bitmap.getWidth(),
                            bitmap.getHeight()
                        )
                    }
                }
            })
            return
        }

        val handler = Handler(Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                try {
                    if (args.mObjectListener != null) {
                        args.mObjectListener.onPrediction(
                            bitmap,
                            bitmap.getWidth(),
                            bitmap.getHeight()
                        )
                    }
                } catch (e: Error) {
                    // prevent callbacks from crashing the app, swallow it
                    e.printStackTrace()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        })
    }

    private  fun runOcrModel(bitmap: Bitmap, args: RunArguments) {
        val ocr = OCR()
        val number = ocr.predict(bitmap, args.mContext)
        val hadUnrecoverableException = ocr.hadUnrecoverableException
        val handler = Handler(Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                try {
                    if (args.mScanListener != null) {
                        if (hadUnrecoverableException) {
                            args.mScanListener.onFatalError()
                        } else {
                            args.mScanListener.onPrediction(
                                number, ocr.expiry, bitmap, ocr.digitBoxes.toMutableList(),
                                ocr.expiryBox
                            )
                        }
                    }
                } catch (e: Error) {
                    // prevent callbacks from crashing the app, swallow it
                    e.printStackTrace()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        })
    }

    private fun runModel() {
        val args = this.nextImage

        val bm: Bitmap?
        if (args.mFrameBytes != null) {
            bm = getBitmap(
                args.mFrameBytes, args.mWidth, args.mHeight, args.mFormat,
                args.mSensorOrientation, args.mRoiCenterYRatio, args.mContext, args.mIsOcr
            )
        } else if (args.mBitmap != null) {
            bm = args.mBitmap
        } else {
            bm = Bitmap.createBitmap(480, 302, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bm)
            val paint = Paint()
            paint.setColor(Color.GRAY)
            canvas.drawRect(0.0f, 0.0f, 480.0f, 302.0f, paint)
        }

        if (args.mIsOcr) {
            runOcrModel(bm, args)
        } else {
            runObjectModel(bm, args)
        }
    }

    override fun run() {
        while (true) {
            try {
                runModel()
            } catch (e: Error) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    internal inner class RunArguments {
        val mFrameBytes: ByteArray?
        val mBitmap: Bitmap?
        val mScanListener: OnScanListener?
        val mObjectListener: OnObjectListener?
        val mContext: Context
        val mWidth: Int
        val mHeight: Int
        val mFormat: Int
        val mSensorOrientation: Int
        val mRoiCenterYRatio: Float
        val mIsOcr: Boolean
        val mObjectDetectFile: File?

        constructor(
            frameBytes: ByteArray?, width: Int, height: Int, format: Int,
            sensorOrientation: Int, scanListener: OnScanListener?, context: Context,
            roiCenterYRatio: Float
        ) {
            mFrameBytes = frameBytes
            mBitmap = null
            mWidth = width
            mHeight = height
            mFormat = format
            mScanListener = scanListener
            mContext = context
            mSensorOrientation = sensorOrientation
            mRoiCenterYRatio = roiCenterYRatio
            mIsOcr = true
            mObjectListener = null
            mObjectDetectFile = null
        }

        constructor(
            frameBytes: ByteArray?, width: Int, height: Int, format: Int,
            sensorOrientation: Int, objectListener: OnObjectListener?, context: Context,
            roiCenterYRatio: Float, objectDetectFile: File?
        ) {
            mFrameBytes = frameBytes
            mBitmap = null
            mWidth = width
            mHeight = height
            mFormat = format
            mScanListener = null
            mContext = context
            mSensorOrientation = sensorOrientation
            mRoiCenterYRatio = roiCenterYRatio
            mIsOcr = false
            mObjectListener = objectListener
            mObjectDetectFile = objectDetectFile
        }

        constructor(bitmap: Bitmap?, scanListener: OnScanListener?, context: Context) {
            mFrameBytes = null
            mBitmap = bitmap
            mWidth = if (bitmap == null) 0 else bitmap.getWidth()
            mHeight = if (bitmap == null) 0 else bitmap.getHeight()
            mFormat = 0
            mScanListener = scanListener
            mContext = context
            mSensorOrientation = 0
            mRoiCenterYRatio = 0f
            mIsOcr = true
            mObjectListener = null
            mObjectDetectFile = null
        }

        constructor(
            bitmap: Bitmap?, objectListener: OnObjectListener?, context: Context,
            objectDetectFile: File?
        ) {
            mFrameBytes = null
            mBitmap = bitmap
            mWidth = if (bitmap == null) 0 else bitmap.getWidth()
            mHeight = if (bitmap == null) 0 else bitmap.getHeight()
            mFormat = 0
            mScanListener = null
            mContext = context
            mSensorOrientation = 0
            mRoiCenterYRatio = 0f
            mIsOcr = false
            mObjectListener = objectListener
            mObjectDetectFile = objectDetectFile
        }
    }
}
