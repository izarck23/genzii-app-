package com.example.ocr

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

object SearchablePdfGenerator {
  private const val TAG = "SearchablePdfGenerator"

  // Standard A4 dimensions in PostScript points (72 points per inch)
  const val PAGE_WIDTH = 595
  const val PAGE_HEIGHT = 842

  /**
   * Generates a multi-page PDF containing high-resolution scanned page images
   * and an embedded searchable text layer.
   */
  suspend fun generateSearchablePdf(
    context: Context,
    document: ScannedDocument,
    targetFile: File? = null
  ): Result<File> = withContext(Dispatchers.IO) {
    val pdfDoc = PdfDocument()
    val outputFile = targetFile ?: File(
      context.cacheDir,
      "${document.title.replace(Regex("[^a-zA-Z0-9._-]"), "_")}.pdf"
    )

    try {
      val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 9.5f
      }

      val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 11f
        isFakeBoldText = true
      }

      val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        strokeWidth = 1f
      }

      for ((index, page) in document.pages.withIndex()) {
        val pageNumber = index + 1
        val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        val pdfPage = pdfDoc.startPage(pageInfo)
        val canvas = pdfPage.canvas

        // Fill background white
        canvas.drawColor(Color.WHITE)

        val bmp = page.currentBitmap

        // Layout:
        // Top 60% of page: High resolution scanned document bitmap
        // Bottom 40%: Recognized searchable and selectable text transcript
        val margin = 28
        val availableWidth = PAGE_WIDTH - (margin * 2)
        val imageHeight = (PAGE_HEIGHT * 0.58f).toInt()

        // Calculate aspect ratio fit for image
        val scale = Math.min(
          availableWidth.toFloat() / bmp.width.toFloat(),
          imageHeight.toFloat() / bmp.height.toFloat()
        )
        val destWidth = (bmp.width * scale).toInt()
        val destHeight = (bmp.height * scale).toInt()
        val imgLeft = margin + ((availableWidth - destWidth) / 2)
        val imgTop = margin + 10

        val destRect = Rect(imgLeft, imgTop, imgLeft + destWidth, imgTop + destHeight)
        canvas.drawBitmap(bmp, null, destRect, null)

        // Divider line
        val dividerY = (imgTop + destHeight + 14).toFloat()
        canvas.drawLine(margin.toFloat(), dividerY, (PAGE_WIDTH - margin).toFloat(), dividerY, linePaint)

        // Text header
        val headerY = dividerY + 16
        canvas.drawText(
          "Recognized Text (Page $pageNumber of ${document.pages.size}):",
          margin.toFloat(),
          headerY,
          headerPaint
        )

        // Render extracted text line by line for PDF readers to index and search
        val rawText = page.extractedText.ifBlank { "No text recognized." }
        val lines = wrapText(rawText, availableWidth.toFloat(), textPaint)

        var currentY = headerY + 15
        val maxY = PAGE_HEIGHT - margin - 10

        for (line in lines) {
          if (currentY > maxY) break
          canvas.drawText(line, margin.toFloat(), currentY, textPaint)
          currentY += 13f
        }

        // Draw page footer
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
          color = Color.GRAY
          textSize = 8f
        }
        canvas.drawText(
          "Genzii Smart Scanner • On-Device ML Kit OCR • Page $pageNumber of ${document.pages.size}",
          margin.toFloat(),
          (PAGE_HEIGHT - 12).toFloat(),
          footerPaint
        )

        pdfDoc.finishPage(pdfPage)
      }

      FileOutputStream(outputFile).use { outputStream ->
        pdfDoc.writeTo(outputStream)
      }

      Log.d(TAG, "Searchable PDF generated successfully: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
      Result.success(outputFile)
    } catch (e: Throwable) {
      Log.w(TAG, "Framework PdfDocument failed (${e.message}), switching to resilient pure PDF engine...")
      generateFallbackSearchablePdf(document, outputFile)
    } finally {
      try {
        pdfDoc.close()
      } catch (_: Throwable) {}
    }
  }

  /**
   * Resilient pure-Kotlin PDF 1.4 generator.
   * Produces fully searchable, selectable standard PDF documents without framework dependencies.
   */
  fun generateFallbackSearchablePdf(document: ScannedDocument, outputFile: File): Result<File> {
    return try {
      val baos = java.io.ByteArrayOutputStream()
      val writer = java.io.PrintWriter(baos)
      val offsets = mutableListOf<Long>()

      // PDF 1.4 Header with binary comment
      writer.print("%PDF-1.4\n")
      writer.print("%\u00e2\u00e3\u00cf\u00d3\n")
      writer.flush()

      fun writeObj(objNum: Int, body: String) {
        writer.flush()
        while (offsets.size < objNum) {
          offsets.add(0L)
        }
        offsets[objNum - 1] = baos.size().toLong()
        writer.print("$objNum 0 obj\n")
        writer.print(body)
        writer.print("\nendobj\n")
        writer.flush()
      }

      val totalPages = document.pages.size.coerceAtLeast(1)
      val pageObjIds = (0 until totalPages).map { i -> 4 + (i * 2) }

      // 1. Catalog
      writeObj(1, "<< /Type /Catalog /Pages 2 0 R >>")

      // 2. Pages
      val kidsString = pageObjIds.joinToString(" ") { "$it 0 R" }
      writeObj(2, "<< /Type /Pages /Kids [$kidsString] /Count $totalPages >>")

      // 3. Font
      writeObj(3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")

      for (i in 0 until totalPages) {
        val pageObjId = 4 + (i * 2)
        val contentObjId = 5 + (i * 2)
        val page = document.pages.getOrNull(i)

        // Write Page object
        writeObj(
          pageObjId,
          "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PAGE_WIDTH $PAGE_HEIGHT] /Resources << /Font << /F1 3 0 R >> >> /Contents $contentObjId 0 R >>"
        )

        // Prepare stream for content
        val streamContent = StringBuilder()
        streamContent.append("BT\n")
        streamContent.append("/F1 12 Tf\n")
        streamContent.append("36 800 Td\n")
        val titleSafe = escapePdf(document.title)
        streamContent.append("($titleSafe) Tj\n")
        streamContent.append("/F1 9 Tf\n")
        streamContent.append("0 -16 Td\n")
        val subtitleSafe = escapePdf("Page ${i + 1} of $totalPages • ML Kit On-Device OCR Transcript")
        streamContent.append("($subtitleSafe) Tj\n")
        streamContent.append("0 -14 Td\n")
        streamContent.append("(${escapePdf("--------------------------------------------------------------------------------")}) Tj\n")

        val rawText = page?.extractedText?.ifBlank { "No text recognized." } ?: "No text recognized."
        val lines = rawText.split("\n")
        var lineCount = 0
        for (line in lines) {
          if (lineCount > 52) break
          val safeLine = escapePdf(line.trim())
          if (safeLine.isNotBlank()) {
            streamContent.append("0 -13 Td\n")
            streamContent.append("($safeLine) Tj\n")
            lineCount++
          } else {
            streamContent.append("0 -10 Td\n")
            lineCount++
          }
        }
        streamContent.append("ET\n")

        val streamBytes = streamContent.toString().toByteArray(Charsets.ISO_8859_1)
        val streamBody = "<< /Length ${streamBytes.size} >>\nstream\n${streamContent}endstream"
        writeObj(contentObjId, streamBody)
      }

      // XRef Table
      writer.flush()
      val startXref = baos.size().toLong()
      val totalObjs = offsets.size + 1
      writer.print("xref\n")
      writer.print("0 $totalObjs\n")
      writer.print("0000000000 65535 f \n")
      for (offset in offsets) {
        writer.printf("%010d 00000 n \n", offset)
      }
      writer.print("trailer\n")
      writer.print("<< /Size $totalObjs /Root 1 0 R >>\n")
      writer.print("startxref\n")
      writer.print("$startXref\n")
      writer.print("%%EOF\n")
      writer.flush()

      FileOutputStream(outputFile).use { fos ->
        fos.write(baos.toByteArray())
      }
      Log.d(TAG, "Fallback pure-Kotlin searchable PDF generated successfully: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
      Result.success(outputFile)
    } catch (e: Throwable) {
      Log.e(TAG, "Fallback PDF generation failed: ${e.message}", e)
      Result.failure(e)
    }
  }

  private fun escapePdf(s: String): String {
    val sb = StringBuilder()
    for (ch in s) {
      when (ch) {
        '\\' -> sb.append("\\\\")
        '(' -> sb.append("\\(")
        ')' -> sb.append("\\)")
        in ' '..'~' -> sb.append(ch)
        else -> sb.append(' ')
      }
    }
    return sb.toString()
  }

  /**
   * Directly exports searchable PDF into a SAF Uri output stream.
   */
  suspend fun exportSearchablePdfToUri(
    context: Context,
    document: ScannedDocument,
    targetUri: Uri
  ): Result<Long> = withContext(Dispatchers.IO) {
    try {
      val tempPdf = generateSearchablePdf(context, document).getOrThrow()
      context.contentResolver.openOutputStream(targetUri)?.use { out ->
        tempPdf.inputStream().use { input ->
          input.copyTo(out)
        }
      }
      val size = tempPdf.length()
      Result.success(size)
    } catch (e: Exception) {
      Log.e(TAG, "exportSearchablePdfToUri failed: ${e.message}", e)
      Result.failure(e)
    }
  }

  private fun wrapText(text: String, maxWidth: Float, paint: Paint): List<String> {
    val result = mutableListOf<String>()
    val paragraphs = text.split("\n")

    for (para in paragraphs) {
      if (para.isBlank()) {
        result.add("")
        continue
      }
      val words = para.split(Regex("\\s+"))
      var currentLine = StringBuilder()

      for (word in words) {
        val testLine = if (currentLine.isEmpty()) word else "${currentLine} $word"
        val measured = paint.measureText(testLine)
        if (measured <= maxWidth) {
          currentLine = StringBuilder(testLine)
        } else {
          if (currentLine.isNotEmpty()) {
            result.add(currentLine.toString())
          }
          currentLine = StringBuilder(word)
        }
      }
      if (currentLine.isNotEmpty()) {
        result.add(currentLine.toString())
      }
    }
    return result
  }
}
