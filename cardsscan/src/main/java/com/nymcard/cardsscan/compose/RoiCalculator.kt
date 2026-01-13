package com.nymcard.cardsscan.compose

import android.graphics.Rect
import kotlin.math.max

object RoiCalculator {

    /**
     * Exact PreviewView (XML) FILL_CENTER ROI math
     */
    fun calculate(
        previewWidth: Int,
        previewHeight: Int,
        bufferWidth: Int,
        bufferHeight: Int,
        roiWidthPercent: Float,
        roiHeightPercent: Float
    ): Rect {

        val scale = max(
            previewWidth.toFloat() / bufferWidth,
            previewHeight.toFloat() / bufferHeight
        )

        val scaledBufferWidth = bufferWidth * scale
        val scaledBufferHeight = bufferHeight * scale

        val dx = (scaledBufferWidth - previewWidth) / 2f
        val dy = (scaledBufferHeight - previewHeight) / 2f

        val roiPreviewWidth = previewWidth * roiWidthPercent
        val roiPreviewHeight = previewHeight * roiHeightPercent

        val roiPreviewLeft = (previewWidth - roiPreviewWidth) / 2f
        val roiPreviewTop = (previewHeight - roiPreviewHeight) / 2f

        val left = ((roiPreviewLeft + dx) / scale).toInt()
        val top = ((roiPreviewTop + dy) / scale).toInt()
        val right = ((roiPreviewLeft + roiPreviewWidth + dx) / scale).toInt()
        val bottom = ((roiPreviewTop + roiPreviewHeight + dy) / scale).toInt()

        return Rect(left, top, right, bottom)
    }
}
