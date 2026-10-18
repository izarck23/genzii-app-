package com.example.ui.screens

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.GenziiRepository
import com.example.data.UserDocumentRepository
import com.example.model.FileType
import com.example.ocr.*
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class OcrViewTab(val label: String) {
  DOCUMENT("Document"),
  TEXT("Extracted Text"),
  COMPARE("Compare")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrScannerScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val scrollState = rememberScrollState()

  // State: Document with multi-page support
  var document by remember {
    val initialPage = createSampleScannedPage()
    mutableStateOf(
      ScannedDocument(
        title = "Research_Paper_Scan",
        pages = mutableListOf(initialPage),
        activePageIndex = 0
      )
    )
  }

  var selectedTab by remember { mutableStateOf(OcrViewTab.DOCUMENT) }
  var isProcessing by remember { mutableStateOf(false) }
  var statusMessage by remember { mutableStateOf<String?>(null) }
  var searchQuery by remember { mutableStateOf("") }
  var showRenameDialog by remember { mutableStateOf(false) }
  var renameInput by remember { mutableStateOf(document.title) }
  var showExportSuccessDialog by remember { mutableStateOf(false) }
  var exportedPdfPath by remember { mutableStateOf("") }
  var combineAllPagesText by remember { mutableStateOf(true) }
  var showCropAdjustDialog by remember { mutableStateOf(false) }
  var fineStraightenAngle by remember { mutableFloatStateOf(0f) }
  var manualCropInset by remember { mutableFloatStateOf(0.04f) }

  // Camera permissions
  var isCameraPermissionGranted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    )
  }
  var wasCameraDenied by remember { mutableStateOf(false) }

  // App Settings Helper
  fun openAppSettings() {
    try {
      val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (_: Exception) {}
  }

  // OCR Processing Function for a single page
  fun processOcrForPage(pageIndex: Int) {
    val page = document.pages.getOrNull(pageIndex) ?: return
    page.isOcrRunning = true
    isProcessing = true
    statusMessage = "Analyzing document image with ML Kit..."

    coroutineScope.launch {
      val result = OcrEngine.extractTextFromBitmap(page.currentBitmap)
      isProcessing = false
      page.isOcrRunning = false

      if (result.isSuccess) {
        val recognized = result.getOrThrow()
        page.extractedText = recognized
        page.isOcrCompleted = true
        statusMessage = "Text extraction complete (${page.extractedText.split(Regex("\\s+")).filter { it.isNotBlank() }.size} words)"
      } else {
        page.errorMessage = result.exceptionOrNull()?.message ?: "OCR extraction failed"
        statusMessage = "OCR warning: ${page.errorMessage}"
      }
      // Force trigger state update
      document = document.copy(pages = ArrayList(document.pages))
    }
  }

  // Add Page Helper
  fun addScannedPageFromBitmap(bitmap: Bitmap) {
    val newPage = ScannedPage(
      originalBitmap = bitmap,
      currentBitmap = bitmap
    )
    val updatedPages = ArrayList(document.pages).apply { add(newPage) }
    val newIndex = updatedPages.size - 1
    document = document.copy(pages = updatedPages, activePageIndex = newIndex)
    processOcrForPage(newIndex)
  }

  // Camera Capture Launcher
  val cameraCaptureLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.TakePicturePreview()
  ) { bitmap: Bitmap? ->
    if (bitmap != null) {
      addScannedPageFromBitmap(bitmap)
      Toast.makeText(context, "Page captured successfully", Toast.LENGTH_SHORT).show()
    }
  }

  // Camera Permission Launcher
  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { isGranted ->
    isCameraPermissionGranted = isGranted
    if (isGranted) {
      wasCameraDenied = false
      cameraCaptureLauncher.launch(null)
    } else {
      wasCameraDenied = true
    }
  }

  // Photo Picker Launcher (JPG, PNG, WebP)
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null) {
      isProcessing = true
      statusMessage = "Importing image from gallery..."
      coroutineScope.launch {
        val result = OcrEngine.decodeBitmapFromUri(context, uri)
        isProcessing = false
        if (result.isSuccess) {
          addScannedPageFromBitmap(result.getOrThrow())
          Toast.makeText(context, "Image imported successfully", Toast.LENGTH_SHORT).show()
        } else {
          Toast.makeText(context, "Failed to load image: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  // SAF Document Picker Launcher (PDFs, Images, Documents)
  val safDocumentLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      isProcessing = true
      statusMessage = "Importing document via Storage Access Framework..."
      coroutineScope.launch {
        val result = OcrEngine.decodeBitmapFromUri(context, uri)
        isProcessing = false
        if (result.isSuccess) {
          addScannedPageFromBitmap(result.getOrThrow())
          Toast.makeText(context, "Document page imported via SAF", Toast.LENGTH_SHORT).show()
        } else {
          Toast.makeText(context, "Could not render document: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  // SAF Searchable PDF Exporter Launcher
  val createPdfLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/pdf")
  ) { uri: Uri? ->
    if (uri != null) {
      isProcessing = true
      statusMessage = "Exporting searchable PDF document..."
      coroutineScope.launch {
        val result = SearchablePdfGenerator.exportSearchablePdfToUri(context, document, uri)
        isProcessing = false
        if (result.isSuccess) {
          val sizeBytes = result.getOrThrow()
          exportedPdfPath = "Saved via Storage Access Framework (${sizeBytes / 1024} KB)"
          showExportSuccessDialog = true
        } else {
          Toast.makeText(context, "PDF export failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  val activePage = document.currentPage

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = document.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
              )
              IconButton(
                onClick = {
                  renameInput = document.title
                  showRenameDialog = true
                },
                modifier = Modifier.size(24.dp)
              ) {
                Icon(Icons.Default.Edit, contentDescription = "Rename", tint = GenziiPurple, modifier = Modifier.size(16.dp))
              }
            }
            Text(
              text = "Page ${if (document.totalPages > 0) document.activePageIndex + 1 else 0} of ${document.totalPages} • ML Kit On-Device",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
        },
        actions = {
          // Share Button
          IconButton(
            onClick = {
              val shareText = if (combineAllPagesText) document.combinedText() else activePage?.extractedText ?: ""
              if (shareText.isNotBlank()) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_SUBJECT, document.title)
                  putExtra(Intent.EXTRA_TEXT, shareText)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Scanned Text via"))
              } else {
                Toast.makeText(context, "No extracted text to share.", Toast.LENGTH_SHORT).show()
              }
            }
          ) {
            Icon(Icons.Default.Share, contentDescription = "Share", tint = GenziiPurple)
          }

          // PDF Export Button
          IconButton(
            onClick = {
              if (document.pages.isNotEmpty()) {
                createPdfLauncher.launch("${document.title}.pdf")
              } else {
                Toast.makeText(context, "Please scan or import at least one page first.", Toast.LENGTH_SHORT).show()
              }
            },
            modifier = Modifier.testTag("ocr_export_pdf_button")
          ) {
            Icon(Icons.Default.PictureAsPdf, contentDescription = "Export Searchable PDF", tint = GenziiPurple)
          }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
      )
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp)
        .verticalScroll(scrollState),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      // Security & On-Device Processing Notice Banner
      Surface(
        color = GenziiPurpleContainer.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(Icons.Default.Security, contentDescription = null, tint = GenziiPurple, modifier = Modifier.size(16.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "100% On-Device ML Kit • Zero Cloud Uploads • Works Completely Offline",
            fontSize = 11.sp,
            color = GenziiPurple,
            fontWeight = FontWeight.Medium
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      // Camera Permission Denied Warning
      if (wasCameraDenied && !isCameraPermissionGranted) {
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = Color(0xFFFFF3E0),
          modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
        ) {
          Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Camera Permission Required", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFE65100))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = "Camera access was denied. You can enable it in App Settings, or import images directly from Gallery or SAF below.",
              fontSize = 12.sp,
              color = Color(0xFFBF360C)
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
              onClick = { openAppSettings() },
              shape = RoundedCornerShape(8.dp)
            ) {
              Icon(Icons.Outlined.Settings, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Open App Settings", fontSize = 12.sp, color = Color(0xFFE65100))
            }
          }
        }
      }

      // Capture & Import Options Bar
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Button(
          onClick = {
            if (isCameraPermissionGranted) {
              cameraCaptureLauncher.launch(null)
            } else {
              cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
          },
          modifier = Modifier
            .weight(1f)
            .testTag("ocr_camera_capture_button"),
          colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Camera", fontSize = 13.sp)
        }

        OutlinedButton(
          onClick = {
            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
          },
          modifier = Modifier
            .weight(1f)
            .testTag("ocr_photo_picker_button"),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = GenziiPurple, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Gallery", color = GenziiPurple, fontSize = 13.sp)
        }

        OutlinedButton(
          onClick = {
            safDocumentLauncher.launch(arrayOf("image/*", "application/pdf"))
          },
          modifier = Modifier
            .weight(1f)
            .testTag("ocr_saf_import_button"),
          shape = RoundedCornerShape(12.dp)
        ) {
          Icon(Icons.Default.FolderOpen, contentDescription = null, tint = GenziiPurple, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("SAF File", color = GenziiPurple, fontSize = 13.sp)
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Status Bar when processing
      if (isProcessing) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          CircularProgressIndicator(modifier = Modifier.size(18.dp), color = GenziiPurple, strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(10.dp))
          Text(statusMessage ?: "Processing...", fontSize = 12.sp, color = GenziiPurple, fontWeight = FontWeight.Medium)
        }
      }

      // Multi-Page Thumbnails Strip (CamScanner style)
      if (document.pages.isNotEmpty()) {
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(10.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = "Pages (${document.pages.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
              )
              Row {
                // Move Left
                IconButton(
                  onClick = {
                    if (document.activePageIndex > 0) {
                      val list = ArrayList(document.pages)
                      val item = list.removeAt(document.activePageIndex)
                      list.add(document.activePageIndex - 1, item)
                      document = document.copy(pages = list, activePageIndex = document.activePageIndex - 1)
                    }
                  },
                  enabled = document.activePageIndex > 0,
                  modifier = Modifier.size(28.dp)
                ) {
                  Icon(Icons.Default.ChevronLeft, contentDescription = "Move Left", modifier = Modifier.size(20.dp))
                }
                // Move Right
                IconButton(
                  onClick = {
                    if (document.activePageIndex < document.pages.size - 1) {
                      val list = ArrayList(document.pages)
                      val item = list.removeAt(document.activePageIndex)
                      list.add(document.activePageIndex + 1, item)
                      document = document.copy(pages = list, activePageIndex = document.activePageIndex + 1)
                    }
                  },
                  enabled = document.activePageIndex < document.pages.size - 1,
                  modifier = Modifier.size(28.dp)
                ) {
                  Icon(Icons.Default.ChevronRight, contentDescription = "Move Right", modifier = Modifier.size(20.dp))
                }
                // Delete Page
                IconButton(
                  onClick = {
                    if (document.pages.size > 1) {
                      val list = ArrayList(document.pages)
                      list.removeAt(document.activePageIndex)
                      val nextIndex = (document.activePageIndex).coerceAtMost(list.size - 1)
                      document = document.copy(pages = list, activePageIndex = nextIndex)
                    } else {
                      Toast.makeText(context, "Cannot delete the only page in scan.", Toast.LENGTH_SHORT).show()
                    }
                  },
                  enabled = document.pages.size > 1,
                  modifier = Modifier.size(28.dp)
                ) {
                  Icon(Icons.Default.DeleteOutline, contentDescription = "Delete Page", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
              }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              itemsIndexed(document.pages) { idx, page ->
                val isSelected = idx == document.activePageIndex
                Box(
                  modifier = Modifier
                    .size(width = 64.dp, height = 86.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                      width = if (isSelected) 2.5.dp else 1.dp,
                      color = if (isSelected) GenziiPurple else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                      shape = RoundedCornerShape(8.dp)
                    )
                    .clickable {
                      document = document.copy(activePageIndex = idx)
                    }
                ) {
                  Image(
                    bitmap = page.currentBitmap.asImageBitmap(),
                    contentDescription = "Page ${idx + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                  )
                  // Page number badge
                  Box(
                    modifier = Modifier
                      .align(Alignment.BottomEnd)
                      .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(topStart = 6.dp))
                      .padding(horizontal = 4.dp, vertical = 2.dp)
                  ) {
                    Text(
                      text = "${idx + 1}",
                      color = Color.White,
                      fontSize = 10.sp,
                      fontWeight = FontWeight.Bold
                    )
                  }
                }
              }

              // Add page item
              item {
                Box(
                  modifier = Modifier
                    .size(width = 64.dp, height = 86.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, GenziiPurple.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .background(GenziiPurpleContainer.copy(alpha = 0.2f))
                    .clickable {
                      if (isCameraPermissionGranted) {
                        cameraCaptureLauncher.launch(null)
                      } else {
                        photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                      }
                    },
                  contentAlignment = Alignment.Center
                ) {
                  Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Add, contentDescription = "Add Page", tint = GenziiPurple, modifier = Modifier.size(24.dp))
                    Text("Add", fontSize = 10.sp, color = GenziiPurple, fontWeight = FontWeight.Bold)
                  }
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))
      }

      // View Tabs: Document vs Text vs Compare
      TabRow(
        selectedTabIndex = selectedTab.ordinal,
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = GenziiPurple,
        divider = {}
      ) {
        OcrViewTab.values().forEach { tab ->
          Tab(
            selected = selectedTab == tab,
            onClick = { selectedTab = tab },
            text = { Text(tab.label, fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal) }
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Tab Content
      when (selectedTab) {
        OcrViewTab.DOCUMENT -> {
          // Document & Image Adjustment Tools
          if (activePage != null) {
            Card(
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(
                modifier = Modifier.padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
              ) {
                // Display Current Page Image
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E1A33)),
                  contentAlignment = Alignment.Center
                ) {
                  Image(
                    bitmap = activePage.currentBitmap.asImageBitmap(),
                    contentDescription = "Scanned Page ${document.activePageIndex + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                  )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Image Adjustment Action Buttons
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                  // Rotate 90
                  OutlinedButton(
                    onClick = {
                      activePage.rotationDegrees = (activePage.rotationDegrees + 90) % 360
                      ImageProcessingUtils.reprocessPage(activePage)
                      document = document.copy(pages = ArrayList(document.pages))
                      processOcrForPage(document.activePageIndex)
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                  ) {
                    Icon(Icons.Default.RotateRight, contentDescription = null, modifier = Modifier.size(16.dp), tint = GenziiPurple)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rotate 90°", fontSize = 12.sp, color = GenziiPurple)
                  }

                  // Straighten & Crop
                  OutlinedButton(
                    onClick = { showCropAdjustDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                  ) {
                    Icon(Icons.Default.Crop, contentDescription = null, modifier = Modifier.size(16.dp), tint = GenziiPurple)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Straighten", fontSize = 12.sp, color = GenziiPurple)
                  }

                  // Auto Detect Edges & Crop
                  OutlinedButton(
                    onClick = {
                      val detected = ImageProcessingUtils.detectDocumentEdges(activePage.originalBitmap)
                      activePage.cropRectRatio = detected
                      ImageProcessingUtils.reprocessPage(activePage)
                      document = document.copy(pages = ArrayList(document.pages))
                      processOcrForPage(document.activePageIndex)
                      Toast.makeText(context, "Document edges automatically detected", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                  ) {
                    Icon(Icons.Default.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp), tint = GenziiPurple)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Auto Edge", fontSize = 12.sp, color = GenziiPurple)
                  }

                  // Reset
                  OutlinedButton(
                    onClick = {
                      activePage.rotationDegrees = 0
                      activePage.cropRectRatio = RectF(0f, 0f, 1f, 1f)
                      activePage.filterMode = ScanFilter.ORIGINAL
                      ImageProcessingUtils.reprocessPage(activePage)
                      document = document.copy(pages = ArrayList(document.pages))
                      processOcrForPage(document.activePageIndex)
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                  ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset", fontSize = 12.sp)
                  }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // CamScanner Filter Modes
                Text(
                  text = "Enhancement Filter: ${activePage.filterMode.label}",
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  ScanFilter.values().forEach { filter ->
                    val isFilterSelected = activePage.filterMode == filter
                    FilterChip(
                      selected = isFilterSelected,
                      onClick = {
                        activePage.filterMode = filter
                        ImageProcessingUtils.reprocessPage(activePage)
                        document = document.copy(pages = ArrayList(document.pages))
                        processOcrForPage(document.activePageIndex)
                      },
                      label = { Text(filter.label, fontSize = 12.sp) },
                      colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = GenziiPurple,
                        selectedLabelColor = GenziiWhite
                      )
                    )
                  }
                }
              }
            }
          }
        }

        OcrViewTab.TEXT -> {
          // Extracted Text Editor & In-text Search
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              // Search in Text Input
              OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search inside extracted text") },
                placeholder = { Text("Type word to locate...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = GenziiPurple) },
                trailingIcon = {
                  if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                      Icon(Icons.Default.Close, contentDescription = "Clear")
                    }
                  }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
              )

              if (searchQuery.isNotBlank()) {
                val currentTextToSearch = if (combineAllPagesText) document.combinedText() else activePage?.extractedText ?: ""
                val matches = OcrEngine.searchInText(currentTextToSearch, searchQuery)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = if (matches.isNotEmpty()) "✓ Found ${matches.size} match(es) for '$searchQuery'" else "No matches found.",
                  fontSize = 12.sp,
                  color = if (matches.isNotEmpty()) GenziiPurple else MaterialTheme.colorScheme.error,
                  fontWeight = FontWeight.Medium
                )
              }

              Spacer(modifier = Modifier.height(12.dp))

              // Switch between single page text vs combined all pages text
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Text(
                    text = if (combineAllPagesText) "All Pages (${document.totalPages})" else "Page ${document.activePageIndex + 1} Only",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Switch(
                    checked = combineAllPagesText,
                    onCheckedChange = { combineAllPagesText = it },
                    modifier = Modifier.size(width = 44.dp, height = 24.dp)
                  )
                }

                // Copy Button
                Button(
                  onClick = {
                    val textToCopy = if (combineAllPagesText) document.combinedText() else activePage?.extractedText ?: ""
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("OCR Extracted Text", textToCopy))
                    Toast.makeText(context, "Extracted text copied to clipboard!", Toast.LENGTH_SHORT).show()
                  },
                  colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple),
                  shape = RoundedCornerShape(10.dp),
                  contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                  Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Copy", fontSize = 12.sp)
                }
              }

              Spacer(modifier = Modifier.height(10.dp))

              // Editable Text Field
              val currentDisplayContent = if (combineAllPagesText) {
                document.combinedText()
              } else {
                activePage?.extractedText ?: ""
              }

              OutlinedTextField(
                value = currentDisplayContent,
                onValueChange = { edited ->
                  if (!combineAllPagesText && activePage != null) {
                    activePage.extractedText = edited
                    document = document.copy(pages = ArrayList(document.pages))
                  }
                },
                readOnly = combineAllPagesText, // Only allow direct editing in single page mode
                label = { Text(if (combineAllPagesText) "Combined Document Text" else "Editable Text (Page ${document.activePageIndex + 1})") },
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 200.dp, max = 340.dp)
                  .testTag("ocr_extracted_text_field"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                  focusedBorderColor = GenziiPurple,
                  unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
              )

              Spacer(modifier = Modifier.height(8.dp))

              // Word count and Character statistics
              val wordCount = currentDisplayContent.split(Regex("\\s+")).filter { it.isNotBlank() && !it.startsWith("---") }.size
              val charCount = currentDisplayContent.length
              Text(
                text = "Stats: $wordCount words • $charCount characters",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }

        OcrViewTab.COMPARE -> {
          // Side-by-side / Stacked Visual Comparison between original document and extracted text
          Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(14.dp)) {
              Text(
                text = "Verification: Original Document vs Extracted OCR",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = GenziiPurple
              )
              Spacer(modifier = Modifier.height(8.dp))

              // Original Document preview
              Text("1. Document Visual:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
              if (activePage != null) {
                Box(
                  modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1A33)),
                  contentAlignment = Alignment.Center
                ) {
                  Image(
                    bitmap = activePage.currentBitmap.asImageBitmap(),
                    contentDescription = "Original scan preview",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                  )
                }
              }

              Spacer(modifier = Modifier.height(12.dp))

              // Recognized transcript preview
              Text("2. Recognized Transcript:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
              Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 120.dp, max = 200.dp)
              ) {
                Column(
                  modifier = Modifier
                    .padding(10.dp)
                    .verticalScroll(rememberScrollState())
                ) {
                  Text(
                    text = activePage?.extractedText?.ifBlank { "No text recognized on this page." } ?: "",
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                  )
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Bottom Primary Action: Save to Secure Vault & Export
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        // Save to Genzii Vault
        Button(
          onClick = {
            val fullText = document.combinedText().ifBlank { "Scanned document text" }
            val fileName = "${document.title}.pdf"
            GenziiRepository.addImportedDocument(
              name = fileName,
              sizeFormatted = "${(fullText.length * 2) / 1024 + 1} KB",
              sizeBytes = (fullText.length * 2L) + 1024L,
              fileType = FileType.PDF,
              content = fullText,
              folder = "Vault"
            )
            Toast.makeText(context, "Saved & encrypted in Genzii Secure Vault!", Toast.LENGTH_SHORT).show()
          },
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("ocr_save_vault_button"),
          colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple),
          shape = RoundedCornerShape(14.dp)
        ) {
          Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Save to Vault", fontWeight = FontWeight.SemiBold)
        }

        // Export as Searchable PDF
        OutlinedButton(
          onClick = {
            createPdfLauncher.launch("${document.title}.pdf")
          },
          modifier = Modifier
            .weight(1f)
            .height(48.dp)
            .testTag("ocr_save_pdf_action"),
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.5.dp, GenziiPurple)
        ) {
          Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = GenziiPurple, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Export PDF", color = GenziiPurple, fontWeight = FontWeight.SemiBold)
        }
      }

      Spacer(modifier = Modifier.height(28.dp))
    }

    // Rename Document Dialog
    if (showRenameDialog) {
      AlertDialog(
        onDismissRequest = { showRenameDialog = false },
        title = { Text("Rename Scanned Document", fontWeight = FontWeight.Bold) },
        text = {
          OutlinedTextField(
            value = renameInput,
            onValueChange = { renameInput = it },
            label = { Text("Document Title") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
          )
        },
        confirmButton = {
          Button(
            onClick = {
              if (renameInput.isNotBlank()) {
                document = document.copy(title = renameInput.trim())
              }
              showRenameDialog = false
            },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
          ) {
            Text("Save")
          }
        },
        dismissButton = {
          TextButton(onClick = { showRenameDialog = false }) {
            Text("Cancel")
          }
        }
      )
    }

    // Export PDF Success Dialog
    if (showExportSuccessDialog) {
      AlertDialog(
        onDismissRequest = { showExportSuccessDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GenziiPurple)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Searchable PDF Exported", fontWeight = FontWeight.Bold)
          }
        },
        text = {
          Text("Your multi-page searchable PDF document has been created successfully with high-resolution page scans and embedded selectable text layer.\n\n$exportedPdfPath")
        },
        confirmButton = {
          Button(
            onClick = { showExportSuccessDialog = false },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
          ) {
            Text("Done")
          }
        }
      )
    }

    // Crop & Straighten Adjustment Dialog
    if (showCropAdjustDialog && activePage != null) {
      AlertDialog(
        onDismissRequest = { showCropAdjustDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Crop, contentDescription = null, tint = GenziiPurple)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Crop, Straighten & Align", fontWeight = FontWeight.Bold, fontSize = 16.sp)
          }
        },
        text = {
          Column {
            Text(
              text = "Straighten Angle: ${if (fineStraightenAngle > 0) "+" else ""}${fineStraightenAngle.toInt()}°",
              fontWeight = FontWeight.SemiBold,
              fontSize = 13.sp
            )
            Slider(
              value = fineStraightenAngle,
              onValueChange = { fineStraightenAngle = it },
              valueRange = -15f..15f,
              steps = 29,
              colors = SliderDefaults.colors(thumbColor = GenziiPurple, activeTrackColor = GenziiPurple)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = "Border Inset Margin: ${(manualCropInset * 100).toInt()}%",
              fontWeight = FontWeight.SemiBold,
              fontSize = 13.sp
            )
            Slider(
              value = manualCropInset,
              onValueChange = { manualCropInset = it },
              valueRange = 0f..0.20f,
              steps = 19,
              colors = SliderDefaults.colors(thumbColor = GenziiPurple, activeTrackColor = GenziiPurple)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text("Quick Margin Presets:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              FilterChip(
                selected = manualCropInset == 0f,
                onClick = { manualCropInset = 0f },
                label = { Text("Full (0%)", fontSize = 11.sp) }
              )
              FilterChip(
                selected = manualCropInset == 0.04f,
                onClick = { manualCropInset = 0.04f },
                label = { Text("Clean (4%)", fontSize = 11.sp) }
              )
              FilterChip(
                selected = manualCropInset == 0.10f,
                onClick = { manualCropInset = 0.10f },
                label = { Text("Tight (10%)", fontSize = 11.sp) }
              )
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              val currentBaseDegrees = (activePage.rotationDegrees / 90) * 90
              activePage.rotationDegrees = (currentBaseDegrees + fineStraightenAngle.toInt() + 360) % 360
              activePage.cropRectRatio = RectF(
                manualCropInset,
                manualCropInset,
                1f - manualCropInset,
                1f - manualCropInset
              )
              ImageProcessingUtils.reprocessPage(activePage)
              document = document.copy(pages = ArrayList(document.pages))
              processOcrForPage(document.activePageIndex)
              showCropAdjustDialog = false
              Toast.makeText(context, "Adjustments applied & page re-scanned", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
          ) {
            Text("Apply & Scan")
          }
        },
        dismissButton = {
          TextButton(onClick = { showCropAdjustDialog = false }) {
            Text("Cancel")
          }
        }
      )
    }
  }
}

/**
 * Creates an initial sample scanned academic page bitmap so when opening the OCR tool,
 * the user can immediately test crop, rotate, contrast filters, ML Kit OCR, and PDF export.
 */
private fun createSampleScannedPage(): ScannedPage {
  val width = 600
  val height = 800
  val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  val canvas = Canvas(bmp)

  // Paper color background
  canvas.drawColor(AndroidColor.rgb(250, 249, 246))

  // Draw header line
  val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = AndroidColor.rgb(40, 20, 80)
    textSize = 28f
    isFakeBoldText = true
  }
  canvas.drawText("GENZII ACADEMIC RESEARCH", 50f, 80f, headerPaint)

  val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = AndroidColor.rgb(100, 100, 120)
    textSize = 16f
  }
  canvas.drawText("Section 4.1: Empirical Robustness & Baseline Evaluation", 50f, 115f, subPaint)

  // Draw divider
  val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = AndroidColor.rgb(180, 170, 200)
    strokeWidth = 2f
  }
  canvas.drawLine(50f, 135f, 550f, 135f, linePaint)

  // Draw paragraph text lines
  val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = AndroidColor.rgb(30, 30, 40)
    textSize = 18f
  }
  val textLines = listOf(
    "The empirical baseline demonstrates high robustness across multilingual",
    "evaluation benches without requiring explicit fine-tuning per domain.",
    "Comprehensive verification validates that zero-knowledge architectures",
    "preserve user document integrity while maintaining sub-second latency.",
    "",
    "Experimental parameters were configured across three benchmark cohorts:",
    "  1. Originality score convergence: 96.4% precision rate.",
    "  2. Optical character recognition accuracy: 99.2% character fidelity.",
    "  3. On-device local processing latency: 142ms average response time.",
    "",
    "Conclusion: Decentralized local intelligence guarantees absolute privacy",
    "and eliminates external telemetry vectors during document analysis."
  )

  var y = 175f
  for (line in textLines) {
    if (line.isNotBlank()) {
      canvas.drawText(line, 50f, y, bodyPaint)
    }
    y += 28f
  }

  val sampleExtractedText = """
GENZII ACADEMIC RESEARCH
Section 4.1: Empirical Robustness & Baseline Evaluation

The empirical baseline demonstrates high robustness across multilingual evaluation benches without requiring explicit fine-tuning per domain. Comprehensive verification validates that zero-knowledge architectures preserve user document integrity while maintaining sub-second latency.

Experimental parameters were configured across three benchmark cohorts:
  1. Originality score convergence: 96.4% precision rate.
  2. Optical character recognition accuracy: 99.2% character fidelity.
  3. On-device local processing latency: 142ms average response time.

Conclusion: Decentralized local intelligence guarantees absolute privacy and eliminates external telemetry vectors during document analysis.
  """.trimIndent()

  return ScannedPage(
    originalBitmap = bmp,
    currentBitmap = bmp,
    extractedText = sampleExtractedText,
    isOcrCompleted = true
  )
}
