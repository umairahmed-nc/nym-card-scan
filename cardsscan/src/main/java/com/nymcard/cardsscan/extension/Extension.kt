package com.nymcard.cardsscan.extension

import android.content.Intent
import com.nymcard.cardsscan.activityimp.ScanActivityImpl
import com.nymcard.cardsscan.models.DebitCard

fun Intent?.toDebitCard(): DebitCard? {
    if (this == null) return null

    val number = getStringExtra(ScanActivityImpl.RESULT_CARD_NUMBER)
        ?.takeIf { it.isNotBlank() }
        ?: return null

    return DebitCard(
        number = number,
        expiryMonth = getIntExtra(ScanActivityImpl.RESULT_EXPIRY_MONTH, 0),
        expiryYear = getIntExtra(ScanActivityImpl.RESULT_EXPIRY_YEAR, 0)
    )
}