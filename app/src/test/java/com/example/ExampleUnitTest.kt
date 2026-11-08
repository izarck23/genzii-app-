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
  fun testOriginalityScanRequiresApiKey() = kotlinx.coroutines.runBlocking {
    GenziiRepository.updateAiSettings(customKey = "", temperature = 0.5f, useStreaming = true)
    val failureResult = GenziiRepository.scanOriginality(
      title = "Attention_Paper.pdf",
      text = "Modern attention mechanisms improve synthesis fidelity in transformer backbones."
    )
    assertTrue("Scan must return failure when API key is not configured, refusing to return fake data", failureResult.isFailure)
    assertTrue(failureResult.exceptionOrNull()?.message?.contains("API key", ignoreCase = true) == true)
  }

  @Test
  fun testOriginalityScanRejectsBlankText() = kotlinx.coroutines.runBlocking {
    val blankResult = GenziiRepository.scanOriginality(
      title = "Empty.txt",
      text = "   "
    )
    assertTrue(blankResult.isFailure)
    assertTrue(blankResult.exceptionOrNull() is IllegalArgumentException)
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

  @Test
  fun testGeminiApiKeyDetection() {
    assertFalse(com.example.network.GeminiService.isKeyValid(""))
    assertFalse(com.example.network.GeminiService.isKeyValid("short"))
    assertFalse(com.example.network.GeminiService.isKeyValid("MY_GEMINI_API_KEY"))
    assertFalse(com.example.network.GeminiService.isKeyValid("PLACEHOLDER_KEY"))
    assertTrue(com.example.network.GeminiService.isKeyValid("AIzaSyValidGeminiKey123456789"))
    assertTrue(com.example.network.GeminiService.isApiKeyConfigured("AIzaSyValidGeminiKey123456789"))
  }

  @Test
  fun testChatRetryLogic() {
    GenziiRepository.clearChatHistory()
    GenziiRepository.sendChatMessage("Explain quantum computing")
    val initialCount = GenziiRepository.chatMessages.value.size
    assertTrue(initialCount >= 2)

    GenziiRepository.retryLastMessage()
    val retryMessages = GenziiRepository.chatMessages.value
    assertTrue(retryMessages.isNotEmpty())
    val lastUser = retryMessages.lastOrNull { it.isUser }
    assertEquals("Explain quantum computing", lastUser?.text)
  }

  @Test
  fun testLegalDocTypes() {
    val types = com.example.ui.screens.LegalDocType.values()
    assertEquals(2, types.size)
    assertTrue(types.contains(com.example.ui.screens.LegalDocType.PRIVACY_POLICY))
    assertTrue(types.contains(com.example.ui.screens.LegalDocType.TERMS_OF_SERVICE))
  }
}
