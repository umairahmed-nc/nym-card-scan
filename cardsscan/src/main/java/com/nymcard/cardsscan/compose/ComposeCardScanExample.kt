package com.nymcard.cardsscan.compose

import android.app.Activity
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nymcard.cardsscan.models.DebitCard

/**
 * Example demonstrating how to use the Compose-based card scanner
 */
@Composable
fun ComposeCardScanExample() {
    val context = LocalContext.current
    var scannedCard by remember { mutableStateOf<DebitCard?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }

    // Activity result launcher for the scan activity
    val scanLauncher = remember {
        if (context is AppCompatActivity) {
            context.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                when (result.resultCode) {
                    Activity.RESULT_OK -> {
                        result.data?.let { data ->
                            val card = ScanActivityComposeHelper.debitCardFromResult(data)
                            scannedCard = card
                            scanError = null
                        }
                    }
                    Activity.RESULT_CANCELED -> {
                        result.data?.let { data ->
                            when {
                                ScanActivityComposeHelper.hasFatalError(data) -> {
                                    scanError = "A fatal error occurred during scanning"
                                }
                                ScanActivityComposeHelper.hasCameraOpenError(data) -> {
                                    scanError = "Could not open camera"
                                }
                                else -> {
                                    scanError = "Scan was cancelled"
                                }
                            }
                        } ?: run {
                            scanError = "Scan was cancelled"
                        }
                    }
                }
            }
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Compose Card Scanner Example",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Scan buttons
        Button(
            onClick = {
                scanLauncher?.launch(
                    ScanActivityComposeHelper.start(context as Activity)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Basic Scan")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                scanLauncher?.launch(
                    ScanActivityComposeHelper.start(
                        context as Activity,
                        "Scan Your Credit Card",
                        "Position your card within the frame"
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Scan with Custom Text")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                scanLauncher?.launch(
                    ScanActivityComposeHelper.start(
                        context as Activity,
                        "Debug Mode Scan",
                        "Debug information will be shown",
                        debugMode = true
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Debug Scan")
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Results display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Scan Results:",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                scannedCard?.let { card ->
                    Text("Card Number: ${card.number}")
                    Text("Expiry: ${card.expiryMonth}/${card.expiryYear}")
                } ?: run {
                    Text("No card scanned yet")
                }

                scanError?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Error: $error",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Clear results button
        if (scannedCard != null || scanError != null) {
            OutlinedButton(
                onClick = {
                    scannedCard = null
                    scanError = null
                }
            ) {
                Text("Clear Results")
            }
        }
    }
}

/**
 * Alternative approach using extension functions
 */
@Composable
fun ComposeCardScanExampleWithExtensions() {
    val context = LocalContext.current
    var scannedCard by remember { mutableStateOf<DebitCard?>(null) }

    val scanLauncher = remember {
        if (context is AppCompatActivity) {
            context.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    result.data?.let { data ->
                        scannedCard = ScanActivityComposeHelper.debitCardFromResult(data)
                    }
                }
            }
        } else null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Button(
            onClick = {
                // Using extension function for cleaner syntax
                scanLauncher?.launch(
                    (context as Activity).startCardScanCompose(
                        scanCardText = "Scan Your Card",
                        positionCardText = "Keep card steady"
                    )
                )
            }
        ) {
            Text("Scan Card (Extension Function)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        scannedCard?.let { card ->
            Text("Scanned: ${card.number}")
        }
    }
}