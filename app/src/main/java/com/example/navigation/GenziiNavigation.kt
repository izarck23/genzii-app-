package com.example.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.ui.screens.*
import com.example.ui.theme.*

sealed class Screen(val route: String) {
  object Splash : Screen("splash")
  object Onboarding : Screen("onboarding")
  object Permissions : Screen("permissions")
  object Login : Screen("login")
  object Signup : Screen("signup")
  object Otp : Screen("otp")
  object ResetPassword : Screen("reset_password")
  object WelcomeBack : Screen("welcome_back")

  // Bottom Navigation tabs
  object Dashboard : Screen("dashboard")
  object AiAssistant : Screen("ai_assistant")
  object OriginalityChecker : Screen("originality_checker")
  object SecureVault : Screen("secure_vault")
  object Profile : Screen("profile")

  // Secondary screens
  object FilePreview : Screen("file_preview/{docId}") {
    fun createRoute(docId: String) = "file_preview/$docId"
  }
  object VaultFolder : Screen("vault_folder/{folderName}") {
    fun createRoute(folderName: String) = "vault_folder/$folderName"
  }
  object PdfTools : Screen("pdf_tools")
  object OcrScanner : Screen("ocr_scanner")
  object CameraXScanner : Screen("camerax_scanner")
  object UrlChecker : Screen("url_checker")
  object WritingTools : Screen("writing_tools")
  object StudyAssistant : Screen("study_assistant")
  object Settings : Screen("settings")
  object Appearance : Screen("appearance")
  object PrivacySecurity : Screen("privacy_security")
  object Notifications : Screen("notifications")
  object Premium : Screen("premium")
}


data class BottomNavItem(
  val label: String,
  val route: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String
)

val BottomNavItems = listOf(
  BottomNavItem(
    label = "Home",
    route = Screen.Dashboard.route,
    selectedIcon = Icons.Filled.Home,
    unselectedIcon = Icons.Outlined.Home,
    testTag = "nav_tab_home"
  ),
  BottomNavItem(
    label = "AI",
    route = Screen.AiAssistant.route,
    selectedIcon = Icons.Filled.AutoAwesome,
    unselectedIcon = Icons.Outlined.AutoAwesome,
    testTag = "nav_tab_ai"
  ),
  BottomNavItem(
    label = "Check",
    route = Screen.OriginalityChecker.route,
    selectedIcon = Icons.Filled.VerifiedUser,
    unselectedIcon = Icons.Outlined.VerifiedUser,
    testTag = "nav_tab_check"
  ),
  BottomNavItem(
    label = "Vault",
    route = Screen.SecureVault.route,
    selectedIcon = Icons.Filled.Lock,
    unselectedIcon = Icons.Outlined.Lock,
    testTag = "nav_tab_vault"
  ),
  BottomNavItem(
    label = "Settings",
    route = Screen.Profile.route,
    selectedIcon = Icons.Filled.Settings,
    unselectedIcon = Icons.Outlined.Settings,
    testTag = "nav_tab_profile"
  )
)

@Composable
fun GenziiBottomNavigationBar(navController: NavHostController) {
  val navBackStackEntry by navController.currentBackStackEntryAsState()
  val currentDestination = navBackStackEntry?.destination

  // Only show the BottomNavigationBar on the 4 primary tabs (Home, AI, Check, Vault)
  // The Settings screen has no bottom navigation bar, matching the reference screenshot exactly
  val isBottomBarVisible = currentDestination?.hierarchy?.any {
    it.route == Screen.Dashboard.route ||
    it.route == Screen.AiAssistant.route ||
    it.route == Screen.OriginalityChecker.route ||
    it.route == Screen.SecureVault.route
  } == true

  if (!isBottomBarVisible) return

  Box(
    modifier = Modifier
      .fillMaxWidth()
      .navigationBarsPadding()
      .padding(horizontal = 16.dp, vertical = 6.dp),
    contentAlignment = Alignment.Center
  ) {
    Surface(
      shape = RoundedCornerShape(28.dp),
      color = MaterialTheme.colorScheme.surface,
      tonalElevation = 6.dp,
      shadowElevation = 10.dp,
      border = BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
      ),
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 480.dp)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        BottomNavItems.forEach { item ->
          val isSelected = currentDestination?.hierarchy?.any { it.route == item.route } == true

          val animatedIndicatorColor by androidx.compose.animation.animateColorAsState(
            targetValue = if (isSelected) GenziiPurple.copy(alpha = 0.12f) else Color.Transparent,
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
            label = "indicatorColor"
          )
          val animatedContentColor by androidx.compose.animation.animateColorAsState(
            targetValue = if (isSelected) GenziiPurple else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
            animationSpec = androidx.compose.animation.core.tween(durationMillis = 200),
            label = "contentColor"
          )

          Box(
            modifier = Modifier
              .weight(1f)
              .clip(RoundedCornerShape(16.dp))
              .clickable {
                navController.navigate(item.route) {
                  popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                  }
                  launchSingleTop = true
                  restoreState = true
                }
              }
              .testTag(item.testTag),
            contentAlignment = Alignment.Center
          ) {
            Column(
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.Center,
              modifier = Modifier.padding(vertical = 4.dp)
            ) {
              Box(
                modifier = Modifier
                  .clip(RoundedCornerShape(14.dp))
                  .background(animatedIndicatorColor)
                  .padding(horizontal = 10.dp, vertical = 3.dp),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                  contentDescription = item.label,
                  tint = animatedContentColor,
                  modifier = Modifier.size(20.dp)
                )
              }

              Spacer(modifier = Modifier.height(2.dp))

              Text(
                text = item.label,
                fontFamily = PoppinsFontFamily,
                fontSize = 10.5.sp,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                color = animatedContentColor,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
              )
            }
          }
        }
      }
    }
  }
}

@Composable
fun GenziiNavGraph(
  navController: NavHostController,
  modifier: Modifier = Modifier
) {
  NavHost(
    navController = navController,
    startDestination = Screen.Splash.route,
    modifier = modifier
  ) {
    composable(Screen.Splash.route) {
      SplashScreen(
        onFinishSplash = {
          navController.navigate(Screen.Onboarding.route) {
            popUpTo(Screen.Splash.route) { inclusive = true }
          }
        }
      )
    }

    composable(Screen.Onboarding.route) {
      OnboardingScreen(
        onFinishOnboarding = {
          navController.navigate(Screen.Login.route) {
            popUpTo(Screen.Onboarding.route) { inclusive = true }
          }
        }
      )
    }

    composable(Screen.Permissions.route) {
      PermissionsScreen(
        onContinue = {
          navController.navigate(Screen.Login.route) {
            popUpTo(Screen.Permissions.route) { inclusive = true }
          }
        }
      )
    }

    composable(Screen.Login.route) {
      LoginScreen(
        onLoginSuccess = {
          navController.navigate(Screen.WelcomeBack.route) {
            popUpTo(Screen.Login.route) { inclusive = true }
          }
        },
        onNavigateToSignup = {
          navController.navigate(Screen.Signup.route)
        },
        onNavigateToForgotPassword = {
          navController.navigate(Screen.ResetPassword.route)
        }
      )
    }

    composable(Screen.Signup.route) {
      CreateAccountScreen(
        onSignupSuccess = {
          navController.navigate(Screen.Otp.route)
        },
        onLoginSuccess = {
          navController.navigate(Screen.WelcomeBack.route) {
            popUpTo(Screen.Login.route) { inclusive = true }
          }
        },
        onNavigateToLogin = {
          navController.popBackStack()
        },
        onBack = {
          navController.popBackStack()
        }
      )
    }

    composable(Screen.Otp.route) {
      OtpVerificationScreen(
        onVerifySuccess = {
          navController.navigate(Screen.WelcomeBack.route) {
            popUpTo(Screen.Login.route) { inclusive = true }
          }
        },
        onBack = {
          navController.popBackStack()
        }
      )
    }

    composable(Screen.ResetPassword.route) {
      ResetPasswordScreen(
        onResetSuccess = {
          navController.popBackStack()
        },
        onBack = {
          navController.popBackStack()
        }
      )
    }

    composable(Screen.WelcomeBack.route) {
      WelcomeBackScreen(
        onContinue = {
          navController.navigate(Screen.Dashboard.route) {
            popUpTo(Screen.WelcomeBack.route) { inclusive = true }
          }
        },
        onQuickAction = { tab ->
          val targetRoute = when (tab) {
            com.example.ui.components.NavigationTab.HOME -> Screen.Dashboard.route
            com.example.ui.components.NavigationTab.AI -> Screen.AiAssistant.route
            com.example.ui.components.NavigationTab.CHECK -> Screen.OriginalityChecker.route
            com.example.ui.components.NavigationTab.VAULT -> Screen.SecureVault.route
            com.example.ui.components.NavigationTab.PROFILE -> Screen.Profile.route
          }
          navController.navigate(targetRoute) {
            popUpTo(Screen.WelcomeBack.route) { inclusive = true }
          }
        },
        onUpgrade = {
          navController.navigate(Screen.Profile.route) {
            popUpTo(Screen.WelcomeBack.route) { inclusive = true }
          }
        }
      )
    }

    // --- Primary Bottom Navigation Destinations ---

    composable(Screen.Dashboard.route) {
      HomeScreen(
        onNavigateToCheck = {
          navController.navigate(Screen.OriginalityChecker.route)
        },
        onNavigateToAi = {
          navController.navigate(Screen.AiAssistant.route)
        },
        onNavigateToVault = {
          navController.navigate(Screen.SecureVault.route)
        },
        onNavigateToUpgrade = {
          navController.navigate(Screen.Premium.route)
        },
        onNavigateToTools = {
          navController.navigate(Screen.PdfTools.route)
        },
        onNavigateToOcr = {
          navController.navigate(Screen.OcrScanner.route)
        },
        onOpenDocument = { docId ->
          navController.navigate(Screen.FilePreview.createRoute(docId))
        },
        onNotificationsClick = {
          navController.navigate(Screen.Notifications.route)
        },
        onProfileClick = {
          navController.navigate(Screen.Profile.route)
        }
      )
    }

    composable(Screen.AiAssistant.route) {
      AiChatScreen(
        onNavigateToWritingTools = {
          navController.navigate(Screen.WritingTools.route)
        }
      )
    }

    composable(Screen.OriginalityChecker.route) {
      OriginalityCheckerScreen(
        onNavigateToVault = {
          navController.navigate(Screen.SecureVault.route)
        },
        onNavigateToPremium = {
          navController.navigate(Screen.Premium.route)
        }
      )
    }

    composable(Screen.SecureVault.route) {
      VaultScreen(
        onOpenDocument = { docId ->
          navController.navigate(Screen.FilePreview.createRoute(docId))
        },
        onOpenFolder = { folderName ->
          navController.navigate(Screen.VaultFolder.createRoute(folderName))
        },
        onNotificationsClick = {
          navController.navigate(Screen.Notifications.route)
        },
        onProfileClick = {
          navController.navigate(Screen.Profile.route)
        }
      )
    }

    composable(Screen.Profile.route) {
      SettingsScreen(
        onNavigateToPrivacy = {
          navController.navigate(Screen.PrivacySecurity.route)
        },
        onNavigateToNotifications = {
          navController.navigate(Screen.Notifications.route)
        },
        onLogout = {
          com.example.auth.FirebaseAuthManager.signOut()
          navController.navigate(Screen.Login.route) {
            popUpTo(0) { inclusive = true }
          }
        },
        onBack = {
          if (!navController.popBackStack()) {
            navController.navigate(Screen.Dashboard.route) {
              popUpTo(navController.graph.findStartDestination().id)
              launchSingleTop = true
            }
          }
        }
      )
    }

    // --- Secondary Detail Screens ---

    composable(
      route = Screen.FilePreview.route,
      arguments = listOf(navArgument("docId") { type = NavType.StringType })
    ) { backStackEntry ->
      val docId = backStackEntry.arguments?.getString("docId") ?: ""
      FilePreviewScreen(
        docId = docId,
        onBack = { navController.popBackStack() }
      )
    }

    composable(
      route = Screen.VaultFolder.route,
      arguments = listOf(navArgument("folderName") { type = NavType.StringType })
    ) { backStackEntry ->
      val folderName = backStackEntry.arguments?.getString("folderName") ?: ""
      VaultFolderViewScreen(
        folderName = folderName,
        onOpenDocument = { docId ->
          navController.navigate(Screen.FilePreview.createRoute(docId))
        },
        onBack = { navController.popBackStack() }
      )
    }

    composable(Screen.PdfTools.route) {
      PdfToolsScreen(
        onBack = { navController.popBackStack() },
        onNavigateToOcr = { navController.navigate(Screen.OcrScanner.route) }
      )
    }

    composable(Screen.OcrScanner.route) {
      OcrScannerScreen(onBack = { navController.popBackStack() })
    }

    composable(Screen.CameraXScanner.route) {
      CameraXDocumentScannerScreen(
        onClose = { navController.popBackStack() },
        onPagesCaptured = { _ ->
          navController.popBackStack()
        }
      )
    }

    composable(Screen.UrlChecker.route) {
      UrlCheckerScreen(onBack = { navController.popBackStack() })
    }

    composable(Screen.WritingTools.route) {
      AiWritingToolsScreen(onBack = { navController.popBackStack() })
    }

    composable(Screen.StudyAssistant.route) {
      StudyAssistantScreen(onBack = { navController.popBackStack() })
    }

    composable(Screen.Settings.route) {
      SettingsScreen(
        onNavigateToAppearance = { navController.navigate(Screen.Appearance.route) },
        onNavigateToPrivacy = { navController.navigate(Screen.PrivacySecurity.route) },
        onNavigateToNotifications = { navController.navigate(Screen.Notifications.route) },
        onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
        onLogout = {
          com.example.auth.FirebaseAuthManager.signOut()
          navController.navigate(Screen.Login.route) {
            popUpTo(0) { inclusive = true }
          }
        },
        onBack = { navController.popBackStack() }
      )
    }

    composable(Screen.Appearance.route) {
      DarkModeToggleScreen(onBack = { navController.popBackStack() })
    }

    composable(Screen.PrivacySecurity.route) {
      PrivacySecurityScreen(onBack = { navController.popBackStack() })
    }

    composable(Screen.Notifications.route) {
      NotificationsScreen(onBack = { navController.popBackStack() })
    }

    composable(Screen.Premium.route) {
      PremiumScreen(onBack = { navController.popBackStack() })
    }
  }
}

