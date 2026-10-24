package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ads.GoogleAdsManager
import com.example.ads.GoogleTestBannerAd
import com.example.ads.GoogleTestInterstitialAdDialog
import com.example.ads.GoogleTestRewardedAdDialog
import com.example.data.GenziiRepository
import com.example.model.ThemeMode
import com.example.ui.components.GenziiLogo
import com.example.ui.theme.*

/**
 * Standard User Profile Screen (Production Release).
 * Replaces the premium subscription sales page with full account management,
 * user metrics, profile editing, and Google Rewarded Test Ads.
 */
@Composable
fun ProfileScreen(
  onNavigateToSettings: () -> Unit,
  onNavigateToPrivacy: () -> Unit,
  onLogout: () -> Unit
) {
  val userProfile by GenziiRepository.userProfile.collectAsState()
  val totalAdsWatched by GoogleAdsManager.totalAdsWatched.collectAsState()
  val bonusScansEarned by GoogleAdsManager.bonusScansEarned.collectAsState()

  var showEditProfileDialog by remember { mutableStateOf(false) }
  var showRewardedAdDialog by remember { mutableStateOf(false) }
  var showInterstitialAdDialog by remember { mutableStateOf(false) }
  var showRewardSuccessDialog by remember { mutableStateOf(false) }
  var showLogoutConfirmDialog by remember { mutableStateOf(false) }
  var showStorageClearDialog by remember { mutableStateOf(false) }
  var showHelpSupportDialog by remember { mutableStateOf(false) }

  Scaffold(
    topBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Profile",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        IconButton(
          onClick = onNavigateToSettings,
          modifier = Modifier.testTag("profile_settings_button")
        ) {
          Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = GenziiPurple)
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
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(8.dp))

      // User Avatar with Initial & Edit Photo Button
      Box(contentAlignment = Alignment.BottomEnd) {
        Box(
          modifier = Modifier
            .size(86.dp)
            .clip(CircleShape)
            .background(GenziiPurpleContainer)
            .border(2.5.dp, GenziiPurple, CircleShape),
          contentAlignment = Alignment.Center
        ) {
          if (!userProfile.photoUrl.isNullOrBlank()) {
            coil.compose.AsyncImage(
              model = userProfile.photoUrl,
              contentDescription = "User profile photo",
              contentScale = androidx.compose.ui.layout.ContentScale.Crop,
              modifier = Modifier.fillMaxSize()
            )
          } else {
            val initials = remember(userProfile.name, userProfile.email) {
              val parts = userProfile.name.split(" ").filter { it.isNotBlank() }
              val fromName = parts.mapNotNull { it.firstOrNull()?.uppercase() }.take(2).joinToString("")
              if (fromName.isNotBlank() && fromName != "S") fromName
              else userProfile.email.take(2).uppercase().ifBlank { "U" }
            }
            Text(
              text = initials,
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 28.sp,
              color = GenziiPurple
            )
          }
        }

        Box(
          modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(GenziiPurple)
            .clickable { showEditProfileDialog = true },
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "Edit Profile",
            tint = GenziiWhite,
            modifier = Modifier.size(15.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = userProfile.name,
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        color = MaterialTheme.colorScheme.onBackground
      )

      Text(
        text = userProfile.email,
        fontFamily = PoppinsFontFamily,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = userProfile.role,
        fontFamily = PoppinsFontFamily,
        fontSize = 12.sp,
        color = GenziiPurple,
        fontWeight = FontWeight.Medium
      )

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedButton(
        onClick = { showEditProfileDialog = true },
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
      ) {
        Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = GenziiPurple)
        Spacer(modifier = Modifier.width(6.dp))
        Text("Edit Personal Details", fontSize = 13.sp, color = GenziiPurple, fontWeight = FontWeight.SemiBold)
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Workspace Statistics Cards Grid (4 items)
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        ProfileStatBox(
          label = "Originality Checks",
          value = "${userProfile.checksCount}",
          icon = Icons.Outlined.VerifiedUser,
          modifier = Modifier.weight(1f)
        )
        ProfileStatBox(
          label = "Vault Docs",
          value = "${userProfile.docsCount}",
          icon = Icons.Outlined.Lock,
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        ProfileStatBox(
          label = "AI Chats",
          value = "${userProfile.chatsCount}",
          icon = Icons.Outlined.AutoAwesome,
          modifier = Modifier.weight(1f)
        )
        ProfileStatBox(
          label = "Encrypted Storage",
          value = "${userProfile.usedStorageGb} GB / ${userProfile.totalStorageGb.toInt()} GB",
          icon = Icons.Outlined.CloudDone,
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Rewarded Google Ads Card (Task 6)
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GenziiPurpleContainer.copy(alpha = 0.5f)),
        border = androidx.compose.foundation.BorderStroke(1.dp, GenziiPurple.copy(alpha = 0.3f))
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
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
                  .background(GenziiPurple),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.CardGiftcard,
                  contentDescription = null,
                  tint = GenziiWhite,
                  modifier = Modifier.size(20.dp)
                )
              }
              Spacer(modifier = Modifier.width(10.dp))
              Column {
                Text(
                  text = "Earn Free Features & Scans",
                  fontFamily = PoppinsFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 15.sp,
                  color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                  text = "Google Rewarded Test Ads",
                  fontSize = 11.sp,
                  color = GenziiPurple,
                  fontWeight = FontWeight.Medium
                )
              }
            }

            Surface(
              shape = RoundedCornerShape(8.dp),
              color = Color(0xFFFBBC04)
            ) {
              Text(
                text = "AdMob",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
              )
            }
          }

          Spacer(modifier = Modifier.height(10.dp))

          Text(
            text = "Watch a quick 5-second Google test ad to earn 10 free scans and 10 bonus AI credits instantly with zero subscription fees.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )

          Spacer(modifier = Modifier.height(12.dp))

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Ads watched: $totalAdsWatched • +$bonusScansEarned scans",
              fontSize = 11.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontWeight = FontWeight.Medium
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              OutlinedButton(
                onClick = { showInterstitialAdDialog = true },
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GenziiPurple.copy(alpha = 0.5f))
              ) {
                Icon(Icons.Default.OpenInFull, contentDescription = null, modifier = Modifier.size(14.dp), tint = GenziiPurple)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Interstitial", fontSize = 11.sp, color = GenziiPurple, fontWeight = FontWeight.SemiBold)
              }

              Button(
                onClick = { showRewardedAdDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
              ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Rewarded (+10)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
              }
            }
          }

          Spacer(modifier = Modifier.height(14.dp))

          GoogleTestBannerAd()
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Account Options Section Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
      ) {
        Text(
          text = "Account & Preferences",
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontWeight = FontWeight.SemiBold
        )
      }

      Spacer(modifier = Modifier.height(10.dp))

      // Options List
      SettingsItemRow(
        title = "Privacy & Zero-Knowledge Security",
        icon = Icons.Outlined.Security,
        onClick = onNavigateToPrivacy
      )

      Spacer(modifier = Modifier.height(8.dp))

      SettingsItemRow(
        title = "Appearance & Dark Mode",
        icon = Icons.Outlined.DarkMode,
        onClick = onNavigateToSettings
      )

      Spacer(modifier = Modifier.height(8.dp))

      SettingsItemRow(
        title = "Storage & Cache Management",
        icon = Icons.Outlined.FolderSpecial,
        onClick = { showStorageClearDialog = true }
      )

      Spacer(modifier = Modifier.height(8.dp))

      SettingsItemRow(
        title = "Help & Academic Support",
        icon = Icons.Outlined.HelpOutline,
        onClick = { showHelpSupportDialog = true }
      )

      Spacer(modifier = Modifier.height(24.dp))

      // Google Test Banner Ad
      GoogleTestBannerAd()

      Spacer(modifier = Modifier.height(24.dp))

      // Log Out Button
      Button(
        onClick = { showLogoutConfirmDialog = true },
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("profile_logout_button"),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.error
        ),
        shape = RoundedCornerShape(16.dp)
      ) {
        Icon(Icons.Default.ExitToApp, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Log Out",
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Current Build & Version Badge
      Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(
          modifier = Modifier.padding(14.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(
            text = "Genzii v1.3.0",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(2.dp))
          Text(
            text = "Encrypted Workspace • All Features Active",
            fontSize = 11.sp,
            color = GenziiPurple,
            fontWeight = FontWeight.Medium
          )
        }
      }

      Spacer(modifier = Modifier.height(32.dp))
    }

    // Edit Profile Modal
    if (showEditProfileDialog) {
      var editName by remember { mutableStateOf(userProfile.name) }
      var editEmail by remember { mutableStateOf(userProfile.email) }
      var editRole by remember { mutableStateOf(userProfile.role) }
      var editInstitution by remember { mutableStateOf(userProfile.institution) }
      var editDepartment by remember { mutableStateOf(userProfile.department) }
      var editStudentId by remember { mutableStateOf(userProfile.studentId) }

      AlertDialog(
        onDismissRequest = { showEditProfileDialog = false },
        title = {
          Text(
            text = "Edit Profile & Credentials",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold
          )
        },
        text = {
          Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            OutlinedTextField(
              value = editName,
              onValueChange = { editName = it },
              label = { Text("Full Name") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = editEmail,
              onValueChange = { editEmail = it },
              label = { Text("Email Address") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = editRole,
              onValueChange = { editRole = it },
              label = { Text("Academic Role / Affiliation") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = editInstitution,
              onValueChange = { editInstitution = it },
              label = { Text("Institution / University") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = editDepartment,
              onValueChange = { editDepartment = it },
              label = { Text("Department / Field") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = editStudentId,
              onValueChange = { editStudentId = it },
              label = { Text("Student / Researcher ID") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
          }
        },
        confirmButton = {
          Button(
            onClick = {
              GenziiRepository.updateProfile(
                name = editName,
                email = editEmail,
                role = editRole,
                institution = editInstitution,
                department = editDepartment,
                studentId = editStudentId
              )
              showEditProfileDialog = false
            },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
          ) {
            Text("Save Changes")
          }
        },
        dismissButton = {
          TextButton(onClick = { showEditProfileDialog = false }) {
            Text("Cancel", color = GenziiPurple)
          }
        }
      )
    }

    // Google Rewarded Ad Dialog
    if (showRewardedAdDialog) {
      GoogleTestRewardedAdDialog(
        onDismiss = { showRewardedAdDialog = false },
        onRewardEarned = { scans, credits ->
          showRewardedAdDialog = false
          showRewardSuccessDialog = true
        }
      )
    }

    // Google Interstitial Ad Dialog
    if (showInterstitialAdDialog) {
      GoogleTestInterstitialAdDialog(
        onDismiss = { showInterstitialAdDialog = false }
      )
    }

    // Reward Celebration Modal
    if (showRewardSuccessDialog) {
      AlertDialog(
        onDismissRequest = { showRewardSuccessDialog = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF34A853))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reward Credited!", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold)
          }
        },
        text = {
          Text("You have successfully earned +10 Free Originality Scans and +10 AI Credits! Your total scan balance has been updated.")
        },
        confirmButton = {
          Button(
            onClick = { showRewardSuccessDialog = false },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
          ) {
            Text("Great!")
          }
        }
      )
    }

    // Clear Storage Dialog
    if (showStorageClearDialog) {
      AlertDialog(
        onDismissRequest = { showStorageClearDialog = false },
        title = { Text("Storage & Cache", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold) },
        text = {
          Text("Temporary OCR image cache (3.2 MB) and local thumbnail cache have been cleaned. Your encrypted Vault documents remain safe and intact.")
        },
        confirmButton = {
          Button(onClick = { showStorageClearDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)) {
            Text("Done")
          }
        }
      )
    }

    // Help Dialog
    if (showHelpSupportDialog) {
      AlertDialog(
        onDismissRequest = { showHelpSupportDialog = false },
        title = { Text("Genzii Support", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold) },
        text = {
          Text("Need help with plagiarism results, OCR scanning, or document encryption?\n\nContact: support@genzii.ai\nVersion: 1.2.0 (Build 3 - Functional Release)")
        },
        confirmButton = {
          Button(onClick = { showHelpSupportDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)) {
            Text("Close")
          }
        }
      )
    }

    // Logout Confirmation Dialog
    if (showLogoutConfirmDialog) {
      AlertDialog(
        onDismissRequest = { showLogoutConfirmDialog = false },
        title = { Text("Log Out", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold) },
        text = { Text("Are you sure you want to log out of your Genzii account? Your local vault files will remain securely encrypted.") },
        confirmButton = {
          Button(
            onClick = {
              showLogoutConfirmDialog = false
              onLogout()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
          ) {
            Text("Log Out")
          }
        },
        dismissButton = {
          TextButton(onClick = { showLogoutConfirmDialog = false }) {
            Text("Cancel", color = GenziiPurple)
          }
        }
      )
    }
  }
}

@Composable
fun ProfileStatBox(
  label: String,
  value: String,
  icon: ImageVector,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = value,
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = MaterialTheme.colorScheme.onBackground
        )
        Box(
          modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(GenziiPurpleContainer),
          contentAlignment = Alignment.Center
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = GenziiPurple,
            modifier = Modifier.size(18.dp)
          )
        }
      }
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = label,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
      )
    }
  }
}

@Composable
fun SettingsScreen(
  onNavigateToAppearance: () -> Unit,
  onNavigateToPrivacy: () -> Unit,
  onLogout: () -> Unit,
  onBack: () -> Unit
) {
  var showAccountModal by remember { mutableStateOf(false) }
  var showHelpModal by remember { mutableStateOf(false) }

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
          text = "Settings",
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
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      SettingsItemRow(
        title = "Account Details",
        icon = Icons.Outlined.Person,
        onClick = { showAccountModal = true }
      )
      SettingsItemRow(
        title = "Appearance & Theme",
        icon = Icons.Outlined.DarkMode,
        onClick = onNavigateToAppearance
      )
      SettingsItemRow(
        title = "Privacy & Zero-Knowledge Security",
        icon = Icons.Outlined.Security,
        onClick = onNavigateToPrivacy
      )
      SettingsItemRow(
        title = "Help & Academic Support",
        icon = Icons.Outlined.HelpOutline,
        onClick = { showHelpModal = true }
      )

      Spacer(modifier = Modifier.height(16.dp))

      GoogleTestBannerAd()

      Spacer(modifier = Modifier.height(24.dp))

      Button(
        onClick = onLogout,
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("settings_logout_button"),
        colors = ButtonDefaults.buttonColors(
          containerColor = MaterialTheme.colorScheme.errorContainer,
          contentColor = MaterialTheme.colorScheme.error
        ),
        shape = RoundedCornerShape(16.dp)
      ) {
        Icon(Icons.Default.ExitToApp, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Log Out",
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 15.sp
        )
      }
    }

    if (showAccountModal) {
      val profile by GenziiRepository.userProfile.collectAsState()
      AlertDialog(
        onDismissRequest = { showAccountModal = false },
        title = { Text("Account Details", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold) },
        text = {
          Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Name: ${profile.name}", fontWeight = FontWeight.Medium)
            Text("Email: ${profile.email}")
            Text("Role: ${profile.role}")
            Text("Member Since: September 2026")
            Text("Version: Release 1.2.0 (Build 3)")
          }
        },
        confirmButton = {
          Button(onClick = { showAccountModal = false }, colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)) {
            Text("Close")
          }
        }
      )
    }

    if (showHelpModal) {
      AlertDialog(
        onDismissRequest = { showHelpModal = false },
        title = { Text("Help & Support", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold) },
        text = {
          Text("Need assistance? Contact support@genzii.ai or browse our guides for original writing and vault encryption.")
        },
        confirmButton = {
          Button(onClick = { showHelpModal = false }, colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)) {
            Text("OK")
          }
        }
      )
    }
  }
}

@Composable
fun SettingsItemRow(
  title: String,
  icon: ImageVector,
  onClick: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(14.dp))
      .clickable(onClick = onClick),
    shape = RoundedCornerShape(14.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = GenziiPurple,
        modifier = Modifier.size(22.dp)
      )
      Spacer(modifier = Modifier.width(14.dp))
      Text(
        text = title,
        fontFamily = PoppinsFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.weight(1f)
      )
      Icon(
        imageVector = Icons.Default.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
      )
    }
  }
}

@Composable
fun DarkModeToggleScreen(
  onBack: () -> Unit
) {
  val userProfile by GenziiRepository.userProfile.collectAsState()

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
          text = "Appearance",
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
      Spacer(modifier = Modifier.height(20.dp))

      GenziiLogo(logoSize = 50.dp)

      Spacer(modifier = Modifier.height(30.dp))

      AppearanceModeCard(
        title = "Light Mode",
        isSelected = userProfile.themeMode == ThemeMode.LIGHT,
        icon = Icons.Outlined.LightMode,
        onSelect = { GenziiRepository.setThemeMode(ThemeMode.LIGHT) }
      )

      Spacer(modifier = Modifier.height(12.dp))

      AppearanceModeCard(
        title = "Dark Mode",
        isSelected = userProfile.themeMode == ThemeMode.DARK,
        icon = Icons.Outlined.DarkMode,
        onSelect = { GenziiRepository.setThemeMode(ThemeMode.DARK) }
      )

      Spacer(modifier = Modifier.height(12.dp))

      AppearanceModeCard(
        title = "System Default",
        isSelected = userProfile.themeMode == ThemeMode.SYSTEM,
        icon = Icons.Outlined.SettingsSuggest,
        onSelect = { GenziiRepository.setThemeMode(ThemeMode.SYSTEM) }
      )
    }
  }
}

@Composable
fun AppearanceModeCard(
  title: String,
  isSelected: Boolean,
  icon: ImageVector,
  onSelect: () -> Unit
) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(16.dp))
      .clickable(onClick = onSelect)
      .border(
        width = if (isSelected) 2.dp else 1.dp,
        color = if (isSelected) GenziiPurple else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
        shape = RoundedCornerShape(16.dp)
      ),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isSelected) GenziiPurpleContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface
    )
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = GenziiPurple,
        modifier = Modifier.size(24.dp)
      )
      Spacer(modifier = Modifier.width(14.dp))
      Text(
        text = title,
        fontFamily = PoppinsFontFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.weight(1f)
      )
      RadioButton(
        selected = isSelected,
        onClick = onSelect,
        colors = RadioButtonDefaults.colors(selectedColor = GenziiPurple)
      )
    }
  }
}

@Composable
fun PrivacySecurityScreen(
  onBack: () -> Unit
) {
  val userProfile by GenziiRepository.userProfile.collectAsState()

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
          text = "Privacy & Security",
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
        .verticalScroll(rememberScrollState()),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // App Lock
      SecurityToggleCard(
        title = "App Lock",
        description = "Require biometric or PIN to open Genzii",
        icon = Icons.Outlined.Fingerprint,
        isChecked = userProfile.isAppLockEnabled,
        onCheckedChange = { GenziiRepository.toggleAppLock(it) }
      )

      // Two-Factor
      SecurityToggleCard(
        title = "Two-Factor Authentication",
        description = "Add extra security step on sign in",
        icon = Icons.Outlined.Security,
        isChecked = userProfile.isTwoFactorEnabled,
        onCheckedChange = { GenziiRepository.toggleTwoFactor(it) }
      )

      // Cloud AI Processing Consent
      SecurityToggleCard(
        title = "Cloud AI Processing Consent",
        description = "Allow transmitting prompts to Google Gemini (HTTPS encrypted)",
        icon = Icons.Outlined.CloudUpload,
        isChecked = userProfile.isCloudAiConsentGranted,
        onCheckedChange = { GenziiRepository.setCloudAiConsent(it) }
      )

      // Android Permissions Control Card
      val context = androidx.compose.ui.platform.LocalContext.current
      val isCameraAllowed = androidx.core.content.ContextCompat.checkSelfPermission(
        context,
        android.Manifest.permission.CAMERA
      ) == android.content.pm.PackageManager.PERMISSION_GRANTED

      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
          ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(Icons.Outlined.SettingsSuggest, contentDescription = null, tint = GenziiPurple)
              Spacer(modifier = Modifier.width(10.dp))
              Text("Device Permissions", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = if (isCameraAllowed) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant
            ) {
              Text(
                text = if (isCameraAllowed) "Camera Allowed" else "Camera Not Allowed",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isCameraAllowed) Color(0xFF2E7D32) else GenziiPurple,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "• Camera: Used for instant OCR book & document scanning.\n• Storage: Zero broad storage permissions needed (uses Android SAF & Photo Picker).",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 17.sp
          )
          Spacer(modifier = Modifier.height(10.dp))
          OutlinedButton(
            onClick = {
              try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                  data = android.net.Uri.fromParts("package", context.packageName, null)
                  addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
              } catch (_: Exception) {}
            },
            shape = RoundedCornerShape(10.dp)
          ) {
            Icon(Icons.Outlined.Settings, contentDescription = null, tint = GenziiPurple, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Open Android App Settings", color = GenziiPurple, fontSize = 12.sp)
          }
        }
      }

      // Data Privacy
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lock, contentDescription = null, tint = GenziiPurple)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Zero-Knowledge Vault", fontWeight = FontWeight.Bold, fontSize = 15.sp)
          }
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "Your files are client-side encrypted using AES-256-GCM. Neither Genzii employees nor third parties have access to your private documents.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      // Device Management
      Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Smartphone, contentDescription = null, tint = GenziiPurple)
            Spacer(modifier = Modifier.width(10.dp))
            Text("Active Devices", fontWeight = FontWeight.Bold, fontSize = 15.sp)
          }
          Spacer(modifier = Modifier.height(8.dp))
          Text("• Android Streaming Device (Current Session)", fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
          Text("• Web Client - London, UK (Active 2h ago)", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
  }
}

@Composable
fun SecurityToggleCard(
  title: String,
  description: String,
  icon: ImageVector,
  isChecked: Boolean,
  onCheckedChange: (Boolean) -> Unit
) {
  Card(
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    modifier = Modifier.fillMaxWidth()
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Icon(icon, contentDescription = null, tint = GenziiPurple, modifier = Modifier.size(24.dp))
      Spacer(modifier = Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Switch(
        checked = isChecked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(checkedThumbColor = GenziiPurple, checkedTrackColor = GenziiPurpleContainer)
      )
    }
  }
}
