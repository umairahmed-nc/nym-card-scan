package com.nymcard.cardsscan.compose

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nymcard.cardsscan.base.ScanBaseActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ScanActivityCompose : ComponentActivity() {

    companion object {
        const val SCAN_CARD_TEXT = "scanCardText"
        const val POSITION_CARD_TEXT = "positionCardText"
        const val DEBUG_MODE = "debug"
        const val RESULT_CARD_NUMBER = "cardNumber"
        const val RESULT_EXPIRY_MONTH = "expiryMonth"
        const val RESULT_EXPIRY_YEAR = "expiryYear"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Only warm up ML thread if not already initialized
        ScanBaseActivity.warmUp(this)
        // Enable edge-to-edge display
        enableEdgeToEdge()

        // Hide system bars for immersive experience
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        // Get intent extras
        val scanCardText = intent.getStringExtra(SCAN_CARD_TEXT)
        val positionCardText = intent.getStringExtra(POSITION_CARD_TEXT)
        val debugMode = intent.getBooleanExtra(DEBUG_MODE, false)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ScanCardScreen(
                        onScanComplete = { result ->
                            handleScanResult(result)
                        }
                    )
                }
            }
        }
    }

    private fun handleScanResult(result: ScanResult) {
        when (result) {
            is ScanResult.Success -> {
                val intent = Intent().apply {
                    putExtra(RESULT_CARD_NUMBER, result.cardNumber)
                    putExtra(RESULT_EXPIRY_MONTH, result.expiryMonth)
                    putExtra(RESULT_EXPIRY_YEAR, result.expiryYear)
                }
                setResult(RESULT_OK, intent)
                finish()
            }

            is ScanResult.Cancelled -> {
                setResult(RESULT_CANCELED)
                finish()
            }

            is ScanResult.Error -> {
                val intent = Intent().apply {
                    putExtra(ScanBaseActivity.RESULT_FATAL_ERROR, true)
                }
                setResult(RESULT_CANCELED, intent)
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Keep screen on during scanning
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        // Remove keep screen on flag
        window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up any resources if needed
    }
}