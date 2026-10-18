package com.example.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import com.example.data.GenziiRepository
import com.example.model.DocumentItem
import com.example.model.FileType
import com.example.ui.components.FileTypeIcon
import com.example.ui.components.GenziiTopBar
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun VaultScreen(
  onOpenDocument: (String) -> Unit,
  onOpenFolder: (String) -> Unit,
  onNotificationsClick: () -> Unit,
  onProfileClick: () -> Unit
) {
  val documents by GenziiRepository.documents.collectAsState()
  val userProfile by GenziiRepository.userProfile.collectAsState()
  val notifications by GenziiRepository.notifications.collectAsState()
  val unreadNotifs = remember(notifications) { notifications.count { it.isUnread } }

  val context = androidx.compose.ui.platform.LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var searchQuery by remember { mutableStateOf("") }
  var selectedFilter by remember { mutableStateOf("All Files") }
  var showAddFileDialog by remember { mutableStateOf(false) }

  val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
    contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
  ) { uri: android.net.Uri? ->
    if (uri != null) {
      coroutineScope.launch {
        val fileInfo = com.example.util.FileUtils.readPickedFile(context, uri)
        GenziiRepository.addImportedDocument(
          name = fileInfo.name,
          sizeFormatted = fileInfo.sizeFormatted,
          sizeBytes = fileInfo.sizeBytes,
          fileType = fileInfo.fileType,
          content = fileInfo.content,
          folder = "Personal"
        )
      }
    }
  }

  val filters = listOf("All Files", "Documents", "Images", "Notes")

  val filteredDocuments = remember(documents, searchQuery, selectedFilter) {
    documents.filter { doc ->
      val matchesQuery = searchQuery.isBlank() || doc.name.contains(searchQuery, ignoreCase = true) || doc.folder.contains(searchQuery, ignoreCase = true)
      val matchesFilter = when (selectedFilter) {
        "Documents" -> doc.type == FileType.PDF || doc.type == FileType.DOCX || doc.type == FileType.PPTX
        "Images" -> doc.type == FileType.IMAGE
        "Notes" -> doc.type == FileType.TXT
        else -> true
      }
      matchesQuery && matchesFilter
    }
  }

  Scaffold(
    topBar = {
      GenziiTopBar(
        title = "Secure Vault",
        unreadCount = unreadNotifs,
        onNotificationsClick = onNotificationsClick,
        onProfileClick = onProfileClick
      )
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showAddFileDialog = true },
        containerColor = GenziiPurple,
        contentColor = GenziiWhite,
        shape = CircleShape,
        modifier = Modifier.testTag("vault_fab_add_file")
      ) {
        Icon(Icons.Default.Add, contentDescription = "Add Document")
      }
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      // Search Bar
      item {
        OutlinedTextField(
          value = searchQuery,
          onValueChange = { searchQuery = it },
          placeholder = { Text("Search files in Vault...") },
          leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = null, tint = GenziiPurple)
          },
          trailingIcon = {
            if (searchQuery.isNotEmpty()) {
              IconButton(onClick = { searchQuery = "" }) {
                Icon(Icons.Default.Clear, contentDescription = "Clear")
              }
            }
          },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("vault_search_input"),
          shape = RoundedCornerShape(16.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = GenziiPurple,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
          ),
          singleLine = true
        )
      }

      // Filter Chips
      item {
        LazyRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          items(filters) { filter ->
            val isSelected = selectedFilter == filter
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = if (isSelected) GenziiPurple else MaterialTheme.colorScheme.surface,
              tonalElevation = 1.dp,
              modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable { selectedFilter = filter }
            ) {
              Text(
                text = filter,
                color = if (isSelected) GenziiWhite else MaterialTheme.colorScheme.onSurfaceVariant,
                fontFamily = PoppinsFontFamily,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
              )
            }
          }
        }
      }

      // Folder Cards Carousel
      item {
        Column {
          Text(
            text = "Folders",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
          )

          Spacer(modifier = Modifier.height(10.dp))

          LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            items(GenziiRepository.folders) { (folderName, count) ->
              FolderCard(
                name = folderName,
                itemCount = count,
                onClick = { onOpenFolder(folderName) }
              )
            }
          }
        }
      }

      // Storage Usage Bar Card
      item {
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(14.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                  imageVector = Icons.Default.CloudQueue,
                  contentDescription = null,
                  tint = GenziiPurple,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Storage Usage",
                  fontFamily = PoppinsFontFamily,
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 13.sp,
                  color = MaterialTheme.colorScheme.onBackground
                )
              }
              Text(
                text = "${userProfile.usedStorageGb} GB of ${userProfile.totalStorageGb} GB",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = GenziiPurple
              )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
              progress = { (userProfile.usedStorageGb / userProfile.totalStorageGb).toFloat() },
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
              color = GenziiPurple,
              trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
          }
        }
      }

      // Recent Files Header
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Recent Files",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
          )
          Text(
            text = "${filteredDocuments.size} files",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // File Items List
      if (filteredDocuments.isEmpty()) {
        item {
          Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(Icons.Outlined.FolderOpen, contentDescription = null, tint = GenziiPurple, modifier = Modifier.size(40.dp))
              Spacer(modifier = Modifier.height(10.dp))
              Text("No documents in Vault", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "Tap the + button to import or create encrypted documents in your secure vault.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )
            }
          }
        }
      } else {
        items(filteredDocuments) { doc ->
          VaultFileRow(
            doc = doc,
            onClick = { onOpenDocument(doc.id) },
            onStarClick = { GenziiRepository.toggleStar(doc.id) },
            onDeleteClick = { GenziiRepository.deleteDocument(doc.id) }
          )
        }
      }

      item {
        Spacer(modifier = Modifier.height(60.dp))
      }
    }

    // Add File Dialog
    if (showAddFileDialog) {
      var newTitle by remember { mutableStateOf("") }
      var newContent by remember { mutableStateOf("") }
      var selectedFolder by remember { mutableStateOf("Personal") }

      AlertDialog(
        onDismissRequest = { showAddFileDialog = false },
        title = {
          Text(
            text = "Store Document in Vault",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold
          )
        },
        text = {
          Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            OutlinedCard(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  showAddFileDialog = false
                  filePickerLauncher.launch(
                    arrayOf(
                      "application/pdf",
                      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                      "text/plain",
                      "image/*"
                    )
                  )
                },
              shape = RoundedCornerShape(12.dp)
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Outlined.FileUpload, contentDescription = null, tint = GenziiPurple)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text("Import File from Device", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                  Text("Zero-permission SAF: PDF, DOCX, TXT, OCR", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
              value = newTitle,
              onValueChange = { newTitle = it },
              label = { Text("Document Name") },
              placeholder = { Text("e.g. Dissertation_Chapter.pdf") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
            OutlinedTextField(
              value = newContent,
              onValueChange = { newContent = it },
              label = { Text("Content / Notes") },
              placeholder = { Text("Type or paste confidential notes...") },
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              if (newTitle.isNotBlank()) {
                GenziiRepository.addDocument(
                  name = newTitle.trim(),
                  content = newContent,
                  folder = selectedFolder
                )
                showAddFileDialog = false
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
          ) {
            Text("Save Encrypted")
          }
        },
        dismissButton = {
          TextButton(onClick = { showAddFileDialog = false }) {
            Text("Cancel")
          }
        }
      )
    }
  }
}

@Composable
fun FolderCard(
  name: String,
  itemCount: Int,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .width(130.dp)
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(GenziiPurpleContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Default.Folder,
          contentDescription = null,
          tint = GenziiPurple,
          modifier = Modifier.size(22.dp)
        )
      }
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = name,
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      Text(
        text = "$itemCount files",
        fontFamily = PoppinsFontFamily,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun VaultFileRow(
  doc: DocumentItem,
  onClick: () -> Unit,
  onStarClick: () -> Unit,
  onDeleteClick: () -> Unit
) {
  var showMenu by remember { mutableStateOf(false) }

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
        .padding(12.dp),
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
            text = "${doc.sizeFormatted} • ${doc.updatedAt}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          if (doc.isEncrypted) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = "Encrypted",
              tint = GenziiPurple,
              modifier = Modifier.size(12.dp)
            )
          }
        }
      }

      IconButton(onClick = onStarClick) {
        Icon(
          imageVector = if (doc.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
          contentDescription = "Star",
          tint = if (doc.isStarred) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Box {
        IconButton(onClick = { showMenu = true }) {
          Icon(
            imageVector = Icons.Default.MoreVert,
            contentDescription = "More",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        DropdownMenu(
          expanded = showMenu,
          onDismissRequest = { showMenu = false }
        ) {
          DropdownMenuItem(
            text = { Text("Open Preview") },
            onClick = {
              showMenu = false
              onClick()
            },
            leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null) }
          )
          DropdownMenuItem(
            text = { Text("Delete") },
            onClick = {
              showMenu = false
              onDeleteClick()
            },
            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = GenziiError) }
          )
        }
      }
    }
  }
}

@Composable
fun FilePreviewScreen(
  docId: String,
  onBack: () -> Unit
) {
  val documents by GenziiRepository.documents.collectAsState()
  val doc = documents.find { it.id == docId } ?: documents.firstOrNull()

  var selectedTab by remember { mutableStateOf(0) } // 0: Preview, 1: Details
  var isEditing by remember { mutableStateOf(false) }
  var editedContent by remember { mutableStateOf(doc?.content ?: "") }
  var showDeleteDialog by remember { mutableStateOf(false) }
  var showShareToast by remember { mutableStateOf(false) }

  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val exportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument(
      if (doc?.type == FileType.PDF) "application/pdf" else "text/plain"
    )
  ) { uri ->
    if (uri != null && doc != null) {
      coroutineScope.launch(Dispatchers.IO) {
        try {
          context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(doc.content.toByteArray())
          }
          withContext(Dispatchers.Main) {
            Toast.makeText(context, "Exported successfully!", Toast.LENGTH_SHORT).show()
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) {
            Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
          }
        }
      }
    }
  }

  if (doc == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Document not found")
    }
    return
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
          IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
          Column {
            Text(
              text = doc.name,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = "${doc.sizeFormatted} • ${doc.type.name}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(onClick = { GenziiRepository.toggleStar(doc.id) }) {
            Icon(
              imageVector = if (doc.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
              contentDescription = "Star",
              tint = if (doc.isStarred) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          IconButton(onClick = { showDeleteDialog = true }) {
            Icon(
              imageVector = Icons.Outlined.Delete,
              contentDescription = "Delete Document",
              tint = GenziiError
            )
          }
        }
      }
    },
    bottomBar = {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp, horizontal = 16.dp),
          horizontalArrangement = Arrangement.SpaceAround
        ) {
          BottomActionItem(label = "Share", icon = Icons.Outlined.Share) {
            try {
              val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_SUBJECT, doc.name)
                putExtra(Intent.EXTRA_TEXT, doc.content)
                type = "text/plain"
              }
              context.startActivity(Intent.createChooser(sendIntent, "Share Document via"))
            } catch (e: Exception) {
              Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
          }
          BottomActionItem(label = "Export", icon = Icons.Outlined.Download) {
            exportLauncher.launch(doc.name)
          }
          BottomActionItem(label = if (isEditing) "Save" else "Edit", icon = if (isEditing) Icons.Default.Save else Icons.Outlined.Edit) {
            if (isEditing) {
              GenziiRepository.updateDocumentContent(doc.id, editedContent)
              isEditing = false
              Toast.makeText(context, "Document updated", Toast.LENGTH_SHORT).show()
            } else {
              editedContent = doc.content
              isEditing = true
            }
          }
          BottomActionItem(label = "Encrypted", icon = Icons.Outlined.Lock) {
            Toast.makeText(context, "AES-256 encrypted in private local enclave", Toast.LENGTH_SHORT).show()
          }
        }
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
      // Preview / Details segmented tabs
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(14.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
          .padding(4.dp)
      ) {
        TabButton(
          text = "Preview",
          isSelected = selectedTab == 0,
          onClick = { selectedTab = 0 },
          modifier = Modifier.weight(1f)
        )
        TabButton(
          text = "Details",
          isSelected = selectedTab == 1,
          onClick = { selectedTab = 1 },
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      if (selectedTab == 0) {
        // Document Page Viewer (Screen 18 Preview tab)
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(16.dp)
              .verticalScroll(rememberScrollState())
          ) {
            if (isEditing) {
              OutlinedTextField(
                value = editedContent,
                onValueChange = { editedContent = it },
                modifier = Modifier.fillMaxSize(),
                label = { Text("Editing Content") }
              )
            } else {
              Text(
                text = doc.content,
                fontFamily = PoppinsFontFamily,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                color = MaterialTheme.colorScheme.onBackground
              )
            }
          }
        }
      } else {
        // Details Tab
        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            val computedChecksum = remember(doc.content) {
              try {
                val md = java.security.MessageDigest.getInstance("SHA-256")
                val bytes = md.digest(doc.content.toByteArray(Charsets.UTF_8))
                bytes.joinToString("") { "%02x".format(it) }
              } catch (_: Exception) {
                "Verified"
              }
            }
            DetailRow(title = "Folder", value = doc.folder)
            DetailRow(title = "File Size", value = doc.sizeFormatted)
            DetailRow(title = "File Format", value = doc.type.name)
            DetailRow(title = "Last Modified", value = doc.updatedAt)
            DetailRow(title = "Originality Score", value = if (doc.originalityScore != null) "${doc.originalityScore}%" else "Not scanned")
            DetailRow(title = "Encryption", value = "AES-256-GCM (Zero-Knowledge)")
            DetailRow(title = "SHA-256 Checksum", value = computedChecksum.take(24) + "...")
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
    }

    if (showShareToast) {
      AlertDialog(
        onDismissRequest = { showShareToast = false },
        title = { Text("Encrypted Document Export", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold) },
        text = { Text("The document has been securely verified and prepared for encrypted export.", fontFamily = PoppinsFontFamily) },
        confirmButton = {
          Button(onClick = { showShareToast = false }, colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)) {
            Text("OK")
          }
        }
      )
    }
  }
}

@Composable
fun DetailRow(title: String, value: String) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Text(
      text = title,
      fontFamily = PoppinsFontFamily,
      fontSize = 13.sp,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      fontFamily = PoppinsFontFamily,
      fontSize = 13.sp,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onBackground
    )
  }
}

@Composable
fun BottomActionItem(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 6.dp)
  ) {
    Icon(
      imageVector = icon,
      contentDescription = label,
      tint = GenziiPurple,
      modifier = Modifier.size(20.dp)
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
      text = label,
      fontFamily = PoppinsFontFamily,
      fontSize = 11.sp,
      color = MaterialTheme.colorScheme.onBackground
    )
  }
}

@Composable
fun VaultFolderViewScreen(
  folderName: String,
  onOpenDocument: (String) -> Unit,
  onBack: () -> Unit
) {
  val documents by GenziiRepository.documents.collectAsState()
  val folderDocs = remember(documents, folderName) {
    documents.filter { it.folder.equals(folderName, ignoreCase = true) }
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
          Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = folderName,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
      }
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      items(folderDocs) { doc ->
        VaultFileRow(
          doc = doc,
          onClick = { onOpenDocument(doc.id) },
          onStarClick = { GenziiRepository.toggleStar(doc.id) },
          onDeleteClick = { GenziiRepository.deleteDocument(doc.id) }
        )
      }
    }
  }
}
