package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.OriginalityReport
import com.example.model.SourceMatch
import com.example.util.PdfExportManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.FileInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfExportManagerTest {

  @Test
  fun testGenerateDocumentPdf() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sampleText = "This is a confidential research document stored in Genzii Secure Vault.\n\nParagraph 2 contains academic notes and findings on optical character recognition and natural language processing."
    val result = PdfExportManager.generateDocumentPdf(context, "Research_Notes", sampleText)
    assertTrue("PDF generation must succeed", result.isSuccess)
    val file = result.getOrThrow()
    assertTrue("File must exist", file.exists())
    assertTrue("File size must be greater than 500 bytes", file.length() > 500)

    // Check PDF magic header %PDF
    val headerBytes = ByteArray(4)
    FileInputStream(file).use { it.read(headerBytes) }
    val header = String(headerBytes)
    assertEquals("%PDF", header)
  }

  @Test
  fun testGenerateOriginalityReportPdf() = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val report = OriginalityReport(
      id = "test-report-1",
      documentTitle = "Machine_Learning_Ethics",
      originalPercentage = 92,
      similarityPercentage = 8,
      matchesCount = 3,
      sourcesCount = 2,
      wordsCount = 1450,
      scanDate = "Oct 18, 2026",
      sources = listOf(
        SourceMatch(domain = "ieee.org", url = "https://ieee.org", matchPercentage = 5, excerpt = "Ethical frameworks in generative AI models"),
        SourceMatch(domain = "acm.org", url = "https://acm.org", matchPercentage = 3, excerpt = "Computational linguistic metrics")
      ),
      highlightedContent = "Ethical AI ensures fairness and accountability in algorithmic processing."
    )

    val result = PdfExportManager.generateOriginalityReportPdf(context, report)
    assertTrue("Originality report PDF generation must succeed", result.isSuccess)
    val file = result.getOrThrow()
    assertTrue("File must exist", file.exists())
    assertTrue("File size must be greater than 1000 bytes", file.length() > 1000)

    val headerBytes = ByteArray(4)
    FileInputStream(file).use { it.read(headerBytes) }
    val header = String(headerBytes)
    assertEquals("%PDF", header)
  }
}
