package com.nymcard.cardsscan.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.nymcard.cardsscan.models.CGSize
import com.nymcard.cardsscan.models.DetectedBox
import com.nymcard.cardsscan.models.Expiry
import com.nymcard.cardsscan.models.FindFourModel
import com.nymcard.cardsscan.models.RecognizeNumbers
import com.nymcard.cardsscan.models.RecognizedDigitsModel
import java.util.Collections

class OCR {
    var digitBoxes: MutableList<DetectedBox> = mutableListOf()
    var expiryBox: DetectedBox? = null
    var expiry: Expiry? = null
    var hadUnrecoverableException: Boolean = false

    private fun detectBoxes(image: Bitmap): MutableList<DetectedBox> {
        val boxes = ArrayList<DetectedBox>()
        for (row in 0..<FindFourModel.rows) {
            for (col in 0..<FindFourModel.cols) {
                if (findFour!!.hasDigits(row, col)) {
                    val confidence: Float = findFour!!.digitConfidence(row, col)
                    val imageSize = CGSize(image.getWidth().toFloat(), image.getHeight().toFloat())
                    val box = DetectedBox(
                        row, col, confidence,
                        FindFourModel.rows, FindFourModel.cols,
                        findFour!!.boxSize, findFour!!.cardSize,
                        imageSize
                    )
                    boxes.add(box)
                }
            }
        }
        return boxes
    }

    private fun detectExpiry(image: Bitmap): ArrayList<DetectedBox> {
        val boxes = ArrayList<DetectedBox>()
        for (row in 0..<FindFourModel.rows) {
            for (col in 0..<FindFourModel.cols) {
                if (findFour!!.hasExpiry(row, col)) {
                    val confidence: Float = findFour!!.expiryConfidence(row, col)
                    val imageSize = CGSize(image.getWidth().toFloat(), image.getHeight().toFloat())
                    val box = DetectedBox(
                        row, col, confidence,
                        FindFourModel.rows, FindFourModel.cols,
                        findFour!!.boxSize, findFour!!.cardSize,
                        imageSize
                    )
                    boxes.add(box)
                }
            }
        }
        return boxes
    }

    private fun runModel(image: Bitmap): String? {
        findFour!!.classifyFrame(image)

        var boxes = detectBoxes(image)
        val expiryBoxes = detectExpiry(image)

        val postDetection = PostDetectionAlgorithm(boxes, findFour!!)
        val recognizeNumbers = RecognizeNumbers(image, FindFourModel.rows, FindFourModel.cols)

        val lines:ArrayList<ArrayList<DetectedBox?>?> = postDetection.horizontalNumbers()
        var number = recognizeNumbers.number(recognizedDigitsModel!!, lines)

        if (number == null) {
            val verticalLines: ArrayList<ArrayList<DetectedBox?>?> = postDetection.verticalNumbers()
            number = recognizeNumbers.number(recognizedDigitsModel!!, verticalLines)
            lines.addAll(verticalLines)
        }

        boxes.clear()
        for (line in lines) {
            line?.let {
                boxes.addAll(line as Collection<DetectedBox>)
            }

        }

        this.digitBoxes = boxes

        this.expiry = null
        if (!expiryBoxes.isEmpty()) {
            Collections.sort<DetectedBox?>(expiryBoxes)
            val lastExpiryBox = expiryBoxes.get(expiryBoxes.size - 1)
            this.expiry = Expiry.from(recognizedDigitsModel!!, image, lastExpiryBox.rect)
            this.expiryBox = if (this.expiry != null) lastExpiryBox else null
        }

        return number
    }

    @Synchronized
    fun predict(image: Bitmap, context: Context): String? {
        try {
            if (findFour == null) {
                findFour = FindFourModel(context)
            }
            if (recognizedDigitsModel == null) {
                recognizedDigitsModel = RecognizedDigitsModel(context)
            }


            // GPU / CPU fallback can remain the same if needed
            return runModel(image)
        } catch (e: Throwable) {
            Log.e("OCR", "Unrecoverable exception in OCR", e)
            hadUnrecoverableException = true
            return null
        }
    }

    companion object {
        private var findFour: FindFourModel? = null
        private var recognizedDigitsModel: RecognizedDigitsModel? = null
        val isInit: Boolean
            get() = findFour != null && recognizedDigitsModel != null
    }
}
