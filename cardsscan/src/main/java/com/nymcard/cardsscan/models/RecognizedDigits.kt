package com.nymcard.cardsscan.models

import android.graphics.Bitmap
import java.util.Collections
import kotlin.collections.ArrayList
import kotlin.collections.indices

class RecognizedDigits private constructor(
    val digits: ArrayList<Int>,
    val confidence: ArrayList<Float?>
) {
    private fun nonMaxSuppression(): ArrayList<Int> {
        val digitsCopy = ArrayList<Int>(this.digits)
        val confidenceCopy = ArrayList<Float?>(this.confidence)

        for (idx in 0..<kNumPredictions - 1) {
            if (digitsCopy.get(idx) != kBackgroundClass && digitsCopy.get(idx + 1) != kBackgroundClass) {
                if (confidenceCopy.get(idx)!! < confidenceCopy.get(idx + 1)!!) {
                    digitsCopy.set(idx, kBackgroundClass)
                    confidenceCopy.set(idx, 1.0f)
                } else {
                    digitsCopy.set(idx + 1, kBackgroundClass)
                    confidenceCopy.set(idx + 1, 1.0f)
                }
            }
        }

        return digitsCopy
    }

    fun stringResult(): String {
        val digits = nonMaxSuppression()
        val result = StringBuilder()
        for (digit in digits) {
            if (digit != kBackgroundClass) {
                result.append(digit)
            }
        }
        return result.toString()
    }


    fun four(): String {
        val digits = nonMaxSuppression()
        var result = stringResult()

        if (result.length < 4) {
            return ""
        }


        var fromLeft = true
        var leftIdx = 0
        var rightIdx = digits.size - 1
        while (result.length > 4) {
            if (fromLeft) {
                if (digits.get(leftIdx) != kBackgroundClass) {
                    result = result.substring(1)
                    digits.set(leftIdx, kBackgroundClass)
                }
                fromLeft = false
                leftIdx++
            } else {
                if (digits.get(rightIdx) != kBackgroundClass) {
                    result = result.substring(0, result.length - 1)
                    digits.set(rightIdx, kBackgroundClass)
                }
                fromLeft = true
                rightIdx--
            }
        }

        // Check spacing consistency
        val positions = ArrayList<Int?>()
        for (idx in digits.indices) {
            if (digits.get(idx) != kBackgroundClass) {
                positions.add(idx)
            }
        }

        val deltas = ArrayList<Int>()
        for (idx in 1..<positions.size) {
            deltas.add(positions.get(idx)!! - positions.get(idx - 1)!!)
        }

        if (!deltas.isEmpty()) {
            Collections.sort<Int>(deltas)
            val maxDelta: Int = deltas.get(deltas.size - 1)!!
            val minDelta: Int = deltas.get(0)!!
            if (maxDelta > (minDelta + 1)) {
                return ""
            }
        }

        return result
    }


    companion object {
        private val kNumPredictions = RecognizedDigitsModel.Companion.kNumPredictions
        private const val kBackgroundClass = 10
        private val kDigitMinConfidence = 0.5.toFloat()
        @JvmStatic
        fun from(model: RecognizedDigitsModel, image: Bitmap, box: CGRect): RecognizedDigits {
            val frame = Bitmap.createBitmap(
                image,
                Math.round(box.x),
                Math.round(box.y),
                Math.round(box.width),
                Math.round(box.height)
            )

            // NEW LiteRT call
            model.classifyFrame(frame)

            val digits = ArrayList<Int>()
            val confidence = ArrayList<Float?>()

            for (col in 0..<kNumPredictions) {
                val argAndConf = model.argAndValueMax(col)

                if (argAndConf.confidence < kDigitMinConfidence) {
                    digits.add(kBackgroundClass)
                } else {
                    digits.add(argAndConf.argMax)
                }
                confidence.add(argAndConf.confidence)
            }

            return RecognizedDigits(digits, confidence)
        }
    }
}
