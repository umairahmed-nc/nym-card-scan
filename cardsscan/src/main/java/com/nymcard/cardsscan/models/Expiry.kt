package com.nymcard.cardsscan.models

import android.graphics.Bitmap

class Expiry {
    private var string: String? = null
    var month: Int = 0
        private set
    var year: Int = 0
        private set

    fun format(): String {
        val result = StringBuilder()
        for (i in 0..<string!!.length) {
            if (i == 4) {
                result.append("/")
            }
            result.append(string!!.get(i))
        }

        return result.toString()
    }

    fun getString(): String {
        return string!!
    }

    companion object {
        fun from(model: RecognizedDigitsModel, image: Bitmap, box: CGRect): Expiry? {
            val digits = RecognizedDigits.Companion.from(model, image, box)
            val string = digits.stringResult()

            if (string.length != 6) {
                return null
            }

            val monthString = string.substring(4)
            val yearString = string.substring(0, 3)

            try {
                val month = monthString.toInt()
                val year = yearString.toInt()

                if (month <= 0 || month > 12) {
                    return null
                }

                val fullYear = (if (year > 90) 1300 else 1400) + year

                val expiry = Expiry()
                expiry.month = month
                expiry.year = fullYear
                expiry.string = string

                return expiry
            } catch (nfe: NumberFormatException) {
                return null
            }
        }
    }
}
