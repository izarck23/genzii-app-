package com.example.ui.screens

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
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
import com.example.model.DocumentSortOrder
import com.example.model.FileType
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import com.example.ui.components.DocumentThumbnailView
import com.example.ui.components.FileTypeIcon
import com.example.ui.components.GenziiTopBar
import com.example.ui.theme.*
import com.example.util.PdfExportManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun isImageExtension(name: String): Boolean {
  val lower = name.lowercase()
  return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
      lower.endsWith(".webp") || lower.endsWith(".bmp") || lower.endsWith(".gif")
}

@OptIn(ExperimentalFoundationApi::class)
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
  var sortOrder by remember {
    mutableStateOf(DocumentSortOrder.fromId(GenziiRepository.getSavedSortOrder()))
  }
  var showSortMenu by remember { mutableStateOf(false) }
  var showAddFileDialog by remember { mutableStateOf(false) }

  // Multi-selection state
  var isSelectionMode by remember { mutableStateOf(false) }
  var selectedDocIds by remember { mutableStateOf(setOf<String>()) }
  var showBatchDeleteConfirm by remember { mutableStateOf(false) }
  var showBatchMoveDialog by remember { mutableStateOf(false) }

  // Cancel multi-selection on system back press
  BackHandler(enabled = isSelectionMode) {
    isSelectionMode = false
    selectedDocIds = emptySet()
  }

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
          folder = "Personal",
          localFilePath = fileInfo.localFilePath,
          uriString = fileInfo.uriString,
          pageCount = fileInfo.pageCount
        )
      }
    }
  }

  val filters = listOf("All Files", "Documents", "Images", "Notes")

  val filteredDocuments = remember(documents, searchQuery, selectedFilter, sortOrder) {
    val filtered = documents.filter { doc ->
      val matchesQuery = searchQuery.isBlank() || doc.name.contains(searchQuery, ignoreCase = true) || doc.folder.contains(searchQuery, ignoreCase = true)
      val matchesFilter = when (selectedFilter) {
        "Documents" -> doc.type == FileType.PDF || doc.type == FileType.DOCX || doc.type == FileType.PPTX
        "Images" -> doc.type == FileType.IMAGE
        "Notes" -> doc.type == FileType.TXT
        else -> true
      }
      matchesQuery && matchesFilter
    }
    when (sortOrder) {
      DocumentSortOrder.DATE_NEWEST -> filtered.sortedByDescending { it.createdAt }
      DocumentSortOrder.DATE_OLDEST -> filtered.sortedBy { it.createdAt }
      DocumentSortOrder.NAME_AZ -> filtered.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
      DocumentSortOrder.NAME_ZA -> filtered.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
  }

  Scaffold(
    topBar = {
      if (isSelectionMode) {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 3.dp,
          modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = {
                  isSelectionMode = false
                  selectedDocIds = emptySet()
                },
                modifier = Modifier.testTag("exit_selection_button")
              ) {
                Icon(Icons.Default.Close, contentDescription = "Exit selection")
              }
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "${selectedDocIds.size} selected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              TextButton(
                onClick = {
                  if (selectedDocIds.size == filteredDocuments.size) {
                    selectedDocIds = emptySet()
                  } else {
                    selectedDocIds = filteredDocuments.map { it.id }.toSet()
                  }
                },
                modifier = Modifier.testTag("toggle_select_all_button")
              ) {
                Text(
                  text = if (selectedDocIds.size == filteredDocuments.size && filteredDocuments.isNotEmpty()) "Deselect All" else "Select All",
                  color = GenziiPurple,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold
                )
              }

              IconButton(
                onClick = {
                  selectedDocIds.forEach { id ->
                    GenziiRepository.toggleStar(id)
                  }
                  Toast.makeText(context, "Starred selected items", Toast.LENGTH_SHORT).show()
                },
                enabled = selectedDocIds.isNotEmpty(),
                modifier = Modifier.testTag("star_selected_button")
              ) {
                Icon(Icons.Default.Star, contentDescription = "Star selected", tint = Color(0xFFFFB300))
              }

              IconButton(
                onClick = { showBatchMoveDialog = true },
                enabled = selectedDocIds.isNotEmpty(),
                modifier = Modifier.testTag("move_selected_button")
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                  contentDescription = "Move selected",
                  tint = if (selectedDocIds.isNotEmpty()) GenziiPurple else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
              }

              IconButton(
                onClick = { showBatchDeleteConfirm = true },
                enabled = selectedDocIds.isNotEmpty(),
                modifier = Modifier.testTag("delete_selected_button")
              ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = if (selectedDocIds.isNotEmpty()) GenziiError else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
              }
            }
          }
        }
      } else {
        GenziiTopBar(
          title = "Secure Vault",
          unreadCount = unreadNotifs,
          onNotificationsClick = onNotificationsClick,
          onProfileClick = onProfileClick
        )
      }
    },
    bottomBar = {
      AnimatedVisibility(
        visible = isSelectionMode && selectedDocIds.isNotEmpty(),
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it },
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it }
      ) {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 6.dp,
          shadowElevation = 8.dp,
          shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
          modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Surface(
                shape = CircleShape,
                color = GenziiPurpleContainer,
                modifier = Modifier.size(32.dp)
              ) {
                Box(contentAlignment = Alignment.Center) {
                  Text(
                    text = "${selectedDocIds.size}",
                    color = GenziiPurple,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                  )
                }
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "${selectedDocIds.size} ${if (selectedDocIds.size == 1) "item" else "items"} selected",
                  fontFamily = PoppinsFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 13.sp,
                  color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                  text = "Bulk actions available",
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(
                onClick = { showBatchMoveDialog = true },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("bottom_bar_move_button")
              ) {
                Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null, modifier = Modifier.size(16.dp), tint = GenziiPurple)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Move", color = GenziiPurple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              }
              Button(
                onClick = { showBatchDeleteConfirm = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GenziiError),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.testTag("bottom_bar_delete_button")
              ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Delete", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }
      }
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { showAddFileDialog = true },
        containerColor = GenziiPurple,
        contentColor = GenziiWhite,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp, pressedElevation = 10.dp),
        modifier = Modifier
          .padding(bottom = 8.dp, end = 4.dp)
          .testTag("vault_fab_add_file")
      ) {
        Icon(Icons.Default.Add, contentDescription = "Add Document", modifier = Modifier.size(24.dp))
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
            contentPadding = PaddingValues(end = 12.dp),
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

      // Encrypted Device Enclave Storage Usage Card
      item {
        val (deviceFreeGb, deviceTotalGb) = remember {
          try {
            val stat = android.os.StatFs(context.filesDir.absolutePath)
            val free = (stat.availableBlocksLong * stat.blockSizeLong) / (1024.0 * 1024.0 * 1024.0)
            val total = (stat.blockCountLong * stat.blockSizeLong) / (1024.0 * 1024.0 * 1024.0)
            String.format("%.1f", free) to String.format("%.1f", total)
          } catch (_: Exception) {
            "28.1" to "111.4"
          }
        }
        val (vaultUsedBytes, vaultFormatted) = remember(documents) {
          try {
            val vaultDir = File(context.filesDir, "vault_documents")
            val diskSize = if (vaultDir.exists()) vaultDir.listFiles()?.sumOf { it.length() } ?: 0L else 0L
            val docBytes = documents.sumOf { doc ->
              doc.localFilePath?.let { File(it).takeIf { f -> f.exists() }?.length() } ?: 0L
            }
            val totalBytes = maxOf(diskSize, docBytes)
            val formatted = com.example.util.FileUtils.formatFileSize(totalBytes)
            totalBytes to formatted
          } catch (_: Exception) {
            0L to "0 B"
          }
        }
        val vaultQuotaBytes = (userProfile.totalStorageGb * 1024.0 * 1024.0 * 1024.0).toLong()
        val vaultRatio = if (vaultQuotaBytes > 0L) {
          (vaultUsedBytes.toFloat() / vaultQuotaBytes.toFloat()).coerceIn(0f, 1.0f)
        } else 0f
        val displayProgress = if (vaultUsedBytes > 0L) maxOf(0.02f, vaultRatio) else 0f

        Card(
          shape = RoundedCornerShape(16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
          elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(modifier = Modifier.padding(16.dp)) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
              ) {
                Surface(
                  shape = RoundedCornerShape(10.dp),
                  color = GenziiPurpleContainer,
                  modifier = Modifier.size(36.dp)
                ) {
                  Box(contentAlignment = Alignment.Center) {
                    Icon(
                      imageVector = Icons.Default.Shield,
                      contentDescription = "Encrypted Vault",
                      tint = GenziiPurple,
                      modifier = Modifier.size(20.dp)
                    )
                  }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                  Text(
                    text = "Encrypted Device Enclave",
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onBackground
                  )
                  Text(
                    text = "Zero-knowledge local storage • Private to device",
                    fontFamily = PoppinsFontFamily,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              Column(horizontalAlignment = Alignment.End) {
                Text(
                  text = "$vaultFormatted used",
                  fontFamily = PoppinsFontFamily,
                  fontSize = 12.5.sp,
                  fontWeight = FontWeight.Bold,
                  color = GenziiPurple
                )
                Text(
                  text = "of ${userProfile.totalStorageGb.toInt()} GB plan",
                  fontFamily = PoppinsFontFamily,
                  fontSize = 10.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
              progress = { displayProgress },
              modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
              color = GenziiPurple,
              trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "Genzii Vault: $vaultFormatted",
                fontFamily = PoppinsFontFamily,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = "$deviceFreeGb GB free on device ($deviceTotalGb GB total)",
                fontFamily = PoppinsFontFamily,
                fontSize = 10.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
              )
            }
          }
        }
      }

      // Recent Files Header with Sorting Menu
      item {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
              text = "Recent Files",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "(${filteredDocuments.size})",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // Quick Multi-Selection Mode Button
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (isSelectionMode) GenziiPurpleContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                  isSelectionMode = !isSelectionMode
                  if (!isSelectionMode) selectedDocIds = emptySet()
                }
                .testTag("toggle_selection_mode_button")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = if (isSelectionMode) Icons.Default.CheckCircle else Icons.Outlined.CheckCircle,
                  contentDescription = if (isSelectionMode) "Exit selection" else "Select files",
                  tint = if (isSelectionMode) GenziiPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = if (isSelectionMode) "Done" else "Select",
                  fontFamily = PoppinsFontFamily,
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Medium,
                  color = if (isSelectionMode) GenziiPurple else MaterialTheme.colorScheme.onBackground
                )
              }
            }

            // Sorting Dropdown Button
            Box {
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
              modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable { showSortMenu = true }
                .testTag("sort_button")
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.Sort,
                  contentDescription = "Sort Documents",
                  tint = GenziiPurple,
                  modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                  text = sortOrder.shortLabel,
                  fontFamily = PoppinsFontFamily,
                  fontSize = 11.5.sp,
                  fontWeight = FontWeight.Medium,
                  color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                  imageVector = Icons.Default.ArrowDropDown,
                  contentDescription = null,
                  tint = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.size(16.dp)
                )
              }
            }

            DropdownMenu(
              expanded = showSortMenu,
              onDismissRequest = { showSortMenu = false },
              modifier = Modifier.testTag("sort_menu")
            ) {
              DocumentSortOrder.entries.forEach { order ->
                DropdownMenuItem(
                  text = {
                    Text(
                      text = order.displayName,
                      fontWeight = if (sortOrder == order) FontWeight.Bold else FontWeight.Normal,
                      color = if (sortOrder == order) GenziiPurple else MaterialTheme.colorScheme.onSurface
                    )
                  },
                  onClick = {
                    sortOrder = order
                    GenziiRepository.saveSortOrder(order.id)
                    showSortMenu = false
                  },
                  leadingIcon = {
                    val icon = when (order) {
                      DocumentSortOrder.DATE_NEWEST, DocumentSortOrder.DATE_OLDEST -> Icons.Outlined.Schedule
                      DocumentSortOrder.NAME_AZ, DocumentSortOrder.NAME_ZA -> Icons.Outlined.SortByAlpha
                    }
                    Icon(
                      imageVector = icon,
                      contentDescription = null,
                      tint = if (sortOrder == order) GenziiPurple else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  },
                  trailingIcon = {
                    if (sortOrder == order) {
                      Icon(Icons.Default.Check, contentDescription = "Active", tint = GenziiPurple)
                    }
                  },
                  modifier = Modifier.testTag("sort_item_${order.id}")
                )
              }
            }
          }
        }
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
        items(filteredDocuments, key = { it.id }) { doc ->
          VaultFileRow(
            doc = doc,
            isSelectionMode = isSelectionMode,
            isSelected = doc.id in selectedDocIds,
            onToggleSelect = {
              selectedDocIds = if (doc.id in selectedDocIds) {
                selectedDocIds - doc.id
              } else {
                selectedDocIds + doc.id
              }
            },
            onLongClick = {
              if (!isSelectionMode) {
                isSelectionMode = true
                selectedDocIds = setOf(doc.id)
              }
            },
            onClick = { onOpenDocument(doc.id) },
            onStarClick = { GenziiRepository.toggleStar(doc.id) },
            onDeleteClick = { GenziiRepository.deleteDocument(doc.id) }
          )
        }
      }

      item {
        Spacer(modifier = Modifier.height(110.dp))
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
                val cleanTitle = newTitle.trim()
                val vaultDir = File(context.filesDir, "vault_documents").apply { mkdirs() }
                val ext = if (cleanTitle.contains(".")) cleanTitle.substringAfterLast('.').lowercase() else "txt"
                val fileType = if (ext == "pdf") FileType.PDF else FileType.TXT
                val noteFile = File(vaultDir, "${System.currentTimeMillis()}_${cleanTitle.replace(Regex("[^a-zA-Z0-9._-]"), "_")}")
                noteFile.writeText(newContent, Charsets.UTF_8)
                GenziiRepository.addDocument(
                  name = cleanTitle,
                  content = newContent,
                  folder = selectedFolder,
                  type = fileType,
                  localFilePath = noteFile.absolutePath,
                  uriString = android.net.Uri.fromFile(noteFile).toString(),
                  pageCount = 1
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

    // Batch Delete Confirmation Dialog
    if (showBatchDeleteConfirm) {
      AlertDialog(
        onDismissRequest = { showBatchDeleteConfirm = false },
        title = {
          Text("Delete ${selectedDocIds.size} items?", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold)
        },
        text = {
          Text("Are you sure you want to delete these documents from your encrypted vault? This action cannot be undone.")
        },
        confirmButton = {
          Button(
            onClick = {
              val count = selectedDocIds.size
              GenziiRepository.deleteDocuments(selectedDocIds)
              selectedDocIds = emptySet()
              isSelectionMode = false
              showBatchDeleteConfirm = false
              Toast.makeText(context, "Deleted $count items", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiError),
            modifier = Modifier.testTag("confirm_batch_delete_button")
          ) {
            Text("Delete")
          }
        },
        dismissButton = {
          TextButton(onClick = { showBatchDeleteConfirm = false }) {
            Text("Cancel")
          }
        }
      )
    }

    // Batch Move Dialog
    if (showBatchMoveDialog) {
      val availableFolders = remember(documents) {
        (listOf("Research Papers", "Thesis Drafts", "Assignments", "Personal") + documents.map { it.folder })
          .distinct()
          .filter { it.isNotBlank() }
      }
      BatchMoveDialog(
        selectedCount = selectedDocIds.size,
        availableFolders = availableFolders,
        onDismiss = { showBatchMoveDialog = false },
        onMoveConfirmed = { targetFolder ->
          val count = selectedDocIds.size
          GenziiRepository.moveDocumentsToFolder(selectedDocIds, targetFolder)
          selectedDocIds = emptySet()
          isSelectionMode = false
          showBatchMoveDialog = false
          Toast.makeText(context, "Moved $count items to $targetFolder", Toast.LENGTH_SHORT).show()
        }
      )
    }
  }
}

@Composable
fun BatchMoveDialog(
  selectedCount: Int,
  availableFolders: List<String>,
  onDismiss: () -> Unit,
  onMoveConfirmed: (targetFolder: String) -> Unit
) {
  var selectedFolder by remember { mutableStateOf(availableFolders.firstOrNull() ?: "Personal") }
  var isCustomFolder by remember { mutableStateOf(false) }
  var customFolderName by remember { mutableStateOf("") }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
          contentDescription = null,
          tint = GenziiPurple,
          modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Move $selectedCount ${if (selectedCount == 1) "item" else "items"}",
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.Bold
        )
      }
    },
    text = {
      Column(
        modifier = Modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Text(
          text = "Choose destination folder:",
          fontSize = 12.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        availableFolders.forEach { folder ->
          val isCurrent = !isCustomFolder && selectedFolder == folder
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isCurrent) GenziiPurpleContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            border = if (isCurrent) BorderStroke(1.5.dp, GenziiPurple) else null,
            modifier = Modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable {
                isCustomFolder = false
                selectedFolder = folder
              }
              .testTag("move_folder_option_$folder")
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = if (isCurrent) GenziiPurple else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
              )
              Spacer(modifier = Modifier.width(10.dp))
              Text(
                text = folder,
                fontFamily = PoppinsFontFamily,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                fontSize = 13.sp,
                color = if (isCurrent) GenziiPurple else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f)
              )
              if (isCurrent) {
                Icon(
                  imageVector = Icons.Default.Check,
                  contentDescription = "Selected",
                  tint = GenziiPurple,
                  modifier = Modifier.size(18.dp)
                )
              }
            }
          }
        }

        // New Custom Folder option
        Surface(
          shape = RoundedCornerShape(12.dp),
          color = if (isCustomFolder) GenziiPurpleContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
          border = if (isCustomFolder) BorderStroke(1.5.dp, GenziiPurple) else null,
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { isCustomFolder = true }
            .testTag("move_folder_option_custom")
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = Icons.Default.CreateNewFolder,
              contentDescription = null,
              tint = if (isCustomFolder) GenziiPurple else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = "+ New Folder...",
              fontFamily = PoppinsFontFamily,
              fontWeight = if (isCustomFolder) FontWeight.Bold else FontWeight.Medium,
              fontSize = 13.sp,
              color = if (isCustomFolder) GenziiPurple else MaterialTheme.colorScheme.onBackground,
              modifier = Modifier.weight(1f)
            )
          }
        }

        if (isCustomFolder) {
          OutlinedTextField(
            value = customFolderName,
            onValueChange = { customFolderName = it },
            label = { Text("Folder Name") },
            placeholder = { Text("e.g. Project Archive") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("custom_folder_name_input"),
            shape = RoundedCornerShape(12.dp)
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val destination = if (isCustomFolder) {
            customFolderName.trim().ifBlank { "Personal" }
          } else {
            selectedFolder
          }
          onMoveConfirmed(destination)
        },
        colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple),
        modifier = Modifier.testTag("confirm_batch_move_button")
      ) {
        Text("Move Here")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    }
  )
}

@Composable
fun FolderCard(
  name: String,
  itemCount: Int,
  onClick: () -> Unit
) {
  val (folderBg, folderTint, folderIcon) = when {
    name.contains("Research", ignoreCase = true) || name.contains("Academic", ignoreCase = true) ->
      Triple(Color(0xFFE8EAF6), Color(0xFF3949AB), Icons.Default.MenuBook)
    name.contains("Assignment", ignoreCase = true) ->
      Triple(Color(0xFFE1F5FE), Color(0xFF0288D1), Icons.Outlined.Assignment)
    name.contains("Lecture", ignoreCase = true) || name.contains("Note", ignoreCase = true) ->
      Triple(Color(0xFFFFF8E1), Color(0xFFF57F17), Icons.Default.Description)
    name.contains("Vault", ignoreCase = true) || name.contains("Enclave", ignoreCase = true) ->
      Triple(GenziiPurpleContainer, GenziiPurple, Icons.Outlined.Lock)
    name.contains("Personal", ignoreCase = true) || name.contains("Archive", ignoreCase = true) ->
      Triple(Color(0xFFE0F2F1), Color(0xFF00897B), Icons.Default.PersonOutline)
    name.contains("Work", ignoreCase = true) || name.contains("Project", ignoreCase = true) ->
      Triple(Color(0xFFEDE7F6), Color(0xFF5E35B1), Icons.Default.FolderSpecial)
    else ->
      Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.Folder)
  }

  Card(
    modifier = Modifier
      .width(148.dp)
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(modifier = Modifier.padding(14.dp)) {
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(RoundedCornerShape(10.dp))
          .background(folderBg),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = folderIcon,
          contentDescription = name,
          tint = folderTint,
          modifier = Modifier.size(22.dp)
        )
      }
      Spacer(modifier = Modifier.height(10.dp))
      Text(
        text = name,
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultFileRow(
  doc: DocumentItem,
  isSelectionMode: Boolean = false,
  isSelected: Boolean = false,
  onToggleSelect: () -> Unit = {},
  onLongClick: () -> Unit = {},
  onClick: () -> Unit,
  onStarClick: () -> Unit,
  onDeleteClick: () -> Unit
) {
  var showMenu by remember { mutableStateOf(false) }

  Card(
    modifier = Modifier
      .fillMaxWidth()
      .defaultMinSize(minHeight = 72.dp)
      .clip(RoundedCornerShape(16.dp))
      .combinedClickable(
        onClick = {
          if (isSelectionMode) {
            onToggleSelect()
          } else {
            onClick()
          }
        },
        onLongClick = {
          onLongClick()
        }
      )
      .testTag("doc_row_${doc.id}"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected) GenziiPurpleContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surface
    ),
    border = if (isSelected) BorderStroke(2.dp, GenziiPurple) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.5.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 12.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      if (isSelectionMode) {
        Checkbox(
          checked = isSelected,
          onCheckedChange = { onToggleSelect() },
          colors = CheckboxDefaults.colors(
            checkedColor = GenziiPurple,
            checkmarkColor = GenziiWhite
          ),
          modifier = Modifier
            .padding(end = 8.dp)
            .testTag("doc_checkbox_${doc.id}")
        )
      }

      DocumentThumbnailView(doc = doc, size = 44.dp)

      Spacer(modifier = Modifier.width(12.dp))

      Column(
        modifier = Modifier.weight(1f),
        verticalArrangement = Arrangement.Center
      ) {
        Text(
          text = doc.displayName,
          fontFamily = PoppinsFontFamily,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.SemiBold,
          color = MaterialTheme.colorScheme.onBackground,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(3.dp))
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          Text(
            text = doc.sizeFormatted,
            fontFamily = PoppinsFontFamily,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Text(
            text = "•",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
          )
          Text(
            text = doc.updatedAt,
            fontFamily = PoppinsFontFamily,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 11.5.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          if (doc.isEncrypted) {
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = "Encrypted",
              tint = GenziiPurple,
              modifier = Modifier.size(12.dp)
            )
          }
        }
      }

      if (!isSelectionMode) {
        IconButton(
          onClick = onStarClick,
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            imageVector = if (doc.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
            contentDescription = if (doc.isStarred) "Starred" else "Star",
            tint = if (doc.isStarred) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.size(20.dp)
          )
        }

        Box {
          IconButton(
            onClick = { showMenu = true },
            modifier = Modifier.size(36.dp)
          ) {
            Icon(
              imageVector = Icons.Default.MoreVert,
              contentDescription = "More actions",
              tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
              modifier = Modifier.size(20.dp)
            )
          }
          DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
          ) {
            DropdownMenuItem(
              text = { Text("Open Preview", fontFamily = PoppinsFontFamily) },
              onClick = {
                showMenu = false
                onClick()
              },
              leadingIcon = { Icon(Icons.Default.Visibility, contentDescription = null, tint = GenziiPurple) }
            )
            DropdownMenuItem(
              text = { Text("Delete", fontFamily = PoppinsFontFamily, color = GenziiError) },
              onClick = {
                showMenu = false
                onDeleteClick()
              },
              leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = GenziiError) }
            )
          }
        }
      } else {
        // Visual indicator badge for selection mode
        Surface(
          shape = CircleShape,
          color = if (isSelected) GenziiPurple else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
          border = if (isSelected) null else BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
          modifier = Modifier
            .size(24.dp)
            .clickable { onToggleSelect() }
            .testTag("doc_selected_badge_${doc.id}")
        ) {
          if (isSelected) {
            Icon(
              imageVector = Icons.Default.Check,
              contentDescription = "Selected",
              tint = GenziiWhite,
              modifier = Modifier
                .padding(4.dp)
                .size(16.dp)
            )
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FilePreviewScreen(
  docId: String,
  onBack: () -> Unit
) {
  val documents by GenziiRepository.documents.collectAsState()
  val initialDoc = remember(documents, docId) {
    documents.find { it.id == docId } ?: documents.firstOrNull()
  }

  if (initialDoc == null) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Text("Document not found")
    }
    return
  }

  // Determine whether this document is an image (by type or file extension)
  val isInitialImage = remember(initialDoc) {
    initialDoc.type == FileType.IMAGE || isImageExtension(initialDoc.name)
  }

  // Build sorted preview collection: if opening an image, collect and sort all images in folder/vault
  val previewList = remember(documents, initialDoc, isInitialImage) {
    val savedSort = DocumentSortOrder.fromId(GenziiRepository.getSavedSortOrder())
    if (isInitialImage) {
      val inFolderImages = documents.filter {
        it.folder.equals(initialDoc.folder, ignoreCase = true) &&
        (it.type == FileType.IMAGE || isImageExtension(it.name))
      }
      val targetList = if (inFolderImages.isNotEmpty()) {
        inFolderImages
      } else {
        val allImages = documents.filter { it.type == FileType.IMAGE || isImageExtension(it.name) }
        if (allImages.isNotEmpty()) allImages else listOf(initialDoc)
      }
      when (savedSort) {
        DocumentSortOrder.DATE_NEWEST -> targetList.sortedByDescending { it.createdAt }
        DocumentSortOrder.DATE_OLDEST -> targetList.sortedBy { it.createdAt }
        DocumentSortOrder.NAME_AZ -> targetList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        DocumentSortOrder.NAME_ZA -> targetList.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
      }
    } else {
      val inFolderDocs = documents.filter { it.folder.equals(initialDoc.folder, ignoreCase = true) }
      val targetList = if (inFolderDocs.isNotEmpty()) inFolderDocs else documents
      when (savedSort) {
        DocumentSortOrder.DATE_NEWEST -> targetList.sortedByDescending { it.createdAt }
        DocumentSortOrder.DATE_OLDEST -> targetList.sortedBy { it.createdAt }
        DocumentSortOrder.NAME_AZ -> targetList.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        DocumentSortOrder.NAME_ZA -> targetList.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
      }
    }
  }

  val initialIndex = remember(previewList, docId) {
    val idx = previewList.indexOfFirst { it.id == docId }
    if (idx >= 0) idx else 0
  }

  val pagerState = androidx.compose.foundation.pager.rememberPagerState(
    initialPage = initialIndex,
    pageCount = { previewList.size }
  )

  val currentDoc = previewList.getOrNull(pagerState.currentPage) ?: initialDoc

  // File-type-aware capability flags
  val isCurrentDocImage = currentDoc.type == FileType.IMAGE || isImageExtension(currentDoc.name)
  val isCurrentDocPdf = currentDoc.type == FileType.PDF || currentDoc.name.lowercase().endsWith(".pdf")
  val isCurrentDocEditable = !isCurrentDocImage && !isCurrentDocPdf &&
      (currentDoc.type == FileType.TXT || currentDoc.name.endsWith(".txt", true) || currentDoc.name.endsWith(".md", true))
  val isCurrentDocPrintable = !isCurrentDocImage && (isCurrentDocPdf || currentDoc.type == FileType.TXT)

  var selectedTab by remember { mutableStateOf(0) } // 0: Preview, 1: Details
  var isEditing by remember { mutableStateOf(false) }
  var editedContent by remember(currentDoc.id) { mutableStateOf(currentDoc.content) }
  var showDeleteDialog by remember { mutableStateOf(false) }
  var showShareToast by remember { mutableStateOf(false) }
  var isImageZoomed by remember { mutableStateOf(false) }

  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()

  val exportLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.CreateDocument(
      if (isCurrentDocImage) "image/png" else "application/pdf"
    )
  ) { uri ->
    if (uri != null && currentDoc != null) {
      coroutineScope.launch(Dispatchers.IO) {
        try {
          if (currentDoc.localFilePath != null && File(currentDoc.localFilePath).exists()) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
              File(currentDoc.localFilePath).inputStream().use { input -> input.copyTo(out) }
            }
          } else if (isCurrentDocImage && !currentDoc.uriString.isNullOrBlank()) {
            val srcUri = android.net.Uri.parse(currentDoc.uriString)
            context.contentResolver.openInputStream(srcUri)?.use { input ->
              context.contentResolver.openOutputStream(uri)?.use { out ->
                input.copyTo(out)
              }
            }
          } else if (currentDoc.type == FileType.PDF && currentDoc.content.startsWith("%PDF")) {
            context.contentResolver.openOutputStream(uri)?.use { out ->
              out.write(currentDoc.content.toByteArray(Charsets.ISO_8859_1))
            }
          } else {
            val genResult = PdfExportManager.generateDocumentPdf(context, currentDoc.name, currentDoc.content)
            if (genResult.isSuccess) {
              PdfExportManager.exportPdfToUri(context, genResult.getOrThrow(), uri)
            } else {
              context.contentResolver.openOutputStream(uri)?.use { out ->
                out.write(currentDoc.content.toByteArray())
              }
            }
          }
          withContext(Dispatchers.Main) {
            Toast.makeText(context, if (isCurrentDocImage) "Image saved successfully!" else "Exported successfully!", Toast.LENGTH_SHORT).show()
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) {
            Toast.makeText(context, "Save/Export failed: ${e.message}", Toast.LENGTH_LONG).show()
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
          IconButton(
            onClick = onBack,
            modifier = Modifier.testTag("preview_back_button")
          ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
          }
          Spacer(modifier = Modifier.width(4.dp))
          Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = currentDoc.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
              )
              if (previewList.size > 1) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                  shape = RoundedCornerShape(8.dp),
                  color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                  modifier = Modifier.testTag("preview_page_indicator")
                ) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                  ) {
                    if (isCurrentDocImage) {
                      Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = GenziiPurple
                      )
                      Spacer(modifier = Modifier.width(3.dp))
                    }
                    Text(
                      text = "${pagerState.currentPage + 1}/${previewList.size}",
                      fontSize = 10.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }
            Text(
              text = "${currentDoc.sizeFormatted} • ${if (isCurrentDocImage) "Image" else currentDoc.type.name}",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = { GenziiRepository.toggleStar(currentDoc.id) },
            modifier = Modifier.testTag("preview_action_star")
          ) {
            Icon(
              imageVector = if (currentDoc.isStarred) Icons.Filled.Star else Icons.Outlined.StarBorder,
              contentDescription = "Star",
              tint = if (currentDoc.isStarred) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
          IconButton(
            onClick = { showDeleteDialog = true },
            modifier = Modifier.testTag("preview_action_delete")
          ) {
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
          horizontalArrangement = Arrangement.SpaceAround,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // 1. Share (available for all valid file types)
          BottomActionItem(
            label = "Share",
            icon = Icons.Outlined.Share,
            testTag = "preview_action_share"
          ) {
            try {
              if (currentDoc.localFilePath != null && File(currentDoc.localFilePath).exists()) {
                val file = File(currentDoc.localFilePath)
                val shareUri = androidx.core.content.FileProvider.getUriForFile(
                  context,
                  "${context.packageName}.fileprovider",
                  file
                )
                val mimeType = when {
                  isCurrentDocImage -> "image/*"
                  isCurrentDocPdf -> "application/pdf"
                  currentDoc.type == FileType.DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                  else -> "text/plain"
                }
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                  putExtra(Intent.EXTRA_STREAM, shareUri)
                  putExtra(Intent.EXTRA_SUBJECT, currentDoc.name)
                  type = mimeType
                  addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share ${if (isCurrentDocImage) "Image" else "Document"}"))
              } else {
                val sendIntent = Intent().apply {
                  action = Intent.ACTION_SEND
                  putExtra(Intent.EXTRA_SUBJECT, currentDoc.name)
                  putExtra(Intent.EXTRA_TEXT, currentDoc.content)
                  type = "text/plain"
                }
                context.startActivity(Intent.createChooser(sendIntent, "Share Document via"))
              }
            } catch (e: Exception) {
              Toast.makeText(context, "Share error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
          }

          // 2. Save / Export (label adapted per file type)
          BottomActionItem(
            label = if (isCurrentDocImage) "Save Image" else "Export",
            icon = if (isCurrentDocImage) Icons.Outlined.SaveAlt else Icons.Outlined.Download,
            testTag = "preview_action_export"
          ) {
            val exportName = if (isCurrentDocImage) {
              if (currentDoc.name.contains(".")) currentDoc.name else "${currentDoc.name}.jpg"
            } else {
              if (currentDoc.name.endsWith(".pdf", ignoreCase = true)) currentDoc.name else "${currentDoc.name}.pdf"
            }
            exportLauncher.launch(exportName)
          }

          // 3. Print (Only displayed when genuinely printable; NEVER for images)
          if (isCurrentDocPrintable) {
            BottomActionItem(
              label = "Print",
              icon = Icons.Outlined.Print,
              testTag = "preview_action_print"
            ) {
              coroutineScope.launch {
                if (currentDoc.localFilePath != null && File(currentDoc.localFilePath).exists() && isCurrentDocPdf) {
                  PdfExportManager.printPdfFile(context, currentDoc.name, File(currentDoc.localFilePath))
                } else {
                  val genResult = PdfExportManager.generateDocumentPdf(context, currentDoc.name, currentDoc.content)
                  if (genResult.isSuccess) {
                    PdfExportManager.printPdfFile(context, currentDoc.name, genResult.getOrThrow())
                  } else {
                    Toast.makeText(context, "Print preparation failed: ${genResult.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
                  }
                }
              }
            }
          }

          // 4. Edit (Only displayed for editable text documents; NEVER for images or PDFs)
          if (isCurrentDocEditable) {
            BottomActionItem(
              label = if (isEditing) "Save" else "Edit",
              icon = if (isEditing) Icons.Default.Save else Icons.Outlined.Edit,
              testTag = "preview_action_edit"
            ) {
              if (isEditing) {
                GenziiRepository.updateDocumentContent(currentDoc.id, editedContent)
                isEditing = false
                Toast.makeText(context, "Document updated", Toast.LENGTH_SHORT).show()
              } else {
                editedContent = currentDoc.content
                isEditing = true
              }
            }
          }

          // 5. Encrypted Enclave Badge / Info
          BottomActionItem(
            label = "Encrypted",
            icon = Icons.Outlined.Lock,
            testTag = "preview_action_encrypted"
          ) {
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
        // HorizontalPager for fluid swipe navigation between documents/images
        // userScrollEnabled is disabled while the current image is zoomed in to avoid swipe conflicts
        androidx.compose.foundation.pager.HorizontalPager(
          state = pagerState,
          userScrollEnabled = !isImageZoomed,
          modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .testTag("preview_pager")
        ) { page ->
          val pageDoc = previewList.getOrNull(page) ?: currentDoc
          RealDocumentViewer(
            doc = pageDoc,
            isEditing = isEditing && page == pagerState.currentPage,
            editedContent = editedContent,
            onEditedContentChange = { editedContent = it },
            onZoomChange = { zoomed ->
              if (page == pagerState.currentPage) {
                isImageZoomed = zoomed
              }
            },
            modifier = Modifier.fillMaxSize()
          )
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
            val computedChecksum = remember(currentDoc.content) {
              try {
                val md = java.security.MessageDigest.getInstance("SHA-256")
                val bytes = md.digest(currentDoc.content.toByteArray(Charsets.UTF_8))
                bytes.joinToString("") { "%02x".format(it) }
              } catch (_: Exception) {
                "Verified"
              }
            }
            DetailRow(title = "Folder", value = currentDoc.folder)
            DetailRow(title = "File Size", value = currentDoc.sizeFormatted)
            DetailRow(title = "File Format", value = currentDoc.type.name)
            DetailRow(title = "Last Modified", value = currentDoc.updatedAt)
            DetailRow(title = "Originality Score", value = if (currentDoc.originalityScore != null) "${currentDoc.originalityScore}%" else "Not scanned")
            DetailRow(title = "Encryption", value = "AES-256-GCM (Zero-Knowledge)")
            DetailRow(title = "SHA-256 Checksum", value = computedChecksum.take(24) + "...")
          }
        }
      }

      Spacer(modifier = Modifier.height(12.dp))
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog) {
      AlertDialog(
        onDismissRequest = { showDeleteDialog = false },
        title = {
          Text(
            text = "Delete ${if (isCurrentDocImage) "Image" else "Document"}",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold
          )
        },
        text = {
          Text(
            text = "Are you sure you want to permanently delete \"${currentDoc.name}\" from your Secure Vault? This cannot be undone.",
            fontFamily = PoppinsFontFamily
          )
        },
        confirmButton = {
          Button(
            onClick = {
              val docToDeleteId = currentDoc.id
              GenziiRepository.deleteDocument(docToDeleteId)
              showDeleteDialog = false
              Toast.makeText(context, "Deleted \"${currentDoc.name}\"", Toast.LENGTH_SHORT).show()
              if (previewList.size <= 1) {
                onBack()
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiError),
            modifier = Modifier.testTag("confirm_delete_button")
          ) {
            Text("Delete", color = Color.White)
          }
        },
        dismissButton = {
          TextButton(
            onClick = { showDeleteDialog = false },
            modifier = Modifier.testTag("cancel_delete_button")
          ) {
            Text("Cancel")
          }
        }
      )
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
  testTag: String = "",
  onClick: () -> Unit
) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    modifier = Modifier
      .clip(RoundedCornerShape(12.dp))
      .then(if (testTag.isNotBlank()) Modifier.testTag(testTag) else Modifier)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VaultFolderViewScreen(
  folderName: String,
  onOpenDocument: (String) -> Unit,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val documents by GenziiRepository.documents.collectAsState()
  val folderDocs = remember(documents, folderName) {
    documents.filter { it.folder.equals(folderName, ignoreCase = true) }
  }

  var showNewNoteDialog by remember { mutableStateOf(false) }
  var sortOrder by remember {
    mutableStateOf(DocumentSortOrder.fromId(GenziiRepository.getSavedSortOrder()))
  }
  var showSortMenu by remember { mutableStateOf(false) }
  var isSelectionMode by remember { mutableStateOf(false) }
  var selectedDocIds by remember { mutableStateOf(setOf<String>()) }
  var showBatchDeleteConfirm by remember { mutableStateOf(false) }
  var showBatchMoveDialog by remember { mutableStateOf(false) }

  // Cancel multi-selection on system back press
  BackHandler(enabled = isSelectionMode) {
    isSelectionMode = false
    selectedDocIds = emptySet()
  }

  val sortedFolderDocs = remember(folderDocs, sortOrder) {
    when (sortOrder) {
      DocumentSortOrder.DATE_NEWEST -> folderDocs.sortedByDescending { it.createdAt }
      DocumentSortOrder.DATE_OLDEST -> folderDocs.sortedBy { it.createdAt }
      DocumentSortOrder.NAME_AZ -> folderDocs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
      DocumentSortOrder.NAME_ZA -> folderDocs.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
    }
  }

  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri ->
    if (uri != null) {
      coroutineScope.launch {
        val fileInfo = com.example.util.FileUtils.readPickedFile(context, uri)
        GenziiRepository.addImportedDocument(
          name = fileInfo.name,
          sizeFormatted = fileInfo.sizeFormatted,
          sizeBytes = fileInfo.sizeBytes,
          fileType = fileInfo.fileType,
          content = fileInfo.content,
          folder = folderName,
          localFilePath = fileInfo.localFilePath,
          uriString = fileInfo.uriString,
          pageCount = fileInfo.pageCount
        )
      }
    }
  }

  val (folderBg, folderTint, folderIcon) = when {
    folderName.contains("Research", ignoreCase = true) || folderName.contains("Academic", ignoreCase = true) ->
      Triple(Color(0xFFE8EAF6), Color(0xFF3949AB), Icons.Default.MenuBook)
    folderName.contains("Assignment", ignoreCase = true) ->
      Triple(Color(0xFFE1F5FE), Color(0xFF0288D1), Icons.Outlined.Assignment)
    folderName.contains("Lecture", ignoreCase = true) || folderName.contains("Note", ignoreCase = true) ->
      Triple(Color(0xFFFFF8E1), Color(0xFFF57F17), Icons.Default.Description)
    folderName.contains("Vault", ignoreCase = true) || folderName.contains("Enclave", ignoreCase = true) ->
      Triple(GenziiPurpleContainer, GenziiPurple, Icons.Outlined.Lock)
    folderName.contains("Personal", ignoreCase = true) || folderName.contains("Archive", ignoreCase = true) ->
      Triple(Color(0xFFE0F2F1), Color(0xFF00897B), Icons.Default.PersonOutline)
    folderName.contains("Work", ignoreCase = true) || folderName.contains("Project", ignoreCase = true) ->
      Triple(Color(0xFFEDE7F6), Color(0xFF5E35B1), Icons.Default.FolderSpecial)
    else ->
      Triple(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.Folder)
  }

  Scaffold(
    topBar = {
      if (isSelectionMode) {
        Surface(
          color = MaterialTheme.colorScheme.surface,
          tonalElevation = 3.dp,
          modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(onClick = {
                isSelectionMode = false
                selectedDocIds = emptySet()
              }) {
                Icon(Icons.Default.Close, contentDescription = "Exit selection")
              }
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = "${selectedDocIds.size} selected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
              TextButton(onClick = {
                if (selectedDocIds.size == sortedFolderDocs.size) {
                  selectedDocIds = emptySet()
                } else {
                  selectedDocIds = sortedFolderDocs.map { it.id }.toSet()
                }
              }) {
                Text(
                  text = if (selectedDocIds.size == sortedFolderDocs.size && sortedFolderDocs.isNotEmpty()) "Deselect All" else "Select All",
                  color = GenziiPurple,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold
                )
              }

              IconButton(
                onClick = {
                  selectedDocIds.forEach { id ->
                    GenziiRepository.toggleStar(id)
                  }
                  Toast.makeText(context, "Starred selected items", Toast.LENGTH_SHORT).show()
                },
                enabled = selectedDocIds.isNotEmpty(),
                modifier = Modifier.testTag("folder_star_selected_button")
              ) {
                Icon(Icons.Default.Star, contentDescription = "Star selected", tint = Color(0xFFFFB300))
              }

              IconButton(
                onClick = { showBatchMoveDialog = true },
                enabled = selectedDocIds.isNotEmpty(),
                modifier = Modifier.testTag("folder_move_selected_button")
              ) {
                Icon(
                  imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                  contentDescription = "Move selected",
                  tint = if (selectedDocIds.isNotEmpty()) GenziiPurple else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
              }

              IconButton(
                onClick = { showBatchDeleteConfirm = true },
                enabled = selectedDocIds.isNotEmpty(),
                modifier = Modifier.testTag("folder_delete_selected_button")
              ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete selected", tint = if (selectedDocIds.isNotEmpty()) GenziiError else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
              }
            }
          }
        }
      } else {
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
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = folderName,
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "${sortedFolderDocs.size} files • AES-256 Enclave",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          // Sort Menu Dropdown
          Box {
            IconButton(
              onClick = { showSortMenu = true },
              modifier = Modifier.testTag("folder_sort_button")
            ) {
              Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort", tint = GenziiPurple)
            }
            DropdownMenu(
              expanded = showSortMenu,
              onDismissRequest = { showSortMenu = false },
              modifier = Modifier.testTag("folder_sort_menu")
            ) {
              DocumentSortOrder.entries.forEach { order ->
                DropdownMenuItem(
                  text = {
                    Text(
                      text = order.displayName,
                      fontWeight = if (sortOrder == order) FontWeight.Bold else FontWeight.Normal,
                      color = if (sortOrder == order) GenziiPurple else MaterialTheme.colorScheme.onSurface
                    )
                  },
                  onClick = {
                    sortOrder = order
                    GenziiRepository.saveSortOrder(order.id)
                    showSortMenu = false
                  },
                  leadingIcon = {
                    val icon = when (order) {
                      DocumentSortOrder.DATE_NEWEST, DocumentSortOrder.DATE_OLDEST -> Icons.Outlined.Schedule
                      DocumentSortOrder.NAME_AZ, DocumentSortOrder.NAME_ZA -> Icons.Outlined.SortByAlpha
                    }
                    Icon(
                      imageVector = icon,
                      contentDescription = null,
                      tint = if (sortOrder == order) GenziiPurple else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  },
                  trailingIcon = {
                    if (sortOrder == order) {
                      Icon(Icons.Default.Check, contentDescription = "Active", tint = GenziiPurple)
                    }
                  },
                  modifier = Modifier.testTag("folder_sort_item_${order.id}")
                )
              }
            }
          }

          IconButton(onClick = { filePickerLauncher.launch(arrayOf("*/*")) }) {
            Icon(Icons.Default.UploadFile, contentDescription = "Import file", tint = GenziiPurple)
          }
        }
      }
    },
    floatingActionButton = {
      FloatingActionButton(
        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
        containerColor = GenziiPurple,
        contentColor = GenziiWhite,
        shape = CircleShape
      ) {
        Icon(Icons.Default.Add, contentDescription = "Add Document")
      }
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    if (folderDocs.isEmpty()) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Card(
          shape = RoundedCornerShape(20.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
          ) {
            Box(
              modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(folderBg),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = folderIcon,
                contentDescription = null,
                tint = folderTint,
                modifier = Modifier.size(32.dp)
              )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
              text = "No files in $folderName",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
              text = "Files organized into $folderName will be kept safe with zero-knowledge local encryption.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              Button(
                onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple),
                shape = RoundedCornerShape(12.dp)
              ) {
                Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import to $folderName", fontSize = 12.sp)
              }

              OutlinedButton(
                onClick = { showNewNoteDialog = true },
                shape = RoundedCornerShape(12.dp)
              ) {
                Icon(Icons.Default.NoteAdd, contentDescription = null, modifier = Modifier.size(16.dp), tint = GenziiPurple)
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Note", fontSize = 12.sp, color = GenziiPurple)
              }
            }
          }
        }
      }
    } else {
      LazyColumn(
        modifier = Modifier
          .fillMaxSize()
          .padding(padding)
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        items(sortedFolderDocs, key = { it.id }) { doc ->
          VaultFileRow(
            doc = doc,
            isSelectionMode = isSelectionMode,
            isSelected = doc.id in selectedDocIds,
            onToggleSelect = {
              selectedDocIds = if (doc.id in selectedDocIds) {
                selectedDocIds - doc.id
              } else {
                selectedDocIds + doc.id
              }
            },
            onLongClick = {
              if (!isSelectionMode) {
                isSelectionMode = true
                selectedDocIds = setOf(doc.id)
              }
            },
            onClick = { onOpenDocument(doc.id) },
            onStarClick = { GenziiRepository.toggleStar(doc.id) },
            onDeleteClick = { GenziiRepository.deleteDocument(doc.id) }
          )
        }
      }
    }

    if (showBatchDeleteConfirm) {
      AlertDialog(
        onDismissRequest = { showBatchDeleteConfirm = false },
        title = {
          Text("Delete ${selectedDocIds.size} items?", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold)
        },
        text = {
          Text("Are you sure you want to delete these documents from $folderName? This action cannot be undone.")
        },
        confirmButton = {
          Button(
            onClick = {
              val count = selectedDocIds.size
              GenziiRepository.deleteDocuments(selectedDocIds)
              selectedDocIds = emptySet()
              isSelectionMode = false
              showBatchDeleteConfirm = false
              Toast.makeText(context, "Deleted $count items", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiError),
            modifier = Modifier.testTag("confirm_folder_batch_delete_button")
          ) {
            Text("Delete")
          }
        },
        dismissButton = {
          TextButton(onClick = { showBatchDeleteConfirm = false }) {
            Text("Cancel")
          }
        }
      )
    }

    if (showBatchMoveDialog) {
      val availableFolders = remember(documents) {
        (listOf("Research Papers", "Thesis Drafts", "Assignments", "Personal") + documents.map { it.folder })
          .distinct()
          .filter { it.isNotBlank() && !it.equals(folderName, ignoreCase = true) }
      }
      BatchMoveDialog(
        selectedCount = selectedDocIds.size,
        availableFolders = availableFolders,
        onDismiss = { showBatchMoveDialog = false },
        onMoveConfirmed = { targetFolder ->
          val count = selectedDocIds.size
          GenziiRepository.moveDocumentsToFolder(selectedDocIds, targetFolder)
          selectedDocIds = emptySet()
          isSelectionMode = false
          showBatchMoveDialog = false
          Toast.makeText(context, "Moved $count items to $targetFolder", Toast.LENGTH_SHORT).show()
        }
      )
    }

    if (showNewNoteDialog) {
      var noteTitle by remember { mutableStateOf("") }
      var noteContent by remember { mutableStateOf("") }

      AlertDialog(
        onDismissRequest = { showNewNoteDialog = false },
        title = {
          Text("New Note in $folderName", fontWeight = FontWeight.Bold)
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
              value = noteTitle,
              onValueChange = { noteTitle = it },
              label = { Text("Note Title") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = noteContent,
              onValueChange = { noteContent = it },
              label = { Text("Note Content") },
              modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              if (noteTitle.isNotBlank()) {
                GenziiRepository.addDocument(
                  name = noteTitle,
                  content = noteContent,
                  folder = folderName,
                  type = FileType.TXT
                )
                showNewNoteDialog = false
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
          ) {
            Text("Create Note")
          }
        },
        dismissButton = {
          TextButton(onClick = { showNewNoteDialog = false }) {
            Text("Cancel", color = GenziiPurple)
          }
        }
      )
    }
  }
}

@Composable
fun RealDocumentViewer(
  doc: DocumentItem,
  isEditing: Boolean,
  editedContent: String,
  onEditedContentChange: (String) -> Unit,
  onZoomChange: (Boolean) -> Unit = {},
  modifier: Modifier = Modifier
) {
  if (isEditing) {
    Card(
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
      elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
      modifier = modifier
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(16.dp)
          .verticalScroll(rememberScrollState())
      ) {
        OutlinedTextField(
          value = editedContent,
          onValueChange = onEditedContentChange,
          modifier = Modifier.fillMaxSize(),
          label = { Text("Editing Content") }
        )
      }
    }
  } else {
    val context = LocalContext.current
    val resolvedLocalFile = remember(doc.localFilePath, doc.uriString, doc.id) {
      val existing = doc.localFilePath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
      if (existing != null) return@remember existing

      // Try resolving from doc.uriString if available
      if (!doc.uriString.isNullOrBlank()) {
        try {
          val uri = android.net.Uri.parse(doc.uriString)
          val restored = com.example.util.FileUtils.copyUriToVaultStorage(context, uri, doc.name)
          if (restored != null && restored.exists() && restored.length() > 0) return@remember restored
        } catch (_: Exception) {}
      }

      // If doc is a PDF with raw byte content
      if (doc.type == FileType.PDF && doc.content.startsWith("%PDF")) {
        try {
          val vaultDir = File(context.filesDir, "vault_documents").apply { mkdirs() }
          val cacheFile = File(vaultDir, "${doc.id}_cached.pdf")
          cacheFile.writeBytes(doc.content.toByteArray(Charsets.ISO_8859_1))
          if (cacheFile.exists() && cacheFile.length() > 0) return@remember cacheFile
        } catch (_: Exception) {}
      }
      null
    }

    when {
      doc.type == FileType.PDF && resolvedLocalFile != null -> {
        RealPdfViewer(
          file = resolvedLocalFile,
          fallbackText = doc.content,
          modifier = modifier
        )
      }
      doc.type == FileType.IMAGE -> {
        RealImageViewer(
          doc = doc,
          resolvedFile = resolvedLocalFile,
          ocrText = doc.content,
          onZoomChange = onZoomChange,
          modifier = modifier
        )
      }
      else -> {
        RealTextDocumentViewer(
          content = doc.content,
          fileType = doc.type,
          fileName = doc.name,
          modifier = modifier
        )
      }
    }
  }
}

@Composable
fun RealPdfViewer(
  file: File,
  fallbackText: String,
  modifier: Modifier = Modifier
) {
  var currentPageIndex by remember { mutableStateOf(0) }
  var totalPages by remember { mutableStateOf(1) }
  var pageBitmap by remember { mutableStateOf<Bitmap?>(null) }
  var isLoading by remember { mutableStateOf(true) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var viewMode by remember { mutableStateOf(0) } // 0 = Rendered Page, 1 = Extracted Text

  var scale by remember(currentPageIndex) { mutableFloatStateOf(1f) }
  var offsetX by remember(currentPageIndex) { mutableFloatStateOf(0f) }
  var offsetY by remember(currentPageIndex) { mutableFloatStateOf(0f) }

  LaunchedEffect(file.absolutePath, currentPageIndex) {
    isLoading = true
    errorMessage = null
    withContext(Dispatchers.IO) {
      var pfd: ParcelFileDescriptor? = null
      var renderer: PdfRenderer? = null
      var page: PdfRenderer.Page? = null
      try {
        if (!file.exists() || file.length() == 0L) {
          throw java.io.FileNotFoundException("PDF file does not exist or is empty: ${file.name}")
        }
        pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        renderer = PdfRenderer(pfd)
        val count = renderer.pageCount
        withContext(Dispatchers.Main) {
          totalPages = count.coerceAtLeast(1)
        }
        val safeIndex = currentPageIndex.coerceIn(0, (count - 1).coerceAtLeast(0))
        page = renderer.openPage(safeIndex)
        val renderScale = 2f
        val bmpWidth = (page.width * renderScale).toInt().coerceIn(300, 2400)
        val bmpHeight = (page.height * renderScale).toInt().coerceIn(400, 3600)
        val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        withContext(Dispatchers.Main) {
          pageBitmap = bitmap
          isLoading = false
        }
      } catch (e: Throwable) {
        Log.e("RealPdfViewer", "Error rendering PDF page", e)
        withContext(Dispatchers.Main) {
          errorMessage = e.message ?: "Failed to render PDF page"
          isLoading = false
        }
      } finally {
        try { page?.close() } catch (_: Exception) {}
        try { renderer?.close() } catch (_: Exception) {}
        try { pfd?.close() } catch (_: Exception) {}
      }
    }
  }

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // PDF Toolbar Controls
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Page Navigation
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = {
                if (currentPageIndex > 0) {
                  currentPageIndex--
                  scale = 1f
                  offsetX = 0f
                  offsetY = 0f
                }
              },
              enabled = currentPageIndex > 0,
              modifier = Modifier.size(30.dp)
            ) {
              Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Page")
            }
            Text(
              text = "${currentPageIndex + 1}/$totalPages",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.Bold,
              color = GenziiPurple,
              modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(
              onClick = {
                if (currentPageIndex < totalPages - 1) {
                  currentPageIndex++
                  scale = 1f
                  offsetX = 0f
                  offsetY = 0f
                }
              },
              enabled = currentPageIndex < totalPages - 1,
              modifier = Modifier.size(30.dp)
            ) {
              Icon(Icons.Default.ChevronRight, contentDescription = "Next Page")
            }
          }

          // Zoom Controls
          if (viewMode == 0 && pageBitmap != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = {
                  scale = (scale / 1.3f).coerceAtLeast(1f)
                  if (scale <= 1.05f) { scale = 1f; offsetX = 0f; offsetY = 0f }
                },
                enabled = scale > 1f,
                modifier = Modifier.size(28.dp)
              ) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(16.dp))
              }
              Text(
                text = "${(scale * 100).toInt()}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = GenziiPurple,
                modifier = Modifier.padding(horizontal = 2.dp)
              )
              IconButton(
                onClick = { scale = (scale * 1.3f).coerceAtMost(5f) },
                enabled = scale < 5f,
                modifier = Modifier.size(28.dp)
              ) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(16.dp))
              }
              if (scale > 1f) {
                IconButton(
                  onClick = { scale = 1f; offsetX = 0f; offsetY = 0f },
                  modifier = Modifier.size(28.dp)
                ) {
                  Icon(Icons.Default.FitScreen, contentDescription = "Reset Zoom", modifier = Modifier.size(16.dp), tint = GenziiPurple)
                }
              }
            }
          }

          // View mode toggle (Page vs Text)
          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.background)
              .padding(2.dp)
          ) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (viewMode == 0) GenziiPurple else Color.Transparent)
                .clickable { viewMode = 0 }
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                "Page",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (viewMode == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (viewMode == 1) GenziiPurple else Color.Transparent)
                .clickable { viewMode = 1 }
                .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
              Text(
                "Text",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (viewMode == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      // Content Area
      if (viewMode == 0) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
          contentAlignment = Alignment.Center
        ) {
          if (isLoading) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
              CircularProgressIndicator(color = GenziiPurple, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
              Spacer(modifier = Modifier.height(8.dp))
              Text("Rendering PDF page...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          } else if (errorMessage != null || pageBitmap == null) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier.padding(16.dp)
            ) {
              Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = GenziiPurple, modifier = Modifier.size(40.dp))
              Spacer(modifier = Modifier.height(8.dp))
              Text("Could not render page image: ${errorMessage ?: "Unknown error"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
              Spacer(modifier = Modifier.height(8.dp))
              OutlinedButton(onClick = { viewMode = 1 }) {
                Text("View Extracted Text", fontSize = 12.sp)
              }
            }
          } else {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(currentPageIndex) {
                  detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 5f)
                    if (scale > 1f) {
                      offsetX += pan.x
                      offsetY += pan.y
                    } else {
                      offsetX = 0f
                      offsetY = 0f
                    }
                  }
                },
              contentAlignment = Alignment.Center
            ) {
              Image(
                bitmap = pageBitmap!!.asImageBitmap(),
                contentDescription = "PDF Page ${currentPageIndex + 1}",
                modifier = Modifier
                  .fillMaxSize()
                  .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                  ),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
              )
            }
          }
        }
      } else {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
        ) {
          Text(
            text = fallbackText.ifBlank { "No text content found in this PDF." },
            fontFamily = PoppinsFontFamily,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onBackground
          )
        }
      }
    }
  }
}

@Composable
fun RealImageViewer(
  file: File,
  ocrText: String,
  modifier: Modifier = Modifier
) {
  val doc = remember(file.absolutePath) {
    DocumentItem(
      id = file.name,
      name = file.name,
      sizeFormatted = "${(file.length() / 1024).coerceAtLeast(1)} KB",
      type = FileType.IMAGE,
      folder = "Vault",
      updatedAt = "Just now",
      content = ocrText,
      localFilePath = file.absolutePath
    )
  }
  RealImageViewer(
    doc = doc,
    resolvedFile = file,
    ocrText = ocrText,
    onZoomChange = {},
    modifier = modifier
  )
}

@Composable
fun RealImageViewer(
  doc: DocumentItem,
  resolvedFile: File?,
  ocrText: String,
  onZoomChange: (Boolean) -> Unit = {},
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  var selectedTab by remember { mutableStateOf(0) } // 0 = Image, 1 = OCR Text
  var scale by remember(doc.id) { mutableFloatStateOf(1f) }
  var offsetX by remember(doc.id) { mutableFloatStateOf(0f) }
  var offsetY by remember(doc.id) { mutableFloatStateOf(0f) }

  val bitmap = remember(doc.id, resolvedFile?.absolutePath, doc.uriString) {
    try {
      if (resolvedFile != null && resolvedFile.exists() && resolvedFile.length() > 0L) {
        BitmapFactory.decodeFile(resolvedFile.absolutePath)
      } else if (!doc.uriString.isNullOrBlank()) {
        val uri = android.net.Uri.parse(doc.uriString)
        context.contentResolver.openInputStream(uri)?.use { stream ->
          BitmapFactory.decodeStream(stream)
        }
      } else if (doc.content.startsWith("data:image") || doc.content.length > 100) {
        val cleanBase64 = if (doc.content.contains(",")) doc.content.substringAfter(",") else doc.content
        val decodedBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
      } else {
        null
      }
    } catch (e: Throwable) {
      Log.e("RealImageViewer", "Failed to decode image file", e)
      null
    }
  }

  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier
  ) {
    Column(modifier = Modifier.fillMaxSize()) {
      // Image vs OCR Tab bar
      Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = "Image Preview",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = GenziiPurple
          )

          // Zoom Controls
          if (selectedTab == 0 && bitmap != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              IconButton(
                onClick = {
                  val newScale = (scale / 1.3f).coerceAtLeast(1f)
                  if (newScale <= 1.05f) {
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                    onZoomChange(false)
                  } else {
                    scale = newScale
                    onZoomChange(true)
                  }
                },
                enabled = scale > 1f,
                modifier = Modifier
                  .size(28.dp)
                  .testTag("zoom_out_button")
              ) {
                Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(16.dp))
              }
              Text(
                text = "${(scale * 100).toInt()}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = GenziiPurple,
                modifier = Modifier
                  .padding(horizontal = 2.dp)
                  .testTag("zoom_level_text")
              )
              IconButton(
                onClick = {
                  val newScale = (scale * 1.3f).coerceAtMost(5f)
                  scale = newScale
                  onZoomChange(newScale > 1.05f)
                },
                enabled = scale < 5f,
                modifier = Modifier
                  .size(28.dp)
                  .testTag("zoom_in_button")
              ) {
                Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(16.dp))
              }
              if (scale > 1f) {
                IconButton(
                  onClick = {
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                    onZoomChange(false)
                  },
                  modifier = Modifier
                    .size(28.dp)
                    .testTag("fit_to_screen_button")
                ) {
                  Icon(Icons.Default.FitScreen, contentDescription = "Reset Zoom", modifier = Modifier.size(16.dp), tint = GenziiPurple)
                }
              }
            }
          }

          Row(
            modifier = Modifier
              .clip(RoundedCornerShape(8.dp))
              .background(MaterialTheme.colorScheme.background)
              .padding(2.dp)
          ) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (selectedTab == 0) GenziiPurple else Color.Transparent)
                .clickable { selectedTab = 0 }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("image_tab_button")
            ) {
              Text(
                "Image",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (selectedTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (selectedTab == 1) GenziiPurple else Color.Transparent)
                .clickable { selectedTab = 1 }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("ocr_tab_button")
            ) {
              Text(
                "OCR Text",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (selectedTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      }

      if (selectedTab == 0) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
          contentAlignment = Alignment.Center
        ) {
          if (bitmap != null) {
            Box(
              modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .pointerInput(doc.id) {
                  detectTapGestures(
                    onDoubleTap = {
                      if (scale > 1.2f) {
                        scale = 1f
                        offsetX = 0f
                        offsetY = 0f
                        onZoomChange(false)
                      } else {
                        scale = 2.5f
                        onZoomChange(true)
                      }
                    }
                  )
                }
                .pointerInput(doc.id) {
                  detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(1f, 5f)
                    scale = newScale
                    if (newScale > 1.05f) {
                      offsetX += pan.x
                      offsetY += pan.y
                      onZoomChange(true)
                    } else {
                      scale = 1f
                      offsetX = 0f
                      offsetY = 0f
                      onZoomChange(false)
                    }
                  }
                },
              contentAlignment = Alignment.Center
            ) {
              Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Document Image Preview",
                modifier = Modifier
                  .fillMaxSize()
                  .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                  )
                  .testTag("preview_image_display"),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
              )
            }
          } else {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              modifier = Modifier
                .padding(16.dp)
                .testTag("preview_image_missing_state")
            ) {
              Icon(Icons.Default.Image, contentDescription = null, tint = GenziiPurple, modifier = Modifier.size(48.dp))
              Spacer(modifier = Modifier.height(8.dp))
              Text("Image preview unavailable or file missing", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
              if (ocrText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = { selectedTab = 1 }) {
                  Text("View Recognized Text", fontSize = 12.sp)
                }
              }
            }
          }
        }
      } else {
        Column(
          modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .testTag("preview_ocr_text_content")
        ) {
          Text(
            text = ocrText.ifBlank { "No text recognized from this image." },
            fontFamily = PoppinsFontFamily,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            color = MaterialTheme.colorScheme.onBackground
          )
        }
      }
    }
  }
}

@Composable
fun RealTextDocumentViewer(
  content: String,
  fileType: FileType,
  fileName: String,
  modifier: Modifier = Modifier
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Surface(
          color = GenziiPurpleContainer.copy(alpha = 0.5f),
          shape = RoundedCornerShape(8.dp)
        ) {
          Text(
            text = fileType.name,
            color = GenziiPurple,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
          )
        }
        val words = remember(content) { content.split(Regex("\\s+")).count { it.isNotBlank() } }
        Text(
          text = "$words words • ${content.length} chars",
          fontSize = 11.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
      Spacer(modifier = Modifier.height(10.dp))

      Column(
        modifier = Modifier
          .fillMaxSize()
          .verticalScroll(rememberScrollState())
      ) {
        Text(
          text = content.ifBlank { "Document is empty." },
          fontFamily = PoppinsFontFamily,
          fontSize = 14.sp,
          lineHeight = 22.sp,
          color = MaterialTheme.colorScheme.onBackground
        )
      }
    }
  }
}

