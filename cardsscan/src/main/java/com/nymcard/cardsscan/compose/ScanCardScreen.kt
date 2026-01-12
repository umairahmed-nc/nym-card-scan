package com.nymcard.cardsscan.compose

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import androidx.lifecycle.compose.LocalLifecycleOwner

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScanCardScreen(
    scanCardText: String? = null,
    positionCardText: String? = null,
    debugMode: Boolean = false,
    onScanResult: (ScanResult) -> Unit,
    viewModel: ScanViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val cameraState by viewModel.cameraState.collectAsStateWithLifecycle()
    val scanResult by viewModel.scanResult.collectAsStateWithLifecycle()

    // Handle scan result
    LaunchedEffect(scanResult) {
        scanResult?.let { result ->
            onScanResult(result)
        }
    }

    // Update texts and debug mode
    LaunchedEffect(scanCardText, positionCardText, debugMode) {
        viewModel.updateTexts(scanCardText, positionCardText)
        viewModel.setDebugMode(debugMode)
    }

    // Handle back press
    BackHandler {
        viewModel.onBackPressed()
    }

    // Camera permission
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    LaunchedEffect(cameraPermissionState.status) {
        if (!cameraPermissionState.status.isGranted && !cameraPermissionState.status.shouldShowRationale) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (cameraPermissionState.status.isGranted) {
            // Camera preview and scanning UI
            CameraPreviewWithOverlay(
                viewModel = viewModel,
                uiState = uiState,
                cameraState = cameraState,
                lifecycleOwner = lifecycleOwner
            )
        } else {
            // Permission request UI
            PermissionRequestContent(
                onRequestPermission = { cameraPermissionState.launchPermissionRequest() }
            )
        }
    }
}

@Composable
private fun CameraPreviewWithOverlay(
    viewModel: ScanViewModel,
    uiState: ScanUiState,
    cameraState: CameraState,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner
) {
    val context = LocalContext.current

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        AndroidView(
            factory = { ctx ->
                PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { previewView ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                viewModel.setupCamera(cameraProvider, lifecycleOwner, preview, imageAnalysis)
            }, ContextCompat.getMainExecutor(context))
        }

        // Overlay with card frame
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            drawCardOverlay()
        }

        // Close button
        IconButton(
            onClick = { viewModel.onCloseClicked() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .size(48.dp)
                .background(
                    Color.Black.copy(alpha = 0.5f),
                    CircleShape
                )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Scan card text
            Text(
                text = uiState.scanCardText,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Card frame area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(480f / 302f)
                    .padding(horizontal = 16.dp)
            ) {
                // Card frame border
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.Transparent,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(2.dp)
                ) {
                    // Detected card info overlay
                    if (uiState.showDetectedInfo) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            uiState.detectedCardNumber?.let { cardNumber ->
                                Text(
                                    text = cardNumber,
                                    color = Color.White,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }

                            uiState.detectedExpiry?.let { expiry ->
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = expiry,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Position card text
            Text(
                text = uiState.positionCardText,
                color = Color.White,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Flashlight button
            IconButton(
                onClick = { viewModel.toggleFlashlight() },
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        Color.White.copy(alpha = 0.2f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (cameraState.isFlashlightOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    contentDescription = if (cameraState.isFlashlightOn) "Turn off flashlight" else "Turn on flashlight",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Error message
        if (cameraState.hasError) {
            Card(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Red.copy(alpha = 0.9f))
            ) {
                Text(
                    text = cameraState.errorMessage ?: "Camera error occurred",
                    color = Color.White,
                    modifier = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun PermissionRequestContent(
    onRequestPermission: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera Permission Required",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "This app needs camera permission to scan your card",
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = onRequestPermission,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Blue)
        ) {
            Text(
                text = "Grant Permission",
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

private fun DrawScope.drawCardOverlay() {
    val canvasWidth = size.width
    val canvasHeight = size.height

    // Calculate card frame dimensions (maintaining 480:302 aspect ratio)
    val cardMargin = 32.dp.toPx()
    val cardWidth = canvasWidth - (cardMargin * 2)
    val cardHeight = cardWidth * (302f / 480f)

    val cardLeft = (canvasWidth - cardWidth) / 2
    val cardTop = (canvasHeight - cardHeight) / 2
    val cardRight = cardLeft + cardWidth
    val cardBottom = cardTop + cardHeight

    val cornerRadius = 12.dp.toPx()

    // Create path for the overlay (everything except the card area)
    val overlayPath = Path().apply {
        // Add the entire canvas
        addRect(Rect(0f, 0f, canvasWidth, canvasHeight))

        // Subtract the card area (rounded rectangle)
        addRoundRect(
            RoundRect(
                left = cardLeft,
                top = cardTop,
                right = cardRight,
                bottom = cardBottom,
                cornerRadius = CornerRadius(cornerRadius)
            )
        )
    }

    // Draw the semi-transparent overlay
    drawPath(
        path = overlayPath,
        color = Color.Black.copy(alpha = 0.6f),
        blendMode = BlendMode.SrcOver
    )

    // Draw the card frame border
    drawRoundRect(
        color = Color.White,
        topLeft = androidx.compose.ui.geometry.Offset(cardLeft, cardTop),
        size = Size(cardWidth, cardHeight),
        cornerRadius = CornerRadius(cornerRadius),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
    )
}