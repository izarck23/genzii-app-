package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.data.GenziiRepository
import com.example.model.FileType
import com.example.model.ThemeMode
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsScreenTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    GenziiRepository.init(context)
  }

  @Test
  fun testThemePersistenceAcrossInit() {
    // 1. Set theme to DARK
    GenziiRepository.setThemeMode(ThemeMode.DARK)
    assertEquals(ThemeMode.DARK, GenziiRepository.userProfile.value.themeMode)

    // 2. Re-initialize from context (simulating app restart)
    GenziiRepository.init(context)
    assertEquals(ThemeMode.DARK, GenziiRepository.userProfile.value.themeMode)

    // 3. Set theme to LIGHT
    GenziiRepository.setThemeMode(ThemeMode.LIGHT)
    assertEquals(ThemeMode.LIGHT, GenziiRepository.userProfile.value.themeMode)

    // 4. Re-initialize from context again
    GenziiRepository.init(context)
    assertEquals(ThemeMode.LIGHT, GenziiRepository.userProfile.value.themeMode)
  }

  @Test
  fun testAccountDeactivationFlow() {
    // Populate profile and documents
    GenziiRepository.updateProfile(
      name = "Active Scholar",
      email = "scholar@genzii.app",
      role = "Research Lead"
    )
    GenziiRepository.addDocument("Draft.pdf", "Encrypted Content", "Personal", FileType.PDF)

    assertTrue(GenziiRepository.documents.value.isNotEmpty())
    assertEquals("Active Scholar", GenziiRepository.userProfile.value.name)

    // Execute account deactivation
    GenziiRepository.deactivateAccount()

    assertEquals("Guest User", GenziiRepository.userProfile.value.name)
    assertEquals("", GenziiRepository.userProfile.value.email)
    assertTrue(GenziiRepository.documents.value.isEmpty())
    assertTrue(GenziiRepository.reports.value.isEmpty())
    assertTrue(GenziiRepository.chatMessages.value.isEmpty())
  }

  @Test
  fun testSettingsScreenRenderingInLightMode() {
    GenziiRepository.updateProfile(
      name = "uix.vikram",
      email = "vikram@example.com",
      role = "UI/UX Designer"
    )
    GenziiRepository.setThemeMode(ThemeMode.LIGHT)

    var backPressed = false
    var notificationsPressed = false

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = false) {
        SettingsScreen(
          onBack = { backPressed = true },
          onNavigateToNotifications = { notificationsPressed = true }
        )
      }
    }

    // Verify Header
    composeTestRule.onNodeWithText("Settings").assertExists()
    composeTestRule.onNodeWithTag("settings_back_button").assertExists()

    // Verify Profile Card loaded actual user name & secondary role
    composeTestRule.onNodeWithText("uix.vikram").assertExists()
    composeTestRule.onNodeWithText("@UI/UX Designer").assertExists()

    // Verify "Other Settings" section
    composeTestRule.onNodeWithText("Other Settings").assertExists()
    composeTestRule.onNodeWithTag("settings_row_profile_details").assertExists()
    composeTestRule.onNodeWithTag("settings_row_password").assertExists()
    composeTestRule.onNodeWithTag("settings_row_notifications").assertExists()
    composeTestRule.onNodeWithTag("settings_row_dark_mode").assertExists()

    // Verify Second Grouped Card
    composeTestRule.onNodeWithTag("settings_row_about").assertExists()
    composeTestRule.onNodeWithTag("settings_row_help").assertExists()
    composeTestRule.onNodeWithTag("settings_row_deactivate").assertExists()

    // Verify clicks
    composeTestRule.onNodeWithTag("settings_back_button").performClick()
    assertTrue(backPressed)

    composeTestRule.onNodeWithTag("settings_row_notifications").performClick()
    assertTrue(notificationsPressed)
  }

  @Test
  fun testSettingsScreenRenderingInDarkMode() {
    GenziiRepository.updateProfile(
      name = "uix.vikram",
      email = "vikram@example.com",
      role = "UI/UX Designer"
    )
    GenziiRepository.setThemeMode(ThemeMode.DARK)

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        SettingsScreen()
      }
    }

    // Verify elements in dark mode
    composeTestRule.onNodeWithText("Settings").assertExists()
    composeTestRule.onNodeWithText("uix.vikram").assertExists()
    composeTestRule.onNodeWithText("Other Settings").assertExists()
    composeTestRule.onNodeWithTag("settings_dark_mode_switch").assertExists()
    composeTestRule.onNodeWithText("Deactivate my account").assertExists()
  }

  @Test
  fun testDarkModeSwitchTogglesTheme() {
    GenziiRepository.setThemeMode(ThemeMode.LIGHT)

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = false) {
        SettingsScreen()
      }
    }

    // Click the Dark mode switch
    composeTestRule.onNodeWithTag("settings_dark_mode_switch").performClick()

    // Verify repository updated to DARK
    assertEquals(ThemeMode.DARK, GenziiRepository.userProfile.value.themeMode)
  }

  @Test
  fun testProfileCardOpensProfileDetailsDialog() {
    GenziiRepository.updateProfile(
      name = "Vikram Scholar",
      email = "vikram@example.com",
      role = "UI/UX Designer"
    )

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = false) {
        SettingsScreen()
      }
    }

    // Click profile card
    composeTestRule.onNodeWithTag("settings_profile_card").performClick()

    // Verify Profile Details dialog opened
    composeTestRule.onNodeWithText("Profile Details").assertExists()
    composeTestRule.onNodeWithText("Manage Profile").assertExists()
    composeTestRule.onNodeWithText("Close").assertExists()
  }

  @Test
  fun testPasswordRowOpensPasswordSecurityDialog() {
    GenziiRepository.updateProfile(
      name = "Vikram Scholar",
      email = "vikram@example.com",
      role = "UI/UX Designer"
    )

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = false) {
        SettingsScreen()
      }
    }

    // Click Password row
    composeTestRule.onNodeWithTag("settings_row_password").performClick()

    // Verify Password dialog
    composeTestRule.onNodeWithText("Password & Security").assertExists()
    composeTestRule.onNodeWithText("Send Reset Link").assertExists()
  }

  @Test
  fun testAboutAndHelpDialogsOpen() {
    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        SettingsScreen()
      }
    }

    // Test About row
    composeTestRule.onNodeWithTag("settings_row_about").performScrollTo().performClick()
    composeTestRule.onNodeWithText("Release 1.3.0 (Build 4)").assertExists()
    composeTestRule.onNodeWithText("© 2026 Genzii Technologies. All rights reserved.").assertExists()
    composeTestRule.onNodeWithText("Done").performClick()

    // Test Help row
    composeTestRule.onNodeWithTag("settings_row_help").performScrollTo().performClick()
    composeTestRule.onNodeWithText("• How does Originality Scanning work?").assertExists()
    composeTestRule.onNodeWithText("Done").performClick()
  }

  @Test
  fun testDeactivateAccountConfirmationDialog() {
    var loggedOut = false

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = true) {
        SettingsScreen(onLogout = { loggedOut = true })
      }
    }

    // Click Deactivate row (scroll to item if needed)
    composeTestRule.onNodeWithTag("settings_row_deactivate").performScrollTo().performClick()

    // Verify dialog
    composeTestRule.onNodeWithText("Deactivate Account").assertExists()
    composeTestRule.onNodeWithText("Cancel").assertExists()
    composeTestRule.onNodeWithText("Deactivate").assertExists()
  }
}
