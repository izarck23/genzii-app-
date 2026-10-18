package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.example.ocr.ImageProcessingUtils
import com.example.ocr.OcrEngine
import com.example.ocr.ScanFilter
import com.example.ocr.ScannedDocument
import com.example.ocr.ScannedPage
import com.example.ocr.SearchablePdfGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OcrFunctionalTest {

  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  private fun createTestBitmap(width: Int = 300, height: Int = 400, bgColor: Int = Color.WHITE): Bitmap {
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    canvas.drawColor(bgColor)
    val paint = Paint().apply {
      color = Color.BLACK
      textSize = 20f
    }
    canvas.drawText("Test Document Text", 20f, 50f, paint)
    return bmp
  }

  @Test
  fun testScannedDocumentMultiPageManagement() {
    val page1 = ScannedPage(
      originalBitmap = createTestBitmap(),
      extractedText = "First page content for OCR analysis."
    )
    val page2 = ScannedPage(
      originalBitmap = createTestBitmap(),
      extractedText = "Second page content with additional details."
    )

    val doc = ScannedDocument(
      title = "Research_Paper",
      pages = mutableListOf(page1, page2),
      activePageIndex = 0
    )

    assertEquals(2, doc.totalPages)
    assertEquals(page1, doc.currentPage)

    val combined = doc.combinedText()
    assertTrue(combined.contains("Page 1"))
    assertTrue(combined.contains("First page content"))
    assertTrue(combined.contains("Page 2"))
    assertTrue(combined.contains("Second page content"))

    val words = doc.totalWordCount()
    assertTrue("Word count should be positive", words > 10)
  }

  @Test
  fun testImageRotation() {
    val original = createTestBitmap(width = 200, height = 300)
    assertEquals(200, original.width)
    assertEquals(300, original.height)

    val rotated90 = ImageProcessingUtils.rotateBitmap(original, 90)
    assertEquals(300, rotated90.width)
    assertEquals(200, rotated90.height)

    val rotated180 = ImageProcessingUtils.rotateBitmap(original, 180)
    assertEquals(200, rotated180.width)
    assertEquals(300, rotated180.height)

    val rotated0 = ImageProcessingUtils.rotateBitmap(original, 0)
    assertEquals(200, rotated0.width)
    assertEquals(300, rotated0.height)
  }

  @Test
  fun testImageCrop() {
    val original = createTestBitmap(width = 400, height = 600)
    val cropRect = RectF(0.1f, 0.1f, 0.9f, 0.9f)
    val cropped = ImageProcessingUtils.cropBitmap(original, cropRect)

    assertTrue("Cropped width should be smaller than original", cropped.width < original.width)
    assertTrue("Cropped height should be smaller than original", cropped.height < original.height)
    assertTrue("Cropped bitmap must not be empty", cropped.width > 50 && cropped.height > 50)
  }

  @Test
  fun testEnhancementFilters() {
    val original = createTestBitmap(width = 200, height = 200)

    val magic = ImageProcessingUtils.applyFilter(original, ScanFilter.MAGIC_COLOR)
    assertNotNull(magic)
    assertEquals(200, magic.width)

    val bw = ImageProcessingUtils.applyFilter(original, ScanFilter.B_AND_W)
    assertNotNull(bw)
    assertEquals(200, bw.width)

    val gray = ImageProcessingUtils.applyFilter(original, ScanFilter.GRAYSCALE)
    assertNotNull(gray)
    assertEquals(200, gray.width)

    val highContrast = ImageProcessingUtils.applyFilter(original, ScanFilter.HIGH_CONTRAST)
    assertNotNull(highContrast)
    assertEquals(200, highContrast.width)
  }

  @Test
  fun testDocumentEdgeDetectionHeuristic() {
    val bitmap = createTestBitmap(width = 300, height = 400)
    val detectedRect = ImageProcessingUtils.detectDocumentEdges(bitmap)

    assertNotNull(detectedRect)
    assertTrue(detectedRect.left >= 0f)
    assertTrue(detectedRect.top >= 0f)
    assertTrue(detectedRect.right <= 1f)
    assertTrue(detectedRect.bottom <= 1f)
    assertTrue(detectedRect.width() > 0.3f)
    assertTrue(detectedRect.height() > 0.3f)
  }

  @Test
  fun testInTextSearch() {
    val sampleText = "The quick brown fox jumps over the lazy dog. The fox is clever and quick."

    val foxMatches = OcrEngine.searchInText(sampleText, "fox")
    assertEquals(2, foxMatches.size)
    assertEquals("fox", sampleText.substring(foxMatches[0]))
    assertEquals("fox", sampleText.substring(foxMatches[1]))

    val quickMatches = OcrEngine.searchInText(sampleText, "quick")
    assertEquals(2, quickMatches.size)

    val notFound = OcrEngine.searchInText(sampleText, "elephant")
    assertTrue(notFound.isEmpty())

    val emptyQuery = OcrEngine.searchInText(sampleText, "")
    assertTrue(emptyQuery.isEmpty())
  }

  @Test
  fun testSearchablePdfGeneration() {
    runBlocking {
      val page1 = ScannedPage(
        originalBitmap = createTestBitmap(300, 400),
        extractedText = "Chapter 1: Autonomous Systems and Optical Verification."
      )
      val page2 = ScannedPage(
        originalBitmap = createTestBitmap(300, 400),
        extractedText = "Chapter 2: Machine Learning On-Device Inference benchmarks."
      )

      val doc = ScannedDocument(
        title = "Robolectric_Test_Scan",
        pages = mutableListOf(page1, page2)
      )

      val targetFile = File(context.cacheDir, "test_output_${System.currentTimeMillis()}.pdf")
      val result = SearchablePdfGenerator.generateSearchablePdf(context, doc, targetFile)
      if (result.isFailure) {
        val ex = result.exceptionOrNull()
        org.junit.Assert.fail("Searchable PDF generation failed with exception: ${ex?.javaClass?.name}: ${ex?.message}")
      }
      val file = result.getOrThrow()
      assertTrue("Generated PDF file should exist", file.exists())
      assertTrue("PDF file size should be non-zero", file.length() > 500)

      // Cleanup
      file.delete()
    }
  }

  @Test
  fun testInvalidBitmapErrorHandling() {
    runBlocking {
      val tinyBmp = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
      val result = OcrEngine.extractTextFromBitmap(tinyBmp)
      assertTrue("Tiny bitmap should fail gracefully without crashing", result.isFailure)
    }
  }
}
