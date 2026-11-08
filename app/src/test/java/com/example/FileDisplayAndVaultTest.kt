package com.example

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ApplicationProvider
import com.example.data.GenziiRepository
import com.example.model.DocumentItem
import com.example.model.FileType
import com.example.ui.screens.VaultScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.FileUtils
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
class FileDisplayAndVaultTest {

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
  fun testDocumentItemWithRealFileAttributes() {
    val dummyFile = File(context.filesDir, "test_doc.pdf").apply {
      writeText("%PDF-1.4 Mock real PDF file content")
    }

    val doc = DocumentItem(
      id = "doc-test-1",
      name = "Research_Report.pdf",
      sizeFormatted = "18 KB",
      type = FileType.PDF,
      folder = "Vault",
      updatedAt = "Today",
      content = "Mock extracted text from PDF",
      isStarred = true,
      localFilePath = dummyFile.absolutePath,
      uriString = "content://com.android.providers.media.documents/document/123",
      pageCount = 3
    )

    assertEquals("Research_Report.pdf", doc.name)
    assertEquals(FileType.PDF, doc.type)
    assertNotNull(doc.localFilePath)
    assertEquals(dummyFile.absolutePath, doc.localFilePath)
    assertTrue(File(doc.localFilePath!!).exists())
    assertEquals(3, doc.pageCount)
    assertEquals("content://com.android.providers.media.documents/document/123", doc.uriString)
  }

  @Test
  fun testAddImportedDocumentPersistsRealFileAttributes() {
    val dummyVaultFile = File(context.filesDir, "vault_documents/sample_img.jpg").apply {
      parentFile?.mkdirs()
      writeBytes(ByteArray(1024) { 0x42 })
    }

    val addedDoc = GenziiRepository.addImportedDocument(
      name = "sample_img.jpg",
      sizeFormatted = "1.0 KB",
      sizeBytes = dummyVaultFile.length(),
      fileType = FileType.IMAGE,
      content = "Extracted OCR text from sample image",
      folder = "Vault",
      localFilePath = dummyVaultFile.absolutePath,
      uriString = "content://media/external/images/media/42",
      pageCount = 1
    )

    assertNotNull(addedDoc)
    assertEquals("sample_img.jpg", addedDoc.name)
    assertEquals(FileType.IMAGE, addedDoc.type)
    assertEquals(dummyVaultFile.absolutePath, addedDoc.localFilePath)
    assertEquals("content://media/external/images/media/42", addedDoc.uriString)
    assertEquals(1, addedDoc.pageCount)

    val found = GenziiRepository.documents.value.find { it.id == addedDoc.id }
    assertNotNull(found)
    assertEquals(dummyVaultFile.absolutePath, found?.localFilePath)
    assertEquals(1, found?.pageCount)
  }

  @Test
  fun testTextAndDocxFileTypeHandling() {
    val textFile = File(context.filesDir, "notes.txt").apply {
      writeText("Real user notes written inside Genzii app.")
    }

    val addedDoc = GenziiRepository.addImportedDocument(
      name = "notes.txt",
      sizeFormatted = "43 B",
      sizeBytes = textFile.length(),
      fileType = FileType.TXT,
      content = textFile.readText(),
      folder = "Personal",
      localFilePath = textFile.absolutePath,
      pageCount = 1
    )

    assertEquals(FileType.TXT, addedDoc.type)
    assertEquals(textFile.absolutePath, addedDoc.localFilePath)
    assertTrue(addedDoc.content.contains("Real user notes"))
  }

  @Test
  fun testDocumentSortingAlphabeticalAndDate() {
    val now = System.currentTimeMillis()
    val docOld = GenziiRepository.addDocument(
      name = "Zeta_Paper.pdf",
      content = "Zeta content",
      folder = "Vault",
      type = FileType.PDF,
      createdAt = now - 50000
    )
    val docMid = GenziiRepository.addDocument(
      name = "Alpha_Study.pdf",
      content = "Alpha content",
      folder = "Vault",
      type = FileType.PDF,
      createdAt = now - 20000
    )
    val docNew = GenziiRepository.addDocument(
      name = "Middle_Notes.txt",
      content = "Middle content",
      folder = "Vault",
      type = FileType.TXT,
      createdAt = now
    )

    val docs = listOf(docOld, docMid, docNew)

    // 1. Date Created (Newest First)
    val newestFirst = docs.sortedByDescending { it.createdAt }
    assertEquals("Middle_Notes.txt", newestFirst[0].name)
    assertEquals("Alpha_Study.pdf", newestFirst[1].name)
    assertEquals("Zeta_Paper.pdf", newestFirst[2].name)

    // 2. Date Created (Oldest First)
    val oldestFirst = docs.sortedBy { it.createdAt }
    assertEquals("Zeta_Paper.pdf", oldestFirst[0].name)
    assertEquals("Alpha_Study.pdf", oldestFirst[1].name)
    assertEquals("Middle_Notes.txt", oldestFirst[2].name)

    // 3. Alphabetical (A-Z)
    val az = docs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    assertEquals("Alpha_Study.pdf", az[0].name)
    assertEquals("Middle_Notes.txt", az[1].name)
    assertEquals("Zeta_Paper.pdf", az[2].name)

    // 4. Alphabetical (Z-A)
    val za = docs.sortedWith(compareByDescending(String.CASE_INSENSITIVE_ORDER) { it.name })
    assertEquals("Zeta_Paper.pdf", za[0].name)
    assertEquals("Middle_Notes.txt", za[1].name)
    assertEquals("Alpha_Study.pdf", za[2].name)
  }

  @Test
  fun testSortOrderPersistence() {
    GenziiRepository.init(context)

    GenziiRepository.saveSortOrder(com.example.model.DocumentSortOrder.NAME_ZA.id)
    assertEquals("name_desc", GenziiRepository.getSavedSortOrder())
    assertEquals(com.example.model.DocumentSortOrder.NAME_ZA, com.example.model.DocumentSortOrder.fromId("name_desc"))

    GenziiRepository.saveSortOrder(com.example.model.DocumentSortOrder.DATE_OLDEST.id)
    assertEquals("date_asc", GenziiRepository.getSavedSortOrder())
    assertEquals(com.example.model.DocumentSortOrder.DATE_OLDEST, com.example.model.DocumentSortOrder.fromId("date_asc"))
  }

  @Test
  fun testBatchDocumentStarAndDeletion() {
    val doc1 = GenziiRepository.addDocument(name = "Batch1.pdf", content = "Content 1", folder = "Vault", type = FileType.PDF)
    val doc2 = GenziiRepository.addDocument(name = "Batch2.pdf", content = "Content 2", folder = "Vault", type = FileType.PDF)

    val selected = setOf(doc1.id, doc2.id)
    assertEquals(2, selected.size)

    selected.forEach { id -> GenziiRepository.toggleStar(id) }
    assertTrue(GenziiRepository.documents.value.find { it.id == doc1.id }?.isStarred == true)
    assertTrue(GenziiRepository.documents.value.find { it.id == doc2.id }?.isStarred == true)

    selected.forEach { id -> GenziiRepository.deleteDocument(id) }
    assertNull(GenziiRepository.documents.value.find { it.id == doc1.id })
    assertNull(GenziiRepository.documents.value.find { it.id == doc2.id })
  }

  @Test
  fun testVaultScreenLongPressSelectionAndSortingUI() {
    val docAlpha = GenziiRepository.addDocument(name = "Alpha.pdf", content = "Alpha content", folder = "Personal", type = FileType.PDF)
    val docBeta = GenziiRepository.addDocument(name = "Beta.pdf", content = "Beta content", folder = "Personal", type = FileType.PDF)

    var openedDocId: String? = null

    composeTestRule.setContent {
      MyApplicationTheme(darkTheme = false) {
        VaultScreen(
          onOpenDocument = { openedDocId = it },
          onOpenFolder = {},
          onNotificationsClick = {},
          onProfileClick = {}
        )
      }
    }

    // 1. Verify documents are displayed
    composeTestRule.onNodeWithTag("doc_row_${docAlpha.id}").assertExists()
    composeTestRule.onNodeWithTag("doc_row_${docBeta.id}").assertExists()

    // 2. Normal click opens the document
    composeTestRule.onNodeWithTag("doc_row_${docAlpha.id}").performClick()
    assertEquals(docAlpha.id, openedDocId)

    // 3. Long press on Alpha activates selection mode
    composeTestRule.onNodeWithTag("doc_row_${docAlpha.id}").performTouchInput { longClick() }

    // 4. Verify selection mode is active and visual indicators are displayed
    composeTestRule.onNodeWithText("1 selected").assertExists()
    composeTestRule.onNodeWithTag("doc_checkbox_${docAlpha.id}").assertExists()
    composeTestRule.onNodeWithTag("doc_checkbox_${docBeta.id}").assertExists()
    composeTestRule.onNodeWithTag("doc_selected_badge_${docAlpha.id}", useUnmergedTree = true).assertExists()

    // 5. Select Beta as well
    composeTestRule.onNodeWithTag("doc_row_${docBeta.id}").performClick()
    composeTestRule.onNodeWithText("2 selected").assertExists()
    composeTestRule.onNodeWithTag("doc_selected_badge_${docBeta.id}", useUnmergedTree = true).assertExists()

    // 6. Deselect Beta individually
    composeTestRule.onNodeWithTag("doc_row_${docBeta.id}").performClick()
    composeTestRule.onNodeWithText("1 selected").assertExists()

    // 7. Test Select All
    composeTestRule.onNodeWithTag("toggle_select_all_button").performClick()
    composeTestRule.onNodeWithText("2 selected").assertExists()
    composeTestRule.onNodeWithTag("move_selected_button").assertExists()
    composeTestRule.onNodeWithTag("delete_selected_button").assertExists()

    // 8. Exit selection mode via Close button
    composeTestRule.onNodeWithTag("exit_selection_button").performClick()
    composeTestRule.onNodeWithText("2 selected").assertDoesNotExist()

    // 9. Verify toggle selection mode button enters selection mode directly
    composeTestRule.onNodeWithTag("toggle_selection_mode_button").performClick()
    composeTestRule.onNodeWithText("0 selected").assertExists()
    composeTestRule.onNodeWithTag("exit_selection_button").performClick()

    // 10. Verify sorting control exists and can be clicked
    composeTestRule.onNodeWithTag("sort_button").assertExists().performClick()
  }
}
