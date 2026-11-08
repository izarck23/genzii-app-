package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ads.GoogleAdsManager
import com.example.ads.GoogleTestBannerAd
import com.example.ads.GoogleTestInterstitialAdDialog
import com.example.ads.GoogleTestRewardedAdDialog
import com.example.data.GenziiRepository
import com.example.model.OriginalityReport
import com.example.model.SourceMatch
import com.example.network.GeminiService
import com.example.ui.components.OriginalityGauge
import com.example.ui.theme.*
import com.example.util.FileUtils
import com.example.util.PdfExportManager
import android.widget.Toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun OriginalityCheckerScreen(
  initialReportId: String? = null,
  onNavigateToVault: () -> Unit
) {
  val context = LocalContext.current
  val reports by GenziiRepository.reports.collectAsState()
  var selectedTab by remember { mutableStateOf(0) } // 0: Text, 1: Document
  var documentTitle by remember { mutableStateOf("") }
  var textInput by remember { mutableStateOf("") }
  var isScanning by remember { mutableStateOf(false) }
  var elapsedScanSeconds by remember { mutableIntStateOf(0) }
  var scanProgress by remember { mutableFloatStateOf(0f) }
  var activeReport by remember {
    mutableStateOf<OriginalityReport?>(
      if (initialReportId != null) reports[initialReportId] else null
    )
  }
  var showFullReportModal by remember { mutableStateOf(false) }
  var showRewardedAdDialog by remember { mutableStateOf(false) }
  var showInterstitialAdDialog by remember { mutableStateOf(false) }
  val userProfile by GenziiRepository.userProfile.collectAsState()
  val isGeminiLive = remember(userProfile.customApiKey) { GeminiService.isApiKeyConfigured(userProfile.customApiKey) }
  var scanErrorMessage by remember { mutableStateOf<String?>(null) }
  var showApiKeyDialog by remember { mutableStateOf(false) }

  val coroutineScope = rememberCoroutineScope()

  LaunchedEffect(isScanning) {
    if (isScanning) {
      elapsedScanSeconds = 0
      while (isScanning) {
        kotlinx.coroutines.delay(1000)
        elapsedScanSeconds++
      }
    }
  }

  val wordCount = remember(textInput) {
    textInput.split(Regex("\\s+")).filter { it.isNotBlank() }.size
  }

  val docPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      coroutineScope.launch {
        try {
          val fileInfo = FileUtils.readPickedFile(context, uri)
          documentTitle = fileInfo.name
          textInput = fileInfo.content
          Toast.makeText(context, "Loaded: ${fileInfo.name} (${fileInfo.content.length} chars)", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
          Toast.makeText(context, "Could not read file: ${e.message}", Toast.LENGTH_LONG).show()
        }
      }
    }
  }

  val exportReportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument("application/pdf")
  ) { uri: Uri? ->
    if (uri != null && activeReport != null) {
      coroutineScope.launch {
        val genResult = PdfExportManager.generateOriginalityReportPdf(context, activeReport!!)
        if (genResult.isSuccess) {
          val file = genResult.getOrThrow()
          val exportResult = PdfExportManager.exportPdfToUri(context, file, uri)
          if (exportResult.isSuccess) {
            Toast.makeText(context, "Originality Report PDF saved successfully!", Toast.LENGTH_SHORT).show()
          } else {
            Toast.makeText(context, "Failed to save PDF: ${exportResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
          }
        } else {
          Toast.makeText(context, "Failed to generate report PDF: ${genResult.exceptionOrNull()?.message}", Toast.LENGTH_LONG).show()
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (activeReport != null) {
            IconButton(
              onClick = { activeReport = null },
              modifier = Modifier.testTag("report_back_button")
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to scan")
            }
          }
          Column {
            Text(
              text = if (activeReport != null) "Originality Results" else "Check for plagiarism",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onBackground
            )
            Text(
              text = if (activeReport != null) activeReport!!.documentTitle else "Verify originality & sources",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        if (activeReport == null && textInput.isNotBlank()) {
          TextButton(
            onClick = {
              textInput = ""
              documentTitle = "Untitled Document"
            }
          ) {
            Text("Clear", color = MaterialTheme.colorScheme.error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
          }
        }
      }
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    if (activeReport == null) {
      // Input Screen (Screen 14)
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(horizontal = 16.dp)
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        // Tab switcher: Text | Document
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(4.dp)
        ) {
          TabButton(
            text = "Text",
            isSelected = selectedTab == 0,
            onClick = { selectedTab = 0 },
            modifier = Modifier.weight(1f)
          )
          TabButton(
            text = "Document",
            isSelected = selectedTab == 1,
            onClick = { selectedTab = 1 },
            modifier = Modifier.weight(1f)
          )
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (selectedTab == 0) {
          // Text Input Tab
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(16.dp)) {
              OutlinedTextField(
                value = documentTitle,
                onValueChange = { documentTitle = it },
                label = { Text("Document Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
              )

              Spacer(modifier = Modifier.height(12.dp))

              OutlinedTextField(
                value = textInput,
                onValueChange = { textInput = it },
                label = { Text("Paste text to check") },
                placeholder = { Text("Paste text or start typing here...") },
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 180.dp)
                  .testTag("originality_text_input"),
                shape = RoundedCornerShape(14.dp)
              )

              Spacer(modifier = Modifier.height(8.dp))

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text(
                  text = "Supported: English, Spanish, French, Academic",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                  text = "$wordCount words",
                  style = MaterialTheme.typography.bodySmall,
                  color = GenziiPurple,
                  fontWeight = FontWeight.SemiBold
                )
              }
            }
          }
        } else {
          // Document Upload Tab
          Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Box(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(160.dp)
                  .clip(RoundedCornerShape(16.dp))
                  .border(
                    width = 2.dp,
                    color = GenziiPurple.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(16.dp)
                  )
                  .background(GenziiPurpleContainer.copy(alpha = 0.3f))
                  .clickable {
                    docPickerLauncher.launch(
                      arrayOf(
                        "application/pdf",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "text/plain",
                        "image/*"
                      )
                    )
                  },
                contentAlignment = Alignment.Center
              ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  Box(
                    modifier = Modifier
                      .size(48.dp)
                      .clip(CircleShape)
                      .background(GenziiPurpleContainer),
                    contentAlignment = Alignment.Center
                  ) {
                    Icon(
                      imageVector = Icons.Default.CloudUpload,
                      contentDescription = "Upload",
                      tint = GenziiPurple,
                      modifier = Modifier.size(26.dp)
                    )
                  }
                  Spacer(modifier = Modifier.height(10.dp))
                  Text(
                    text = "Tap to choose or upload file",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground
                  )
                  Spacer(modifier = Modifier.height(4.dp))
                  Text(
                    text = "Supported formats: .docx, .pdf, .txt",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              Spacer(modifier = Modifier.height(16.dp))

              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .clip(RoundedCornerShape(12.dp))
                  .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                  .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.Default.Description,
                  contentDescription = null,
                  tint = GenziiPurple
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = documentTitle,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground
                  )
                  Text(
                    text = "Selected from Secure Vault",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Scanning indicator or Scan Button
        if (isScanning) {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 12.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp,
                    color = GenziiPurple
                  )
                  Spacer(modifier = Modifier.width(10.dp))
                  Text(
                    text = "Analyzing Document",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                  )
                }

                // Loading Timer Badge
                Surface(
                  shape = RoundedCornerShape(12.dp),
                  color = GenziiPurpleContainer
                ) {
                  Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(
                      imageVector = Icons.Outlined.Timer,
                      contentDescription = null,
                      tint = GenziiPurple,
                      modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                      text = String.format("%02d:%02d", elapsedScanSeconds / 60, elapsedScanSeconds % 60),
                      fontFamily = PoppinsFontFamily,
                      fontWeight = FontWeight.Bold,
                      fontSize = 12.sp,
                      color = GenziiPurple
                    )
                  }
                }
              }

              Spacer(modifier = Modifier.height(16.dp))

              LinearProgressIndicator(
                modifier = Modifier
                  .fillMaxWidth()
                  .height(8.dp)
                  .clip(RoundedCornerShape(4.dp)),
                color = GenziiPurple,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
              )

              Spacer(modifier = Modifier.height(14.dp))

              val statusText = when {
                elapsedScanSeconds < 3 -> "Tokenizing document contents & extracting syntactic structure..."
                elapsedScanSeconds < 7 -> "Connecting to Gemini 2.5 Flash academic verification neural engine..."
                elapsedScanSeconds < 12 -> "Cross-checking citations, semantics, and literature fingerprints..."
                else -> "Finalizing originality score and synthesizing evidence report..."
              }

              Text(
                text = statusText,
                fontFamily = PoppinsFontFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )
            }
          }
        } else {
          // Honest Error Message Banner
          if (scanErrorMessage != null) {
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
              shape = RoundedCornerShape(16.dp),
              colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
              )
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Analysis Unavailable",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onErrorContainer
                  )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                  text = scanErrorMessage!!,
                  fontFamily = PoppinsFontFamily,
                  fontSize = 12.sp,
                  lineHeight = 17.sp,
                  color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.End,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  TextButton(
                    onClick = { scanErrorMessage = null }
                  ) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.onErrorContainer, fontSize = 12.sp)
                  }
                  if (!isGeminiLive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                      onClick = { showApiKeyDialog = true },
                      colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
                    ) {
                      Icon(Icons.Default.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Set API Key", fontSize = 12.sp)
                    }
                  } else {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                      onClick = {
                        scanErrorMessage = null
                        isScanning = true
                        coroutineScope.launch {
                          val result = GenziiRepository.scanOriginality(
                            title = documentTitle.ifBlank { "Scanned_Text.txt" },
                            text = textInput
                          )
                          isScanning = false
                          if (result.isSuccess) {
                            activeReport = result.getOrThrow()
                            scanErrorMessage = null
                            showInterstitialAdDialog = true
                          } else {
                            val ex = result.exceptionOrNull()
                            scanErrorMessage = ex?.message ?: "Analysis could not be completed. Please check your network and try again."
                          }
                        }
                      },
                      colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
                    ) {
                      Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                      Spacer(modifier = Modifier.width(6.dp))
                      Text("Retry Scan", fontSize = 12.sp)
                    }
                  }
                }
              }
            }
          }

          Button(
            onClick = {
              if (textInput.isNotBlank()) {
                scanErrorMessage = null
                if (!isGeminiLive) {
                  scanErrorMessage = "Google Gemini API key is required to perform real-time originality and plagiarism analysis. Please enter your API key to proceed."
                  showApiKeyDialog = true
                  return@Button
                }
                isScanning = true
                coroutineScope.launch {
                  val result = GenziiRepository.scanOriginality(
                    title = documentTitle.ifBlank { "Scanned_Text.txt" },
                    text = textInput
                  )
                  isScanning = false
                  if (result.isSuccess) {
                    activeReport = result.getOrThrow()
                    scanErrorMessage = null
                    showInterstitialAdDialog = true
                  } else {
                    val ex = result.exceptionOrNull()
                    scanErrorMessage = ex?.message ?: "Analysis could not be completed. Please check your network connection and API key."
                  }
                }
              }
            },
            enabled = textInput.isNotBlank(),
            modifier = Modifier
              .fillMaxWidth()
              .height(54.dp)
              .testTag("start_originality_scan_button"),
            colors = ButtonDefaults.buttonColors(
              containerColor = GenziiPurple,
              contentColor = GenziiWhite
            ),
            shape = RoundedCornerShape(16.dp)
          ) {
            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = if (textInput.isBlank()) "Enter Text to Scan" else "Scan",
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          OutlinedButton(
            onClick = { showRewardedAdDialog = true },
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp),
            shape = RoundedCornerShape(14.dp)
          ) {
            Icon(Icons.Default.CardGiftcard, contentDescription = null, tint = GenziiPurple, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Earn +10 Extra Scans (Watch Test Ad)", color = GenziiPurple, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
          }

          Spacer(modifier = Modifier.height(14.dp))

          GoogleTestBannerAd()
        }

        Spacer(modifier = Modifier.height(28.dp))
      }
    } else {
      // Results View (Screen 15)
      val report = activeReport!!
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Circular Gauge
        item {
          Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = GenziiPurpleContainer,
                modifier = Modifier.padding(bottom = 12.dp)
              ) {
                Row(
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = GenziiPurple,
                    modifier = Modifier.size(14.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text(
                    text = "Verified by Google Gemini 2.5 Flash",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GenziiPurple
                  )
                }
              }

              OriginalityGauge(
                originalPercentage = report.originalPercentage,
                similarityPercentage = report.similarityPercentage
              )

              Spacer(modifier = Modifier.height(20.dp))

              // 3 Metric Stat cards: Matches, Sources, Words
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
              ) {
                MetricColumn(title = "Matches", value = report.matchesCount.toString())
                MetricColumn(title = "Sources", value = report.sourcesCount.toString())
                MetricColumn(title = "Words", value = report.wordsCount.toString())
              }
            }
          }
        }

        // AI-Generated Writing Analysis Card (Distinguishing AI Analysis)
        if (report.aiAnalysis.isNotBlank()) {
          item {
            Card(
              shape = RoundedCornerShape(18.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                  Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = GenziiPurple,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "AI-Generated Originality Analysis",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground
                  )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                  text = report.aiAnalysis,
                  fontFamily = PoppinsFontFamily,
                  fontSize = 13.sp,
                  lineHeight = 18.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }

        // Verified Sources Section Header
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Verified source matches",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onBackground
            )
            Text(
              text = if (report.sources.isEmpty()) "0 matches found" else "${report.sources.size} identified",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        // Verified sources list or clean zero-match card
        if (report.sources.isEmpty()) {
          item {
            Card(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(14.dp),
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
              elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFE8F5E9)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(24.dp)
                  )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "No Plagiarism or External Matches Found",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground
                  )
                  Spacer(modifier = Modifier.height(2.dp))
                  Text(
                    text = "Zero matching literature found across indexed public sources. Content exhibits authentic authorship.",
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
            }
          }
        } else {
          items(report.sources) { source ->
            SourceMatchCard(source = source)
          }
        }

        // Action Buttons: View Full Report & New Scan
        item {
          Column(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Button(
              onClick = { showFullReportModal = true },
              modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("view_full_report_button"),
              colors = ButtonDefaults.buttonColors(
                containerColor = GenziiPurple,
                contentColor = GenziiWhite
              ),
              shape = RoundedCornerShape(16.dp)
            ) {
              Text(
                text = "View Full Report",
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
              )
            }

            OutlinedButton(
              onClick = { activeReport = null },
              modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
              shape = RoundedCornerShape(16.dp)
            ) {
              Text(
                text = "Scan Another Document",
                color = GenziiPurple,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold
              )
            }

            Spacer(modifier = Modifier.height(14.dp))

            GoogleTestBannerAd()
          }
        }
      }
    }

    // Full Report Modal Sheet
    if (showFullReportModal && activeReport != null) {
      val report = activeReport!!
      AlertDialog(
        onDismissRequest = { showFullReportModal = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Verified, contentDescription = null, tint = GenziiPurple)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Originality Certificate", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold)
          }
        },
        text = {
          Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
          ) {
            Text(
              text = "Document: ${report.documentTitle}",
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp
            )
            Text(
              text = "Scanned: ${report.scanDate}",
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Card(
              colors = CardDefaults.cardColors(containerColor = GenziiPurpleContainer),
              shape = RoundedCornerShape(12.dp)
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceAround
              ) {
                Text("${report.originalPercentage}% Authentic", fontWeight = FontWeight.Bold, color = GenziiPurple)
                Text("${report.similarityPercentage}% Similarity", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
              }
            }
            if (report.aiAnalysis.isNotBlank()) {
              Spacer(modifier = Modifier.height(12.dp))
              Text(
                text = "AI-Generated Originality Assessment:",
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = report.aiAnalysis,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onBackground
              )
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "Highlighted Matches Excerpt:",
              fontWeight = FontWeight.SemiBold,
              fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
              text = report.highlightedContent,
              fontSize = 12.sp,
              lineHeight = 17.sp,
              color = MaterialTheme.colorScheme.onBackground
            )
          }
        },
        confirmButton = {
          Row(
            verticalAlignment = Alignment.CenterVertically
          ) {
            OutlinedButton(
              onClick = {
                coroutineScope.launch {
                  val genResult = PdfExportManager.generateOriginalityReportPdf(context, report)
                  if (genResult.isSuccess) {
                    PdfExportManager.printPdfFile(context, "Originality Report - ${report.documentTitle}", genResult.getOrThrow())
                  } else {
                    Toast.makeText(context, "Failed to prepare print document", Toast.LENGTH_SHORT).show()
                  }
                }
              },
              modifier = Modifier.padding(end = 6.dp)
            ) {
              Icon(Icons.Filled.Print, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Print / PDF", fontSize = 11.sp)
            }

            Button(
              onClick = {
                val filename = "${report.documentTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_")}_Report.pdf"
                exportReportLauncher.launch(filename)
              },
              colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
            ) {
              Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text("Export PDF", fontSize = 11.sp)
            }
          }
        },
        dismissButton = {
          TextButton(onClick = { showFullReportModal = false }) {
            Text("Close")
          }
        }
      )
    }

    if (showRewardedAdDialog) {
      GoogleTestRewardedAdDialog(
        onDismiss = { showRewardedAdDialog = false },
        onRewardEarned = { scans, credits ->
          showRewardedAdDialog = false
        }
      )
    }

    if (showInterstitialAdDialog) {
      GoogleTestInterstitialAdDialog(
        onDismiss = { showInterstitialAdDialog = false }
      )
    }

    // Gemini API Setup Dialog
    if (showApiKeyDialog) {
      var enteredKey by remember { mutableStateOf(userProfile.customApiKey) }
      var keyVisible by remember { mutableStateOf(false) }

      AlertDialog(
        onDismissRequest = { showApiKeyDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = GenziiPurple)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Gemini API Setup", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold)
          }
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "Powered by Google Gemini 2.5 Flash. Enter your Gemini API key to activate genuine originality checking, verified academic source discovery, and literature similarity scanning.",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
              value = enteredKey,
              onValueChange = { enteredKey = it },
              label = { Text("Gemini API Key") },
              placeholder = { Text("AIzaSy...") },
              singleLine = true,
              visualTransformation = if (keyVisible) androidx.compose.ui.text.input.VisualTransformation.None else androidx.compose.ui.text.input.PasswordVisualTransformation(),
              trailingIcon = {
                IconButton(onClick = { keyVisible = !keyVisible }) {
                  Icon(
                    imageVector = if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (keyVisible) "Hide key" else "Show key"
                  )
                }
              },
              modifier = Modifier.fillMaxWidth()
            )

            Text(
              text = "Keys are securely stored in your private device enclave and never sent to third parties.",
              fontSize = 10.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              GenziiRepository.updateAiSettings(
                customKey = enteredKey.trim(),
                temperature = userProfile.temperature,
                useStreaming = userProfile.useStreaming
              )
              showApiKeyDialog = false
              scanErrorMessage = null
              Toast.makeText(context, "Gemini API key saved!", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
          ) {
            Text("Save Key")
          }
        },
        dismissButton = {
          TextButton(onClick = { showApiKeyDialog = false }) {
            Text("Cancel")
          }
        }
      )
    }
  }
}

@Composable
fun TabButton(
  text: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(if (isSelected) GenziiPurple else Color.Transparent)
      .clickable(onClick = onClick)
      .padding(vertical = 10.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      fontFamily = PoppinsFontFamily,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      color = if (isSelected) GenziiWhite else MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 14.sp
    )
  }
}

@Composable
fun MetricColumn(title: String, value: String) {
  Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text = value,
      fontFamily = PoppinsFontFamily,
      fontWeight = FontWeight.Bold,
      fontSize = 20.sp,
      color = MaterialTheme.colorScheme.onBackground
    )
    Text(
      text = title,
      fontFamily = PoppinsFontFamily,
      fontSize = 12.sp,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
fun SourceMatchCard(source: SourceMatch) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(36.dp)
          .clip(CircleShape)
          .background(GenziiPurpleContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Public,
          contentDescription = null,
          tint = GenziiPurple,
          modifier = Modifier.size(18.dp)
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = source.domain,
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.SemiBold,
          fontSize = 14.sp,
          color = MaterialTheme.colorScheme.onBackground
        )
        Text(
          text = source.excerpt,
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }

      Spacer(modifier = Modifier.width(8.dp))

      Text(
        text = "${source.matchPercentage}%",
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = GenziiPurple
      )
    }
  }
}
