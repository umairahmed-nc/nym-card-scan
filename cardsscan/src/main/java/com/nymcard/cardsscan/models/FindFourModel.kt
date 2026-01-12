package com.nymcard.cardsscan.models

import android.content.Context
import android.util.Log
import com.google.ai.edge.litert.TensorBuffer
import com.nymcard.cardsscan.ml.LiteRtImageClassifier
import java.lang.Float
import kotlin.Array
import kotlin.Boolean
import kotlin.Exception
import kotlin.FloatArray
import kotlin.Int
import kotlin.String

class FindFourModel(context: Context) : LiteRtImageClassifier(context.getAssets()) {
    val boxSize: CGSize = CGSize(80f, 36f)
    val cardSize: CGSize = CGSize(480f, 302f)

    private val labelProbArray: Array<Array<Array<FloatArray?>?>?>

    init {
        val classes = 3
        labelProbArray = Array<Array<Array<FloatArray?>?>?>(1) {
            Array<Array<FloatArray?>?>(rows) {
                Array<FloatArray?>(
                    cols
                ) { FloatArray(classes) }
            }
        }
    }

    fun hasDigits(row: Int, col: Int): Boolean {
        return this.digitConfidence(row, col) >= 0.5f
    }

    fun hasExpiry(row: Int, col: Int): Boolean {
        return this.expiryConfidence(row, col) >= 0.5f
    }

    fun digitConfidence(row: Int, col: Int): kotlin.Float {
        val digitClass = 1
        return labelProbArray[0]!![row]!![col]!![digitClass]
    }

    fun expiryConfidence(row: Int, col: Int): kotlin.Float {
        val expiryClass = 2
        return labelProbArray[0]!![row]!![col]!![expiryClass]
    }

    override val imageSizeX: Int
        get() = 480

    override val imageSizeY: Int
        get() = 302

    override val numBytesPerChannel: Int
        get() = 4 // float

    override fun addPixelValue(pixelValue: Int) {
        imgData!!.putFloat(((pixelValue shr 16) and 0xFF) / 255f)
        imgData!!.putFloat(((pixelValue shr 8) and 0xFF) / 255f)
        imgData!!.putFloat((pixelValue and 0xFF) / 255f)
    }

    override val modelAssetName: String
        get() = "findfour.tflite" // update with your LiteRT compiled model

    override fun runInference() {
        if (compiledModel == null) return

        try {
            // 1️⃣ Create LiteRT input/output buffers
            val inputBuffers: MutableList<TensorBuffer> = compiledModel!!.createInputBuffers().toMutableList()
            val outputBuffers: MutableList<TensorBuffer> = compiledModel!!.createOutputBuffers().toMutableList()

            // 2️⃣ Flatten imgData into float array
            imgData!!.rewind()
            val numFloats = imgData!!.capacity() / Float.BYTES
            val inputArray = FloatArray(numFloats)
            for (i in 0..<numFloats) {
                inputArray[i] = imgData!!.getFloat()
            }

            // 3️⃣ Write input into LiteRT buffer
            inputBuffers.get(0).writeFloat(inputArray)

            // 4️⃣ Run model
            compiledModel!!.run(inputBuffers, outputBuffers)

            // 5️⃣ Read output directly into labelProbArray
            val results = outputBuffers.get(0).readFloat()
            var index = 0
            for (r in 0..<rows) {
                for (c in 0..<cols) {
                    for (cls in 0..2) {
                        labelProbArray[0]!![r]!![c]!![cls] = results[index++]
                    }
                }
            }

            // 6️⃣ Close buffers
            for (b in inputBuffers) b.close()
            for (b in outputBuffers) b.close()
        } catch (e: Exception) {
            Log.e(TAG, "Inference failed", e)
        }
    }

    companion object {
        private const val TAG = "FindFourModel"

        const val rows: Int = 34
        const val cols: Int = 51
    }
}
