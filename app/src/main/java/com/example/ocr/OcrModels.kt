package com.example.ocr

import android.graphics.Bitmap
import android.graphics.RectF
import java.util.UUID

/**
 * Filter mode applied to scanned document pages (CamScanner style).
 */
enum class ScanFilter(val label: String, val description: String) {
  ORIGINAL("Original", "Original color capture"),
  MAGIC_COLOR("Magic Color", "Vibrant colors, sharpened text and bright paper"),
  B_AND_W("B&W", "Clean high-contrast monochrome print"),
  GRAYSCALE("Grayscale", "Smooth academic grayscale"),
  HIGH_CONTRAST("High Contrast", "Punchy dark text on bright background")
}

/**
 * Represents an individual scanned page within a multi-page document.
 */
data class ScannedPage(
  val id: String = UUID.randomUUID().toString(),
  val originalBitmap: Bitmap,
  var currentBitmap: Bitmap = originalBitmap,
  var rotationDegrees: Int = 0,
  var filterMode: ScanFilter = ScanFilter.ORIGINAL,
  var cropRectRatio: RectF = RectF(0f, 0f, 1f, 1f),
  var extractedText: String = "",
  var isOcrCompleted: Boolean = false,
  var isOcrRunning: Boolean = false,
  var errorMessage: String? = null
)

/**
 * Represents a multi-page scanned document with metadata.
 */
data class ScannedDocument(
  val id: String = "doc-ocr-${System.currentTimeMillis() % 100000}",
  var title: String = "Scan_${System.currentTimeMillis() % 10000}",
  val pages: MutableList<ScannedPage> = mutableListOf(),
  var activePageIndex: Int = 0,
  var createdAt: Long = System.currentTimeMillis()
) {
  val currentPage: ScannedPage?
    get() = pages.getOrNull(activePageIndex)

  val totalPages: Int
    get() = pages.size

  fun combinedText(): String {
    if (pages.isEmpty()) return ""
    return pages.mapIndexed { index, page ->
      val pageHeader = if (pages.size > 1) "--- Page ${index + 1} ---\n" else ""
      val content = page.extractedText.ifBlank { "[No text recognized on this page]" }
      "$pageHeader$content"
    }.joinToString("\n\n")
  }

  fun totalWordCount(): Int {
    val text = combinedText()
    return text.split(Regex("\\s+")).filter { it.isNotBlank() && !it.startsWith("---") }.size
  }
}
