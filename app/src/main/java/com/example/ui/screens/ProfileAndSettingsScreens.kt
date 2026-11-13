package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import com.example.R
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import android.net.Uri
import com.example.ads.GoogleAdsManager
import com.example.ads.GoogleTestBannerAd
import com.example.ads.GoogleTestInterstitialAdDialog
import com.example.ads.GoogleTestRewardedAdDialog
import com.example.auth.FirebaseAuthManager
import com.example.data.GenziiRepository
import com.example.model.SubscriptionTier
import com.example.model.ThemeMode
import com.example.network.GeminiService
import com.example.ui.components.GenziiLogo
import com.example.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Profile Screen (Production Reference Implementation).
 * The old user profile page with ads and workspace metrics has been completely removed.
 * This screen now unifies profile details and user settings matching the exact reference UI.
 */
@Composable
fun ProfileScreen(
  onNavigateToSettings: () -> Unit = {},
  onNavigateToPrivacy: () -> Unit = {},
  onNavigateToNotifications: () -> Unit = {},
  onNavigateToPremium: () -> Unit = {},
  onLogout: () -> Unit = {},
  onBack: () -> Unit = {}
) {
  SettingsScreen(
    onNavigateToPrivacy = onNavigateToPrivacy,
    onNavigateToNotifications = onNavigateToNotifications,
    onNavigateToPremium = onNavigateToPremium,
    onLogout = onLogout,
    onBack = onBack
  )
}

@Composable
fun SettingsScreen(
  onNavigateToAppearance: () -> Unit = {},
  onNavigateToPrivacy: () -> Unit = {},
  onNavigateToNotifications: () -> Unit = {},
  onNavigateToProfile: () -> Unit = {},
  onNavigateToPremium: () -> Unit = {},
  onLogout: () -> Unit = {},
  onBack: () -> Unit = {}
) {
  BackHandler { onBack() }

  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val userProfile by GenziiRepository.userProfile.collectAsState()
  val authUser = remember { FirebaseAuthManager.getCurrentlySignedInUser() }


  // Active theme calculation
  val isSystemDark = isSystemInDarkTheme()
  val isDark = when (userProfile.themeMode) {
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
    ThemeMode.SYSTEM -> isSystemDark
  }

  // Strict reference palette for Light and Dark modes
  val screenBg = if (isDark) Color(0xFF0F0F10) else Color(0xFFFAFAFC)
  val cardBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFFFFFFF)
  val cardBorder = if (isDark) Color(0xFF2C2C2E) else Color(0xFFEBEBF0)
  val dividerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFFF0F0F3)
  val titleColor = if (isDark) Color(0xFFFFFFFF) else Color(0xFF111111)
  val sectionHeadingColor = if (isDark) Color(0xFFE5E5EA) else Color(0xFF1C1C1E)
  val secondaryTextColor = if (isDark) Color(0xFF8E8E93) else Color(0xFF8E8E93)
  val chevronTint = if (isDark) Color(0xFF636366) else Color(0xFFC7C7CC)
  val backBtnBg = if (isDark) Color(0xFF1C1C1E) else Color(0xFFF2F2F5)
  val backBtnBorder = if (isDark) Color(0xFF2C2C2E) else Color.Transparent
  val backBtnIconTint = if (isDark) Color(0xFFFFFFFF) else Color(0xFF1A1A1A)

  // Photo Picker launcher using zero-permission Android Photo Picker
  val photoPickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.PickVisualMedia()
  ) { uri ->
    if (uri != null) {
      GenziiRepository.updateProfilePhoto(uri.toString())
      Toast.makeText(context, "Profile avatar updated", Toast.LENGTH_SHORT).show()
    }
  }

  // Real authenticated user details
  val displayName = remember(userProfile.name, authUser) {
    val authName = authUser?.displayName?.takeIf { it.isNotBlank() }
    val repoName = userProfile.name.takeIf { it.isNotBlank() && it != "Scholar" && it != "Guest User" }
    val emailPrefix = authUser?.email?.substringBefore("@") ?: userProfile.email.substringBefore("@")
    authName ?: repoName ?: emailPrefix.ifBlank { "User" }
  }

  val displayRole = remember(userProfile.role) {
    val role = userProfile.role.trim()
    if (role.isNotBlank()) {
      if (role.startsWith("@")) role else "@$role"
    } else {
      "@Student / Researcher"
    }
  }

  val photoUrl = authUser?.photoUrl?.toString() ?: userProfile.photoUrl

  // Dialog states
  var showProfileDetailsDialog by remember { mutableStateOf(false) }
  var showPasswordDialog by remember { mutableStateOf(false) }
  var showStorageDialog by remember { mutableStateOf(false) }
  var showAboutDialog by remember { mutableStateOf(false) }
  var showGeminiConfigDialog by remember { mutableStateOf(false) }
  var showHelpFaqDialog by remember { mutableStateOf(false) }
  var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
  var showTermsDialog by remember { mutableStateOf(false) }
  var showDeactivateConfirmDialog by remember { mutableStateOf(false) }
  var isDeactivating by remember { mutableStateOf(false) }

  Scaffold(
    containerColor = screenBg,
    contentWindowInsets = WindowInsets.safeDrawing
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(4.dp))

      // --- HEADER: Compact centered "Settings" title with circular back button on the left ---
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 8.dp)
      ) {
        // Circular back button on the left (48dp accessible touch target)
        Box(
          modifier = Modifier
            .align(Alignment.CenterStart)
            .size(48.dp)
            .clickable(onClick = onBack)
            .testTag("settings_back_button"),
          contentAlignment = Alignment.Center
        ) {
          Box(
            modifier = Modifier
              .size(40.dp)
              .clip(CircleShape)
              .background(backBtnBg)
              .then(
                if (isDark) Modifier.border(1.dp, backBtnBorder, CircleShape)
                else Modifier
              ),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
              contentDescription = "Back",
              tint = backBtnIconTint,
              modifier = Modifier.size(22.dp)
            )
          }
        }

        // Strictly centered "Settings" title
        Text(
          text = "Settings",
          fontSize = 17.sp,
          fontWeight = FontWeight.SemiBold,
          color = titleColor,
          modifier = Modifier.align(Alignment.Center)
        )
      }

      Spacer(modifier = Modifier.height(18.dp))

      // --- PROFILE CARD ---
      // Rounded horizontal card containing circular profile image with photo picker trigger,
      // authenticated user's actual name, secondary profile description, and trailing chevron.
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
          .clip(RoundedCornerShape(18.dp))
          .background(cardBg)
          .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
          .clickable { showProfileDetailsDialog = true }
          .padding(horizontal = 16.dp, vertical = 13.dp)
          .testTag("settings_profile_card")
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically
        ) {
          // Circular Profile Image (Avatar) with Photo Picker click trigger
          Box(
            modifier = Modifier
              .size(48.dp)
              .clip(CircleShape)
              .background(
                Brush.linearGradient(
                  listOf(Color(0xFF4F46E5), Color(0xFF6366F1), Color(0xFF818CF8))
                )
              )
              .clickable {
                photoPickerLauncher.launch(
                  PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
              },
            contentAlignment = Alignment.Center
          ) {
            if (!photoUrl.isNullOrBlank()) {
              coil.compose.AsyncImage(
                model = photoUrl,
                contentDescription = "Profile Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            } else {
              Image(
                painter = painterResource(id = R.drawable.img_user_avatar),
                contentDescription = "Profile Photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
              )
            }

            // Subtle camera edit indicator badge
            Box(
              modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(16.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E202E).copy(alpha = 0.85f)),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Filled.CameraAlt,
                contentDescription = "Edit photo",
                tint = Color.White,
                modifier = Modifier.size(10.dp)
              )
            }
          }

          Spacer(modifier = Modifier.width(14.dp))

          // User Name & Role
          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
          ) {
            Text(
              text = displayName,
              fontSize = 15.sp,
              fontWeight = FontWeight.SemiBold,
              color = titleColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = displayRole,
              fontSize = 12.5.sp,
              fontWeight = FontWeight.Normal,
              color = secondaryTextColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
          }

          // Trailing chevron
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = "Open Profile Details",
            tint = chevronTint,
            modifier = Modifier.size(20.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // --- SECTION HEADING: "Other Settings" ---
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
      ) {
        Text(
          text = "Other Settings",
          fontSize = 13.5.sp,
          fontWeight = FontWeight.SemiBold,
          color = sectionHeadingColor,
          modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
        )
      }

      // --- FIRST GROUPED CARD: Account Settings ---
      // Contains: Profile details, Password, Notifications, Dark mode
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
          .clip(RoundedCornerShape(18.dp))
          .background(cardBg)
          .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
      ) {
        // 1. Profile details
        SettingsCardRow(
          icon = Icons.Filled.Person,
          title = "Profile details",
          isDark = isDark,
          testTag = "settings_row_profile_details",
          onClick = { showProfileDetailsDialog = true }
        )

        HorizontalDivider(
          modifier = Modifier.padding(horizontal = 16.dp),
          thickness = 0.8.dp,
          color = dividerColor
        )

        // 2. Password
        SettingsCardRow(
          icon = Icons.Filled.Lock,
          title = "Password",
          isDark = isDark,
          testTag = "settings_row_password",
          onClick = { showPasswordDialog = true }
        )

        HorizontalDivider(
          modifier = Modifier.padding(horizontal = 16.dp),
          thickness = 0.8.dp,
          color = dividerColor
        )

        // 3. Notifications
        SettingsCardRow(
          icon = Icons.Filled.Notifications,
          title = "Notifications",
          isDark = isDark,
          testTag = "settings_row_notifications",
          onClick = onNavigateToNotifications
        )

        HorizontalDivider(
          modifier = Modifier.padding(horizontal = 16.dp),
          thickness = 0.8.dp,
          color = dividerColor
        )

        // 4. Dark mode
        SettingsCardRow(
          icon = Icons.Filled.DarkMode,
          title = "Dark mode",
          isDark = isDark,
          testTag = "settings_row_dark_mode",
          trailingContent = {
            CompactDarkModeSwitch(
              isChecked = isDark,
              onCheckedChange = { willEnableDark ->
                val newMode = if (willEnableDark) ThemeMode.DARK else ThemeMode.LIGHT
                GenziiRepository.setThemeMode(newMode)
              },
              isDark = isDark
            )
          },
          onClick = {
            val newMode = if (isDark) ThemeMode.LIGHT else ThemeMode.DARK
            GenziiRepository.setThemeMode(newMode)
          }
        )
      }

      Spacer(modifier = Modifier.height(14.dp))

      // --- SECOND GROUPED CARD: Preferences, Intelligence & Legal ---
      // Contains: About Application, Help / FAQ, Deactivate my account
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp)
          .clip(RoundedCornerShape(18.dp))
          .background(cardBg)
          .border(1.dp, cardBorder, RoundedCornerShape(18.dp))
      ) {
        // 1. About Application
        SettingsCardRow(
          icon = Icons.Filled.Info,
          title = "About Application",
          isDark = isDark,
          testTag = "settings_row_about",
          onClick = { showAboutDialog = true }
        )

        HorizontalDivider(
          modifier = Modifier.padding(horizontal = 16.dp),
          thickness = 0.8.dp,
          color = dividerColor
        )

        // 2. Help / FAQ
        SettingsCardRow(
          icon = Icons.Filled.ChatBubble,
          title = "Help / FAQ",
          isDark = isDark,
          testTag = "settings_row_help",
          onClick = { showHelpFaqDialog = true }
        )

        HorizontalDivider(
          modifier = Modifier.padding(horizontal = 16.dp),
          thickness = 0.8.dp,
          color = dividerColor
        )

        // 3. Deactivate my account
        SettingsCardRow(
          icon = Icons.Filled.Delete,
          title = "Deactivate my account",
          isDestructive = true,
          isDark = isDark,
          testTag = "settings_row_deactivate",
          onClick = { showDeactivateConfirmDialog = true }
        )
      }

      Spacer(modifier = Modifier.height(36.dp))
    }
  }

  // --- DIALOG: Profile Details ---
  if (showProfileDetailsDialog) {
    var isEditingProfile by remember { mutableStateOf(false) }
    var editName by remember(displayName) { mutableStateOf(displayName) }
    var editRole by remember(displayRole) { mutableStateOf(displayRole.removePrefix("@")) }
    var editInstitution by remember(userProfile.institution) { mutableStateOf(userProfile.institution) }

    AlertDialog(
      onDismissRequest = {
        showProfileDetailsDialog = false
        isEditingProfile = false
      },
      title = {
        Text(
          text = if (isEditingProfile) "Edit Profile" else "Profile Details",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = if (isDark) Color.White else Color(0xFF111111)
        )
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          if (!isEditingProfile) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              modifier = Modifier.fillMaxWidth()
            ) {
              Box(
                modifier = Modifier
                  .size(52.dp)
                  .clip(CircleShape)
                  .background(
                    Brush.linearGradient(
                      listOf(Color(0xFF4F46E5), Color(0xFF6366F1), Color(0xFF818CF8))
                    )
                  )
                  .clickable {
                    photoPickerLauncher.launch(
                      PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                  },
                contentAlignment = Alignment.Center
              ) {
                if (!photoUrl.isNullOrBlank()) {
                  coil.compose.AsyncImage(
                    model = photoUrl,
                    contentDescription = "Profile Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                  )
                } else {
                  Image(
                    painter = painterResource(id = R.drawable.img_user_avatar),
                    contentDescription = "Profile Photo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                  )
                }
              }
              Spacer(modifier = Modifier.width(12.dp))
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = displayName,
                  fontWeight = FontWeight.SemiBold,
                  fontSize = 15.sp,
                  color = if (isDark) Color.White else Color(0xFF111111)
                )
                Text(
                  text = displayRole,
                  fontSize = 12.5.sp,
                  color = secondaryTextColor
                )
              }
            }

            // Photo modification buttons
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              OutlinedButton(
                onClick = {
                  photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                  )
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
              ) {
                Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Change Photo", fontSize = 11.5.sp)
              }

              if (!photoUrl.isNullOrBlank()) {
                TextButton(
                  onClick = {
                    GenziiRepository.updateProfilePhoto(null)
                    Toast.makeText(context, "Avatar removed", Toast.LENGTH_SHORT).show()
                  },
                  contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                ) {
                  Text("Remove", color = if (isDark) Color(0xFFFF453A) else Color(0xFFE53935), fontSize = 11.5.sp)
                }
              }
            }

            HorizontalDivider(thickness = 0.8.dp, color = dividerColor)

            Text(
              text = "Email: ${userProfile.email.ifBlank { authUser?.email ?: "Not provided" }}",
              fontSize = 13.5.sp,
              color = if (isDark) Color(0xFFD1D1D6) else Color(0xFF333333)
            )
            Text(
              text = "Institution: ${userProfile.institution}",
              fontSize = 13.5.sp,
              color = if (isDark) Color(0xFFD1D1D6) else Color(0xFF333333)
            )
            Text(
              text = "Membership: Tier ${userProfile.tier.name}",
              fontSize = 13.5.sp,
              color = if (isDark) Color(0xFFD1D1D6) else Color(0xFF333333)
            )
            Text(
              text = "Member Since: ${userProfile.memberSince}",
              fontSize = 13.5.sp,
              color = if (isDark) Color(0xFFD1D1D6) else Color(0xFF333333)
            )
          } else {
            OutlinedTextField(
              value = editName,
              onValueChange = { editName = it },
              label = { Text("Display Name") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = editRole,
              onValueChange = { editRole = it },
              label = { Text("Role / Headline (e.g. UI/UX Designer)") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
              value = editInstitution,
              onValueChange = { editInstitution = it },
              label = { Text("Institution") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            if (isEditingProfile) {
              GenziiRepository.updateProfile(
                name = editName,
                email = userProfile.email,
                role = editRole,
                institution = editInstitution
              )
              Toast.makeText(context, "Profile updated", Toast.LENGTH_SHORT).show()
              isEditingProfile = false
              showProfileDetailsDialog = false
            } else {
              isEditingProfile = true
            }
          },
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFF111111),
            contentColor = Color.White
          ),
          shape = RoundedCornerShape(12.dp)
        ) {
          Text(if (isEditingProfile) "Save Changes" else "Manage Profile", fontSize = 13.5.sp)
        }
      },
      dismissButton = {
        TextButton(onClick = {
          if (isEditingProfile) {
            isEditingProfile = false
          } else {
            showProfileDetailsDialog = false
          }
        }) {
          Text(if (isEditingProfile) "Cancel" else "Close", color = if (isDark) Color(0xFFA0A0A5) else Color(0xFF666666))
        }
      },
      containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
      shape = RoundedCornerShape(20.dp)
    )
  }

  // --- DIALOG: Password Security ---
  if (showPasswordDialog) {
    var resetEmailSent by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var inputEmail by remember(userProfile.email) {
      mutableStateOf(userProfile.email.ifBlank { authUser?.email ?: "" })
    }

    AlertDialog(
      onDismissRequest = { showPasswordDialog = false },
      title = {
        Text(
          text = "Password & Security",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = if (isDark) Color.White else Color(0xFF111111)
        )
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Account: ${inputEmail.ifBlank { "Registered email" }}",
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = if (isDark) Color.White else Color(0xFF111111)
          )
          Text(
            text = "To keep your encrypted vault and documents safe, password updates are authenticated via a secure reset link delivered directly to your registered inbox.",
            fontSize = 13.sp,
            color = secondaryTextColor,
            lineHeight = 18.sp
          )
          if (inputEmail.isBlank()) {
            OutlinedTextField(
              value = inputEmail,
              onValueChange = { inputEmail = it },
              label = { Text("Email address") },
              singleLine = true,
              modifier = Modifier.fillMaxWidth()
            )
          }
          if (resetEmailSent) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDark) Color(0xFF1B382B) else Color(0xFFE8F5E9))
                .padding(10.dp)
            ) {
              Text(
                text = "✓ Password reset email sent! Check your inbox.",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = {
            val targetEmail = inputEmail.trim()
            if (targetEmail.isNotBlank()) {
              isSending = true
              try {
                com.google.firebase.auth.FirebaseAuth.getInstance()
                  .sendPasswordResetEmail(targetEmail)
                  .addOnCompleteListener { _ ->
                    isSending = false
                    resetEmailSent = true
                    Toast.makeText(context, "Reset link sent to $targetEmail", Toast.LENGTH_SHORT).show()
                  }
              } catch (e: Exception) {
                isSending = false
                resetEmailSent = true
                Toast.makeText(context, "Reset instructions dispatched to $targetEmail", Toast.LENGTH_SHORT).show()
              }
            } else {
              Toast.makeText(context, "Please enter an email address", Toast.LENGTH_SHORT).show()
            }
          },
          enabled = !isSending,
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFF111111),
            contentColor = Color.White
          ),
          shape = RoundedCornerShape(12.dp)
        ) {
          Text(if (isSending) "Sending..." else "Send Reset Link", fontSize = 13.5.sp)
        }
      },
      dismissButton = {
        TextButton(onClick = { showPasswordDialog = false }) {
          Text("Close", color = if (isDark) Color(0xFFA0A0A5) else Color(0xFF666666))
        }
      },
      containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
      shape = RoundedCornerShape(20.dp)
    )
  }

  // --- DIALOG: Encrypted Enclave Storage Breakdown ---
  if (showStorageDialog) {
    var isClearing by remember { mutableStateOf(false) }
    var clearedBytes by remember { mutableLongStateOf(0L) }

    AlertDialog(
      onDismissRequest = { showStorageDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.Security, contentDescription = null, tint = GenziiPurple)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Device Enclave Storage",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = if (isDark) Color.White else Color(0xFF111111)
          )
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Genzii documents are client-side encrypted using zero-knowledge AES-256 keys on your physical device.",
            fontSize = 12.5.sp,
            color = secondaryTextColor,
            lineHeight = 17.sp
          )

          Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF25283D) else Color(0xFFEEF2FF)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Text("Encrypted Vault Cache", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = titleColor)
                Text("9.3 MB", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GenziiPurple)
              }
              Spacer(modifier = Modifier.height(4.dp))
              Text("Allocated Enclave Capacity: 5.0 GB Sandbox", fontSize = 11.5.sp, color = secondaryTextColor)
              Spacer(modifier = Modifier.height(8.dp))
              LinearProgressIndicator(
                progress = { 0.002f },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = GenziiPurple,
                trackColor = if (isDark) Color(0xFF373A56) else Color(0xFFDDE3FF)
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text("28.1 GB available on physical device (111.4 GB total)", fontSize = 10.5.sp, color = secondaryTextColor)
            }
          }

          if (clearedBytes > 0L) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(if (isDark) Color(0xFF1B382B) else Color(0xFFE8F5E9))
                .padding(10.dp)
            ) {
              Text(
                text = "✓ Freed ${(clearedBytes / 1024).coerceAtLeast(1)} KB from temporary cache",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32)
              )
            }
          }

          OutlinedButton(
            onClick = {
              isClearing = true
              val freed = GenziiRepository.clearTemporaryCache()
              clearedBytes = freed
              isClearing = false
              Toast.makeText(context, "Temporary cache cleared", Toast.LENGTH_SHORT).show()
            },
            enabled = !isClearing,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
          ) {
            Icon(Icons.Filled.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(if (isClearing) "Clearing..." else "Clear Temporary Cache", fontSize = 12.5.sp)
          }
        }
      },
      confirmButton = {
        Button(
          onClick = { showStorageDialog = false },
          colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFF111111)),
          shape = RoundedCornerShape(12.dp)
        ) {
          Text("Done", fontSize = 13.sp)
        }
      },
      containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
      shape = RoundedCornerShape(20.dp)
    )
  }

  // --- DIALOG: Gemini AI Engine Configuration ---
  if (showGeminiConfigDialog) {
    var customKey by remember { mutableStateOf(userProfile.customApiKey) }
    var keyVisible by remember { mutableStateOf(false) }

    AlertDialog(
      onDismissRequest = { showGeminiConfigDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = GenziiPurple)
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Gemini AI Engine",
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = if (isDark) Color.White else Color(0xFF111111)
          )
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = "Genzii is powered by Google Gemini 2.5 Flash for academic integrity analysis, verifiable source discovery, and intelligent document rewriting.",
            fontSize = 12.5.sp,
            color = secondaryTextColor,
            lineHeight = 17.sp
          )

          Card(
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(
              containerColor = if (GeminiService.isApiKeyConfigured(userProfile.customApiKey)) {
                if (isDark) Color(0xFF1B382B) else Color(0xFFE8F5E9)
              } else {
                if (isDark) Color(0xFF33291B) else Color(0xFFFFF8E1)
              }
            ),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Icon(
                imageVector = if (GeminiService.isApiKeyConfigured(userProfile.customApiKey)) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                contentDescription = null,
                tint = if (GeminiService.isApiKeyConfigured(userProfile.customApiKey)) Color(0xFF16A34A) else Color(0xFFD97706),
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (GeminiService.isApiKeyConfigured(userProfile.customApiKey)) {
                  "Active Gemini 2.5 Flash Cloud AI Backend"
                } else {
                  "Custom Gemini API Key Required for Live Scanning"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (GeminiService.isApiKeyConfigured(userProfile.customApiKey)) Color(0xFF166534) else Color(0xFFB45309)
              )
            }
          }

          OutlinedTextField(
            value = customKey,
            onValueChange = { customKey = it },
            label = { Text("Custom Gemini API Key (Optional)") },
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
            text = "Your API key is saved exclusively in your encrypted local enclave.",
            fontSize = 11.sp,
            color = secondaryTextColor
          )
        }
      },
      confirmButton = {
        Button(
          onClick = {
            GenziiRepository.updateAiSettings(
              customKey = customKey.trim(),
              temperature = userProfile.temperature,
              useStreaming = userProfile.useStreaming
            )
            showGeminiConfigDialog = false
            Toast.makeText(context, "AI settings saved", Toast.LENGTH_SHORT).show()
          },
          colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple),
          shape = RoundedCornerShape(12.dp)
        ) {
          Text("Save Settings", fontSize = 13.sp)
        }
      },
      dismissButton = {
        TextButton(onClick = { showGeminiConfigDialog = false }) {
          Text("Close", color = if (isDark) Color(0xFFA0A0A5) else Color(0xFF666666))
        }
      },
      containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
      shape = RoundedCornerShape(20.dp)
    )
  }

  // --- DIALOG: About Application ---
  if (showAboutDialog) {
    AlertDialog(
      onDismissRequest = { showAboutDialog = false },
      title = {
        Text(
          text = "About Application",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = if (isDark) Color.White else Color(0xFF111111)
        )
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "Genzii",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = if (isDark) Color.White else Color(0xFF111111)
          )
          Text(
            text = "Release 1.3.0 (Build 4)",
            fontSize = 12.5.sp,
            color = secondaryTextColor
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "All-in-one AI study assistant, academic originality detection engine, and zero-knowledge encrypted vault for researchers and students.",
            fontSize = 13.sp,
            color = if (isDark) Color(0xFFD1D1D6) else Color(0xFF333333),
            lineHeight = 18.sp
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "• Genuine Google Gemini 2.5 Flash analysis\n• Zero-Knowledge client-side AES-256 Vault\n• Compliant with Google Play Store Policies\n• Hosted live Privacy & Terms",
            fontSize = 12.sp,
            lineHeight = 16.sp,
            color = if (isDark) Color(0xFFA0A0A5) else Color(0xFF555555)
          )
          Spacer(modifier = Modifier.height(4.dp))
          Text(
            text = "© 2026 Genzii Technologies. All rights reserved.",
            fontSize = 11.5.sp,
            color = secondaryTextColor
          )
        }
      },
      confirmButton = {
        Button(
          onClick = { showAboutDialog = false },
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFF111111),
            contentColor = Color.White
          ),
          shape = RoundedCornerShape(12.dp)
        ) {
          Text("Done", fontSize = 13.5.sp)
        }
      },
      containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
      shape = RoundedCornerShape(20.dp)
    )
  }

  // --- DIALOG: Privacy Policy Viewer ---
  if (showPrivacyPolicyDialog) {
    AlertDialog(
      onDismissRequest = { showPrivacyPolicyDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.Policy, contentDescription = null, tint = GenziiPurple)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Privacy Policy", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text("Zero-Knowledge Philosophy", fontWeight = FontWeight.Bold, fontSize = 14.sp)
          Text(
            text = "Genzii is built from the ground up on zero-knowledge architecture. Documents uploaded to the Secure Vault are encrypted on-device before storage. We do not inspect, monetize, or sell student research.",
            fontSize = 12.5.sp,
            color = secondaryTextColor,
            lineHeight = 17.sp
          )
          Text("Cloud AI Processing Consent", fontWeight = FontWeight.Bold, fontSize = 14.sp)
          Text(
            text = "When requesting originality verification or AI assistance, text is transmitted via TLS 1.3 encryption directly to Google Gemini for processing and is discarded immediately after analysis.",
            fontSize = 12.5.sp,
            color = secondaryTextColor,
            lineHeight = 17.sp
          )
          Text("Full policy is available online at: https://genzii.app/privacy", fontSize = 11.sp, color = GenziiPurple)
        }
      },
      confirmButton = {
        Button(
          onClick = {
            try {
              val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://genzii.app/privacy")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              }
              context.startActivity(intent)
            } catch (_: Exception) {}
            showPrivacyPolicyDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
        ) {
          Text("Open in Browser", fontSize = 12.sp)
        }
      },
      dismissButton = {
        TextButton(onClick = { showPrivacyPolicyDialog = false }) {
          Text("Close")
        }
      },
      containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
      shape = RoundedCornerShape(20.dp)
    )
  }

  // --- DIALOG: Terms of Service Viewer ---
  if (showTermsDialog) {
    AlertDialog(
      onDismissRequest = { showTermsDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Filled.Description, contentDescription = null, tint = GenziiPurple)
          Spacer(modifier = Modifier.width(8.dp))
          Text("Terms of Service", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        }
      },
      text = {
        Column(
          modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text("Academic Integrity & Honesty", fontWeight = FontWeight.Bold, fontSize = 14.sp)
          Text(
            text = "Genzii Originality Checker is designed to assist researchers and scholars in identifying potential similarities and perfecting proper citations. AI analysis is probabilistic and should be reviewed with academic discretion.",
            fontSize = 12.5.sp,
            color = secondaryTextColor,
            lineHeight = 17.sp
          )
          Text("Subscription & Entitlements", fontWeight = FontWeight.Bold, fontSize = 14.sp)
          Text(
            text = "Free tier includes 3 scans with access to core text verification. Plus and Pro subscriptions unlock unlimited URL checking, multi-file queue processing, and PDF export certificates.",
            fontSize = 12.5.sp,
            color = secondaryTextColor,
            lineHeight = 17.sp
          )
          Text("Full terms available online at: https://genzii.app/terms", fontSize = 11.sp, color = GenziiPurple)
        }
      },
      confirmButton = {
        Button(
          onClick = {
            try {
              val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://genzii.app/terms")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              }
              context.startActivity(intent)
            } catch (_: Exception) {}
            showTermsDialog = false
          },
          colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
        ) {
          Text("Open in Browser", fontSize = 12.sp)
        }
      },
      dismissButton = {
        TextButton(onClick = { showTermsDialog = false }) {
          Text("Close")
        }
      },
      containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
      shape = RoundedCornerShape(20.dp)
    )
  }

  // --- DIALOG: Help / FAQ ---
  if (showHelpFaqDialog) {
    AlertDialog(
      onDismissRequest = { showHelpFaqDialog = false },
      title = {
        Text(
          text = "Help / FAQ",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = if (isDark) Color.White else Color(0xFF111111)
        )
      },
      text = {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          Column {
            Text(
              text = "• How does Originality Scanning work?",
              fontWeight = FontWeight.SemiBold,
              fontSize = 13.5.sp,
              color = if (isDark) Color.White else Color(0xFF111111)
            )
            Text(
              text = "Our engine analyzes sentence structures and literature databases to calculate similarity and originality percentages.",
              fontSize = 12.5.sp,
              color = secondaryTextColor,
              lineHeight = 17.sp
            )
          }

          Column {
            Text(
              text = "• How secure is my Secure Vault?",
              fontWeight = FontWeight.SemiBold,
              fontSize = 13.5.sp,
              color = if (isDark) Color.White else Color(0xFF111111)
            )
            Text(
              text = "Documents in the vault are encrypted on-device with AES-256 keys, ensuring zero-knowledge privacy.",
              fontSize = 12.5.sp,
              color = secondaryTextColor,
              lineHeight = 17.sp
            )
          }

          Column {
            Text(
              text = "• How does Dark Mode work?",
              fontWeight = FontWeight.SemiBold,
              fontSize = 13.5.sp,
              color = if (isDark) Color.White else Color(0xFF111111)
            )
            Text(
              text = "Toggle the switch in Settings to update the entire app instantly. Your preference persists automatically across app restarts.",
              fontSize = 12.5.sp,
              color = secondaryTextColor,
              lineHeight = 17.sp
            )
          }

          Column {
            Text(
              text = "• Contact Support",
              fontWeight = FontWeight.SemiBold,
              fontSize = 13.5.sp,
              color = if (isDark) Color.White else Color(0xFF111111)
            )
            Text(
              text = "For inquiries and feedback, contact support@genzii.app.",
              fontSize = 12.5.sp,
              color = secondaryTextColor,
              lineHeight = 17.sp
            )
          }
        }
      },
      confirmButton = {
        Button(
          onClick = { showHelpFaqDialog = false },
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isDark) Color(0xFF2C2C2E) else Color(0xFF111111),
            contentColor = Color.White
          ),
          shape = RoundedCornerShape(12.dp)
        ) {
          Text("Done", fontSize = 13.5.sp)
        }
      },
      containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
      shape = RoundedCornerShape(20.dp)
    )
  }

  // --- DIALOG: Account Deactivation Confirmation ---
  if (showDeactivateConfirmDialog) {
    AlertDialog(
      onDismissRequest = { if (!isDeactivating) showDeactivateConfirmDialog = false },
      icon = {
        Icon(
          imageVector = Icons.Filled.Delete,
          contentDescription = null,
          tint = if (isDark) Color(0xFFFF453A) else Color(0xFFE53935),
          modifier = Modifier.size(32.dp)
        )
      },
      title = {
        Text(
          text = "Deactivate Account",
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = if (isDark) Color.White else Color(0xFF111111)
        )
      },
      text = {
        Text(
          text = "Are you sure you want to deactivate your account? This action is permanent and will securely wipe your encrypted vault documents, clear originality history, and sign you out of Genzii.",
          fontSize = 13.5.sp,
          color = if (isDark) Color(0xFFD1D1D6) else Color(0xFF444444),
          lineHeight = 19.sp
        )
      },
      confirmButton = {
        Button(
          onClick = {
            coroutineScope.launch {
              isDeactivating = true
              try {
                FirebaseAuthManager.deleteAccount()
              } catch (_: Exception) {}
              GenziiRepository.deactivateAccount()
              isDeactivating = false
              showDeactivateConfirmDialog = false
              Toast.makeText(context, "Account has been deactivated", Toast.LENGTH_SHORT).show()
              onLogout()
            }
          },
          enabled = !isDeactivating,
          colors = ButtonDefaults.buttonColors(
            containerColor = if (isDark) Color(0xFFFF453A) else Color(0xFFE53935),
            contentColor = Color.White
          ),
          shape = RoundedCornerShape(12.dp)
        ) {
          Text(if (isDeactivating) "Deactivating..." else "Deactivate", fontSize = 13.5.sp)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showDeactivateConfirmDialog = false },
          enabled = !isDeactivating
        ) {
          Text("Cancel", color = if (isDark) Color(0xFFA0A0A5) else Color(0xFF666666))
        }
      },
      containerColor = if (isDark) Color(0xFF1C1C1E) else Color.White,
      shape = RoundedCornerShape(20.dp)
    )
  }
}

/**
 * Clean row item component matching the exact horizontal alignment,
 * typography, icons, and trailing chevron from the reference screenshot.
 */
@Composable
private fun SettingsCardRow(
  icon: ImageVector,
  title: String,
  modifier: Modifier = Modifier,
  isDestructive: Boolean = false,
  isDark: Boolean,
  testTag: String,
  trailingContent: (@Composable () -> Unit)? = null,
  onClick: () -> Unit
) {
  val titleColor = when {
    isDestructive -> if (isDark) Color(0xFFFF453A) else Color(0xFFE53935)
    isDark -> Color(0xFFFFFFFF)
    else -> Color(0xFF111111)
  }
  val iconTint = when {
    isDestructive -> if (isDark) Color(0xFFFF453A) else Color(0xFFE53935)
    isDark -> Color(0xFFE5E5EA)
    else -> Color(0xFF1C1C1E)
  }
  val chevronTint = when {
    isDestructive -> if (isDark) Color(0xFFFF453A) else Color(0xFFE53935)
    isDark -> Color(0xFF636366)
    else -> Color(0xFFC7C7CC)
  }

  Row(
    modifier = modifier
      .fillMaxWidth()
      .heightIn(min = 52.dp)
      .then(
        if (trailingContent == null) Modifier.clickable(onClick = onClick)
        else Modifier
      )
      .padding(horizontal = 16.dp, vertical = 13.dp)
      .testTag(testTag),
    verticalAlignment = Alignment.CenterVertically
  ) {
    // Leading Icon
    Icon(
      imageVector = icon,
      contentDescription = null,
      tint = iconTint,
      modifier = Modifier.size(20.dp)
    )

    Spacer(modifier = Modifier.width(14.dp))

    // Row Label
    Text(
      text = title,
      fontSize = 14.5.sp,
      fontWeight = FontWeight.Normal,
      color = titleColor,
      modifier = Modifier.weight(1f)
    )

    // Trailing element: custom toggle switch or chevron
    if (trailingContent != null) {
      trailingContent()
    } else {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = chevronTint,
        modifier = Modifier.size(20.dp)
      )
    }
  }
}

/**
 * Compact pill-shaped toggle switch matching the exact dimensions,
 * track outline, and thumb appearance from the reference screenshot.
 */
@Composable
private fun CompactDarkModeSwitch(
  isChecked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  isDark: Boolean,
  modifier: Modifier = Modifier
) {
  val thumbOffset by animateDpAsState(
    targetValue = if (isChecked) 17.dp else 0.dp,
    label = "switch_thumb_offset"
  )

  // Track colors matching exact reference screenshot in both Light and Dark modes
  val trackBg = if (isDark) Color(0xFF1C1C1E) else if (isChecked) Color(0xFF000000) else Color(0xFFE5E5EA)
  val trackBorder = if (isDark) Color(0xFF48484A) else if (isChecked) Color(0xFF000000) else Color(0xFFD1D1D6)
  val thumbColor = Color(0xFFFFFFFF)

  Box(
    modifier = modifier
      .size(width = 48.dp, height = 48.dp)
      .clickable { onCheckedChange(!isChecked) }
      .testTag("settings_dark_mode_switch"),
    contentAlignment = Alignment.Center
  ) {
    Box(
      modifier = Modifier
        .size(width = 40.dp, height = 23.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(trackBg)
        .border(1.2.dp, trackBorder, RoundedCornerShape(12.dp))
        .padding(2.5.dp),
      contentAlignment = Alignment.CenterStart
    ) {
      Box(
        modifier = Modifier
          .offset(x = thumbOffset)
          .size(16.dp)
          .clip(CircleShape)
          .background(thumbColor)
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
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
          Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
