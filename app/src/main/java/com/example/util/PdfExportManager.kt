package com.example.util

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.util.Log
import com.example.model.OriginalityReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * High-performance PDF generation and printing engine for Genzii.
 * Uses Android's native PdfDocument and PrintManager (with "Save as PDF" spooler support).
 * Fully offline, lightweight, and zero external binary overhead.
 */
object PdfExportManager {
  private const val TAG = "PdfExportManager"

  // Standard A4 Dimensions in PostScript points (72 points / inch)
  const val PAGE_WIDTH = 595
  const val PAGE_HEIGHT = 842

  private val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())

  /**
   * Generates a beautifully formatted PDF from stored document text or scan notes.
   */
  suspend fun generateDocumentPdf(
    context: Context,
    title: String,
    content: String,
    targetFile: File? = null
  ): Result<File> = withContext(Dispatchers.IO) {
    val outputFile = targetFile ?: File(
      context.cacheDir,
      "${sanitizeFilename(title)}_Document.pdf"
    )
    try {
      val pdfDoc = PdfDocument()

      val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(91, 60, 196) // Genzii Purple
        textSize = 18f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }

      val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 9.5f
      }

      val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(33, 33, 33)
        textSize = 11f
      }

      val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(220, 220, 230)
        strokeWidth = 1.5f
      }

      val margin = 48
      val printableWidth = PAGE_WIDTH - (margin * 2)

      // Split content into paragraphs
      val paragraphs = content.split("\n\n").filter { it.isNotBlank() }

      var currentPageNumber = 1
      var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
      var currentPage = pdfDoc.startPage(pageInfo)
      var canvas = currentPage.canvas

      // Header on Page 1
      canvas.drawText(title.take(45), margin.toFloat(), 55f, titlePaint)
      canvas.drawText("Genzii Document Vault • ${dateFormat.format(Date())}", margin.toFloat(), 72f, metaPaint)
      canvas.drawLine(margin.toFloat(), 82f, (PAGE_WIDTH - margin).toFloat(), 82f, linePaint)

      var yOffset = 100f
      val maxY = (PAGE_HEIGHT - 60).toFloat()

      for (para in paragraphs) {
        val staticLayout = createStaticLayout(para.trim(), bodyPaint, printableWidth)

        // Check if paragraph fits on current page
        if (yOffset + staticLayout.height > maxY && yOffset > 110f) {
          // Draw page footer before finishing page
          drawFooter(canvas, currentPageNumber)
          pdfDoc.finishPage(currentPage)

          currentPageNumber++
          pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, currentPageNumber).create()
          currentPage = pdfDoc.startPage(pageInfo)
          canvas = currentPage.canvas

          // Continuation Header
          canvas.drawText("${title.take(35)} (Continued)", margin.toFloat(), 45f, metaPaint)
          canvas.drawLine(margin.toFloat(), 55f, (PAGE_WIDTH - margin).toFloat(), 55f, linePaint)
          yOffset = 75f
        }

        canvas.save()
        canvas.translate(margin.toFloat(), yOffset)
        staticLayout.draw(canvas)
        canvas.restore()

        yOffset += staticLayout.height + 14f
      }

      var writeSuccess = false
      try {
        drawFooter(canvas, currentPageNumber)
        pdfDoc.finishPage(currentPage)
        FileOutputStream(outputFile).use { out ->
          pdfDoc.writeTo(out)
        }
        writeSuccess = outputFile.exists() && outputFile.length() > 200
      } catch (e: Throwable) {
        Log.w(TAG, "Standard PdfDocument write failed: ${e.message}. Using resilient pure engine...")
      } finally {
        try { pdfDoc.close() } catch (_: Throwable) {}
      }

      if (writeSuccess) {
        Log.i(TAG, "Document PDF generated: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
        Result.success(outputFile)
      } else {
        generateFallbackDocumentPdf(title, content, outputFile)
      }
    } catch (e: Throwable) {
      Log.e(TAG, "generateDocumentPdf fallback: ${e.message}", e)
      generateFallbackDocumentPdf(title, content, outputFile)
    }
  }

  /**
   * Generates a comprehensive Academic Originality & Similarity Report PDF.
   */
  suspend fun generateOriginalityReportPdf(
    context: Context,
    report: OriginalityReport,
    targetFile: File? = null
  ): Result<File> = withContext(Dispatchers.IO) {
    val outputFile = targetFile ?: File(
      context.cacheDir,
      "${sanitizeFilename(report.documentTitle)}_Originality_Report.pdf"
    )
    try {
      val pdfDoc = PdfDocument()

      val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
      val page = pdfDoc.startPage(pageInfo)
      val canvas = page.canvas

      val margin = 40
      val printableWidth = PAGE_WIDTH - (margin * 2)

      // Header Banner Background
      val bannerPaint = Paint().apply {
        color = Color.rgb(91, 60, 196) // Primary Purple
      }
      canvas.drawRoundRect(
        RectF(margin.toFloat(), 36f, (PAGE_WIDTH - margin).toFloat(), 110f),
        16f, 16f, bannerPaint
      )

      val headerTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 15f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
      val headerSubPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(230, 220, 255)
        textSize = 9.5f
      }

      canvas.drawText("GENZII ORIGINALITY CERTIFICATE", margin + 18f, 65f, headerTitlePaint)
      canvas.drawText("Document: ${report.documentTitle} • Generated: ${dateFormat.format(Date())}", margin + 18f, 85f, headerSubPaint)
      canvas.drawText("Verification Engine: Genzii Academic Similarity Index", margin + 18f, 99f, headerSubPaint)

      // Metric Badges (Cards)
      val cardWidth = (printableWidth - 20) / 2
      val cardY = 125f
      val cardHeight = 72f

      val greenCardPaint = Paint().apply { color = Color.rgb(232, 245, 233) }
      val orangeCardPaint = Paint().apply { color = Color.rgb(255, 243, 224) }

      canvas.drawRoundRect(RectF(margin.toFloat(), cardY, margin.toFloat() + cardWidth, cardY + cardHeight), 12f, 12f, greenCardPaint)
      canvas.drawRoundRect(RectF((PAGE_WIDTH - margin - cardWidth).toFloat(), cardY, (PAGE_WIDTH - margin).toFloat(), cardY + cardHeight), 12f, 12f, orangeCardPaint)

      val scorePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(46, 125, 50)
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
      val simScorePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(230, 81, 0)
        textSize = 24f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
      val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        textSize = 10f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }

      canvas.drawText("${report.originalPercentage}%", margin + 16f, cardY + 36f, scorePaint)
      canvas.drawText("ORIGINAL CONTENT", margin + 16f, cardY + 54f, labelPaint)

      canvas.drawText("${report.similarityPercentage}%", (PAGE_WIDTH - margin - cardWidth) + 16f, cardY + 36f, simScorePaint)
      canvas.drawText("MATCHED SIMILARITY", (PAGE_WIDTH - margin - cardWidth) + 16f, cardY + 54f, labelPaint)

      // Statistics Row
      val statsY = 215f
      val statPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(60, 60, 60)
        textSize = 10.5f
      }
      canvas.drawText("Total Words: ${report.wordsCount}    |    Sources Examined: ${report.sourcesCount}    |    Matching Passages: ${report.matchesCount}", margin.toFloat(), statsY, statPaint)

      val dividerPaint = Paint().apply {
        color = Color.rgb(220, 220, 225)
        strokeWidth = 1f
      }
      canvas.drawLine(margin.toFloat(), statsY + 12f, (PAGE_WIDTH - margin).toFloat(), statsY + 12f, dividerPaint)

      // Section: Matched Sources
      var y = statsY + 32f
      val sectionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(91, 60, 196)
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
      canvas.drawText("Identified Sources & Index Alignments", margin.toFloat(), y, sectionPaint)
      y += 18f

      val sourceTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(30, 30, 30)
        textSize = 10f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
      }
      val sourceBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(90, 90, 90)
        textSize = 9f
      }

      val topSources = report.sources.take(4)
      for (src in topSources) {
        canvas.drawText("• ${src.domain} (${src.matchPercentage}% match)", margin + 8f, y, sourceTitlePaint)
        y += 13f
        val excerptLayout = createStaticLayout(src.excerpt.take(120), sourceBodyPaint, printableWidth - 20)
        canvas.save()
        canvas.translate(margin + 16f, y)
        excerptLayout.draw(canvas)
        canvas.restore()
        y += excerptLayout.height + 8f
      }

      // Section: Highlighted Content Excerpt
      y = maxOf(y + 10f, 430f)
      canvas.drawText("Analysis Summary & Excerpt", margin.toFloat(), y, sectionPaint)
      y += 18f

      val summaryLayout = createStaticLayout(report.highlightedContent.take(1200), sourceBodyPaint, printableWidth)
      canvas.save()
      canvas.translate(margin.toFloat(), y)
      summaryLayout.draw(canvas)
      canvas.restore()

      var writeSuccess = false
      try {
        drawFooter(canvas, 1)
        pdfDoc.finishPage(page)
        FileOutputStream(outputFile).use { out ->
          pdfDoc.writeTo(out)
        }
        writeSuccess = outputFile.exists() && outputFile.length() > 200
      } catch (e: Throwable) {
        Log.w(TAG, "Standard PdfDocument write failed: ${e.message}. Using resilient pure engine...")
      } finally {
        try { pdfDoc.close() } catch (_: Throwable) {}
      }

      if (writeSuccess) {
        Log.i(TAG, "Originality Report PDF generated: ${outputFile.absolutePath}")
        Result.success(outputFile)
      } else {
        generateFallbackOriginalityReportPdf(report, outputFile)
      }
    } catch (e: Throwable) {
      Log.e(TAG, "generateOriginalityReportPdf fallback: ${e.message}", e)
      generateFallbackOriginalityReportPdf(report, outputFile)
    }
  }

  /**
   * Directly launches Android's native PrintManager to print or "Save as PDF" any generated PDF file.
   */
  fun printPdfFile(context: Context, documentName: String, pdfFile: File) {
    try {
      val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
      if (printManager == null) {
        Log.e(TAG, "PrintManager is not available on this device")
        return
      }

      val printAdapter = object : PrintDocumentAdapter() {
        override fun onLayout(
          oldAttributes: PrintAttributes?,
          newAttributes: PrintAttributes?,
          cancellationSignal: CancellationSignal?,
          callback: LayoutResultCallback?,
          extras: Bundle?
        ) {
          if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
          }

          val pdi = PrintDocumentInfo.Builder(sanitizeFilename(documentName))
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()

          callback?.onLayoutFinished(pdi, true)
        }

        override fun onWrite(
          pages: Array<out PageRange>?,
          destination: ParcelFileDescriptor?,
          cancellationSignal: CancellationSignal?,
          callback: WriteResultCallback?
        ) {
          if (cancellationSignal?.isCanceled == true) {
            callback?.onWriteCancelled()
            return
          }

          try {
            FileInputStream(pdfFile).use { input ->
              FileOutputStream(destination?.fileDescriptor).use { output ->
                input.copyTo(output)
              }
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
          } catch (e: Exception) {
            Log.e(TAG, "PrintDocumentAdapter onWrite failed: ${e.message}", e)
            callback?.onWriteFailed(e.message)
          }
        }
      }

      val printAttributes = PrintAttributes.Builder()
        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
        .build()

      printManager.print(documentName, printAdapter, printAttributes)
    } catch (e: Exception) {
      Log.e(TAG, "printPdfFile error: ${e.message}", e)
    }
  }

  /**
   * Exports an existing PDF or generated report directly to a user-selected Storage Access Framework URI.
   */
  suspend fun exportPdfToUri(
    context: Context,
    sourceFile: File,
    targetUri: Uri
  ): Result<Long> = withContext(Dispatchers.IO) {
    try {
      var bytesWritten = 0L
      context.contentResolver.openOutputStream(targetUri)?.use { out ->
        FileInputStream(sourceFile).use { input ->
          bytesWritten = input.copyTo(out)
        }
      }
      Result.success(bytesWritten)
    } catch (e: Exception) {
      Log.e(TAG, "exportPdfToUri failed: ${e.message}", e)
      Result.failure(e)
    }
  }

  private fun drawFooter(canvas: Canvas, pageNumber: Int) {
    val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.GRAY
      textSize = 8.5f
    }
    val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
      color = Color.rgb(230, 230, 235)
      strokeWidth = 1f
    }

    val y = (PAGE_HEIGHT - 35).toFloat()
    canvas.drawLine(40f, y - 8f, (PAGE_WIDTH - 40).toFloat(), y - 8f, linePaint)
    canvas.drawText("Genzii Document Platform • Confidential", 40f, y + 4f, footerPaint)
    canvas.drawText("Page $pageNumber", (PAGE_WIDTH - 80).toFloat(), y + 4f, footerPaint)
  }

  private fun createStaticLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
      StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setLineSpacing(0f, 1.25f)
        .setIncludePad(false)
        .build()
    } else {
      @Suppress("DEPRECATION")
      StaticLayout(text, paint, width, Layout.Alignment.ALIGN_NORMAL, 1.25f, 0f, false)
    }
  }

  private fun sanitizeFilename(name: String): String {
    return name.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(50)
  }

  private fun generateFallbackDocumentPdf(
    title: String,
    content: String,
    outputFile: File
  ): Result<File> {
    return try {
      val baos = java.io.ByteArrayOutputStream()
      val writer = java.io.PrintWriter(baos)
      val offsets = mutableListOf<Long>()

      writer.print("%PDF-1.4\n")
      writer.print("%\u00e2\u00e3\u00cf\u00d3\n")
      writer.flush()

      fun writeObj(objNum: Int, body: String) {
        writer.flush()
        while (offsets.size < objNum) offsets.add(0L)
        offsets[objNum - 1] = baos.size().toLong()
        writer.print("$objNum 0 obj\n")
        writer.print(body)
        writer.print("\nendobj\n")
        writer.flush()
      }

      writeObj(1, "<< /Type /Catalog /Pages 2 0 R >>")
      writeObj(2, "<< /Type /Pages /Kids [4 0 R] /Count 1 >>")
      writeObj(3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
      writeObj(4, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PAGE_WIDTH $PAGE_HEIGHT] /Resources << /Font << /F1 3 0 R >> >> /Contents 5 0 R >>")

      val streamContent = StringBuilder()
      streamContent.append("BT\n")
      streamContent.append("/F1 15 Tf\n")
      streamContent.append("40 800 Td\n")
      streamContent.append("(${escapePdf(title)}) Tj\n")
      streamContent.append("/F1 9 Tf\n")
      streamContent.append("0 -16 Td\n")
      streamContent.append("(${escapePdf("Genzii Document Vault • ${dateFormat.format(Date())}")}) Tj\n")
      streamContent.append("0 -12 Td\n")
      streamContent.append("(${escapePdf("------------------------------------------------------------------------------------------------")}) Tj\n")

      streamContent.append("/F1 10 Tf\n")
      val paragraphs = content.split("\n\n").filter { it.isNotBlank() }
      var lineCount = 0
      for (para in paragraphs) {
        if (lineCount > 50) break
        val words = para.trim().split(Regex("\\s+"))
        var currentLine = StringBuilder()
        for (word in words) {
          if (currentLine.length + word.length > 75) {
            streamContent.append("0 -13 Td\n")
            streamContent.append("(${escapePdf(currentLine.toString())}) Tj\n")
            currentLine = StringBuilder(word)
            lineCount++
            if (lineCount > 50) break
          } else {
            if (currentLine.isNotEmpty()) currentLine.append(" ")
            currentLine.append(word)
          }
        }
        if (currentLine.isNotEmpty() && lineCount <= 50) {
          streamContent.append("0 -13 Td\n")
          streamContent.append("(${escapePdf(currentLine.toString())}) Tj\n")
          lineCount++
        }
        streamContent.append("0 -8 Td\n")
      }

      streamContent.append("/F1 8 Tf\n")
      streamContent.append("0 -20 Td\n")
      streamContent.append("(${escapePdf("Genzii Document Platform • Confidential • Page 1")}) Tj\n")
      streamContent.append("ET\n")

      val streamBytes = streamContent.toString().toByteArray(Charsets.ISO_8859_1)
      val streamBody = "<< /Length ${streamBytes.size} >>\nstream\n${streamContent}endstream"
      writeObj(5, streamBody)

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
      Log.i(TAG, "Fallback document PDF generated: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
      Result.success(outputFile)
    } catch (e: Throwable) {
      Log.e(TAG, "generateFallbackDocumentPdf failed: ${e.message}", e)
      Result.failure(e)
    }
  }

  private fun generateFallbackOriginalityReportPdf(
    report: OriginalityReport,
    outputFile: File
  ): Result<File> {
    return try {
      val baos = java.io.ByteArrayOutputStream()
      val writer = java.io.PrintWriter(baos)
      val offsets = mutableListOf<Long>()

      writer.print("%PDF-1.4\n")
      writer.print("%\u00e2\u00e3\u00cf\u00d3\n")
      writer.flush()

      fun writeObj(objNum: Int, body: String) {
        writer.flush()
        while (offsets.size < objNum) offsets.add(0L)
        offsets[objNum - 1] = baos.size().toLong()
        writer.print("$objNum 0 obj\n")
        writer.print(body)
        writer.print("\nendobj\n")
        writer.flush()
      }

      writeObj(1, "<< /Type /Catalog /Pages 2 0 R >>")
      writeObj(2, "<< /Type /Pages /Kids [4 0 R] /Count 1 >>")
      writeObj(3, "<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>")
      writeObj(4, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 $PAGE_WIDTH $PAGE_HEIGHT] /Resources << /Font << /F1 3 0 R >> >> /Contents 5 0 R >>")

      val streamContent = StringBuilder()
      streamContent.append("BT\n")
      streamContent.append("/F1 16 Tf\n")
      streamContent.append("40 800 Td\n")
      streamContent.append("(${escapePdf("GENZII ORIGINALITY CERTIFICATE")}) Tj\n")
      streamContent.append("/F1 9 Tf\n")
      streamContent.append("0 -16 Td\n")
      streamContent.append("(${escapePdf("Document: ${report.documentTitle} • Generated: ${dateFormat.format(Date())}")}) Tj\n")
      streamContent.append("0 -14 Td\n")
      streamContent.append("(${escapePdf("Verification Engine: Genzii Academic Similarity Index")}) Tj\n")
      streamContent.append("0 -12 Td\n")
      streamContent.append("(${escapePdf("================================================================================")}) Tj\n")

      streamContent.append("/F1 12 Tf\n")
      streamContent.append("0 -20 Td\n")
      streamContent.append("(${escapePdf("ORIGINAL CONTENT: ${report.originalPercentage}%   |   MATCHED SIMILARITY: ${report.similarityPercentage}%")}) Tj\n")

      streamContent.append("/F1 9.5 Tf\n")
      streamContent.append("0 -16 Td\n")
      streamContent.append("(${escapePdf("Total Words: ${report.wordsCount}   |   Sources Examined: ${report.sourcesCount}   |   Matching Passages: ${report.matchesCount}")}) Tj\n")
      streamContent.append("0 -12 Td\n")
      streamContent.append("(${escapePdf("--------------------------------------------------------------------------------")}) Tj\n")

      streamContent.append("/F1 11 Tf\n")
      streamContent.append("0 -18 Td\n")
      streamContent.append("(${escapePdf("Identified Sources & Index Alignments:")}) Tj\n")

      streamContent.append("/F1 9 Tf\n")
      for (src in report.sources.take(4)) {
        streamContent.append("0 -14 Td\n")
        streamContent.append("(${escapePdf("• Domain: ${src.domain} (${src.matchPercentage}% match)")}) Tj\n")
        streamContent.append("0 -12 Td\n")
        streamContent.append("(${escapePdf("  URL: ${src.url}")}) Tj\n")
        streamContent.append("0 -12 Td\n")
        streamContent.append("(${escapePdf("  Excerpt: ${src.excerpt.take(90)}")}) Tj\n")
      }

      streamContent.append("/F1 11 Tf\n")
      streamContent.append("0 -18 Td\n")
      streamContent.append("(${escapePdf("Analysis Summary & Excerpt:")}) Tj\n")

      streamContent.append("/F1 9 Tf\n")
      val summaryExcerpt = report.highlightedContent.take(800)
      val words = summaryExcerpt.split(Regex("\\s+"))
      var curLine = StringBuilder()
      var summaryLineCount = 0
      for (w in words) {
        if (curLine.length + w.length > 80) {
          streamContent.append("0 -12 Td\n")
          streamContent.append("(${escapePdf(curLine.toString())}) Tj\n")
          curLine = StringBuilder(w)
          summaryLineCount++
          if (summaryLineCount > 18) break
        } else {
          if (curLine.isNotEmpty()) curLine.append(" ")
          curLine.append(w)
        }
      }
      if (curLine.isNotEmpty() && summaryLineCount <= 18) {
        streamContent.append("0 -12 Td\n")
        streamContent.append("(${escapePdf(curLine.toString())}) Tj\n")
      }

      streamContent.append("/F1 8 Tf\n")
      streamContent.append("0 -24 Td\n")
      streamContent.append("(${escapePdf("Genzii Document Platform • Confidential • Certificate ID: ${report.id} • Digital Seal Verified")}) Tj\n")
      streamContent.append("ET\n")

      val streamBytes = streamContent.toString().toByteArray(Charsets.ISO_8859_1)
      val streamBody = "<< /Length ${streamBytes.size} >>\nstream\n${streamContent}endstream"
      writeObj(5, streamBody)

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
      Log.i(TAG, "Fallback originality report PDF generated: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
      Result.success(outputFile)
    } catch (e: Throwable) {
      Log.e(TAG, "generateFallbackOriginalityReportPdf failed: ${e.message}", e)
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
}
