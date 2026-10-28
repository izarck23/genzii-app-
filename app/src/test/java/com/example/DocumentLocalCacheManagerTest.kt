package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.DocumentLocalCacheManager
import com.example.data.GenziiRepository
import com.example.model.DocumentItem
import com.example.model.FileType
import com.example.model.UserDocumentMetadata
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocumentLocalCacheManagerTest {

  private lateinit var context: Context
  private lateinit var cacheManager: DocumentLocalCacheManager

  @Before
  fun setUp() = runBlocking {
    context = ApplicationProvider.getApplicationContext()
    cacheManager = DocumentLocalCacheManager.getInstance(context)
    cacheManager.clearAllCache()
    GenziiRepository.init(context)
  }

  @Test
  fun testCacheAndRetrieveDocumentOffline() = runBlocking {
    val doc = DocumentItem(
      id = "doc-test-1",
      name = "Deep_Learning_Lecture_Notes.pdf",
      sizeFormatted = "2.4 MB",
      type = FileType.PDF,
      folder = "Lecture Notes",
      updatedAt = "Just now",
      content = "Extracted optical character text from ML Kit text recognition scanner.",
      isStarred = true,
      originalityScore = 94
    )

    // Save to cache
    cacheManager.cacheDocument(doc)

    // Retrieve from offline cache
    val cachedDocs = cacheManager.getCachedDocuments()
    assertEquals(1, cachedDocs.size)

    val retrieved = cachedDocs.first()
    assertEquals("doc-test-1", retrieved.id)
    assertEquals("Deep_Learning_Lecture_Notes.pdf", retrieved.name)
    assertEquals("2.4 MB", retrieved.sizeFormatted)
    assertEquals(FileType.PDF, retrieved.type)
    assertEquals("Lecture Notes", retrieved.folder)
    assertEquals("Extracted optical character text from ML Kit text recognition scanner.", retrieved.content)
    assertTrue(retrieved.isStarred)
    assertEquals(94, retrieved.originalityScore)
  }

  @Test
  fun testMergeFirestoreMetadataIntoOfflineCache() = runBlocking {
    // 1. Initial ML Kit scan saved locally
    val mlKitDoc = DocumentItem(
      id = "doc-firestore-1",
      name = "Research_Paper.pdf",
      sizeFormatted = "1.8 MB",
      type = FileType.PDF,
      folder = "Research Papers",
      updatedAt = "Just now",
      content = "Detailed abstract and methodology scanned with CameraX OCR."
    )
    cacheManager.cacheDocument(mlKitDoc)

    // 2. Incoming metadata from Firestore
    val firestoreMeta = listOf(
      UserDocumentMetadata(
        id = "doc-firestore-1",
        userId = "user-123",
        name = "Research_Paper_Renamed.pdf",
        sizeFormatted = "1.8 MB",
        fileType = "PDF",
        folder = "Research Papers",
        updatedAt = System.currentTimeMillis(),
        originalityScore = 98,
        isStarred = true
      ),
      UserDocumentMetadata(
        id = "doc-firestore-2",
        userId = "user-123",
        name = "Syllabus.docx",
        sizeFormatted = "450 KB",
        fileType = "DOCX",
        folder = "Assignments",
        updatedAt = System.currentTimeMillis()
      )
    )

    cacheManager.cacheFirestoreMetadata(firestoreMeta)

    val cached = cacheManager.getCachedDocuments()
    assertEquals(2, cached.size)

    // Verify existing document updated metadata while preserving local OCR content
    val updated = cached.first { it.id == "doc-firestore-1" }
    assertEquals("Research_Paper_Renamed.pdf", updated.name)
    assertEquals(98, updated.originalityScore)
    assertTrue(updated.isStarred)
    assertEquals("Detailed abstract and methodology scanned with CameraX OCR.", updated.content)

    // Verify new Firestore document cached for offline browsing
    val newDoc = cached.first { it.id == "doc-firestore-2" }
    assertEquals("Syllabus.docx", newDoc.name)
    assertEquals(FileType.DOCX, newDoc.type)
  }

  @Test
  fun testDeleteCachedDocument() = runBlocking {
    val doc1 = DocumentItem(id = "d1", name = "A.pdf", sizeFormatted = "1 MB", type = FileType.PDF, folder = "Vault", updatedAt = "Today")
    val doc2 = DocumentItem(id = "d2", name = "B.pdf", sizeFormatted = "2 MB", type = FileType.PDF, folder = "Vault", updatedAt = "Today")

    cacheManager.cacheDocuments(listOf(doc1, doc2))
    assertEquals(2, cacheManager.getCachedDocuments().size)

    cacheManager.deleteCachedDocument("d1")
    val remaining = cacheManager.getCachedDocuments()
    assertEquals(1, remaining.size)
    assertEquals("d2", remaining.first().id)
  }

  @Test
  fun testGenziiRepositoryRestoresCachedDocumentsOnInit() = runBlocking {
    // Populate cache with a document
    val doc = DocumentItem(
      id = "doc-offline-restore",
      name = "Restored_Offline_Document.pdf",
      sizeFormatted = "500 KB",
      type = FileType.PDF,
      folder = "Vault",
      updatedAt = "Just now",
      content = "Offline restored document content."
    )
    cacheManager.cacheDocument(doc)

    // Initialize repository from context
    GenziiRepository.init(context)

    // Give IO coroutine a brief moment to restore
    kotlinx.coroutines.delay(100)

    val repoDocs = GenziiRepository.documents.value
    assertTrue(repoDocs.any { it.id == "doc-offline-restore" })
  }
}
