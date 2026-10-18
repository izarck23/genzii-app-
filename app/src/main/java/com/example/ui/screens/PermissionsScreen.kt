package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.GenziiRepository
import com.example.ui.components.GenziiLogo
import com.example.ui.theme.*

@Composable
fun PermissionsScreen(
  onContinue: () -> Unit
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var isCameraGranted by remember {
    mutableStateOf(
      ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    )
  }
  var hasRequestedCamera by remember { mutableStateOf(false) }
  var wasCameraDenied by remember { mutableStateOf(false) }

  // Automatically recheck permissions when user returns from Settings or background
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        isCameraGranted = granted
        if (granted) {
          wasCameraDenied = false
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    hasRequestedCamera = true
    isCameraGranted = granted
    wasCameraDenied = !granted
  }

  fun openAppSettings() {
    try {
      val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", context.packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      context.startActivity(intent)
    } catch (_: Exception) {}
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background,
    bottomBar = {
      Surface(
        modifier = Modifier
          .fillMaxWidth()
          .navigationBarsPadding(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Button(
            onClick = {
              GenziiRepository.setPermissionsOnboardingCompleted(true)
              onContinue()
            },
            modifier = Modifier
              .fillMaxWidth()
              .height(52.dp)
              .testTag("permissions_continue_button"),
            colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple),
            shape = RoundedCornerShape(16.dp)
          ) {
            Text(
              text = if (isCameraGranted) "Continue to Genzii" else "Continue to Workspace",
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.SemiBold,
              fontSize = 15.sp,
              color = GenziiWhite
            )
          }

          Spacer(modifier = Modifier.height(6.dp))

          Text(
            text = "Permissions are optional and can be modified in Android Settings anytime.",
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
          )
        }
      }
    }
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .statusBarsPadding()
        .padding(horizontal = 20.dp, vertical = 12.dp)
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(10.dp))
      GenziiLogo(logoSize = 36.dp)
      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "Permissions & Privacy Setup",
        style = MaterialTheme.typography.displayMedium,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onBackground
      )

      Spacer(modifier = Modifier.height(6.dp))

      Text(
        text = "Genzii follows Android's official security and privacy model. Understand how your device permissions are used before entering.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        lineHeight = 20.sp
      )

      Spacer(modifier = Modifier.height(24.dp))

      // Card 1: Camera Permission
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(CircleShape)
                  .background(if (isCameraGranted) Color(0xFFE8F5E9) else GenziiPurpleContainer),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = if (isCameraGranted) Icons.Default.Check else Icons.Outlined.PhotoCamera,
                  contentDescription = null,
                  tint = if (isCameraGranted) Color(0xFF2E7D32) else GenziiPurple,
                  modifier = Modifier.size(22.dp)
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "Camera Access",
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                  text = if (isCameraGranted) "Granted & Active" else "Document & Book OCR Scanning",
                  fontSize = 12.sp,
                  color = if (isCameraGranted) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (isCameraGranted) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
            ) {
              Text(
                text = if (isCameraGranted) "✓ Granted" else "Required for OCR",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isCameraGranted) Color(0xFF2E7D32) else GenziiPurple,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          Text(
            text = "Used exclusively when you open the OCR Scanner to capture printed research papers, library books, and handwritten notes for instant text extraction.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 17.sp
          )

          Spacer(modifier = Modifier.height(12.dp))

          if (isCameraGranted) {
            Surface(
              shape = RoundedCornerShape(10.dp),
              color = Color(0xFFF1F8E9),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Icon(Icons.Default.Verified, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                  text = "Camera is configured for on-device OCR scanning.",
                  fontSize = 12.sp,
                  color = Color(0xFF2E7D32),
                  fontWeight = FontWeight.Medium
                )
              }
            }
          } else {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Button(
                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple),
                shape = RoundedCornerShape(12.dp)
              ) {
                Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Allow Camera", fontSize = 13.sp)
              }

              if (wasCameraDenied) {
                OutlinedButton(
                  onClick = { openAppSettings() },
                  modifier = Modifier.weight(1f),
                  shape = RoundedCornerShape(12.dp)
                ) {
                  Icon(Icons.Outlined.Settings, contentDescription = null, tint = GenziiPurple, modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Open Settings", fontSize = 13.sp, color = GenziiPurple)
                }
              }
            }

            if (wasCameraDenied) {
              Spacer(modifier = Modifier.height(8.dp))
              Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFFFF3E0),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier.padding(10.dp),
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFFE65100), modifier = Modifier.size(16.dp))
                  Spacer(modifier = Modifier.width(8.dp))
                  Text(
                    text = "Permission denied. Tap 'Open Settings' to grant camera access manually, or continue without camera OCR.",
                    fontSize = 11.sp,
                    color = Color(0xFFE65100),
                    lineHeight = 15.sp
                  )
                }
              }
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Card 2: Zero-Permission Storage Model (SAF & Photo Picker)
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
              Box(
                modifier = Modifier
                  .size(40.dp)
                  .clip(CircleShape)
                  .background(Color(0xFFE8F5E9)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Outlined.FolderShared,
                  contentDescription = null,
                  tint = Color(0xFF2E7D32),
                  modifier = Modifier.size(22.dp)
                )
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column {
                Text(
                  text = "File & Document Access",
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                  text = "Android System SAF & Photo Picker",
                  fontSize = 12.sp,
                  color = Color(0xFF2E7D32)
                )
              }
            }

            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFFE8F5E9)
            ) {
              Text(
                text = "✓ Zero-Permission",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2E7D32),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          Text(
            text = "Genzii complies strictly with Google Play policies: it never requests broad storage access (READ_EXTERNAL_STORAGE). Instead, you selectively choose PDF, DOCX, TXT, and image files via Android's secure system picker.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 17.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Card 3: Local Processing Guarantee
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(GenziiPurpleContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Outlined.Security,
                contentDescription = null,
                tint = GenziiPurple,
                modifier = Modifier.size(22.dp)
              )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
              Text(
                text = "Local-First File Processing",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
              )
              Text(
                text = "No automatic cloud uploads",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          Text(
            text = "Authorized files are parsed, extracted, and processed 100% locally on your device. OCR text recognition runs locally via Google ML Kit. No file is uploaded without your explicit review and consent.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 17.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))
    }
  }
}
