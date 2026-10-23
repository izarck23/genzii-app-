package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.GenziiRepository
import com.example.model.DocumentItem
import com.example.model.FileType
import com.example.util.FileUtils
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FileDisplayAndVaultTest {

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
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
}
