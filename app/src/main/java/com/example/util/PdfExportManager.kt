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
    try {
      val outputFile = targetFile ?: File(
        context.cacheDir,
        "${sanitizeFilename(title)}_Document.pdf"
      )

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

      drawFooter(canvas, currentPageNumber)
      pdfDoc.finishPage(currentPage)

      FileOutputStream(outputFile).use { out ->
        pdfDoc.writeTo(out)
      }
      pdfDoc.close()

      Log.i(TAG, "Document PDF generated: ${outputFile.absolutePath} (${outputFile.length()} bytes)")
      Result.success(outputFile)
    } catch (e: Exception) {
      Log.e(TAG, "generateDocumentPdf failed: ${e.message}", e)
      Result.failure(e)
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
    try {
      val outputFile = targetFile ?: File(
        context.cacheDir,
        "${sanitizeFilename(report.documentTitle)}_Originality_Report.pdf"
      )

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

      // Footer
      drawFooter(canvas, 1)

      pdfDoc.finishPage(page)

      FileOutputStream(outputFile).use { out ->
        pdfDoc.writeTo(out)
      }
      pdfDoc.close()

      Log.i(TAG, "Originality Report PDF generated: ${outputFile.absolutePath}")
      Result.success(outputFile)
    } catch (e: Exception) {
      Log.e(TAG, "generateOriginalityReportPdf failed: ${e.message}", e)
      Result.failure(e)
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
}
