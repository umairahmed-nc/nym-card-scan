package com.nymcard.cardsscan.activityimp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.TextView
import com.nymcard.cardsscan.R
import com.nymcard.cardsscan.base.ScanBaseActivity
import com.nymcard.cardsscan.models.DetectedBox
import com.nymcard.cardsscan.models.Expiry

class ScanActivityImpl : ScanBaseActivity() {
    private var mInDebugMode = false

    protected override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan_card)

        val scanCardText = getIntent().getStringExtra(SCAN_CARD_TEXT)
        if (!TextUtils.isEmpty(scanCardText)) {
            (findViewById<View?>(R.id.scanCard) as TextView).setText(scanCardText)
        }

        val positionCardText = getIntent().getStringExtra(POSITION_CARD_TEXT)
        if (!TextUtils.isEmpty(positionCardText)) {
            (findViewById<View?>(R.id.positionCard) as TextView).setText(positionCardText)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf<String>(Manifest.permission.CAMERA), 110)
            } else {
                mIsPermissionCheckDone = true
            }
        } else {
            // no permission checks
            mIsPermissionCheckDone = true
        }

        findViewById<View?>(R.id.closeButton).setOnClickListener(object : View.OnClickListener {
            override fun onClick(v: View?) {
                onBackPressed()
            }
        })

        mInDebugMode = getIntent().getBooleanExtra("debug", false)

        setViewIds(
            R.id.flashlightButton, R.id.cardRectangle, R.id.shadedBackground, R.id.texture,
            R.id.cardNumber, R.id.expiry
        )
    }

    override fun onCardScanned(numberResult: String?, month: String?, year: String?) {
        val intent = Intent()
        intent.putExtra(RESULT_CARD_NUMBER, numberResult)
        intent.putExtra(RESULT_EXPIRY_MONTH, month)
        intent.putExtra(RESULT_EXPIRY_YEAR, year)
        setResult(RESULT_OK, intent)
        finish()
    }

    public override fun onPrediction(
        number: String?, expiry: Expiry?, bitmap: Bitmap?,
        digitBoxes: MutableList<DetectedBox?>?, expiryBox: DetectedBox?
    ) {
        super.onPrediction(number, expiry, bitmap, digitBoxes, expiryBox)
    }

    companion object {
        const val SCAN_CARD_TEXT: String = "scanCardText"
        const val POSITION_CARD_TEXT: String = "positionCardText"
        const val RESULT_CARD_NUMBER: String = "cardNumber"
        const val RESULT_EXPIRY_MONTH: String = "expiryMonth"
        const val RESULT_EXPIRY_YEAR: String = "expiryYear"
        private const val TAG = "ScanActivityImpl"
        private const val startTimeMs: Long = 0
    }
}
