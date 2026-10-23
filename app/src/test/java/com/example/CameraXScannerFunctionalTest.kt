package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import androidx.test.core.app.ApplicationProvider
import com.example.ocr.ScannedDocument
import com.example.ocr.ScannedPage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CameraXScannerFunctionalTest {

  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  private fun createSampleDocumentBitmap(width: Int = 400, height: Int = 600, titleText: String = "INVOICE #9821"): Bitmap {
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)

    val paint = Paint().apply {
      color = Color.BLACK
      textSize = 24f
      isAntiAlias = true
    }
    canvas.drawText(titleText, 30f, 60f, paint)
    paint.textSize = 16f
    canvas.drawText("Total Amount: $450.00 USD", 30f, 100f, paint)
    canvas.drawText("Due Date: November 15, 2026", 30f, 140f, paint)
    return bitmap
  }

  @Test
  fun testOrientationMatrixTransformForSensorRotation() {
    val rawBitmap = createSampleDocumentBitmap(width = 400, height = 600)
    assertEquals(400, rawBitmap.width)
    assertEquals(600, rawBitmap.height)

    // Simulate 90-degree sensor orientation (standard mobile portrait photo sensor)
    val matrix90 = Matrix().apply { postRotate(90f) }
    val rotated90 = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix90, true)
    assertEquals(600, rotated90.width)
    assertEquals(400, rotated90.height)

    // Simulate 270-degree sensor orientation
    val matrix270 = Matrix().apply { postRotate(270f) }
    val rotated270 = Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix270, true)
    assertEquals(600, rotated270.width)
    assertEquals(400, rotated270.height)
  }

  @Test
  fun testMultiPageBatchScanningFlow() {
    val page1Bitmap = createSampleDocumentBitmap(titleText = "Page 1 - Contract Agreement")
    val page2Bitmap = createSampleDocumentBitmap(titleText = "Page 2 - Terms & Conditions")

    val scannedPage1 = ScannedPage(
      originalBitmap = page1Bitmap,
      currentBitmap = page1Bitmap,
      extractedText = "Contract Agreement between Party A and Party B signed on October 2026.",
      isOcrCompleted = true
    )

    val scannedPage2 = ScannedPage(
      originalBitmap = page2Bitmap,
      currentBitmap = page2Bitmap,
      extractedText = "Terms & Conditions: All deliverables subject to on-device validation.",
      isOcrCompleted = true
    )

    val batchPages = listOf(scannedPage1, scannedPage2)

    val doc = ScannedDocument(
      title = "CameraX_MultiPage_Scan",
      pages = batchPages.toMutableList(),
      activePageIndex = 0
    )

    assertEquals(2, doc.totalPages)
    assertEquals("CameraX_MultiPage_Scan", doc.title)
    assertTrue(doc.combinedText().contains("Contract Agreement"))
    assertTrue(doc.combinedText().contains("Terms & Conditions"))
    assertTrue("Total word count should reflect all document pages", doc.totalWordCount() >= 20)
  }

  @Test
  fun testScannedPageTextAnalysis() {
    val page = ScannedPage(
      originalBitmap = createSampleDocumentBitmap(),
      extractedText = "Genzii Document Scanner accurately extracts text using on-device ML Kit."
    )

    val words = page.extractedText.split(Regex("\\s+")).filter { it.isNotBlank() }
    assertEquals(10, words.size)
    assertEquals(72, page.extractedText.length)
    assertTrue(page.extractedText.contains("ML Kit"))
  }
}
