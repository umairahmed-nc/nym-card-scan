# Card Scanner Architecture Documentation

## Overview

The Card Scanner system is a sophisticated Android application that uses machine learning to scan and extract information from credit/debit cards. The architecture consists of several interconnected components that handle camera operations, image processing, machine learning inference, and user interface.

## Component Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        USER INTERFACE LAYER                     │
├─────────────────────────────────────────────────────────────────┤
│  ScanActivity (Entry Point)                                    │
│  ├── ScanActivityImpl (Traditional View-based UI)              │
│  └── ScanActivityCompose (Modern Compose UI)                   │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      CAMERA & PROCESSING LAYER                  │
├─────────────────────────────────────────────────────────────────┤
│  ScanBaseActivity (Core scanning logic)                        │
│  ├── Camera Management                                          │
│  ├── Preview Handling                                           │
│  ├── Frame Processing                                            │
│  └── Result Aggregation                                         │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      THREADING LAYER                            │
├─────────────────────────────────────────────────────────────────┤
│  CameraThread (Background camera operations)                   │
│  MachineLearningThread (ML processing queue)                   │
└─────────────────────────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    MACHINE LEARNING LAYER                       │
├─────────────────────────────────────────────────────────────────┤
│  LiteRtImageClassifier (Base ML class)                         │
│  ├── FindFourModel (Card detection)                             │
│  ├── RecognizedDigitsModel (Number recognition)                 │
│  └── OCR (Orchestrates ML models)                               │
└─────────────────────────────────────────────────────────────────┘
```

## Detailed Component Analysis

### 1. ScanActivity (Entry Point)

**Purpose**: Factory class that provides entry points for starting card scanning

**Key Responsibilities**:
- Creates intents for different scanning modes
- Handles ML warmup
- Extracts results from scanning activities

**Key Methods**:
```kotlin
// Start traditional view-based scanning
fun start(activity: Activity): Intent

// Start Compose-based scanning with custom text
fun start(activity: Activity, scanCardText: String?, positionCardText: String?): Intent

// Warm up ML models
fun warmUp(activity: Activity)

// Extract card data from result intent
fun debitCardFromResult(intent: Intent): DebitCard?
```

**Flow**:
1. Client calls `ScanActivity.start()`
2. Warms up ML models via `ScanBaseActivity.warmUp()`
3. Creates intent for either `ScanActivityImpl` or `ScanActivityCompose`
4. Returns intent for client to start

### 2. ScanActivityImpl (Traditional UI Implementation)

**Purpose**: Traditional Android View-based implementation of card scanning UI

**Key Responsibilities**:
- Manages camera permissions
- Sets up UI layout with XML views
- Handles user interactions (close button, flashlight)
- Processes scan results and returns to caller

**Lifecycle**:
```kotlin
onCreate() {
    // 1. Set up UI layout
    setContentView(R.layout.activity_scan_card)
    
    // 2. Configure custom text if provided
    setupCustomText()
    
    // 3. Check camera permissions
    checkCameraPermissions()
    
    // 4. Set up click listeners
    setupClickListeners()
    
    // 5. Configure view IDs for base class
    setViewIds(...)
}

onCardScanned() {
    // 6. Package results and finish activity
    val intent = Intent()
    intent.putExtra(RESULT_CARD_NUMBER, numberResult)
    intent.putExtra(RESULT_EXPIRY_MONTH, month)
    intent.putExtra(RESULT_EXPIRY_YEAR, year)
    setResult(RESULT_OK, intent)
    finish()
}
```

### 3. ScanBaseActivity (Core Scanning Engine)

**Purpose**: Abstract base class containing all core scanning logic

**Key Responsibilities**:
- Camera lifecycle management
- Frame processing coordination
- ML thread management
- Result aggregation and error correction
- UI state management

**Key Components**:

#### Camera Management
```kotlin
// Camera initialization and configuration
private fun setUpCamera()
private fun setUpCameraPreview()
private fun startCameraPreview()

// Camera lifecycle
override fun onResume() // Start camera
override fun onPause()  // Stop camera
```

#### Frame Processing Pipeline
```kotlin
// Camera preview callback - receives frames
override fun onPreviewFrame(data: ByteArray?, camera: Camera?) {
    // 1. Queue frame for ML processing
    machineLearningThread?.post(
        data, width, height, format, 
        sensorOrientation, this, context, roiCenterYRatio
    )
}

// ML result callback - receives predictions
override fun onPrediction(
    number: String?, expiry: Expiry?, bitmap: Bitmap?,
    digitBoxes: MutableList<DetectedBox?>?, expiryBox: DetectedBox?
) {
    // 2. Aggregate results with error correction
    aggregateResults(number, expiry)
    
    // 3. Update UI
    updateUI(number, expiry)
    
    // 4. Check if scan is complete
    if (shouldFinishScan()) {
        onCardScanned(finalNumber, finalMonth, finalYear)
    }
}
```

#### Result Aggregation
```kotlin
private val numberResults = mutableMapOf<String, Int>()
private val expiryResults = mutableMapOf<Expiry, Int>()

// Error correction through multiple predictions
private fun aggregateResults(number: String?, expiry: Expiry?) {
    number?.let { numberResults[it] = (numberResults[it] ?: 0) + 1 }
    expiry?.let { expiryResults[it] = (expiryResults[it] ?: 0) + 1 }
    
    // Use most frequent results
    val bestNumber = numberResults.maxByOrNull { it.value }?.key
    val bestExpiry = expiryResults.maxByOrNull { it.value }?.key
}
```

### 4. CameraThread (Background Camera Operations)

**Purpose**: Handles camera operations on a background thread to avoid blocking UI

**Architecture**:
```kotlin
class CameraThread : Thread() {
    // Synchronous camera opening
    @Synchronized
    fun startCamera(listener: OnCameraOpenListener?) {
        this.listener = listener
        notify() // Wake up background thread
    }
    
    override fun run() {
        while (true) {
            // 1. Wait for camera open request
            val listener = waitForOpenRequest()
            
            // 2. Open camera on background thread
            var camera: Camera? = null
            try {
                camera = Camera.open()
            } catch (e: Exception) {
                // Handle camera errors
            }
            
            // 3. Return result on main thread
            Handler(Looper.getMainLooper()).post {
                listener.onCameraOpen(camera)
            }
        }
    }
}
```

**Benefits**:
- Prevents ANR (Application Not Responding) errors
- Handles camera initialization failures gracefully
- Provides clean separation between UI and camera operations

### 5. MachineLearningThread (ML Processing Queue)

**Purpose**: Processes camera frames through ML models on a background thread

**Architecture**:
```kotlin
class MachineLearningThread : Runnable {
    private val queue = LinkedList<RunArguments>()
    
    // Queue frame for processing
    @Synchronized
    fun post(bytes: ByteArray?, width: Int, height: Int, ...) {
        val args = RunArguments(bytes, width, height, ...)
        queue.push(args)
        notify() // Wake up processing thread
    }
    
    override fun run() {
        while (true) {
            // 1. Get next frame from queue
            val args = nextImage
            
            // 2. Convert camera data to bitmap
            val bitmap = getBitmap(args.frameBytes, ...)
            
            // 3. Run ML inference
            if (args.isOcr) {
                runOcrModel(bitmap, args)
            } else {
                runObjectModel(bitmap, args)
            }
        }
    }
}
```

**Processing Pipeline**:
1. **Frame Queuing**: Camera frames are queued for processing
2. **Image Conversion**: YUV camera data → RGB Bitmap
3. **Image Preprocessing**: Crop, rotate, resize for ML models
4. **ML Inference**: Run through OCR models
5. **Result Callback**: Return results to main thread

### 6. LiteRtImageClassifier (ML Base Class)

**Purpose**: Abstract base class for all ML models using LiteRT (TensorFlow Lite)

**Key Features**:
```kotlin
abstract class LiteRtImageClassifier(private val assetManager: AssetManager) {
    // Model management
    protected var compiledModel: CompiledModel?
    protected var options: CompiledModel.Options
    
    // Input buffer for model
    var imgData: ByteBuffer?
    
    // Abstract methods for subclasses
    protected abstract val imageSizeX: Int
    protected abstract val imageSizeY: Int
    protected abstract val modelAssetName: String?
    protected abstract fun addPixelValue(pixelValue: Int)
    protected abstract fun runInference()
}
```

**Processing Flow**:
```kotlin
fun classifyFrame(bitmap: Bitmap) {
    // 1. Convert bitmap to model input format
    convertBitmapToByteBuffer(bitmap)
    
    // 2. Run inference
    runInference()
    
    // 3. Results handled by subclass
}
```

### 7. ML Model Implementations

#### FindFourModel (Card Detection)
- **Purpose**: Detects card regions and digit/expiry locations
- **Input**: 480x302 RGB image
- **Output**: Grid of confidence scores for digit and expiry locations

#### RecognizedDigitsModel (Number Recognition)
- **Purpose**: Recognizes individual digits from detected regions
- **Input**: Cropped digit regions
- **Output**: Digit classifications (0-9)

#### OCR (ML Orchestrator)
```kotlin
class OCR {
    fun predict(bitmap: Bitmap, context: Context): String? {
        // 1. Initialize models if needed
        initializeModels(context)
        
        // 2. Detect card regions
        findFour?.classifyFrame(bitmap)
        val digitBoxes = detectBoxes(bitmap)
        val expiryBoxes = detectExpiry(bitmap)
        
        // 3. Recognize digits in detected regions
        val recognizedNumbers = recognizeNumbers?.predict(bitmap, digitBoxes)
        
        // 4. Extract and validate card number
        val cardNumber = extractCardNumber(recognizedNumbers)
        
        // 5. Extract expiry date
        expiry = extractExpiry(expiryBoxes, bitmap)
        
        return cardNumber
    }
}
```

## Data Flow

### Complete Scanning Flow

```
1. User starts scanning
   ScanActivity.start() → ScanActivityImpl.onCreate()

2. Camera initialization
   ScanBaseActivity.onResume() → CameraThread.startCamera()

3. Frame processing loop
   Camera.onPreviewFrame() → MachineLearningThread.post()
   
4. ML processing
   MachineLearningThread.run() → OCR.predict()
   ├── FindFourModel.classifyFrame() (detect regions)
   └── RecognizedDigitsModel.predict() (recognize digits)

5. Result aggregation
   OCR.predict() → ScanBaseActivity.onPrediction()
   → Result aggregation and error correction

6. Scan completion
   ScanBaseActivity.onCardScanned() → ScanActivityImpl.onCardScanned()
   → Return results to caller
```

### Error Handling & Recovery

1. **Camera Errors**: CameraThread handles camera initialization failures
2. **ML Errors**: OCR class tracks unrecoverable exceptions
3. **Permission Errors**: ScanActivityImpl handles camera permission requests
4. **Result Validation**: Multiple predictions aggregated for accuracy

### Performance Optimizations

1. **Background Processing**: Camera and ML operations on separate threads
2. **Frame Queuing**: Latest frame strategy prevents processing backlog
3. **Model Caching**: ML models initialized once and reused
4. **GPU Acceleration**: Optional GPU acceleration for ML models
5. **Memory Management**: Proper cleanup of camera and ML resources

## Modern Compose Integration

The new `ScanViewModel` and `ScanCardScreen` provide a modern Compose-based implementation:

```kotlin
// ScanViewModel manages ML thread and state
@HiltViewModel
class ScanViewModel : ViewModel(), OnScanListener {
    private var machineLearningThread: MachineLearningThread?
    
    fun processFrame(bytes: ByteArray, width: Int, height: Int, ...) {
        machineLearningThread?.post(bytes, width, height, ..., this, ...)
    }
    
    override fun onPrediction(number: String?, expiry: Expiry?, ...) {
        // Update UI state and aggregate results
    }
}

// ScanCardScreen provides Compose UI
@Composable
fun ScanCardScreen(viewModel: ScanViewModel = hiltViewModel()) {
    // CameraX integration
    val imageAnalysis = ImageAnalysis.Builder().build()
    imageAnalysis.setAnalyzer { imageProxy ->
        // Send frames to ViewModel for processing
        viewModel.processFrame(...)
    }
}
```

This architecture provides a clean separation of concerns, robust error handling, and efficient processing of camera frames through machine learning models to extract card information.