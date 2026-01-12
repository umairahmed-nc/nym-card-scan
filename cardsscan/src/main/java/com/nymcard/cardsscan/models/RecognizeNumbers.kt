package com.nymcard.cardsscan.models

import android.graphics.Bitmap
import com.nymcard.cardsscan.utils.DebitCardUtils.luhnCheck

class RecognizeNumbers(private val image: Bitmap, numRows: Int, numCols: Int) {
    private val recognizedDigits: Array<Array<RecognizedDigits?>?>

    init {
        this.recognizedDigits =
            Array<Array<RecognizedDigits?>?>(numRows) { arrayOfNulls<RecognizedDigits>(numCols) }
    }

    fun number(model: RecognizedDigitsModel, lines: ArrayList<ArrayList<DetectedBox?>?>): String? {
        for (line in lines) {
            val candidateNumber = StringBuilder()

            if (line != null) {
                for (word in line) {
                    val recognized = this.cachedDigits(model, word!!)
                    if (recognized == null) {
                        return null
                    }

                    candidateNumber.append(recognized.stringResult())
                }
            }

            if (candidateNumber.length == 16 && luhnCheck(candidateNumber.toString())) {
                return candidateNumber.toString()
            }
        }

        return null
    }

    private fun cachedDigits(model: RecognizedDigitsModel, box: DetectedBox): RecognizedDigits? {
        if (this.recognizedDigits[box.row]!![box.col] == null) {
            this.recognizedDigits[box.row]!![box.col] =
                RecognizedDigits.from(model, image, box.rect)
        }

        return this.recognizedDigits[box.row]!![box.col]
    }
}
