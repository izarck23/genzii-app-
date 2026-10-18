package com.example.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ads.GoogleTestRewardedAdDialog
import com.example.data.GenziiRepository
import com.example.model.ChatMessage
import com.example.model.DocumentItem
import com.example.network.GeminiService
import com.example.ui.theme.*
import com.example.util.FileUtils
import kotlinx.coroutines.launch

@Composable
fun AiChatScreen(
  onNavigateToWritingTools: () -> Unit
) {
  val context = LocalContext.current
  val messages by GenziiRepository.chatMessages.collectAsState()
  val isGenerating by GenziiRepository.isGenerating.collectAsState()
  val userProfile by GenziiRepository.userProfile.collectAsState()
  val vaultDocuments by GenziiRepository.documents.collectAsState()

  var inputQuery by remember { mutableStateOf("") }
  var attachedFile by remember { mutableStateOf<FileUtils.PickedFileInfo?>(null) }
  var isProcessingFile by remember { mutableStateOf(false) }

  var showAttachDialog by remember { mutableStateOf(false) }
  var showVaultDocPicker by remember { mutableStateOf(false) }
  var showConsentDialog by remember { mutableStateOf(false) }
  var showClearChatDialog by remember { mutableStateOf(false) }
  var showRewardedAdDialog by remember { mutableStateOf(false) }

  val listState = rememberLazyListState()
  val coroutineScope = rememberCoroutineScope()

  val promptChips = listOf(
    "Summarize document",
    "Identify research gaps",
    "Refine academic tone",
    "Draft abstract",
    "Check citations"
  )

  // SAF Document Picker (Zero-permission storage model)
  val filePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.OpenDocument()
  ) { uri: Uri? ->
    if (uri != null) {
      isProcessingFile = true
      coroutineScope.launch {
        try {
          val pickedInfo = FileUtils.readPickedFile(context, uri)
          attachedFile = pickedInfo
          Toast.makeText(context, "Attached: ${pickedInfo.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
          Toast.makeText(context, "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
          isProcessingFile = false
        }
      }
    }
  }

  // System Photo Picker for documents and figures
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri: Uri? ->
    if (uri != null) {
      isProcessingFile = true
      coroutineScope.launch {
        try {
          val pickedInfo = FileUtils.readPickedFile(context, uri)
          attachedFile = pickedInfo
          Toast.makeText(context, "Attached Image (OCR processed): ${pickedInfo.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
          Toast.makeText(context, "Error reading image: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
          isProcessingFile = false
        }
      }
    }
  }

  fun handleSendPrompt(text: String) {
    if (text.isBlank()) return
    // Check if cloud processing consent is required
    val needsConsent = !userProfile.isCloudAiConsentGranted && GeminiService.isApiKeyConfigured(userProfile.customApiKey)
    if (needsConsent) {
      inputQuery = text
      showConsentDialog = true
      return
    }

    val finalQuery = text.trim()
    val currAttached = attachedFile
    inputQuery = ""
    attachedFile = null

    GenziiRepository.sendChatMessage(
      userText = finalQuery,
      attachedFileName = currAttached?.name,
      attachedFileContent = currAttached?.content
    )

    coroutineScope.launch {
      listState.animateScrollToItem((messages.size + 1).coerceAtLeast(0))
    }
  }

  // Auto scroll on new messages or streaming chunks
  LaunchedEffect(messages.lastOrNull()?.text?.length, messages.size) {
    if (messages.isNotEmpty()) {
      listState.scrollToItem(messages.size - 1)
    }
  }

  Scaffold(
    topBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(38.dp)
              .clip(CircleShape)
              .background(GenziiPurpleContainer),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Filled.AutoAwesome,
              contentDescription = null,
              tint = GenziiPurple,
              modifier = Modifier.size(22.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Text(
                text = "Genzii Smart AI",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
              )
              Spacer(modifier = Modifier.width(6.dp))
              Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (GeminiService.isApiKeyConfigured(userProfile.customApiKey)) GenziiPurpleSoft.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceVariant
              ) {
                Text(
                  text = if (GeminiService.isApiKeyConfigured(userProfile.customApiKey)) "3.5 Flash" else "Local AI",
                  fontSize = 9.sp,
                  fontWeight = FontWeight.Bold,
                  color = GenziiPurple,
                  modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                )
              }
            }
            Text(
              text = if (isGenerating) "Generating response..." else "Conversational Academic Assistant",
              style = MaterialTheme.typography.bodySmall,
              color = if (isGenerating) GenziiPurple else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
          IconButton(
            onClick = { showClearChatDialog = true },
            modifier = Modifier.testTag("ai_clear_chat_button")
          ) {
            Icon(
              imageVector = Icons.Outlined.DeleteSweep,
              contentDescription = "Clear Chat History",
              tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          IconButton(
            onClick = onNavigateToWritingTools,
            modifier = Modifier.testTag("ai_writing_tools_shortcut")
          ) {
            Icon(
              imageVector = Icons.Outlined.Build,
              contentDescription = "Writing Tools",
              tint = GenziiPurple
            )
          }
        }
      }
    },
    bottomBar = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding()
          .background(MaterialTheme.colorScheme.surface)
          .padding(horizontal = 16.dp, vertical = 6.dp)
      ) {
        // Stop generation bar when active
        AnimatedVisibility(visible = isGenerating) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.Center
          ) {
            Surface(
              shape = RoundedCornerShape(16.dp),
              color = MaterialTheme.colorScheme.surfaceVariant,
              shadowElevation = 2.dp,
              modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                  GenziiRepository.cancelActiveGeneration()
                }
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(10.dp)
                    .background(Color(0xFFE53935), RoundedCornerShape(2.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Stop generating",
                  fontFamily = PoppinsFontFamily,
                  fontSize = 12.sp,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }
        }

        // Active attached file badge
        AnimatedVisibility(visible = attachedFile != null || isProcessingFile) {
          Card(
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 6.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = GenziiPurpleContainer.copy(alpha = 0.7f))
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                if (isProcessingFile) {
                  CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = GenziiPurple)
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Reading & parsing file locally...",
                    fontSize = 12.sp,
                    color = GenziiPurple
                  )
                } else if (attachedFile != null) {
                  Icon(
                    imageVector = Icons.Outlined.AttachFile,
                    contentDescription = null,
                    tint = GenziiPurple,
                    modifier = Modifier.size(16.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Column {
                    Text(
                      text = attachedFile!!.name,
                      fontSize = 12.sp,
                      fontWeight = FontWeight.SemiBold,
                      color = GenziiPurple,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis
                    )
                    Text(
                      text = "${attachedFile!!.sizeFormatted} • ${attachedFile!!.wordCount} words (Local context attached)",
                      fontSize = 10.sp,
                      color = GenziiPurpleDark.copy(alpha = 0.8f)
                    )
                  }
                }
              }

              if (attachedFile != null && !isProcessingFile) {
                IconButton(
                  onClick = { attachedFile = null },
                  modifier = Modifier.size(24.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove attachment",
                    tint = GenziiPurple,
                    modifier = Modifier.size(16.dp)
                  )
                }
              }
            }
          }
        }

        // Chat Input Row
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          IconButton(
            onClick = { showAttachDialog = true },
            modifier = Modifier.testTag("chat_attach_button")
          ) {
            Icon(
              imageVector = Icons.Outlined.AttachFile,
              contentDescription = "Attach Document",
              tint = if (attachedFile != null) GenziiPurple else MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          TextField(
            value = inputQuery,
            onValueChange = { inputQuery = it },
            placeholder = {
              Text(
                text = if (attachedFile != null) "Ask about ${attachedFile!!.name}..." else "Ask Genzii anything...",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
              )
            },
            colors = TextFieldDefaults.colors(
              focusedContainerColor = Color.Transparent,
              unfocusedContainerColor = Color.Transparent,
              focusedIndicatorColor = Color.Transparent,
              unfocusedIndicatorColor = Color.Transparent
            ),
            modifier = Modifier
              .weight(1f)
              .testTag("chat_input_field"),
            singleLine = false,
            maxLines = 4
          )

          if (isGenerating) {
            IconButton(
              onClick = { GenziiRepository.cancelActiveGeneration() },
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color(0xFFE53935))
                .testTag("chat_stop_button")
            ) {
              Box(
                modifier = Modifier
                  .size(12.dp)
                  .background(Color.White, RoundedCornerShape(2.dp))
              )
            }
          } else {
            IconButton(
              onClick = { handleSendPrompt(inputQuery) },
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (inputQuery.isNotBlank() || attachedFile != null) GenziiPurple else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                .testTag("chat_send_button")
            ) {
              Icon(
                imageVector = Icons.Default.ArrowUpward,
                contentDescription = "Send",
                tint = GenziiWhite,
                modifier = Modifier.size(20.dp)
              )
            }
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
    ) {
      // Suggestion prompt chips row
      LazyRow(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        items(promptChips) { chip ->
          Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 1.dp,
            shadowElevation = 1.dp,
            modifier = Modifier
              .clip(RoundedCornerShape(16.dp))
              .clickable {
                handleSendPrompt(chip)
              }
          ) {
            Text(
              text = chip,
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Medium,
              fontSize = 12.sp,
              color = GenziiPurple,
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
          }
        }
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

      // Chat Messages List
      LazyColumn(
        state = listState,
        modifier = Modifier
          .fillMaxSize()
          .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(vertical = 12.dp)
      ) {
        items(messages) { msg ->
          ChatBubbleItem(
            message = msg,
            onActionChipClick = { chipText ->
              handleSendPrompt(chipText)
            },
            onRetryClick = {
              GenziiRepository.retryLastMessage()
            }
          )
        }
      }
    }

    // Attachment Options Dialog
    if (showAttachDialog) {
      AlertDialog(
        onDismissRequest = { showAttachDialog = false },
        title = {
          Text(
            text = "Attach Context Document",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold
          )
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
              text = "Attach a file for deep contextual AI analysis. Files are parsed locally on your device.",
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Option 1: System Document Picker (SAF)
            OutlinedCard(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  showAttachDialog = false
                  filePickerLauncher.launch(
                    arrayOf(
                      "application/pdf",
                      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                      "text/plain",
                      "application/msword"
                    )
                  )
                },
              shape = RoundedCornerShape(12.dp)
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Outlined.Description, contentDescription = null, tint = GenziiPurple)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text("Device Document (PDF / DOCX / TXT)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                  Text("Zero-permission Android file selector", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }

            // Option 2: System Photo Picker
            OutlinedCard(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  showAttachDialog = false
                  photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                },
              shape = RoundedCornerShape(12.dp)
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Outlined.PhotoCamera, contentDescription = null, tint = GenziiPurple)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text("Document Photo / Image (Local OCR)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                  Text("Instant ML Kit optical character recognition", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }

            // Option 3: From Genzii Vault
            OutlinedCard(
              modifier = Modifier
                .fillMaxWidth()
                .clickable {
                  showAttachDialog = false
                  showVaultDocPicker = true
                },
              shape = RoundedCornerShape(12.dp)
            ) {
              Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Outlined.Lock, contentDescription = null, tint = GenziiPurple)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text("Select from Secure Vault", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                  Text("Choose from ${vaultDocuments.size} encrypted vault documents", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        },
        confirmButton = {
          TextButton(onClick = { showAttachDialog = false }) {
            Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      )
    }

    // Vault Document Selector Dialog
    if (showVaultDocPicker) {
      AlertDialog(
        onDismissRequest = { showVaultDocPicker = false },
        title = { Text("Select from Vault", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold) },
        text = {
          if (vaultDocuments.isEmpty()) {
            Text("No documents found in Vault yet. Upload a document first.")
          } else {
            LazyColumn(modifier = Modifier.heightIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
              items(vaultDocuments) { doc ->
                Card(
                  modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                      attachedFile = FileUtils.PickedFileInfo(
                        name = doc.name,
                        sizeBytes = 24000L,
                        sizeFormatted = doc.sizeFormatted,
                        fileType = doc.type,
                        content = doc.content,
                        wordCount = doc.content.split(Regex("\\s+")).size
                      )
                      showVaultDocPicker = false
                      Toast.makeText(context, "Attached from Vault: ${doc.name}", Toast.LENGTH_SHORT).show()
                    },
                  shape = RoundedCornerShape(10.dp),
                  colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                  Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                  ) {
                    Icon(Icons.Outlined.Description, contentDescription = null, tint = GenziiPurple, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                      Text(doc.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                      Text("${doc.folder} • ${doc.sizeFormatted}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                  }
                }
              }
            }
          }
        },
        confirmButton = {
          TextButton(onClick = { showVaultDocPicker = false }) { Text("Close") }
        }
      )
    }

    // Cloud AI Consent Dialog
    if (showConsentDialog) {
      AlertDialog(
        onDismissRequest = { showConsentDialog = false },
        icon = {
          Icon(Icons.Outlined.CloudUpload, contentDescription = null, tint = GenziiPurple, modifier = Modifier.size(32.dp))
        },
        title = {
          Text(
            text = "Cloud AI Processing Consent",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp
          )
        },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
              text = "Genzii processes files locally by default. To provide deep academic synthesis and originality reasoning, this query will be securely transmitted to Google Gemini over encrypted HTTPS.",
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
              text = "• Data is processed in compliance with strict privacy standards.\n• No unauthorized sharing or public indexing.\n• You can revoke cloud consent at any time in AI Settings.",
              fontSize = 12.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 17.sp
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              GenziiRepository.setCloudAiConsent(true)
              showConsentDialog = false
              if (inputQuery.isNotBlank()) {
                handleSendPrompt(inputQuery)
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
          ) {
            Text("Grant Consent & Continue")
          }
        },
        dismissButton = {
          TextButton(
            onClick = {
              showConsentDialog = false
              // Run in local mode
              if (inputQuery.isNotBlank()) {
                val q = inputQuery
                inputQuery = ""
                GenziiRepository.sendChatMessage(q)
              }
            }
          ) {
            Text("Use Local AI Only", color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      )
    }

    // Clear Chat Confirmation Dialog
    if (showClearChatDialog) {
      AlertDialog(
        onDismissRequest = { showClearChatDialog = false },
        title = { Text("Clear Chat History?", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold) },
        text = { Text("This will remove all current messages and reset the conversation session.") },
        confirmButton = {
          Button(
            onClick = {
              GenziiRepository.clearChatHistory()
              showClearChatDialog = false
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
          ) {
            Text("Clear All")
          }
        },
        dismissButton = {
          TextButton(onClick = { showClearChatDialog = false }) { Text("Cancel") }
        }
      )
    }

    // Rewarded Ad Dialog
    if (showRewardedAdDialog) {
      GoogleTestRewardedAdDialog(
        onDismiss = { showRewardedAdDialog = false },
        onRewardEarned = { scans, credits ->
          showRewardedAdDialog = false
          Toast.makeText(context, "Earned 10 AI credits!", Toast.LENGTH_SHORT).show()
        }
      )
    }
  }
}

@Composable
fun ChatBubbleItem(
  message: ChatMessage,
  onActionChipClick: (String) -> Unit,
  onRetryClick: () -> Unit
) {
  val clipboardManager = LocalClipboardManager.current
  val context = LocalContext.current

  if (message.isUser) {
    // User message bubble (right aligned, purple)
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.End
    ) {
      Column(horizontalAlignment = Alignment.End) {
        if (message.attachedFileName != null) {
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = GenziiPurpleSoft.copy(alpha = 0.5f),
            modifier = Modifier.padding(bottom = 4.dp)
          ) {
            Row(
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(Icons.Outlined.AttachFile, contentDescription = null, tint = GenziiPurple, modifier = Modifier.size(12.dp))
              Spacer(modifier = Modifier.width(4.dp))
              Text(
                text = message.attachedFileName,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = GenziiPurple
              )
            }
          }
        }

        Box(
          modifier = Modifier
            .widthIn(max = 290.dp)
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 4.dp, bottomStart = 18.dp, bottomEnd = 18.dp))
            .background(GenziiPurple)
            .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
          Text(
            text = message.text,
            color = GenziiWhite,
            fontFamily = PoppinsFontFamily,
            fontSize = 14.sp,
            lineHeight = 20.sp
          )
        }
      }
    }
  } else {
    // Assistant message card (left aligned, white surface card with border, streaming cursor, markdown)
    val transition = rememberInfiniteTransition()
    val cursorAlpha by transition.animateFloat(
      initialValue = 1f,
      targetValue = 0f,
      animationSpec = infiniteRepeatable(
        animation = tween(500),
        repeatMode = RepeatMode.Reverse
      )
    )

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.Start
    ) {
      Card(
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.widthIn(max = 330.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          // Assistant Header
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
          ) {
            Box(
              modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(GenziiPurpleContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = GenziiPurple,
                modifier = Modifier.size(14.dp)
              )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = "Genzii AI",
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.SemiBold,
              fontSize = 12.sp,
              color = GenziiPurple
            )

            if (message.isStreaming) {
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "● Streaming",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = GenziiPurple
              )
            }

            Spacer(modifier = Modifier.weight(1f))
            Text(
              text = message.timestamp,
              fontSize = 10.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }

          // Markdown Formatted Content
          if (message.text.isBlank() && message.isStreaming) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.padding(vertical = 8.dp)
            ) {
              CircularProgressIndicator(modifier = Modifier.size(16.dp), color = GenziiPurple, strokeWidth = 2.dp)
              Spacer(modifier = Modifier.width(8.dp))
              Text("Thinking...", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          } else {
            MarkdownContentView(text = message.text)

            // Blinking cursor during streaming
            if (message.isStreaming) {
              Text(
                text = "▌",
                color = GenziiPurple,
                fontSize = 14.sp,
                modifier = Modifier.alpha(cursorAlpha)
              )
            }
          }

          // Error banner if any
          if (message.isError) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFFFDE8E8)
            ) {
              Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                  text = "Failed to complete. Tap Retry to generate again.",
                  fontSize = 11.sp,
                  color = Color(0xFFC53030)
                )
              }
            }
          }

          // Bullet points legacy card if available
          if (message.bullets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
              shape = RoundedCornerShape(10.dp),
              colors = CardDefaults.cardColors(containerColor = GenziiPurpleSoft.copy(alpha = 0.4f))
            ) {
              Column(modifier = Modifier.padding(8.dp)) {
                message.bullets.forEach { bullet ->
                  Row(
                    modifier = Modifier.padding(vertical = 2.dp),
                    verticalAlignment = Alignment.Top
                  ) {
                    Text(
                      text = "• ",
                      color = GenziiPurple,
                      fontWeight = FontWeight.Bold,
                      fontSize = 13.sp
                    )
                    Text(
                      text = bullet,
                      fontFamily = PoppinsFontFamily,
                      fontSize = 12.sp,
                      lineHeight = 16.sp,
                      color = MaterialTheme.colorScheme.onBackground
                    )
                  }
                }
              }
            }
          }

          if (message.followUpPrompt != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
              text = message.followUpPrompt,
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Medium,
              fontSize = 12.sp,
              color = GenziiPurple
            )
          }

          // Suggestion chips
          if (message.suggestionChips.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.fillMaxWidth()
            ) {
              items(message.suggestionChips) { chip ->
                Button(
                  onClick = { onActionChipClick(chip) },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = GenziiPurpleContainer,
                    contentColor = GenziiPurple
                  ),
                  shape = RoundedCornerShape(12.dp),
                  contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                  modifier = Modifier.height(30.dp)
                ) {
                  Text(
                    text = chip,
                    fontFamily = PoppinsFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                  )
                }
              }
            }
          }

          // Action row: Copy, Retry, Save
          if (!message.isStreaming && message.text.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
              horizontalArrangement = Arrangement.End,
              verticalAlignment = Alignment.CenterVertically
            ) {
              IconButton(
                onClick = {
                  clipboardManager.setText(AnnotatedString(message.text))
                  Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(28.dp)
              ) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
              }

              IconButton(
                onClick = onRetryClick,
                modifier = Modifier.size(28.dp)
              ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Retry / Regenerate", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
              }

              IconButton(
                onClick = {
                  GenziiRepository.addDocument(
                    name = "AI_Notes_${System.currentTimeMillis() % 1000}.txt",
                    content = message.text,
                    folder = "Research Papers"
                  )
                  Toast.makeText(context, "Saved to Secure Vault!", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.size(28.dp)
              ) {
                Icon(Icons.Outlined.BookmarkBorder, contentDescription = "Save to Vault", tint = GenziiPurple, modifier = Modifier.size(15.dp))
              }
            }
          }
        }
      }
    }
  }
}

/**
 * Renders Markdown-formatted text with headings, bullet points, blockquotes, code snippets, and bold styling.
 */
@Composable
fun MarkdownContentView(text: String) {
  val lines = remember(text) { text.lines() }

  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    var inCodeBlock = false
    val codeBlockLines = mutableListOf<String>()

    for (line in lines) {
      val trimmed = line.trim()

      if (trimmed.startsWith("```")) {
        if (inCodeBlock) {
          // Render code block
          val codeText = codeBlockLines.joinToString("\n")
          codeBlockLines.clear()
          inCodeBlock = false
          Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF1E1A33),
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 4.dp)
          ) {
            Text(
              text = codeText,
              color = Color(0xFFE2E8F0),
              fontFamily = FontFamily.Monospace,
              fontSize = 11.sp,
              modifier = Modifier.padding(10.dp)
            )
          }
        } else {
          inCodeBlock = true
        }
        continue
      }

      if (inCodeBlock) {
        codeBlockLines.add(line)
        continue
      }

      when {
        trimmed.startsWith("# ") -> {
          Text(
            text = trimmed.removePrefix("# ").trim(),
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 4.dp)
          )
        }
        trimmed.startsWith("## ") || trimmed.startsWith("### ") -> {
          val heading = trimmed.replace(Regex("^#+\\s*"), "")
          Text(
            text = heading,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = GenziiPurple,
            modifier = Modifier.padding(top = 4.dp)
          )
        }
        trimmed.startsWith("• ") || trimmed.startsWith("* ") || trimmed.startsWith("- ") -> {
          val content = trimmed.removePrefix("• ").removePrefix("* ").removePrefix("- ").trim()
          Row(
            modifier = Modifier.padding(vertical = 1.dp),
            verticalAlignment = Alignment.Top
          ) {
            Text(
              text = "• ",
              color = GenziiPurple,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp
            )
            Text(
              text = parseInlineMarkdown(content),
              fontFamily = PoppinsFontFamily,
              fontSize = 13.sp,
              lineHeight = 18.sp,
              color = MaterialTheme.colorScheme.onBackground
            )
          }
        }
        trimmed.startsWith("> ") -> {
          Surface(
            shape = RoundedCornerShape(4.dp),
            color = GenziiPurpleSoft.copy(alpha = 0.35f),
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 2.dp)
          ) {
            Box(
              modifier = Modifier
                .border(2.dp, GenziiPurple, RoundedCornerShape(4.dp))
                .padding(8.dp)
            ) {
              Text(
                text = parseInlineMarkdown(trimmed.removePrefix("> ").trim()),
                fontFamily = PoppinsFontFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onBackground
              )
            }
          }
        }
        trimmed.isNotBlank() -> {
          Text(
            text = parseInlineMarkdown(line),
            fontFamily = PoppinsFontFamily,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            color = MaterialTheme.colorScheme.onBackground
          )
        }
      }
    }
  }
}

/**
 * Parses bold (**text**), italic (*text*), and inline code (`code`) into AnnotatedString.
 */
fun parseInlineMarkdown(text: String): AnnotatedString {
  return buildAnnotatedString {
    var cursor = 0
    val boldRegex = Regex("\\*\\*(.*?)\\*\\*|`(.*?)`|\\*(.*?)\\*")
    val matches = boldRegex.findAll(text)

    for (match in matches) {
      if (match.range.first > cursor) {
        append(text.substring(cursor, match.range.first))
      }
      val boldText = match.groups[1]?.value
      val codeText = match.groups[2]?.value
      val italicText = match.groups[3]?.value

      if (boldText != null) {
        withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = GenziiPurpleDark)) {
          append(boldText)
        }
      } else if (codeText != null) {
        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = Color(0xFFF1F5F9), color = GenziiPurple)) {
          append(" $codeText ")
        }
      } else if (italicText != null) {
        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
          append(italicText)
        }
      }
      cursor = match.range.last + 1
    }

    if (cursor < text.length) {
      append(text.substring(cursor))
    }
  }
}
