package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GenziiRepository
import com.example.ads.GoogleAdsManager
import com.example.ads.GoogleTestBannerAd
import com.example.ads.GoogleTestRewardedAdDialog
import com.example.model.DocumentItem
import com.example.model.FileType
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.util.FileUtils
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
  onNavigateToCheck: () -> Unit,
  onNavigateToAi: () -> Unit,
  onNavigateToVault: () -> Unit,
  onNavigateToUpgrade: () -> Unit,
  onNavigateToTools: () -> Unit,
  onNavigateToOcr: () -> Unit = {},
  onOpenDocument: (String) -> Unit,
  onNotificationsClick: () -> Unit,
  onProfileClick: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val snackbarHostState = remember { SnackbarHostState() }

  val userProfile by GenziiRepository.userProfile.collectAsState()
  val documents by GenziiRepository.documents.collectAsState()
  val notifications by GenziiRepository.notifications.collectAsState()
  val unreadNotifs = remember(notifications) { notifications.count { it.isUnread } }

  var showUploadOptionsDialog by remember { mutableStateOf(false) }
  var isUploading by remember { mutableStateOf(false) }
  var showRewardedAdDialog by remember { mutableStateOf(false) }

  // File Picker Launcher for .pdf, .docx, .txt
  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      coroutineScope.launch {
        isUploading = true
        try {
          val pickedInfo = FileUtils.readPickedFile(context, uri)
          val addedDoc = GenziiRepository.addImportedDocument(
            name = pickedInfo.name,
            sizeFormatted = pickedInfo.sizeFormatted,
            sizeBytes = pickedInfo.sizeBytes,
            fileType = pickedInfo.fileType,
            content = pickedInfo.content,
            folder = "Research Papers"
          )
          snackbarHostState.showSnackbar(
            message = "Uploaded '${addedDoc.name}' to Vault (${addedDoc.sizeFormatted})",
            actionLabel = "Open"
          ).let { result ->
            if (result == SnackbarResult.ActionPerformed) {
              onOpenDocument(addedDoc.id)
            }
          }
        } catch (e: Exception) {
          snackbarHostState.showSnackbar("Upload failed: ${e.message}")
        } finally {
          isUploading = false
        }
      }
    }
  }

  Scaffold(
    topBar = {
      GenziiTopBar(
        unreadCount = unreadNotifs,
        onNotificationsClick = onNotificationsClick,
        onProfileClick = onProfileClick
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showUploadOptionsDialog = true },
        containerColor = GenziiPurple,
        contentColor = GenziiWhite,
        shape = CircleShape,
        modifier = Modifier.testTag("dashboard_fab_upload")
      ) {
        Icon(
          imageVector = Icons.Default.CloudUpload,
          contentDescription = "Upload Document"
        )
      }
    },
    snackbarHost = { SnackbarHost(snackbarHostState) },
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Welcome Greeting
      item {
        Column(modifier = Modifier.padding(top = 4.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "Good morning, ${userProfile.name.split(" ").first()}! 👋",
              style = MaterialTheme.typography.displayMedium,
              color = MaterialTheme.colorScheme.onBackground
            )
          }
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "Here's what's happening with your workspace.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Production Release Workspace Highlights & Rewarded Bonus Card
      item {
        Card(
          modifier = Modifier.fillMaxWidth(),
          shape = RoundedCornerShape(18.dp),
          colors = CardDefaults.cardColors(containerColor = GenziiPurple),
          elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
          Column(modifier = Modifier.padding(18.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(GenziiWhite.copy(alpha = 0.2f)),
                  contentAlignment = Alignment.Center
                ) {
                  Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = GenziiWhite,
                    modifier = Modifier.size(20.dp)
                  )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    text = "Workspace & Security Hub",
                    color = GenziiWhite,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                  )
                  Text(
                    text = "${userProfile.checksLeft} checks available • ${userProfile.tier.name} Plan",
                    color = GenziiPurpleSoft,
                    fontSize = 12.sp
                  )
                }
              }

              Button(
                onClick = { showRewardedAdDialog = true },
                colors = ButtonDefaults.buttonColors(
                  containerColor = GenziiWhite,
                  contentColor = GenziiPurple
                ),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
              ) {
                Icon(Icons.Default.CardGiftcard, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = "+10 Free",
                  fontFamily = PoppinsFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
              progress = { (userProfile.checksLeft.toFloat() / userProfile.totalChecks).coerceIn(0f, 1f) },
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
              color = GenziiWhite,
              trackColor = GenziiWhite.copy(alpha = 0.25f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "Watch 5s test ad to add +10 bonus scans",
                color = GenziiPurpleSoft,
                fontSize = 11.sp
              )
              Text(
                text = "${userProfile.checksLeft} / ${userProfile.totalChecks}",
                color = GenziiWhite,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }
      }

      // User Stats Row
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          StatCard(
            value = userProfile.checksCount.toString(),
            label = "Checks",
            modifier = Modifier.weight(1f)
          )
          StatCard(
            value = userProfile.docsCount.toString(),
            label = "Docs",
            modifier = Modifier.weight(1f)
          )
          StatCard(
            value = userProfile.chatsCount.toString(),
            label = "Chats",
            modifier = Modifier.weight(1f)
          )
        }
      }

      // Quick Actions Section
      item {
        Column {
          Text(
            text = "Quick Actions",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
          )
          Spacer(modifier = Modifier.height(12.dp))
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            QuickActionButton(
              label = "AI Writer",
              icon = Icons.Filled.EditNote,
              onClick = onNavigateToAi
            )
            QuickActionButton(
              label = "Check",
              icon = Icons.Filled.VerifiedUser,
              onClick = onNavigateToCheck
            )
            QuickActionButton(
              label = "Vault",
              icon = Icons.Filled.Lock,
              onClick = onNavigateToVault
            )
            QuickActionButton(
              label = "Tools",
              icon = Icons.Filled.GridView,
              onClick = onNavigateToTools
            )
          }
        }
      }

      // Uploading Indicator if active
      if (isUploading) {
        item {
          Card(
            colors = CardDefaults.cardColors(containerColor = GenziiPurpleContainer),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(14.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = GenziiPurple,
                strokeWidth = 2.dp
              )
              Spacer(modifier = Modifier.width(12.dp))
              Text(
                text = "Processing file stream & encrypting to Vault...",
                fontSize = 13.sp,
                color = GenziiPurple,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }
      }

      // Recent Activity Header
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Recent Activity",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
          )
          TextButton(onClick = onNavigateToVault) {
            Text(
              text = "View all",
              color = GenziiPurple,
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.SemiBold,
              fontSize = 13.sp
            )
          }
        }
      }

      // Recent Activity Items
      if (documents.isEmpty()) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = GenziiPurple, modifier = Modifier.size(36.dp))
              Spacer(modifier = Modifier.height(8.dp))
              Text("No documents yet", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Tap the upload button below to import and secure your documents.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )
            }
          }
        }
      } else {
        items(documents.take(5)) { doc ->
          RecentActivityCard(
            doc = doc,
            onClick = { onOpenDocument(doc.id) }
          )
        }
      }

      item {
        GoogleTestBannerAd()
      }

      // Generous bottom spacer so FAB and bottom navigation bar never hide content
      item {
        Spacer(modifier = Modifier.height(96.dp))
      }
    }

    // Upload Options Dialog / Sheet
    if (showUploadOptionsDialog) {
      AlertDialog(
        onDismissRequest = { showUploadOptionsDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.UploadFile, contentDescription = null, tint = GenziiPurple)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Document Upload & Creation", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold)
          }
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "Choose an option to add and encrypt documents into your Genzii Vault:",
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Option 1: Device File Picker
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                  showUploadOptionsDialog = false
                  filePickerLauncher.launch(
                    arrayOf(
                      "application/pdf",
                      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                      "text/plain",
                      "image/*"
                    )
                  )
                },
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = GenziiPurple)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text("Pick from Device Storage", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                  Text(".pdf, .docx, .txt files supported", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }

            // Option 2: Create New Text Draft
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                  showUploadOptionsDialog = false
                  val newDoc = GenziiRepository.addDocument(
                    name = "New_Document_${System.currentTimeMillis() % 10000}.txt",
                    content = "Enter your notes, draft essays, or research references here.",
                    folder = "Research Papers",
                    type = FileType.TXT
                  )
                  coroutineScope.launch {
                    snackbarHostState.showSnackbar("Created '${newDoc.name}' in Vault")
                  }
                },
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.NoteAdd, contentDescription = null, tint = GenziiPurple)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text("Create New Text Document", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                  Text("Start writing a fresh encrypted document", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }

            // Option 3: OCR Document Scanner (CamScanner)
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                  showUploadOptionsDialog = false
                  onNavigateToOcr()
                },
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.DocumentScanner, contentDescription = null, tint = GenziiPurple)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text("OCR Document Scanner", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                  Text("Scan documents with Camera, Auto Edge & ML Kit OCR", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }

            // Option 4: PDF Tools Suite
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                  showUploadOptionsDialog = false
                  onNavigateToTools()
                },
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = GenziiPurple)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text("PDF Utilities Suite", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                  Text("Merge, Split, Compress, Convert & Rotate documents", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        },
        confirmButton = {
          TextButton(onClick = { showUploadOptionsDialog = false }) {
            Text("Cancel", color = GenziiPurple)
          }
        }
      )
    }

    if (showRewardedAdDialog) {
      GoogleTestRewardedAdDialog(
        onDismiss = { showRewardedAdDialog = false },
        onRewardEarned = { scans, credits ->
          showRewardedAdDialog = false
          coroutineScope.launch {
            snackbarHostState.showSnackbar("Success! Earned +$scans Originality Scans & +$credits AI Credits")
          }
        }
      )
    }
  }
}

@Composable
fun QuickActionButton(
  label: String,
  icon: ImageVector,
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick)
      .padding(8.dp)
  ) {
    Box(
      modifier = Modifier
        .size(54.dp)
        .clip(RoundedCornerShape(16.dp))
        .background(GenziiPurpleContainer),
      contentAlignment = Alignment.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = label,
        tint = GenziiPurple,
        modifier = Modifier.size(26.dp)
      )
    }
    Spacer(modifier = Modifier.height(6.dp))
    Text(
      text = label,
      fontFamily = PoppinsFontFamily,
      fontSize = 12.sp,
      fontWeight = FontWeight.Medium,
      color = MaterialTheme.colorScheme.onBackground
    )
  }
}

@Composable
fun RecentActivityCard(
  doc: DocumentItem,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      FileTypeIcon(type = doc.type)

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = doc.name,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = "${doc.folder} • ${doc.updatedAt}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          if (doc.originalityScore != null) {
            Text(
              text = " • ${doc.originalityScore}% original",
              style = MaterialTheme.typography.bodySmall,
              color = GenziiPurple,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }

      Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = "View file",
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
      )
    }
  }
}
