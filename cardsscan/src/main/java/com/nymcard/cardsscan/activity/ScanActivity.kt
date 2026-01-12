package com.nymcard.cardsscan.activity

import android.app.Activity
import android.content.Intent
import android.text.TextUtils
import com.nymcard.cardsscan.activityimp.ScanActivityImpl
import com.nymcard.cardsscan.base.ScanBaseActivity.Companion.warmUp
import com.nymcard.cardsscan.models.DebitCard

object ScanActivity {
    fun start(activity: Activity): Intent {
        warmUp(activity.applicationContext)
        val intent = Intent(activity, ScanActivityImpl::class.java)
        return intent
    }

    fun start(activity: Activity, scanCardText: String?, positionCardText: String?): Intent {
        warmUp(activity.applicationContext)
        val intent = Intent(activity, ScanActivityImpl::class.java)
        intent.putExtra(ScanActivityImpl.SCAN_CARD_TEXT, scanCardText)
        intent.putExtra(ScanActivityImpl.POSITION_CARD_TEXT, positionCardText)
        return intent
    }

    fun warmUp(activity: Activity) {
        warmUp(activity.applicationContext)
    }

    fun debitCardFromResult(intent: Intent): DebitCard? {
        val number = intent.getStringExtra(ScanActivityImpl.RESULT_CARD_NUMBER)
        val month = intent.getIntExtra(ScanActivityImpl.RESULT_EXPIRY_MONTH, 0)
        val year = intent.getIntExtra(ScanActivityImpl.RESULT_EXPIRY_YEAR, 0)
        if (TextUtils.isEmpty(number)) {
            return null
        }
        return DebitCard(number, month, year)
    }
}
