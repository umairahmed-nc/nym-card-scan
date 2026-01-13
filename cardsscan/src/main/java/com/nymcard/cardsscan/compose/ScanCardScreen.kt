package com.nymcard.cardsscan.compose

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.guava.await

@Composable
fun ScanCardScreen(
    scanViewModel: ScanViewModel = hiltViewModel(),
    onScanComplete: (ScanResult) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    val cameraState by scanViewModel.cameraState.collectAsState()
    val uiState by scanViewModel.uiState.collectAsState()
    val scanResult by scanViewModel.scanResult.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    val previewView = remember { PreviewView(context) }

    // Detect final scan result
    LaunchedEffect(scanResult) {
        scanResult?.let { onScanComplete(it) }
    }

    // Setup CameraX only once
    LaunchedEffect(Unit) {
        val cameraProvider = ProcessCameraProvider.getInstance(context).await()
        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val imageAnalysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        scanViewModel.setupCamera(cameraProvider, lifecycleOwner, preview, imageAnalysis)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Overlay with exact ROI from XML
        CardScanOverlay(
            showDetectedInfo = uiState.showDetectedInfo,
            detectedNumber = uiState.detectedCardNumber,
            detectedExpiry = uiState.detectedExpiry
        )

        // Flashlight toggle button
        IconButton(
            onClick = { scanViewModel.toggleFlashlight() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = if (cameraState.isFlashlightOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                contentDescription = "Flashlight"
            )
        }

        // Close button
        IconButton(
            onClick = { scanViewModel.onCloseClicked() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close"
            )
        }

        // Optional debug info
        if (uiState.debugMode) {
            Text(
                text = "Detected: ${uiState.detectedCardNumber ?: ""} / ${uiState.detectedExpiry ?: ""}",
                color = Color.Red,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
            )
        }
    }

    // Error Dialog
    if (uiState.showErrorDialog) {
        AlertDialog(
            onDismissRequest = { /* Don't allow dismiss by clicking outside */ },
            title = { Text(text = uiState.errorDialogTitle) },
            text = { Text(text = uiState.errorDialogMessage) },
            confirmButton = {
                TextButton(onClick = { scanViewModel.onErrorDialogRetry() }) {
                    Text("Try Again")
                }
            },
            dismissButton = {
                TextButton(onClick = { scanViewModel.onErrorDialogCancel() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Handle lifecycle events
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> scanViewModel.onActivityResume()
                Lifecycle.Event.ON_PAUSE -> scanViewModel.onActivityPause()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@Composable
fun CardScanOverlay(
    showDetectedInfo: Boolean,
    detectedNumber: String?,
    detectedExpiry: String?
) {
    Box(modifier = Modifier.fillMaxSize()) {

        // Canvas for card overlay
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(alpha = 0.99f) // Enable hardware acceleration for blend modes
        ) {
            drawCardOverlay()
        }

        // Detected card info below card frame
        if (showDetectedInfo) {
            // We'll use Modifier.align with padding from bottom of card frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp), // temporary, adjust later if needed
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    detectedNumber?.let {
                        Text(
                            text = it,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    detectedExpiry?.let {
                        Text(
                            text = it,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

