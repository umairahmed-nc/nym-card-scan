package com.nymcard.cardsscan.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.nymcard.cardsscan.models.DetectedBox

object ImageUtils {
    fun drawBoxesOnImage(
        frame: Bitmap,
        boxes: MutableList<DetectedBox>,
        expiryBox: DetectedBox?
    ): Bitmap {
        val paint = Paint(0)
        paint.setColor(Color.GREEN)
        paint.setStyle(Paint.Style.STROKE)
        paint.setStrokeWidth(3f)

        val mutableBitmap = frame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)
        for (box in boxes) {
            canvas.drawRect(box.rect.newInstance, paint)
        }

        paint.setColor(Color.RED)
        if (expiryBox != null) {
            canvas.drawRect(expiryBox.rect.newInstance, paint)
        }

        return mutableBitmap
    }
}
