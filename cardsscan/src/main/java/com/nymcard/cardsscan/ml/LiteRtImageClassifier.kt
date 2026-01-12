package com.nymcard.cardsscan.ml

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.util.Log
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import com.google.ai.edge.litert.LiteRtException
import java.nio.ByteBuffer
import java.nio.ByteOrder

abstract class LiteRtImageClassifier(private val assetManager: AssetManager) {
    private val intValues = IntArray(this.imageSizeX * this.imageSizeY)

    var imgData: ByteBuffer? = null
    protected var compiledModel: CompiledModel? = null
    protected var options: CompiledModel.Options = CompiledModel.Options(Accelerator.CPU)

    init {
        recreateModel()
    }


    private fun allocateInputBuffer() {
        // 2️⃣ allocate input buffer (same as before)
        imgData = ByteBuffer.allocateDirect(
            (DIM_BATCH_SIZE
                    * this.imageSizeX
                    * this.imageSizeY
                    * DIM_PIXEL_SIZE
                    * this.numBytesPerChannel)
        )
        imgData!!.order(ByteOrder.nativeOrder())
    }


    @Throws(LiteRtException::class)
    private fun recreateModel() {
        if (compiledModel != null) {
            compiledModel!!.close()
        }
        compiledModel = CompiledModel.create(
            assetManager,
            this.modelAssetName!!,
            options
        )
        allocateInputBuffer()
    }

    @Throws(LiteRtException::class)
    fun useGpu() {
        compiledModel!!.close()
        options = CompiledModel.Options(Accelerator.GPU)
        recreateModel()
    }

    fun close() {
        if (compiledModel != null) {
            compiledModel!!.close()
        }
    }


    fun classifyFrame(bitmap: Bitmap) {
        if (compiledModel == null) {
            Log.e(TAG, "Image classifier has not been initialized; Skipped.")
            return
        }
        convertBitmapToByteBuffer(bitmap)
        runInference()
    }

    private fun convertBitmapToByteBuffer(bitmap: Bitmap) {
        if (imgData == null) {
            return
        }
        imgData!!.rewind()

        val resizedBitmap = Bitmap.createScaledBitmap(
            bitmap,
            this.imageSizeX,
            this.imageSizeY, false
        )
        resizedBitmap.getPixels(
            intValues, 0, resizedBitmap.getWidth(), 0, 0,
            resizedBitmap.getWidth(), resizedBitmap.getHeight()
        )
        // Convert the image to floating point.
        var pixel = 0
        for (i in 0..<this.imageSizeX) {
            for (j in 0..<this.imageSizeY) {
                val `val` = intValues[pixel++]
                addPixelValue(`val`)
            }
        }
    }


    protected abstract val imageSizeX: Int

    protected abstract val imageSizeY: Int

    protected abstract val numBytesPerChannel: Int

    protected abstract fun addPixelValue(pixelValue: Int)

    protected abstract fun runInference()

    protected abstract val modelAssetName: String?

    companion object {
        private const val TAG = "LiteRtClassifier"
        private const val DIM_BATCH_SIZE = 1

        private const val DIM_PIXEL_SIZE = 3
    }
}
