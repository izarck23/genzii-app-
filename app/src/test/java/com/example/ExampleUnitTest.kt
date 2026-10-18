package com.example

import com.example.data.GenziiRepository
import com.example.model.FileType
import com.example.model.SubscriptionTier
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun testVaultAddAndDeleteDocument() {
    val initialCount = GenziiRepository.documents.value.size
    val newDoc = GenziiRepository.addDocument(
      name = "Test_Evaluation.pdf",
      content = "Confidential test evaluation draft",
      folder = "Personal",
      type = FileType.PDF
    )
    assertEquals(initialCount + 1, GenziiRepository.documents.value.size)
    assertTrue(GenziiRepository.documents.value.any { it.id == newDoc.id })

    GenziiRepository.deleteDocument(newDoc.id)
    assertEquals(initialCount, GenziiRepository.documents.value.size)
  }

  @Test
  fun testOriginalityScan() = kotlinx.coroutines.runBlocking {
    val report = GenziiRepository.scanOriginality(
      title = "Attention_Paper.pdf",
      text = "Modern attention mechanisms improve synthesis fidelity in transformer backbones."
    )
    assertNotNull(report)
    assertTrue(report.originalPercentage in 50..100)
    assertEquals(100 - report.originalPercentage, report.similarityPercentage)
    assertTrue(report.sources.isNotEmpty())
  }

  @Test
  fun testUrlScanner() {
    val safeResult = GenziiRepository.scanUrl("https://nature.com/articles/science")
    assertTrue(safeResult.isSafe)
    assertTrue(safeResult.sslValid)

    val phishingResult = GenziiRepository.scanUrl("http://phishing-site-test.xyz")
    assertFalse(phishingResult.isSafe)
  }

  @Test
  fun testSubscriptionUpgrade() {
    GenziiRepository.upgradeTier(SubscriptionTier.PRO)
    assertEquals(SubscriptionTier.PRO, GenziiRepository.userProfile.value.tier)
    assertEquals(100.0, GenziiRepository.userProfile.value.totalStorageGb, 0.01)
  }

  @Test
  fun testFileUtilsFormat() {
    assertEquals("1.2 MB", com.example.util.FileUtils.formatFileSize(0))
    val formatted = com.example.util.FileUtils.formatFileSize(1048576)
    assertEquals("1.0 MB", formatted)
    val formattedKb = com.example.util.FileUtils.formatFileSize(2048)
    assertEquals("2 KB", formattedKb)
  }

  @Test
  fun testCloudAiConsentAndSettings() {
    assertFalse(GenziiRepository.userProfile.value.isCloudAiConsentGranted)
    GenziiRepository.setCloudAiConsent(true)
    assertTrue(GenziiRepository.userProfile.value.isCloudAiConsentGranted)

    GenziiRepository.updateAiSettings(
      customKey = "AIzaSyTestKey1234567890",
      temperature = 0.7f,
      useStreaming = true
    )
    assertEquals("AIzaSyTestKey1234567890", GenziiRepository.userProfile.value.customApiKey)
    assertEquals(0.7f, GenziiRepository.userProfile.value.temperature, 0.01f)
    assertTrue(GenziiRepository.userProfile.value.useStreaming)
  }

  @Test
  fun testChatStreamingAndCancel() {
    GenziiRepository.clearChatHistory()
    assertEquals(1, GenziiRepository.chatMessages.value.size)

    GenziiRepository.sendChatMessage("Summarize my document", "Paper.pdf", "Introduction: Neural attention mechanisms...")
    val messages = GenziiRepository.chatMessages.value
    assertTrue(messages.size >= 2)
    val userMsg = messages.first { it.isUser }
    assertEquals("Summarize my document", userMsg.text)
    assertEquals("Paper.pdf", userMsg.attachedFileName)

    // Test cancellation
    GenziiRepository.cancelActiveGeneration()
    assertFalse(GenziiRepository.isGenerating.value)
  }
}
