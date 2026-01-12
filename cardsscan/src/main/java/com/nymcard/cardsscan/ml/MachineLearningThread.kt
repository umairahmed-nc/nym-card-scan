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
