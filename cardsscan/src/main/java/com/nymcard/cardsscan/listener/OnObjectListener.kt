package com.nymcard.cardsscan.listener

import android.graphics.Bitmap

interface OnObjectListener {
    fun onPrediction(
        bitmap: Bitmap?, imageWidth: Int,
        imageHeight: Int
    )

    fun onObjectFatalError()
}
