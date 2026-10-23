package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.outlined.CallMerge
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.RotateRight
import androidx.compose.material.icons.automirrored.outlined.BrandingWatermark
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.data.GenziiRepository
import com.example.model.FileType
import com.example.model.UrlScanResult
import com.example.network.GeminiService
import com.example.ui.theme.*
import com.example.util.FileUtils
import com.example.util.PdfCompressionResult
import com.example.util.PdfOperationResult
import com.example.util.PdfToolsEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ToolGridItem(
  val title: String,
  val icon: ImageVector,
  val description: String
)

@Composable
fun PdfToolsScreen(onBack: () -> Unit, onNavigateToOcr: () -> Unit = {}) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  var isProcessing by remember { mutableStateOf(false) }
  var statusMessage by remember { mutableStateOf<String?>(null) }

  // Tool dialog states
  var showMergeDialog by remember { mutableStateOf(false) }
  var mergeUris by remember { mutableStateOf<List<Uri>>(emptyList()) }

  var showSplitDialog by remember { mutableStateOf(false) }
  var splitUri by remember { mutableStateOf<Uri?>(null) }
  var splitPageCount by remember { mutableIntStateOf(1) }
  var splitRangeText by remember { mutableStateOf("1") }

  var showCompressDialog by remember { mutableStateOf(false) }
  var compressUri by remember { mutableStateOf<Uri?>(null) }
  var compressLevel by remember { mutableStateOf("Balanced") }

  var showRotateDialog by remember { mutableStateOf(false) }
  var rotateUri by remember { mutableStateOf<Uri?>(null) }
  var rotateAngle by remember { mutableIntStateOf(90) }

  var showWatermarkDialog by remember { mutableStateOf(false) }
  var watermarkUri by remember { mutableStateOf<Uri?>(null) }
  var watermarkText by remember { mutableStateOf("CONFIDENTIAL") }

  var showConvertChoiceDialog by remember { mutableStateOf(false) }

  // Result dialog states
  var operationResult by remember { mutableStateOf<PdfOperationResult?>(null) }
  var compressionResult by remember { mutableStateOf<PdfCompressionResult?>(null) }
  var showSuccessDialog by remember { mutableStateOf(false) }
  var activeExportFile by remember { mutableStateOf<File?>(null) }

  val tools = listOf(
    ToolGridItem("Merge PDF", Icons.AutoMirrored.Outlined.CallMerge, "Combine multiple documents into one"),
    ToolGridItem("Split PDF", Icons.AutoMirrored.Outlined.CallSplit, "Extract specific page ranges"),
    ToolGridItem("Compress", Icons.Outlined.Compress, "Reduce file size up to 70%"),
    ToolGridItem("Convert", Icons.Outlined.Transform, "Convert photos to PDF or extract text"),
    ToolGridItem("Rotate", Icons.AutoMirrored.Outlined.RotateRight, "Orient portrait/landscape pages"),
    ToolGridItem("Add Watermark", Icons.AutoMirrored.Outlined.BrandingWatermark, "Stamp security or copyright notice")
  )

  // SAF Multiple PDF Picker for Merge
  val mergePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenMultipleDocuments()
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      mergeUris = uris
      showMergeDialog = true
    }
  }

  // Single PDF Picker for Split, Compress, Rotate, Watermark, and PDF-to-Text
  var pendingAction by remember { mutableStateOf("") }
  val singlePdfPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      when (pendingAction) {
        "Split PDF" -> {
          splitUri = uri
          splitPageCount = PdfToolsEngine.getPdfPageCount(context, uri)
          splitRangeText = if (splitPageCount > 1) "1-2" else "1"
          showSplitDialog = true
        }
        "Compress" -> {
          compressUri = uri
          showCompressDialog = true
        }
        "Rotate" -> {
          rotateUri = uri
          showRotateDialog = true
        }
        "Add Watermark" -> {
          watermarkUri = uri
          showWatermarkDialog = true
        }
        "PDF to Text" -> {
          coroutineScope.launch {
            isProcessing = true
            statusMessage = "Extracting text transcript from PDF..."
            val fileInfo = FileUtils.readPickedFile(context, uri)
            val targetFile = File(context.cacheDir, "Genzii_Transcript_${System.currentTimeMillis()}.txt")
            targetFile.writeText(fileInfo.content)
            GenziiRepository.addImportedDocument(
              name = targetFile.name,
              sizeFormatted = "${(targetFile.length() / 1024).coerceAtLeast(1)} KB",
              sizeBytes = targetFile.length(),
              fileType = FileType.TXT,
              content = fileInfo.content
            )
            isProcessing = false
            activeExportFile = targetFile
            operationResult = PdfOperationResult(
              outputFile = targetFile,
              pageCount = 1,
              fileSizeBytes = targetFile.length(),
              summary = "Extracted searchable text transcript and saved to Secure Vault."
            )
            showSuccessDialog = true
          }
        }
      }
    }
  }

  // Photo Picker for Images to PDF
  val imagesPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickMultipleVisualMedia()
  ) { uris: List<Uri> ->
    if (uris.isNotEmpty()) {
      coroutineScope.launch {
        isProcessing = true
        statusMessage = "Compiling ${uris.size} images into unified PDF document..."
        val result = PdfToolsEngine.imagesToPdf(context, uris)
        isProcessing = false
        if (result.isSuccess) {
          val res = result.getOrThrow()
          activeExportFile = res.outputFile
          operationResult = res
          compressionResult = null
          showSuccessDialog = true
        } else {
          Toast.makeText(context, "Error: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  // SAF Export Document
  val exportFileLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/pdf")
  ) { destUri: Uri? ->
    if (destUri != null && activeExportFile != null) {
      coroutineScope.launch(Dispatchers.IO) {
        try {
          context.contentResolver.openOutputStream(destUri)?.use { out ->
            activeExportFile!!.inputStream().use { input -> input.copyTo(out) }
          }
          withContext(Dispatchers.Main) {
            Toast.makeText(context, "Document exported successfully!", Toast.LENGTH_SHORT).show()
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
          }
        }
      }
    }
  }

  Scaffold(
    topBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "PDF Tools",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
      }
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp)
    ) {
      Text(
        text = "All PDF tools in one place",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Feature Banner: OCR Document Scanner
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .clickable { onNavigateToOcr() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = GenziiPurpleContainer.copy(alpha = 0.6f))
      ) {
        Row(
          modifier = Modifier.padding(14.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Box(
            modifier = Modifier
              .size(44.dp)
              .clip(RoundedCornerShape(12.dp))
              .background(GenziiPurple),
            contentAlignment = Alignment.Center
          ) {
            Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = GenziiWhite, modifier = Modifier.size(24.dp))
          }
          Spacer(modifier = Modifier.width(12.dp))
          Column(modifier = Modifier.weight(1f)) {
            Text("OCR Camera Document Scanner", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = GenziiPurple)
            Text("Scan paper, crop, auto-detect edges & ML Kit OCR", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Icon(Icons.Default.ChevronRight, contentDescription = "Open Scanner", tint = GenziiPurple)
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      if (isProcessing) {
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
        ) {
          Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = GenziiPurple, strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Text(statusMessage ?: "Processing document...", fontSize = 13.sp, color = GenziiPurple, fontWeight = FontWeight.Medium)
          }
        }
      }

      LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.weight(1f)
      ) {
        items(tools) { item ->
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .height(130.dp)
              .clip(RoundedCornerShape(16.dp))
              .clickable {
                when (item.title) {
                  "Merge PDF" -> mergePickerLauncher.launch(arrayOf("application/pdf"))
                  "Split PDF" -> {
                    pendingAction = "Split PDF"
                    singlePdfPickerLauncher.launch(arrayOf("application/pdf"))
                  }
                  "Compress" -> {
                    pendingAction = "Compress"
                    singlePdfPickerLauncher.launch(arrayOf("application/pdf"))
                  }
                  "Convert" -> showConvertChoiceDialog = true
                  "Rotate" -> {
                    pendingAction = "Rotate"
                    singlePdfPickerLauncher.launch(arrayOf("application/pdf"))
                  }
                  "Add Watermark" -> {
                    pendingAction = "Add Watermark"
                    singlePdfPickerLauncher.launch(arrayOf("application/pdf"))
                  }
                }
              },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
          ) {
            Column(
              modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
              verticalArrangement = Arrangement.SpaceBetween
            ) {
              Box(
                modifier = Modifier
                  .size(38.dp)
                  .clip(RoundedCornerShape(10.dp))
                  .background(GenziiPurpleContainer),
                contentAlignment = Alignment.Center
              ) {
                Icon(item.icon, contentDescription = null, tint = GenziiPurple, modifier = Modifier.size(20.dp))
              }
              Column {
                Text(item.title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(item.description, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
              }
            }
          }
        }
      }
    }

    // 1. Merge PDF Dialog
    if (showMergeDialog && mergeUris.isNotEmpty()) {
      AlertDialog(
        onDismissRequest = { showMergeDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.CallMerge, contentDescription = null, tint = GenziiPurple)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Merge ${mergeUris.size} Documents", fontWeight = FontWeight.Bold, fontSize = 16.sp)
          }
        },
        text = {
          Column {
            Text("Combine the following selected PDF files into one continuous document:", fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier.fillMaxWidth().heightIn(max = 140.dp)
            ) {
              Column(modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState())) {
                mergeUris.forEachIndexed { idx, uri ->
                  Text("${idx + 1}. ${uri.lastPathSegment ?: "Document_${idx + 1}.pdf"}", fontSize = 11.sp, maxLines = 1)
                }
              }
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              showMergeDialog = false
              coroutineScope.launch {
                isProcessing = true
                statusMessage = "Merging ${mergeUris.size} PDF files..."
                val result = PdfToolsEngine.mergePdfs(context, mergeUris)
                isProcessing = false
                if (result.isSuccess) {
                  val res = result.getOrThrow()
                  activeExportFile = res.outputFile
                  operationResult = res
                  compressionResult = null
                  showSuccessDialog = true
                } else {
                  Toast.makeText(context, "Merge failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
          ) {
            Text("Merge Now")
          }
        },
        dismissButton = {
          TextButton(onClick = { showMergeDialog = false }) {
            Text("Cancel")
          }
        }
      )
    }

    // 2. Split PDF Dialog
    if (showSplitDialog && splitUri != null) {
      AlertDialog(
        onDismissRequest = { showSplitDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.CallSplit, contentDescription = null, tint = GenziiPurple)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Split PDF (Total $splitPageCount Pages)", fontWeight = FontWeight.Bold, fontSize = 16.sp)
          }
        },
        text = {
          Column {
            Text("Specify page numbers or range to extract (e.g. 1-3 or 1,2):", fontSize = 12.sp)
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
              value = splitRangeText,
              onValueChange = { splitRangeText = it },
              label = { Text("Page Range") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              AssistChip(
                onClick = { splitRangeText = "1" },
                label = { Text("Page 1", fontSize = 11.sp) }
              )
              if (splitPageCount > 1) {
                AssistChip(
                  onClick = { splitRangeText = "1-${(splitPageCount / 2).coerceAtLeast(1)}" },
                  label = { Text("First Half", fontSize = 11.sp) }
                )
                AssistChip(
                  onClick = { splitRangeText = "1-$splitPageCount" },
                  label = { Text("All", fontSize = 11.sp) }
                )
              }
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              showSplitDialog = false
              val pages = parsePageRange(splitRangeText, splitPageCount)
              coroutineScope.launch {
                isProcessing = true
                statusMessage = "Extracting ${pages.size} pages..."
                val result = PdfToolsEngine.splitPdf(context, splitUri!!, pages)
                isProcessing = false
                if (result.isSuccess) {
                  val res = result.getOrThrow()
                  activeExportFile = res.outputFile
                  operationResult = res
                  compressionResult = null
                  showSuccessDialog = true
                } else {
                  Toast.makeText(context, "Split failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
          ) {
            Text("Extract Pages")
          }
        },
        dismissButton = {
          TextButton(onClick = { showSplitDialog = false }) {
            Text("Cancel")
          }
        }
      )
    }

    // 3. Compress PDF Dialog
    if (showCompressDialog && compressUri != null) {
      AlertDialog(
        onDismissRequest = { showCompressDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Compress, contentDescription = null, tint = GenziiPurple)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Compress PDF", fontWeight = FontWeight.Bold, fontSize = 16.sp)
          }
        },
        text = {
          Column {
            Text("Select compression intensity to reduce file size:", fontSize = 12.sp)
            Spacer(modifier = Modifier.height(10.dp))
            listOf("Balanced", "High", "Light").forEach { level ->
              val desc = when (level) {
                "Balanced" -> "70% quality • ~40% size reduction (Recommended)"
                "High" -> "55% quality • ~65% size reduction (Smallest file)"
                else -> "85% quality • ~20% size reduction (Best clarity)"
              }
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(8.dp))
                  .clickable { compressLevel = level }
                  .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                RadioButton(selected = compressLevel == level, onClick = { compressLevel = level })
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                  Text(level, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                  Text(desc, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              showCompressDialog = false
              coroutineScope.launch {
                isProcessing = true
                statusMessage = "Compressing PDF with $compressLevel preset..."
                val result = PdfToolsEngine.compressPdf(context, compressUri!!, compressLevel)
                isProcessing = false
                if (result.isSuccess) {
                  val res = result.getOrThrow()
                  activeExportFile = res.outputFile
                  compressionResult = res
                  operationResult = null
                  showSuccessDialog = true
                } else {
                  Toast.makeText(context, "Compression failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
          ) {
            Text("Compress Document")
          }
        },
        dismissButton = {
          TextButton(onClick = { showCompressDialog = false }) {
            Text("Cancel")
          }
        }
      )
    }

    // 4. Convert Choice Dialog
    if (showConvertChoiceDialog) {
      AlertDialog(
        onDismissRequest = { showConvertChoiceDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Transform, contentDescription = null, tint = GenziiPurple)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Convert Document", fontWeight = FontWeight.Bold, fontSize = 16.sp)
          }
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                  showConvertChoiceDialog = false
                  imagesPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
              colors = CardDefaults.cardColors(containerColor = GenziiPurpleContainer.copy(alpha = 0.4f))
            ) {
              Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhotoLibrary, contentDescription = null, tint = GenziiPurple)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text("Photos / Scans to PDF", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                  Text("Combine camera photos & scans into clean PDF", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }

            Card(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable {
                  showConvertChoiceDialog = false
                  pendingAction = "PDF to Text"
                  singlePdfPickerLauncher.launch(arrayOf("application/pdf"))
                },
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
              Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.TextFields, contentDescription = null, tint = GenziiPurple)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text("PDF to Text Transcript", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                  Text("Extract all text transcript into editable .txt", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        },
        confirmButton = {},
        dismissButton = {
          TextButton(onClick = { showConvertChoiceDialog = false }) {
            Text("Cancel")
          }
        }
      )
    }

    // 5. Rotate Dialog
    if (showRotateDialog && rotateUri != null) {
      AlertDialog(
        onDismissRequest = { showRotateDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.RotateRight, contentDescription = null, tint = GenziiPurple)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Rotate PDF", fontWeight = FontWeight.Bold, fontSize = 16.sp)
          }
        },
        text = {
          Column {
            Text("Select rotation angle for all pages:", fontSize = 12.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilterChip(
                selected = rotateAngle == 90,
                onClick = { rotateAngle = 90 },
                label = { Text("90° Clockwise", fontSize = 11.sp) }
              )
              FilterChip(
                selected = rotateAngle == 180,
                onClick = { rotateAngle = 180 },
                label = { Text("180° Flip", fontSize = 11.sp) }
              )
              FilterChip(
                selected = rotateAngle == 270,
                onClick = { rotateAngle = 270 },
                label = { Text("270° (90° CCW)", fontSize = 11.sp) }
              )
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              showRotateDialog = false
              coroutineScope.launch {
                isProcessing = true
                statusMessage = "Rotating document pages by $rotateAngle°..."
                val result = PdfToolsEngine.rotatePdf(context, rotateUri!!, rotateAngle)
                isProcessing = false
                if (result.isSuccess) {
                  val res = result.getOrThrow()
                  activeExportFile = res.outputFile
                  operationResult = res
                  compressionResult = null
                  showSuccessDialog = true
                } else {
                  Toast.makeText(context, "Rotation failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
          ) {
            Text("Rotate Document")
          }
        },
        dismissButton = {
          TextButton(onClick = { showRotateDialog = false }) {
            Text("Cancel")
          }
        }
      )
    }

    // 6. Watermark Dialog
    if (showWatermarkDialog && watermarkUri != null) {
      AlertDialog(
        onDismissRequest = { showWatermarkDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.BrandingWatermark, contentDescription = null, tint = GenziiPurple)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add Watermark", fontWeight = FontWeight.Bold, fontSize = 16.sp)
          }
        },
        text = {
          Column {
            Text("Enter watermark text stamp to apply across all pages:", fontSize = 12.sp)
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
              value = watermarkText,
              onValueChange = { watermarkText = it },
              label = { Text("Watermark Text") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
              AssistChip(onClick = { watermarkText = "CONFIDENTIAL" }, label = { Text("CONFIDENTIAL", fontSize = 10.sp) })
              AssistChip(onClick = { watermarkText = "DRAFT" }, label = { Text("DRAFT", fontSize = 10.sp) })
              AssistChip(onClick = { watermarkText = "DO NOT COPY" }, label = { Text("DO NOT COPY", fontSize = 10.sp) })
            }
          }
        },
        confirmButton = {
          Button(
            onClick = {
              showWatermarkDialog = false
              coroutineScope.launch {
                isProcessing = true
                statusMessage = "Stamping \"$watermarkText\" watermark..."
                val result = PdfToolsEngine.watermarkPdf(context, watermarkUri!!, watermarkText)
                isProcessing = false
                if (result.isSuccess) {
                  val res = result.getOrThrow()
                  activeExportFile = res.outputFile
                  operationResult = res
                  compressionResult = null
                  showSuccessDialog = true
                } else {
                  Toast.makeText(context, "Watermarking failed: ${result.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
                }
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
          ) {
            Text("Stamp Watermark")
          }
        },
        dismissButton = {
          TextButton(onClick = { showWatermarkDialog = false }) {
            Text("Cancel")
          }
        }
      )
    }

    // Success & Result Action Dialog
    if (showSuccessDialog && (operationResult != null || compressionResult != null)) {
      val file = activeExportFile ?: operationResult?.outputFile ?: compressionResult?.outputFile
      AlertDialog(
        onDismissRequest = { showSuccessDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GenziiPurple)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Operation Successful", fontWeight = FontWeight.Bold, fontSize = 16.sp)
          }
        },
        text = {
          Column {
            if (compressionResult != null) {
              val comp = compressionResult!!
              Text("Compression completed:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
              Spacer(modifier = Modifier.height(4.dp))
              Text("• Original Size: ${formatBytes(comp.originalSizeBytes)}", fontSize = 12.sp)
              Text("• Compressed Size: ${formatBytes(comp.compressedSizeBytes)}", fontSize = 12.sp, color = GenziiPurple, fontWeight = FontWeight.Bold)
              Text("• Reduction: -${comp.reductionPercentage}% across ${comp.pageCount} pages", fontSize = 12.sp)
            } else if (operationResult != null) {
              val op = operationResult!!
              Text(op.summary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
              Spacer(modifier = Modifier.height(4.dp))
              Text("• File: ${op.outputFile.name}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
              Text("• Size: ${formatBytes(op.fileSizeBytes)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        },
        confirmButton = {
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
              onClick = {
                file?.let { target ->
                  val vaultDir = File(context.filesDir, "vault_documents").apply { mkdirs() }
                  val vaultFile = File(vaultDir, "${System.currentTimeMillis()}_${target.name}")
                  try {
                    target.copyTo(vaultFile, overwrite = true)
                  } catch (e: Exception) {
                    Log.e("ToolScreens", "Could not copy to vault_documents", e)
                  }
                  val persistentPath = if (vaultFile.exists()) vaultFile.absolutePath else target.absolutePath
                  var pages = 1
                  if (target.name.endsWith(".pdf", ignoreCase = true)) {
                    try {
                      val pfd = ParcelFileDescriptor.open(vaultFile.takeIf { it.exists() } ?: target, ParcelFileDescriptor.MODE_READ_ONLY)
                      val renderer = PdfRenderer(pfd)
                      pages = renderer.pageCount.coerceAtLeast(1)
                      renderer.close()
                      pfd.close()
                    } catch (_: Exception) {}
                  }

                  GenziiRepository.addImportedDocument(
                    name = target.name,
                    sizeFormatted = formatBytes(target.length()),
                    sizeBytes = target.length(),
                    fileType = if (target.name.endsWith(".txt")) FileType.TXT else FileType.PDF,
                    content = if (target.name.endsWith(".txt")) target.readText() else "Processed PDF document: ${target.name}",
                    folder = "Vault",
                    localFilePath = persistentPath,
                    pageCount = pages
                  )
                  Toast.makeText(context, "Saved to Secure Vault!", Toast.LENGTH_SHORT).show()
                }
              }
            ) {
              Text("Save to Vault", fontSize = 11.sp)
            }

            Button(
              onClick = {
                file?.let {
                  exportFileLauncher.launch(it.name)
                }
              },
              colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
            ) {
              Text("Export File", fontSize = 11.sp)
            }
          }
        },
        dismissButton = {
          TextButton(onClick = { showSuccessDialog = false }) {
            Text("Done")
          }
        }
      )
    }
  }
}

/**
 * Parses user page range input such as "1-3, 5" or "2,4" into 1-indexed integers.
 */
private fun parsePageRange(rangeStr: String, maxPages: Int): List<Int> {
  val pages = mutableSetOf<Int>()
  val parts = rangeStr.split(",")
  for (part in parts) {
    val trimmed = part.trim()
    if (trimmed.contains("-")) {
      val range = trimmed.split("-")
      val start = range.getOrNull(0)?.toIntOrNull() ?: 1
      val end = range.getOrNull(1)?.toIntOrNull() ?: maxPages
      for (p in start.coerceAtLeast(1)..end.coerceAtMost(maxPages)) {
        pages.add(p)
      }
    } else {
      trimmed.toIntOrNull()?.let {
        if (it in 1..maxPages) pages.add(it)
      }
    }
  }
  return if (pages.isEmpty()) (1..maxPages.coerceAtMost(2)).toList() else pages.sorted()
}

private fun formatBytes(bytes: Long): String {
  return when {
    bytes > 1024 * 1024 -> String.format("%.2f MB", bytes.toDouble() / (1024.0 * 1024.0))
    bytes > 1024 -> "${bytes / 1024} KB"
    else -> "$bytes B"
  }
}

@Composable
fun UrlCheckerScreen(onBack: () -> Unit) {
  var urlInput by remember { mutableStateOf("https://example.com/research-paper") }
  var scanResult by remember { mutableStateOf<UrlScanResult?>(null) }
  var isScanning by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "URL Checker",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
      }
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp)
    ) {
      Text(
        text = "Check URLs for safety, authenticity & academic integrity",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(16.dp))

      OutlinedTextField(
        value = urlInput,
        onValueChange = { urlInput = it },
        label = { Text("Website or Article URL") },
        leadingIcon = { Icon(Icons.Default.Link, contentDescription = null, tint = GenziiPurple) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp)
      )

      Spacer(modifier = Modifier.height(14.dp))

      Button(
        onClick = {
          if (urlInput.isNotBlank()) {
            scanResult = GenziiRepository.scanUrl(urlInput)
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp),
        colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple),
        shape = RoundedCornerShape(14.dp)
      ) {
        Text("Scan URL", fontWeight = FontWeight.Bold)
      }

      Spacer(modifier = Modifier.height(20.dp))

      scanResult?.let { result ->
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(
                modifier = Modifier
                  .size(36.dp)
                  .clip(CircleShape)
                  .background(if (result.isSafe) GenziiSuccess.copy(alpha = 0.15f) else GenziiError.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = if (result.isSafe) Icons.Default.CheckCircle else Icons.Default.Warning,
                  contentDescription = null,
                  tint = if (result.isSafe) GenziiSuccess else GenziiError
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = if (result.isSafe) "Safe & Verified" else "Caution Advised",
                  fontWeight = FontWeight.Bold,
                  fontSize = 16.sp,
                  color = if (result.isSafe) GenziiSuccess else GenziiError
                )
                Text(
                  text = result.url,
                  fontSize = 12.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            SafetyCheckRow("No malware detected", result.noMalware)
            SafetyCheckRow("Not a phishing site", result.notPhishing)
            SafetyCheckRow("SSL certificate valid", result.sslValid)
            SafetyCheckRow("Originality index rating (${result.originalityPercentage}%)", true)
          }
        }
      }
    }
  }
}

@Composable
fun SafetyCheckRow(text: String, isPassed: Boolean) {
  Row(
    modifier = Modifier.padding(vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically
  ) {
    Icon(
      imageVector = if (isPassed) Icons.Default.Check else Icons.Default.Close,
      contentDescription = null,
      tint = if (isPassed) GenziiSuccess else GenziiError,
      modifier = Modifier.size(16.dp)
    )
    Spacer(modifier = Modifier.width(8.dp))
    Text(text, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
  }
}

@Composable
fun AiWritingToolsScreen(onBack: () -> Unit) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val clipboardManager = LocalClipboardManager.current
  val userProfile by GenziiRepository.userProfile.collectAsState()

  var selectedTool by remember { mutableStateOf("Paraphraser") }
  var sourceText by remember {
    mutableStateOf("Due to the fact that transformers are scaling up quickly, memory throughput is an issue.")
  }
  var transformedText by remember {
    mutableStateOf("As transformer models rapidly scale, memory throughput presents a critical bottleneck.")
  }
  var isTransforming by remember { mutableStateOf(false) }

  val tools = listOf("Essay Writer", "Paraphraser", "Grammar Checker", "Summarizer", "Tone Changer", "Content Generator")

  Scaffold(
    topBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Writing Tools",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
      }
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp)
        .verticalScroll(rememberScrollState())
    ) {
      // Tool selection chips
      LazyColumn(
        modifier = Modifier.height(160.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        items(tools) { tool ->
          val isSelected = selectedTool == tool
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable { selectedTool = tool },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
              containerColor = if (isSelected) GenziiPurpleContainer else MaterialTheme.colorScheme.surface
            )
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = tool,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) GenziiPurple else MaterialTheme.colorScheme.onBackground
              )
              Icon(Icons.Default.ChevronRight, contentDescription = null, tint = GenziiPurple)
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      OutlinedTextField(
        value = sourceText,
        onValueChange = { sourceText = it },
        label = { Text("Input Text for $selectedTool") },
        modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp),
        shape = RoundedCornerShape(14.dp)
      )

      Spacer(modifier = Modifier.height(12.dp))

      Button(
        onClick = {
          if (sourceText.isNotBlank()) {
            isTransforming = true
            coroutineScope.launch {
              val result = GeminiService.generateWritingTransformation(
                tool = selectedTool,
                sourceText = sourceText,
                customApiKey = userProfile.customApiKey
              )
              transformedText = result
              isTransforming = false
            }
          }
        },
        enabled = !isTransforming && sourceText.isNotBlank(),
        modifier = Modifier.fillMaxWidth().height(48.dp),
        colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple),
        shape = RoundedCornerShape(12.dp)
      ) {
        if (isTransforming) {
          CircularProgressIndicator(modifier = Modifier.size(20.dp), color = GenziiWhite, strokeWidth = 2.dp)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Transforming...", fontWeight = FontWeight.Bold)
        } else {
          Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("Transform with $selectedTool", fontWeight = FontWeight.Bold)
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "$selectedTool Output",
              fontWeight = FontWeight.Bold,
              color = GenziiPurple,
              fontSize = 13.sp
            )
            Row {
              IconButton(onClick = {
                clipboardManager.setText(AnnotatedString(transformedText))
                Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
              }) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = GenziiPurple, modifier = Modifier.size(18.dp))
              }
              IconButton(onClick = {
                GenziiRepository.addImportedDocument(
                  name = "${selectedTool.replace(" ", "_")}_Output.txt",
                  sizeFormatted = "${(transformedText.length * 1.5).toInt()} B",
                  sizeBytes = transformedText.length.toLong(),
                  fileType = FileType.TXT,
                  content = transformedText
                )
                Toast.makeText(context, "Saved to Secure Vault!", Toast.LENGTH_SHORT).show()
              }) {
                Icon(Icons.Default.Save, contentDescription = "Save to Vault", tint = GenziiPurple, modifier = Modifier.size(18.dp))
              }
            }
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = transformedText,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onBackground
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))
    }
  }
}

@Composable
fun StudyAssistantScreen(onBack: () -> Unit) {
  var activeCardIndex by remember { mutableStateOf(0) }
  var isFlipped by remember { mutableStateOf(false) }

  val cards = GenziiRepository.flashcards
  val currentCard = cards[activeCardIndex]

  Scaffold(
    topBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(onClick = onBack) {
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Study Assistant",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
      }
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 20.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = "Interactive Flashcards & Quiz Generator",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text = "Card ${activeCardIndex + 1} of ${cards.size} • ${currentCard.category}",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = GenziiPurple
      )

      Spacer(modifier = Modifier.height(12.dp))

      // Flippable Flashcard
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .height(240.dp)
          .clip(RoundedCornerShape(20.dp))
          .clickable { isFlipped = !isFlipped },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
          containerColor = if (isFlipped) GenziiPurpleContainer else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
      ) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
              text = if (isFlipped) "ANSWER" else "QUESTION",
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              color = GenziiPurple
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
              text = if (isFlipped) currentCard.answer else currentCard.question,
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Medium,
              fontSize = 16.sp,
              lineHeight = 24.sp,
              color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
              text = "Tap card to flip",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Button(
          onClick = {
            isFlipped = false
            activeCardIndex = if (activeCardIndex > 0) activeCardIndex - 1 else cards.size - 1
          },
          colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
        ) {
          Text("Previous")
        }

        Button(
          onClick = {
            isFlipped = false
            activeCardIndex = (activeCardIndex + 1) % cards.size
          },
          colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
        ) {
          Text("Next Card")
        }
      }
    }
  }
}

@Composable
fun NotificationsScreen(onBack: () -> Unit) {
  val notifications by GenziiRepository.notifications.collectAsState()

  Scaffold(
    topBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Notifications",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }

        TextButton(onClick = { GenziiRepository.markAllNotificationsRead() }) {
          Text("Mark all read", color = GenziiPurple, fontSize = 12.sp)
        }
      }
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      items(notifications) { notif ->
        Card(
          shape = RoundedCornerShape(14.dp),
          colors = CardDefaults.cardColors(
            containerColor = if (notif.isUnread) GenziiPurpleContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
          ),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top
          ) {
            Box(
              modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(GenziiPurpleContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = when (notif.type) {
                  "check" -> Icons.Default.VerifiedUser
                  "vault" -> Icons.Default.Lock
                  "security" -> Icons.Default.Security
                  else -> Icons.Default.Notifications
                },
                contentDescription = null,
                tint = GenziiPurple,
                modifier = Modifier.size(18.dp)
              )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = notif.title,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground
              )
              Spacer(modifier = Modifier.height(2.dp))
              Text(
                text = notif.description,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = notif.timestamp,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
              )
            }
          }
        }
      }
    }
  }
}
