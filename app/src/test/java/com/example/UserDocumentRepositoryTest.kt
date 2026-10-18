package com.example

import com.example.data.UserDocumentRepository
import com.example.model.DocumentItem
import com.example.model.FileType
import com.example.model.UserDocumentMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserDocumentRepositoryTest {

  @Test
  fun testUserDocumentMetadataSerialization() {
    val metadata = UserDocumentMetadata(
      id = "doc-123",
      userId = "user-456",
      name = "Research_Thesis.docx",
      sizeFormatted = "2.4 MB",
      fileType = "DOCX",
      folder = "Academic",
      updatedAt = 1700000000000L,
      originalityScore = 94,
      isEncrypted = true,
      isStarred = true,
      wordCount = 1250,
      tags = listOf("research", "draft")
    )

    val map = metadata.toMap()
    assertEquals("doc-123", map["id"])
    assertEquals("user-456", map["userId"])
    assertEquals("Research_Thesis.docx", map["name"])
    assertEquals("2.4 MB", map["sizeFormatted"])
    assertEquals("DOCX", map["fileType"])
    assertEquals("Academic", map["folder"])
    assertEquals(1700000000000L, map["updatedAt"])
    assertEquals(94, map["originalityScore"])
    assertEquals(true, map["isEncrypted"])
    assertEquals(true, map["isStarred"])
    assertEquals(1250, map["wordCount"])

    val restored = UserDocumentMetadata.fromMap("doc-123", map)
    assertEquals(metadata.id, restored.id)
    assertEquals(metadata.userId, restored.userId)
    assertEquals(metadata.name, restored.name)
    assertEquals(metadata.sizeFormatted, restored.sizeFormatted)
    assertEquals(metadata.fileType, restored.fileType)
    assertEquals(metadata.folder, restored.folder)
    assertEquals(metadata.updatedAt, restored.updatedAt)
    assertEquals(metadata.originalityScore, restored.originalityScore)
    assertEquals(metadata.isEncrypted, restored.isEncrypted)
    assertEquals(metadata.isStarred, restored.isStarred)
    assertEquals(metadata.wordCount, restored.wordCount)
    assertEquals(metadata.tags, restored.tags)
  }

  @Test
  fun testConstantsAndCollections() {
    assertEquals("users", UserDocumentRepository.COLLECTION_USERS)
    assertEquals("documents", UserDocumentRepository.COLLECTION_DOCUMENTS)
  }

  @Test
  fun testDefaultMetadataValues() {
    val defaultMeta = UserDocumentMetadata(id = "default-1")
    assertEquals("PDF", defaultMeta.fileType)
    assertEquals("Vault", defaultMeta.folder)
    assertTrue(defaultMeta.isEncrypted)
    assertFalse(defaultMeta.isStarred)
  }
}
