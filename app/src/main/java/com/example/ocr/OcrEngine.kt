package com.example.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume

object OcrEngine {
  private const val TAG = "OcrEngine"

  private val recognizer by lazy {
    TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
  }

  /**
   * Performs optical character recognition on a Bitmap using on-device ML Kit.
   * Completely offline and local to the device.
   */
  suspend fun extractTextFromBitmap(bitmap: Bitmap): Result<String> = withContext(Dispatchers.Default) {
    try {
      if (bitmap.isRecycled) {
        return@withContext Result.failure(IllegalStateException("Image buffer has been released."))
      }

      // Check for extremely small or blank image
      if (bitmap.width < 20 || bitmap.height < 20) {
        return@withContext Result.failure(IllegalArgumentException("Image dimensions are too small for OCR scanning."))
      }

      val inputImage = InputImage.fromBitmap(bitmap, 0)

      val text = suspendCancellableCoroutine<String> { continuation ->
        recognizer.process(inputImage)
          .addOnSuccessListener { visionText ->
            val resultText = visionText.text
            if (continuation.isActive) {
              continuation.resume(resultText)
            }
          }
          .addOnFailureListener { exception ->
            val msg = exception.message ?: ""
            if (msg.contains("waiting for", ignoreCase = true) || msg.contains("download", ignoreCase = true)) {
              Log.w(TAG, "ML Kit OCR model initializing/downloading: $msg")
              if (continuation.isActive) {
                continuation.resume("Text recognition module is downloading. Please tap Re-run OCR in a few moments.")
              }
            } else {
              Log.e(TAG, "ML Kit OCR failed: $msg", exception)
              if (continuation.isActive) {
                continuation.resume("")
              }
            }
          }
      }

      if (text.isBlank()) {
        Result.success("No readable text detected in this page. Try adjusting lighting, contrast, or crop.")
      } else {
        Result.success(text.trim())
      }
    } catch (e: Exception) {
      Log.e(TAG, "extractTextFromBitmap error: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Safely decodes a Bitmap from an image or document Uri (JPG, JPEG, PNG, WebP).
   * Scales down gigantic images to prevent OutOfMemoryError.
   */
  suspend fun decodeBitmapFromUri(context: Context, uri: Uri): Result<Bitmap> = withContext(Dispatchers.IO) {
    try {
      // Check if it's a PDF
      val mimeType = context.contentResolver.getType(uri) ?: ""
      val uriString = uri.toString().lowercase()
      if (mimeType.contains("pdf") || uriString.endsWith(".pdf")) {
        val pdfBmp = renderFirstPageOfPdf(context, uri)
        if (pdfBmp != null) {
          return@withContext Result.success(pdfBmp)
        }
      }

      // First pass: decode bounds to check dimensions
      val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
      }

      if (options.outWidth <= 0 || options.outHeight <= 0) {
        return@withContext Result.failure(IllegalArgumentException("Unsupported or unreadable file format."))
      }

      // Calculate sample size for max dimension of 2400px
      val maxDimension = 2400
      var sampleSize = 1
      while (options.outWidth / sampleSize > maxDimension || options.outHeight / sampleSize > maxDimension) {
        sampleSize *= 2
      }

      val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
      }

      val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, decodeOptions)
      } ?: return@withContext Result.failure(IllegalStateException("Unable to read image data from selected source."))

      Result.success(bitmap)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to decode bitmap from uri: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Renders the first page of a PDF document to a high-res Bitmap for OCR scanning.
   */
  private fun renderFirstPageOfPdf(context: Context, uri: Uri): Bitmap? {
    var tempFile: File? = null
    var pfd: ParcelFileDescriptor? = null
    var renderer: PdfRenderer? = null
    try {
      tempFile = File(context.cacheDir, "temp_pdf_render_${System.currentTimeMillis()}.pdf")
      context.contentResolver.openInputStream(uri)?.use { input ->
        tempFile.outputStream().use { output ->
          input.copyTo(output)
        }
      }
      pfd = ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY)
      renderer = PdfRenderer(pfd)
      if (renderer.pageCount > 0) {
        val page = renderer.openPage(0)
        val targetWidth = (page.width * 2f).toInt().coerceIn(600, 2400)
        val targetHeight = (page.height * 2f).toInt().coerceIn(800, 3200)
        val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        return bitmap
      }
    } catch (e: Throwable) {
      Log.w(TAG, "PDF page rendering failed: ${e.message}")
    } finally {
      try { renderer?.close() } catch (_: Throwable) {}
      try { pfd?.close() } catch (_: Throwable) {}
      try { tempFile?.delete() } catch (_: Throwable) {}
    }
    return null
  }

  /**
   * Searches for queries in recognized text and returns the character ranges.
   */
  fun searchInText(text: String, query: String): List<IntRange> {
    if (query.isBlank() || text.isBlank()) return emptyList()
    val matches = mutableListOf<IntRange>()
    var startIndex = 0
    val lowerText = text.lowercase()
    val lowerQuery = query.lowercase()

    while (startIndex < lowerText.length) {
      val found = lowerText.indexOf(lowerQuery, startIndex)
      if (found != -1) {
        matches.add(found until (found + lowerQuery.length))
        startIndex = found + lowerQuery.length
      } else {
        break
      }
    }
    return matches
  }
}
