package com.nymcard.cardsscan.models

import android.content.Context
import com.google.ai.edge.litert.TensorBuffer
import com.nymcard.cardsscan.ml.LiteRtImageClassifier
import java.lang.Float
import kotlin.Array
import kotlin.Exception
import kotlin.FloatArray
import kotlin.Int
import kotlin.String

class RecognizedDigitsModel(context: Context) : LiteRtImageClassifier(context.getAssets()) {
    private val labelProbArray: Array<Array<Array<FloatArray?>?>?>

    init {
        labelProbArray = Array<Array<Array<FloatArray?>?>?>(1) {
            Array<Array<FloatArray?>?>(1) {
                Array<FloatArray?>(
                    kNumPredictions
                ) { FloatArray(classes) }
            }
        }
    }

    override val imageSizeX: Int
        get() = 80

    override val imageSizeY: Int
        get() = 36

    override val numBytesPerChannel: Int
        get() = 4

    override fun addPixelValue(pixelValue: Int) {
        imgData!!.putFloat(((pixelValue shr 16) and 0xFF) / 255f)
        imgData!!.putFloat(((pixelValue shr 8) and 0xFF) / 255f)
        imgData!!.putFloat((pixelValue and 0xFF) / 255f)
    }

    override val modelAssetName: String
        get() = "fourrecognize.tflite" // update with your LiteRT compiled model

    fun argAndValueMax(col: Int): ArgMaxAndConfidence {
        var maxIdx = -1
        var maxValue = -1.0.toFloat()
        for (idx in 0..<classes) {
            val value = this.labelProbArray[0]!![0]!![col]!![idx]
            if (value > maxValue) {
                maxIdx = idx
                maxValue = value
            }
        }

        return ArgMaxAndConfidence(maxIdx, maxValue)
    }

    override fun runInference() {
        if (compiledModel == null) return

        try {
            val inputBuffers: MutableList<TensorBuffer> = compiledModel!!.createInputBuffers().toMutableList()
            val outputBuffers: MutableList<TensorBuffer> = compiledModel!!.createOutputBuffers().toMutableList()

            imgData!!.rewind()
            val numFloats = imgData!!.capacity() / Float.BYTES
            val inputArray = FloatArray(numFloats)
            for (i in 0..<numFloats) {
                inputArray[i] = imgData!!.getFloat()
            }

            inputBuffers.get(0).writeFloat(inputArray)

            compiledModel!!.run(inputBuffers, outputBuffers)

            val results = outputBuffers.get(0).readFloat()
            var index = 0
            for (col in 0..<kNumPredictions) {
                for (cls in 0..<classes) {
                    labelProbArray[0]!![0]!![col]!![cls] = results[index++]
                }
            }

            for (b in inputBuffers) b.close()
            for (b in outputBuffers) b.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    inner class ArgMaxAndConfidence(@JvmField val argMax: Int, @JvmField val confidence: kotlin.Float)
    companion object {
        const val kNumPredictions: Int = 17
        private const val classes = 11
    }
}
