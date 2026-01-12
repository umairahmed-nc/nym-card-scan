package com.nymcard.cardsscan.compose

import android.app.Activity
import android.content.Intent
import android.text.TextUtils
import com.nymcard.cardsscan.base.ScanBaseActivity.Companion.warmUp
import com.nymcard.cardsscan.models.DebitCard

/**
 * Compose-based equivalent of ScanActivity helper object.
 * Provides the same API as the original ScanActivity but uses the Compose implementation.
 */
object ScanActivityComposeHelper {
    
    /**
     * Start the Compose-based card scanning activity
     */
    fun start(activity: Activity): Intent {
        warmUp(activity.applicationContext)
        return Intent(activity, ScanActivityCompose::class.java)
    }

    /**
     * Start the Compose-based card scanning activity with custom texts
     */
    fun start(activity: Activity, scanCardText: String?, positionCardText: String?): Intent {
        warmUp(activity.applicationContext)
        val intent = Intent(activity, ScanActivityCompose::class.java)
        intent.putExtra(ScanActivityCompose.SCAN_CARD_TEXT, scanCardText)
        intent.putExtra(ScanActivityCompose.POSITION_CARD_TEXT, positionCardText)
        return intent
    }

    /**
     * Start the Compose-based card scanning activity with debug mode
     */
    fun start(
        activity: Activity, 
        scanCardText: String?, 
        positionCardText: String?, 
        debugMode: Boolean
    ): Intent {
        warmUp(activity.applicationContext)
        val intent = Intent(activity, ScanActivityCompose::class.java)
        intent.putExtra(ScanActivityCompose.SCAN_CARD_TEXT, scanCardText)
        intent.putExtra(ScanActivityCompose.POSITION_CARD_TEXT, positionCardText)
        intent.putExtra(ScanActivityCompose.DEBUG_MODE, debugMode)
        return intent
    }

    /**
     * Warm up the ML models
     */
    fun warmUp(activity: Activity) {
        warmUp(activity.applicationContext)
    }

    /**
     * Extract DebitCard from scan result intent
     */
    fun debitCardFromResult(intent: Intent): DebitCard? {
        val number = intent.getStringExtra(ScanActivityCompose.RESULT_CARD_NUMBER)
        val monthStr = intent.getStringExtra(ScanActivityCompose.RESULT_EXPIRY_MONTH)
        val yearStr = intent.getStringExtra(ScanActivityCompose.RESULT_EXPIRY_YEAR)
        
        if (TextUtils.isEmpty(number)) {
            return null
        }
        
        val month = monthStr?.toIntOrNull() ?: 0
        val year = yearStr?.toIntOrNull() ?: 0
        
        return DebitCard(number, month, year)
    }

    /**
     * Check if the result contains a fatal error
     */
    fun hasFatalError(intent: Intent): Boolean {
        return intent.getBooleanExtra(com.nymcard.cardsscan.base.ScanBaseActivity.RESULT_FATAL_ERROR, false)
    }

    /**
     * Check if the result contains a camera open error
     */
    fun hasCameraOpenError(intent: Intent): Boolean {
        return intent.getBooleanExtra(com.nymcard.cardsscan.base.ScanBaseActivity.RESULT_CAMERA_OPEN_ERROR, false)
    }
}

/**
 * Extension functions for easier usage
 */

/**
 * Start card scanning with default settings
 */
fun Activity.startCardScanCompose(): Intent {
    return ScanActivityComposeHelper.start(this)
}

/**
 * Start card scanning with custom texts
 */
fun Activity.startCardScanCompose(
    scanCardText: String? = null,
    positionCardText: String? = null
): Intent {
    return ScanActivityComposeHelper.start(this, scanCardText, positionCardText)
}

/**
 * Start card scanning with all options
 */
fun Activity.startCardScanCompose(
    scanCardText: String? = null,
    positionCardText: String? = null,
    debugMode: Boolean = false
): Intent {
    return ScanActivityComposeHelper.start(this, scanCardText, positionCardText, debugMode)
}