package com.nymcard.cardsscan.listener

import android.graphics.Bitmap
import com.nymcard.cardsscan.models.DetectedBox
import com.nymcard.cardsscan.models.Expiry

interface OnScanListener {
    fun onPrediction(
        number: String?, expiry: Expiry?, bitmap: Bitmap?,
        digitBoxes: MutableList<DetectedBox?>?, expiryBox: DetectedBox?
    )

    fun onFatalError()
}
