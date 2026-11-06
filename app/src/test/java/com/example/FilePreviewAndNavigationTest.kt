package com.example

import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.test.core.app.ApplicationProvider
import com.example.data.GenziiRepository
import com.example.model.DocumentItem
import com.example.model.FileType
import com.example.navigation.GenziiBottomNavigationBar
import com.example.navigation.Screen
import com.example.ui.screens.FilePreviewScreen
import com.example.ui.screens.RealImageViewer
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w600dp-h1200dp")
class FilePreviewAndNavigationTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    GenziiRepository.deactivateAccount()
    GenziiRepository.init(context)
  }

  @Test
  fun testImagePreviewHidesEditAndPrintActions() {
    val dummyImageFile = File(context.filesDir, "vault_documents/sample_photo.jpg").apply {
      parentFile?.mkdirs()
      writeBytes(ByteArray(512) { 0xFF.toByte() })
    }

    val imageDoc = GenziiRepository.addImportedDocument(
      name = "sample_photo.jpg",
      sizeFormatted = "512 B",
      sizeBytes = dummyImageFile.length(),
      fileType = FileType.IMAGE,
      content = "OCR text extracted from photo",
      folder = "Vault",
      localFilePath = dummyImageFile.absolutePath,
      uriString = null,
      pageCount = 1
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        FilePreviewScreen(
          docId = imageDoc.id,
          onBack = {}
        )
      }
    }

    // Share should be present
    composeTestRule.onNodeWithTag("preview_action_share").assertIsDisplayed()
    // Save Image / Export should be present
    composeTestRule.onNodeWithTag("preview_action_export").assertIsDisplayed()
    // Print MUST NOT be displayed for images
    composeTestRule.onNodeWithTag("preview_action_print").assertDoesNotExist()
    // Edit MUST NOT be displayed for images
    composeTestRule.onNodeWithTag("preview_action_edit").assertDoesNotExist()
  }

  @Test
  fun testPdfPreviewDisplaysPrintAndHidesEditAction() {
    val dummyPdfFile = File(context.filesDir, "vault_documents/report.pdf").apply {
      parentFile?.mkdirs()
      writeText("%PDF-1.4 Mock PDF content")
    }

    val pdfDoc = GenziiRepository.addImportedDocument(
      name = "report.pdf",
      sizeFormatted = "2 KB",
      sizeBytes = dummyPdfFile.length(),
      fileType = FileType.PDF,
      content = "Extracted text from PDF report",
      folder = "Vault",
      localFilePath = dummyPdfFile.absolutePath,
      uriString = null,
      pageCount = 1
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        FilePreviewScreen(
          docId = pdfDoc.id,
          onBack = {}
        )
      }
    }

    // Share should be present
    composeTestRule.onNodeWithTag("preview_action_share").assertIsDisplayed()
    // Export should be present
    composeTestRule.onNodeWithTag("preview_action_export").assertIsDisplayed()
    // Print MUST be displayed for PDF documents
    composeTestRule.onNodeWithTag("preview_action_print").assertIsDisplayed()
    // Edit MUST NOT be displayed for PDF documents
    composeTestRule.onNodeWithTag("preview_action_edit").assertDoesNotExist()
  }

  @Test
  fun testTextDocumentDisplaysEditAndPrintActions() {
    val textDoc = GenziiRepository.addDocument(
      name = "Meeting_Notes.txt",
      content = "Agenda: 1. Architecture, 2. Roadmap",
      folder = "Vault",
      type = FileType.TXT
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        FilePreviewScreen(
          docId = textDoc.id,
          onBack = {}
        )
      }
    }

    // Share should be present
    composeTestRule.onNodeWithTag("preview_action_share").assertIsDisplayed()
    // Export should be present
    composeTestRule.onNodeWithTag("preview_action_export").assertIsDisplayed()
    // Print should be displayed
    composeTestRule.onNodeWithTag("preview_action_print").assertIsDisplayed()
    // Edit should be displayed for text documents
    composeTestRule.onNodeWithTag("preview_action_edit").assertIsDisplayed()
  }

  @Test
  fun testMultipleImagesInFolderShowGalleryPageIndicator() {
    val imgFile1 = File(context.filesDir, "vault_documents/photo1.png").apply {
      parentFile?.mkdirs()
      writeBytes(ByteArray(256) { 0x11 })
    }
    val imgFile2 = File(context.filesDir, "vault_documents/photo2.png").apply {
      parentFile?.mkdirs()
      writeBytes(ByteArray(256) { 0x22 })
    }

    val doc1 = GenziiRepository.addImportedDocument(
      name = "photo1.png",
      sizeFormatted = "256 B",
      sizeBytes = imgFile1.length(),
      fileType = FileType.IMAGE,
      content = "Text 1",
      folder = "Receipts",
      localFilePath = imgFile1.absolutePath,
      uriString = null,
      pageCount = 1
    )
    GenziiRepository.addImportedDocument(
      name = "photo2.png",
      sizeFormatted = "256 B",
      sizeBytes = imgFile2.length(),
      fileType = FileType.IMAGE,
      content = "Text 2",
      folder = "Receipts",
      localFilePath = imgFile2.absolutePath,
      uriString = null,
      pageCount = 1
    )

    composeTestRule.setContent {
      MyApplicationTheme {
        FilePreviewScreen(
          docId = doc1.id,
          onBack = {}
        )
      }
    }

    // Should display the horizontal pager
    composeTestRule.onNodeWithTag("preview_pager").assertIsDisplayed()
    // Should display the page indicator (e.g. 1/2)
    composeTestRule.onNodeWithTag("preview_page_indicator").assertIsDisplayed()
  }

  @Test
  fun testRealImageViewerZoomAndResetControls() {
    val dummyFile = File(context.filesDir, "vault_documents/zoom_test.jpg").apply {
      parentFile?.mkdirs()
      // Generate a tiny 1x1 valid PNG so BitmapFactory can decode it
      val bmp = android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888)
      outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }

    val doc = DocumentItem(
      id = "zoom-doc",
      name = "zoom_test.jpg",
      sizeFormatted = "10 KB",
      type = FileType.IMAGE,
      folder = "Vault",
      updatedAt = "Just now",
      content = "OCR recognized text here",
      localFilePath = dummyFile.absolutePath
    )

    var zoomedState = false

    composeTestRule.setContent {
      MyApplicationTheme {
        RealImageViewer(
          doc = doc,
          resolvedFile = dummyFile,
          ocrText = doc.content,
          onZoomChange = { zoomedState = it }
        )
      }
    }

    // Zoom in button should be displayed
    composeTestRule.onNodeWithTag("zoom_in_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("zoom_level_text").assertTextEquals("100%")

    // Click Zoom In
    composeTestRule.onNodeWithTag("zoom_in_button").performClick()
    assertTrue(zoomedState)
    composeTestRule.onNodeWithTag("zoom_level_text").assertTextEquals("130%")

    // Fit to screen button should now be visible
    composeTestRule.onNodeWithTag("fit_to_screen_button").assertIsDisplayed()

    // Click Fit to screen / reset
    composeTestRule.onNodeWithTag("fit_to_screen_button").performClick()
    assertFalse(zoomedState)
    composeTestRule.onNodeWithTag("zoom_level_text").assertTextEquals("100%")

    // Toggle to OCR text tab
    composeTestRule.onNodeWithTag("ocr_tab_button").performClick()
    composeTestRule.onNodeWithTag("preview_ocr_text_content").assertIsDisplayed()
    composeTestRule.onNodeWithText("OCR recognized text here").assertIsDisplayed()

    // Toggle back to Image tab
    composeTestRule.onNodeWithTag("image_tab_button").performClick()
    composeTestRule.onNodeWithTag("preview_image_display").assertIsDisplayed()
  }

  @Test
  fun testBottomNavigationBarDestinationsAndTags() {
    composeTestRule.setContent {
      MyApplicationTheme {
        val navController = rememberNavController()
        NavHost(navController = navController, startDestination = Screen.Dashboard.route) {
          composable(Screen.Dashboard.route) {
            androidx.compose.material3.Text("Home Screen")
          }
        }
        GenziiBottomNavigationBar(navController = navController)
      }
    }

    // Home, AI, Check, Vault, Settings tabs must all exist with their respective test tags
    composeTestRule.onNodeWithTag("nav_tab_home").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_ai").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_check").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_vault").assertIsDisplayed()
    composeTestRule.onNodeWithTag("nav_tab_profile").assertIsDisplayed()
  }

  @Test
  fun testOnboardingScreenRendersAllIllustrationsWithoutCrashing() {
    composeTestRule.setContent {
      MyApplicationTheme {
        com.example.ui.screens.OnboardingScreen(onFinishOnboarding = {})
      }
    }
    // Verify first step renders with title and illustration without crash
    composeTestRule.onNodeWithText("Smart AI").assertIsDisplayed()
    composeTestRule.onNodeWithText("AI writing assistant").assertIsDisplayed()

    // Step 2
    composeTestRule.onNodeWithTag("onboarding_primary_button").performClick()
    composeTestRule.onNodeWithText("Originality Checker").assertIsDisplayed()
    composeTestRule.onNodeWithText("Detect matching content").assertIsDisplayed()

    // Step 3
    composeTestRule.onNodeWithTag("onboarding_primary_button").performClick()
    composeTestRule.onNodeWithText("Secure Vault").assertIsDisplayed()
    composeTestRule.onNodeWithText("Secure storage").assertIsDisplayed()
  }
}
