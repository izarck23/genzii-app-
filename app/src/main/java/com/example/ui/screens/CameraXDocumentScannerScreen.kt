package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ocr.OcrEngine
import com.example.ocr.ScannedPage
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

private const val TAG = "CameraXScanner"

/**
 * High-performance Document Scanner powered by AndroidX CameraX and Google ML Kit on-device text recognition.
 * Captures document pages with auto-orientation, framing guidance, touch-to-focus, and instant OCR parsing.
 */
@Composable
fun CameraXDocumentScannerScreen(
  onClose: () -> Unit,
  onPagesCaptured: (List<ScannedPage>) -> Unit
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current
  val coroutineScope = rememberCoroutineScope()

  // Camera permissions state
  var hasCameraPermission by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    )
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    hasCameraPermission = granted
    if (!granted) {
      Toast.makeText(context, "Camera permission is required for live document scanning.", Toast.LENGTH_SHORT).show()
    }
  }

  // CameraX controls state
  var isTorchOn by remember { mutableStateOf(false) }
  var useFrontCamera by remember { mutableStateOf(false) }
  var showGridLines by remember { mutableStateOf(true) }
  var isCapturing by remember { mutableStateOf(false) }
  var statusMessage by remember { mutableStateOf<String?>(null) }
  var focusTapOffset by remember { mutableStateOf<Offset?>(null) }

  // Batch captured pages
  val capturedPages = remember { mutableStateListOf<ScannedPage>() }
  var previewPageModal by remember { mutableStateOf<ScannedPage?>(null) }

  // Camera references
  var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
  var cameraControlRef by remember { mutableStateOf<CameraControl?>(null) }
  var imageCaptureRef by remember { mutableStateOf<ImageCapture?>(null) }

  // Photo gallery picker fallback
  val galleryLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
  ) { uri ->
    if (uri != null) {
      coroutineScope.launch {
        statusMessage = "Processing imported image..."
        val decodeResult = OcrEngine.decodeBitmapFromUri(context, uri)
        if (decodeResult.isSuccess) {
          val bitmap = decodeResult.getOrThrow()
          val ocrResult = OcrEngine.extractTextFromBitmap(bitmap)
          val text = ocrResult.getOrDefault("")
          val page = ScannedPage(
            originalBitmap = bitmap,
            currentBitmap = bitmap,
            extractedText = text,
            isOcrCompleted = true
          )
          capturedPages.add(page)
          previewPageModal = page
          statusMessage = null
        } else {
          statusMessage = null
          Toast.makeText(context, "Failed to load image from gallery.", Toast.LENGTH_SHORT).show()
        }
      }
    }
  }

  if (!hasCameraPermission) {
    // Permission request screen
    Scaffold(
      containerColor = MaterialTheme.colorScheme.background,
      topBar = {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text("Document Scanner", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
      }
    ) { padding ->
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Box(
              modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(GenziiPurpleContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(Icons.Default.CameraAlt, contentDescription = null, tint = GenziiPurple, modifier = Modifier.size(36.dp))
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
              text = "Camera Permission Required",
              fontWeight = FontWeight.Bold,
              fontSize = 18.sp,
              textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = "Genzii uses CameraX to capture ultra-crisp document images and process them on-device with ML Kit OCR. Zero cloud uploads required.",
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = TextAlign.Center,
              lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
              onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("request_camera_permission_button"),
              colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple),
              shape = RoundedCornerShape(14.dp)
            ) {
              Icon(Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Allow Camera Access", fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedButton(
              onClick = { galleryLauncher.launch("image/*") },
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
              shape = RoundedCornerShape(14.dp)
            ) {
              Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = GenziiPurple, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Import from Gallery Instead", color = GenziiPurple)
            }
          }
        }
      }
    }
    return
  }

  // Active CameraX Viewfinder Layout
  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Black)
  ) {
    // 1. Live CameraX Preview
    AndroidView(
      modifier = Modifier
        .fillMaxSize()
        .pointerInput(Unit) {
          detectTapGestures { offset ->
            focusTapOffset = offset
            // Trigger CameraX tap-to-focus and auto-exposure metering
            previewViewRef?.let { pView ->
              try {
                val factory = pView.meteringPointFactory
                val point = factory.createPoint(offset.x, offset.y)
                val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
                  .setAutoCancelDuration(2, TimeUnit.SECONDS)
                  .build()
                cameraControlRef?.startFocusAndMetering(action)
              } catch (e: Exception) {
                Log.w(TAG, "Focus metering failed: ${e.message}")
              }
            }
          }
        },
      factory = { ctx ->
        val previewView = PreviewView(ctx).apply {
          scaleType = PreviewView.ScaleType.FILL_CENTER
          implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        previewViewRef = previewView

        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
        cameraProviderFuture.addListener({
          try {
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
              it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageCapture = ImageCapture.Builder()
              .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
              .build()
            imageCaptureRef = imageCapture

            val cameraSelector = if (useFrontCamera) {
              CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
              CameraSelector.DEFAULT_BACK_CAMERA
            }

            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
              lifecycleOwner,
              cameraSelector,
              preview,
              imageCapture
            )
            cameraControlRef = camera.cameraControl
            camera.cameraControl.enableTorch(isTorchOn)
          } catch (e: Exception) {
            Log.e(TAG, "CameraX binding failed", e)
          }
        }, ContextCompat.getMainExecutor(ctx))

        previewView
      },
      update = { previewView ->
        previewViewRef = previewView
        try {
          cameraControlRef?.enableTorch(isTorchOn)
        } catch (_: Exception) {}
      }
    )

    // 2. Document Framing & Cutout Overlay
    DocumentViewfinderOverlay(showGridLines = showGridLines)

    // 3. Animated Tap-to-Focus Indicator
    focusTapOffset?.let { offset ->
      Box(
        modifier = Modifier
          .offset(x = (offset.x.toInt() - 24).dp, y = (offset.y.toInt() - 24).dp)
          .size(48.dp)
          .border(2.dp, GenziiCyan, RoundedCornerShape(8.dp))
      )
      LaunchedEffect(offset) {
        kotlinx.coroutines.delay(1200)
        focusTapOffset = null
      }
    }

    // 4. Top Controls Bar (Header)
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .statusBarsPadding()
        .padding(horizontal = 16.dp, vertical = 12.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      // Close Button
      IconButton(
        onClick = onClose,
        modifier = Modifier
          .size(44.dp)
          .background(Color.Black.copy(alpha = 0.5f), CircleShape)
      ) {
        Icon(Icons.Default.Close, contentDescription = "Close Scanner", tint = Color.White)
      }

      // Scanner Title & Mode Badge
      Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = RoundedCornerShape(20.dp)
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = GenziiCyan, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "CameraX • ML Kit OCR",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
          )
        }
      }

      // Top Actions: Torch, Grid, Lens Flip
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Torch Toggle
        IconButton(
          onClick = {
            isTorchOn = !isTorchOn
            try {
              cameraControlRef?.enableTorch(isTorchOn)
            } catch (e: Exception) {
              Log.w(TAG, "Failed to toggle torch: ${e.message}")
            }
          },
          modifier = Modifier
            .size(44.dp)
            .background(if (isTorchOn) GenziiPurple else Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
          Icon(
            imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
            contentDescription = "Toggle Torch",
            tint = Color.White
          )
        }

        // Grid Toggle
        IconButton(
          onClick = { showGridLines = !showGridLines },
          modifier = Modifier
            .size(44.dp)
            .background(if (showGridLines) GenziiPurple.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
          Icon(Icons.Default.GridOn, contentDescription = "Toggle Grid", tint = Color.White)
        }

        // Camera Lens Switch (Back / Front)
        IconButton(
          onClick = {
            useFrontCamera = !useFrontCamera
            isTorchOn = false
            // Rebind camera with new selector
            val provider = ProcessCameraProvider.getInstance(context).get()
            provider.unbindAll()
            val cameraSelector = if (useFrontCamera) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            val preview = Preview.Builder().build().also {
              previewViewRef?.let { pView -> it.setSurfaceProvider(pView.surfaceProvider) }
            }
            val imgCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
            imageCaptureRef = imgCapture
            val camera = provider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imgCapture)
            cameraControlRef = camera.cameraControl
          },
          modifier = Modifier
            .size(44.dp)
            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
          Icon(Icons.Default.FlipCameraAndroid, contentDescription = "Switch Camera Lens", tint = Color.White)
        }
      }
    }

    // 5. Center Status / Processing Banner
    if (statusMessage != null || isCapturing) {
      Box(
        modifier = Modifier
          .align(Alignment.Center)
          .padding(24.dp)
      ) {
        Surface(
          color = Color.Black.copy(alpha = 0.85f),
          shape = RoundedCornerShape(16.dp),
          border = BorderStroke(1.dp, GenziiCyan.copy(alpha = 0.6f))
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = GenziiCyan, strokeWidth = 2.5.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
              text = statusMessage ?: "Extracting text with on-device ML Kit...",
              color = Color.White,
              fontSize = 13.sp,
              fontWeight = FontWeight.Medium
            )
          }
        }
      }
    }

    // 6. Bottom Controls Bar
    Column(
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .fillMaxWidth()
        .navigationBarsPadding()
        .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Guidance pill
      Surface(
        color = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.padding(bottom = 12.dp)
      ) {
        Text(
          text = if (capturedPages.isEmpty()) "Align paper document inside frame • Tap shutter" else "${capturedPages.size} page(s) ready • Tap review or scan next",
          color = Color.White.copy(alpha = 0.9f),
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
      }

      // Horizontal list of already captured pages
      if (capturedPages.isNotEmpty()) {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
        ) {
          itemsIndexed(capturedPages) { idx, page ->
            Box(
              modifier = Modifier
                .size(width = 54.dp, height = 72.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(2.dp, GenziiCyan, RoundedCornerShape(8.dp))
                .clickable { previewPageModal = page }
            ) {
              Image(
                bitmap = page.currentBitmap.asImageBitmap(),
                contentDescription = "Page ${idx + 1}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
              )
              // Page badge
              Box(
                modifier = Modifier
                  .align(Alignment.BottomEnd)
                  .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(topStart = 6.dp))
                  .padding(horizontal = 4.dp, vertical = 2.dp)
              ) {
                Text(text = "${idx + 1}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
              }
            }
          }
        }
      }

      // Primary Action Row: Gallery | Shutter | Finish Review
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Gallery Import Option
        IconButton(
          onClick = { galleryLauncher.launch("image/*") },
          modifier = Modifier
            .size(52.dp)
            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
          Icon(Icons.Default.PhotoLibrary, contentDescription = "Import from gallery", tint = Color.White)
        }

        // Shutter Button
        Box(
          modifier = Modifier
            .size(78.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.25f))
            .clickable(enabled = !isCapturing) {
              val imgCapture = imageCaptureRef
              if (imgCapture == null) {
                Toast.makeText(context, "Camera is still initializing...", Toast.LENGTH_SHORT).show()
                return@clickable
              }

              triggerHapticFeedback(context)
              isCapturing = true
              statusMessage = "Capturing high-res frame..."

              imgCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                  override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                    val rawBitmap = imageProxy.toBitmap()
                    imageProxy.close()

                    // Rotate bitmap to match screen orientation
                    val orientedBitmap = if (rotationDegrees != 0) {
                      val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
                      Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                    } else {
                      rawBitmap
                    }

                    // Process on-device with ML Kit Text Recognition
                    coroutineScope.launch(Dispatchers.Default) {
                      withContext(Dispatchers.Main) {
                        statusMessage = "Analyzing document with ML Kit OCR..."
                      }

                      val ocrResult = OcrEngine.extractTextFromBitmap(orientedBitmap)
                      val recognizedText = ocrResult.getOrDefault("")

                      val page = ScannedPage(
                        originalBitmap = orientedBitmap,
                        currentBitmap = orientedBitmap,
                        extractedText = recognizedText,
                        isOcrCompleted = true
                      )

                      withContext(Dispatchers.Main) {
                        capturedPages.add(page)
                        previewPageModal = page
                        isCapturing = false
                        statusMessage = null
                      }
                    }
                  }

                  override fun onError(exception: ImageCaptureException) {
                    isCapturing = false
                    statusMessage = null
                    Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_SHORT).show()
                  }
                }
              )
            }
            .testTag("camerax_shutter_button"),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .size(62.dp)
              .clip(CircleShape)
              .background(if (isCapturing) GenziiCyan else Color.White)
          )
        }

        // Finish & Review Button
        if (capturedPages.isNotEmpty()) {
          Button(
            onClick = {
              onPagesCaptured(capturedPages.toList())
            },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple),
            shape = RoundedCornerShape(20.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            modifier = Modifier.testTag("finish_scanning_button")
          ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Review (${capturedPages.size})",
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
          }
        } else {
          // Placeholder spacer for symmetrical balance
          Spacer(modifier = Modifier.size(52.dp))
        }
      }
    }
  }

  // 7. Captured Page Preview Bottom Sheet / Dialog
  previewPageModal?.let { page ->
    AlertDialog(
      onDismissRequest = { previewPageModal = null },
      title = {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text("Captured Page Preview", fontWeight = FontWeight.Bold, fontSize = 16.sp)
          Surface(
            color = GenziiPurpleContainer,
            shape = RoundedCornerShape(8.dp)
          ) {
            Text(
              text = "ML Kit OCR Ready",
              color = GenziiPurple,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
          }
        }
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 380.dp)
            .verticalScroll(rememberScrollState())
        ) {
          // Image thumbnail
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(180.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(Color.Black)
          ) {
            Image(
              bitmap = page.currentBitmap.asImageBitmap(),
              contentDescription = "Captured Image",
              modifier = Modifier.fillMaxSize(),
              contentScale = ContentScale.Fit
            )
          }

          Spacer(modifier = Modifier.height(12.dp))

          // OCR Extracted Text Card
          Text("Extracted Text Preview:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
          Spacer(modifier = Modifier.height(4.dp))
          Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(10.dp)) {
              Text(
                text = page.extractedText.ifBlank { "No readable text detected." },
                fontSize = 12.sp,
                lineHeight = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
              )
              Spacer(modifier = Modifier.height(6.dp))
              val words = page.extractedText.split(Regex("\\s+")).filter { it.isNotBlank() }.size
              Text(
                text = "$words words • ${page.extractedText.length} characters recognized",
                fontSize = 11.sp,
                color = GenziiPurple,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }
      },
      confirmButton = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          // Scan Next Page
          OutlinedButton(
            onClick = { previewPageModal = null },
            shape = RoundedCornerShape(10.dp)
          ) {
            Text("Scan Next Page")
          }

          // Finish & Open in OCR Editor
          Button(
            onClick = {
              previewPageModal = null
              onPagesCaptured(capturedPages.toList())
            },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple),
            shape = RoundedCornerShape(10.dp)
          ) {
            Text("Finish & Review")
          }
        }
      },
      dismissButton = {
        TextButton(
          onClick = {
            capturedPages.remove(page)
            previewPageModal = null
          }
        ) {
          Text("Retake / Discard", color = MaterialTheme.colorScheme.error)
        }
      }
    )
  }
}

/**
 * Custom Viewfinder Canvas Overlay that draws a semi-transparent dark mask with a document cutout,
 * prominent glowing corner reticles, and optional alignment grid lines.
 */
@Composable
private fun DocumentViewfinderOverlay(
  showGridLines: Boolean
) {
  Canvas(modifier = Modifier.fillMaxSize()) {
    val canvasWidth = size.width
    val canvasHeight = size.height

    // Calculate document framing rectangle (proportions ~ A4 document 1:1.41)
    val docWidth = canvasWidth * 0.85f
    val docHeight = (docWidth * 1.4f).coerceAtMost(canvasHeight * 0.70f)
    val left = (canvasWidth - docWidth) / 2f
    val top = (canvasHeight - docHeight) / 2f - 20.dp.toPx()
    val right = left + docWidth
    val bottom = top + docHeight

    val docRect = Rect(left, top, right, bottom)
    val docPath = Path().apply {
      addRoundRect(RoundRect(docRect, CornerRadius(16.dp.toPx(), 16.dp.toPx())))
    }

    // 1. Draw darkened transparent mask outside the document cutout
    clipPath(docPath, clipOp = ClipOp.Difference) {
      drawRect(color = Color.Black.copy(alpha = 0.55f))
    }

    // 2. Optional alignment grid lines inside the document frame
    if (showGridLines) {
      val oneThirdX = left + docWidth / 3f
      val twoThirdX = left + 2 * docWidth / 3f
      val oneThirdY = top + docHeight / 3f
      val twoThirdY = top + 2 * docHeight / 3f

      val gridColor = Color.White.copy(alpha = 0.20f)
      drawLine(gridColor, Offset(oneThirdX, top), Offset(oneThirdX, bottom), strokeWidth = 1f)
      drawLine(gridColor, Offset(twoThirdX, top), Offset(twoThirdX, bottom), strokeWidth = 1f)
      drawLine(gridColor, Offset(left, oneThirdY), Offset(right, oneThirdY), strokeWidth = 1f)
      drawLine(gridColor, Offset(left, twoThirdY), Offset(right, twoThirdY), strokeWidth = 1f)
    }

    // 3. Document framing border (subtle)
    drawRoundRect(
      color = Color.White.copy(alpha = 0.35f),
      topLeft = Offset(left, top),
      size = Size(docWidth, docHeight),
      cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
      style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
    )

    // 4. Glowing Corner Reticles (CamScanner style)
    val cornerLen = 28.dp.toPx()
    val cornerStroke = 4.dp.toPx()
    val cornerColor = Color(0xFF00E5FF) // Genzii Vibrant Cyan

    // Top-Left
    drawLine(cornerColor, Offset(left - 2, top), Offset(left + cornerLen, top), strokeWidth = cornerStroke)
    drawLine(cornerColor, Offset(left, top - 2), Offset(left, top + cornerLen), strokeWidth = cornerStroke)

    // Top-Right
    drawLine(cornerColor, Offset(right + 2, top), Offset(right - cornerLen, top), strokeWidth = cornerStroke)
    drawLine(cornerColor, Offset(right, top - 2), Offset(right, top + cornerLen), strokeWidth = cornerStroke)

    // Bottom-Left
    drawLine(cornerColor, Offset(left - 2, bottom), Offset(left + cornerLen, bottom), strokeWidth = cornerStroke)
    drawLine(cornerColor, Offset(left, bottom + 2), Offset(left, bottom - cornerLen), strokeWidth = cornerStroke)

    // Bottom-Right
    drawLine(cornerColor, Offset(right + 2, bottom), Offset(right - cornerLen, bottom), strokeWidth = cornerStroke)
    drawLine(cornerColor, Offset(right, bottom + 2), Offset(right, bottom - cornerLen), strokeWidth = cornerStroke)
  }
}

/**
 * Trigger subtle tactile vibration on shutter press
 */
private fun triggerHapticFeedback(context: Context) {
  try {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      vibrator?.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
    } else {
      @Suppress("DEPRECATION")
      vibrator?.vibrate(45)
    }
  } catch (_: Exception) {}
}
