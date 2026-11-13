package com.example

import com.example.data.GenziiRepository
import com.example.model.OriginalityReport
import com.example.model.PremiumFeature
import com.example.model.SourceMatch
import com.example.model.SubscriptionTier
import com.example.network.UrlContentExtractor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OriginalityCheckerPhase2Test {

  @Before
  fun setUp() {
    GenziiRepository.clearAllReports()
    GenziiRepository.upgradeTier(SubscriptionTier.FREE)
  }

  @Test
  fun testHtmlTextExtractionAndBoilerplateRemoval() {
    val sampleHtml = """
      <!DOCTYPE html>
      <html>
        <head>
          <title>Academic Research Paper: Quantum Entanglement and Information Theory</title>
          <script>console.log("tracking");</script>
          <style>.navbar { color: red; }</style>
        </head>
        <body>
          <nav>
            <a href="/">Home</a>
            <a href="/login">Login</a>
          </nav>
          <header>
            <h1>Site Header</h1>
          </header>
          <article>
            <h1>Quantum Information Advances</h1>
            <p>Quantum entanglement represents one of the most profound phenomena in modern theoretical physics &amp; quantum computing.</p>
            <p>Recent experiments conducted in high-energy physics laboratories have demonstrated non-local state correlation across microscopic superconducting qubits.</p>
            <p>These findings validate Einstein, Podolsky, and Rosen's foundational inquiries while providing practical architecture for quantum cryptographic protocols.</p>
          </article>
          <footer>
            <p>&copy; 2026 Academic Publishing Group. All rights reserved.</p>
          </footer>
        </body>
      </html>
    """.trimIndent()

    val extracted = UrlContentExtractor.extractReadableText(sampleHtml)

    // Verify scripts, navigation, and styles are stripped
    assertFalse(extracted.contains("console.log"))
    assertFalse(extracted.contains(".navbar"))
    assertFalse(extracted.contains("Site Header"))
    assertFalse(extracted.contains("All rights reserved"))

    // Verify article text is retained
    assertTrue(extracted.contains("Quantum entanglement represents one of the most profound phenomena"))
    assertTrue(extracted.contains("superconducting qubits"))
    assertTrue(extracted.contains("foundational inquiries"))

    // Verify HTML entity unescaping
    assertTrue(extracted.contains("theoretical physics & quantum computing"))
    assertFalse(extracted.contains("&amp;"))
  }

  @Test
  fun testHtmlEntityUnescaping() {
    val raw = "Research &amp; Development &quot;Verified&quot; &lt;Paper&gt; &#39;Authored&#39; &mdash; Chapter 1 &copy; 2026"
    val unescaped = UrlContentExtractor.unescapeHtml(raw)
    assertEquals("Research & Development \"Verified\" <Paper> 'Authored' — Chapter 1 © 2026", unescaped)
  }

  @Test
  fun testUrlContentExtractorRejectsSsrfAndPrivateNetworks() = runBlocking {
    // 192.168.1.1
    val resultPrivate = UrlContentExtractor.extractText("http://192.168.1.1/secret.html")
    assertTrue(resultPrivate.isFailure)
    val ex = resultPrivate.exceptionOrNull()
    assertTrue(ex is SecurityException)
    assertTrue(ex?.message?.contains("SSRF Security Violation") == true)

    // Localhost
    val resultLocalhost = UrlContentExtractor.extractText("http://localhost:8080/data")
    assertTrue(resultLocalhost.isFailure)
    assertTrue(resultLocalhost.exceptionOrNull() is SecurityException)

    // Non-HTTP protocol
    val resultFtp = UrlContentExtractor.extractText("ftp://files.example.com/file.txt")
    assertTrue(resultFtp.isFailure)
    assertTrue(resultFtp.exceptionOrNull() is SecurityException)
  }

  @Test
  fun testUrlCheckerFeatureAccessControlEnforced() = runBlocking {
    // Free tier user must be blocked from URL scanning
    GenziiRepository.upgradeTier(SubscriptionTier.FREE)
    assertFalse(GenziiRepository.isFeatureEntitled(PremiumFeature.URL_CHECKER))

    val freeScanResult = GenziiRepository.extractAndScanUrl("https://nature.com/articles/d41586-024-00000-0")
    assertTrue(freeScanResult.isFailure)
    val exception = freeScanResult.exceptionOrNull()
    assertTrue(exception is IllegalStateException)
    assertTrue(exception?.message?.contains("requires a Genzii Plus or Pro subscription") == true)

    // Plus and Pro tier users are entitled
    GenziiRepository.upgradeTier(SubscriptionTier.PLUS)
    assertTrue(GenziiRepository.isFeatureEntitled(PremiumFeature.URL_CHECKER))

    GenziiRepository.upgradeTier(SubscriptionTier.PRO)
    assertTrue(GenziiRepository.isFeatureEntitled(PremiumFeature.URL_CHECKER))
  }

  @Test
  fun testScanHistoryManagement() {
    assertEquals(0, GenziiRepository.reports.value.size)

    val report1 = OriginalityReport(
      id = "report-test-001",
      documentTitle = "[Web] Quantum Computing Foundations",
      originalPercentage = 95,
      similarityPercentage = 5,
      matchesCount = 1,
      sourcesCount = 1,
      wordsCount = 450,
      scanDate = "Nov 12, 2026",
      sources = listOf(
        SourceMatch(domain = "arxiv.org", url = "https://arxiv.org/abs/123", matchPercentage = 5, excerpt = "quantum algorithms")
      ),
      highlightedContent = "Authentic document with minor references.",
      aiAnalysis = "Syntactically robust with consistent academic terminology."
    )

    val report2 = OriginalityReport(
      id = "report-test-002",
      documentTitle = "Thesis_Chapter2.pdf",
      originalPercentage = 88,
      similarityPercentage = 12,
      matchesCount = 2,
      sourcesCount = 1,
      wordsCount = 890,
      scanDate = "Nov 12, 2026",
      sources = listOf(
        SourceMatch(domain = "nature.com", url = "https://nature.com/articles/456", matchPercentage = 12, excerpt = "cellular biology")
      ),
      highlightedContent = "Annotated excerpt.",
      aiAnalysis = "High academic coherence."
    )

    // Insert reports
    GenziiRepository.addTestReport(report1)
    GenziiRepository.addTestReport(report2)

    assertEquals(2, GenziiRepository.reports.value.size)
    assertNotNull(GenziiRepository.reports.value["report-test-001"])
    assertNotNull(GenziiRepository.reports.value["report-test-002"])

    // Delete single report
    GenziiRepository.deleteReport("report-test-001")
    assertEquals(1, GenziiRepository.reports.value.size)
    assertNull(GenziiRepository.reports.value["report-test-001"])
    assertNotNull(GenziiRepository.reports.value["report-test-002"])

    // Clear all reports
    GenziiRepository.clearAllReports()
    assertEquals(0, GenziiRepository.reports.value.size)
  }
}
