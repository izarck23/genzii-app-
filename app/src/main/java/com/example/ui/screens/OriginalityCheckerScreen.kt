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
  var documentTitle by remember { mutableStateOf("Research_Paper.pdf") }
  var textInput by remember {
    mutableStateOf(
      "Modern transformer backbones rely heavily on multi-head scaled dot-product attention mechanisms. As model capacity scales into billions of parameters, memory throughput and quadratic complexity become principal bottlenecks during inference and pre-training phases. We introduce a hybrid sparse attention operator coupled with dynamic low-rank projections."
    )
  }
  var isScanning by remember { mutableStateOf(false) }
  var scanProgress by remember { mutableStateOf(0f) }
  var activeReport by remember {
    mutableStateOf<OriginalityReport?>(
      if (initialReportId != null) reports[initialReportId] else null
    )
  }
  var showFullReportModal by remember { mutableStateOf(false) }
  var showRewardedAdDialog by remember { mutableStateOf(false) }
  var showInterstitialAdDialog by remember { mutableStateOf(false) }
  val isGeminiLive = remember { GeminiService.isApiKeyConfigured() }

  val coroutineScope = rememberCoroutineScope()
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
        } catch (e: Exception) {
          // Keep existing content
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
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            LinearProgressIndicator(
              progress = { scanProgress },
              modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
              color = GenziiPurple
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
              text = "Checking against 14B scholarly web pages & papers...",
              fontFamily = PoppinsFontFamily,
              fontSize = 13.sp,
              color = GenziiPurple,
              fontWeight = FontWeight.Medium
            )
          }
        } else {
          Button(
            onClick = {
              isScanning = true
              scanProgress = 0f
              coroutineScope.launch {
                for (p in 1..10) {
                  delay(150)
                  scanProgress = p / 10f
                }
                val result = GenziiRepository.scanOriginality(
                  title = documentTitle,
                  text = textInput
                )
                isScanning = false
                activeReport = result
                showInterstitialAdDialog = true
              }
            },
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
              text = "Scan",
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
                    text = if (report.scanDate.contains("Gemini")) "Verified by Google Gemini 3.5 Flash" else "Genzii Academic Neural Engine",
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

        // Top Sources Section Header
        item {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Top sources",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onBackground
            )
            Text(
              text = "${report.sources.size} identified",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        // Top sources list
        items(report.sources) { source ->
          SourceMatchCard(source = source)
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
          Button(
            onClick = { showFullReportModal = false },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
          ) {
            Text("Done")
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
