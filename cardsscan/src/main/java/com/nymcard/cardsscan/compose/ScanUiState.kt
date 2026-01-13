package com.nymcard.cardsscan.compose

data class ScanUiState(
    val scanCardText: String = "Scan your card",
    val positionCardText: String = "Position your card in the frame",
    val debugMode: Boolean = false,
    val detectedCardNumber: String? = null,
    val detectedExpiry: String? = null,
    val showDetectedInfo: Boolean = false,
    val showErrorDialog: Boolean = false,
    val errorDialogTitle: String = "Failed to Detect",
    val errorDialogMessage: String = "Failed to detect card. Please try again."
)

data class CameraState(
    val isInitialized: Boolean = false,
    val hasError: Boolean = false,
    val errorMessage: String? = null,
    val isFlashlightOn: Boolean = false
)

sealed class ScanResult {
    data class Success(
        val cardNumber: String?,
        val expiryMonth: String?,
        val expiryYear: String?
    ) : ScanResult()
    
    object Cancelled : ScanResult()
    
    data class Error(val message: String) : ScanResult()
}