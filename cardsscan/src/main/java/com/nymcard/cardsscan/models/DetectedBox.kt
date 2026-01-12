package com.nymcard.cardsscan.models

import kotlin.Any
import kotlin.Comparable
import kotlin.Int

class DetectedBox(
    row: Int, col: Int, confidence: Float, numRows: Int, numCols: Int,
    boxSize: CGSize, cardSize: CGSize, imageSize: CGSize
) : Comparable<Any> {
    val row: Int
    val col: Int
    val rect: CGRect
    private val confidence: Float

    init {
        val w = boxSize.width * imageSize.width / cardSize.width
        val h = boxSize.height * imageSize.height / cardSize.height
        val x = (imageSize.width - w) / ((numCols - 1).toFloat()) * (col.toFloat())
        val y = (imageSize.height - h) / ((numRows - 1).toFloat()) * (row.toFloat())
        this.rect = CGRect(x, y, w, h)
        this.row = row
        this.col = col
        this.confidence = confidence
    }

    override fun compareTo(o: Any): Int {
        return this.confidence.compareTo((o as DetectedBox).confidence)
    }


}
