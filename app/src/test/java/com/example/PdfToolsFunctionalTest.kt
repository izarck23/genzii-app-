package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.util.PdfToolsEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfToolsFunctionalTest {

  private val context: Context
    get() = ApplicationProvider.getApplicationContext()

  private val tempFiles = mutableListOf<File>()

  @Before
  fun setUp() {
    tempFiles.clear()
  }

  @After
  fun tearDown() {
    tempFiles.forEach { file ->
      try {
        if (file.exists()) file.delete()
      } catch (_: Exception) {}
    }
    tempFiles.clear()
  }

  private fun createSampleImageFile(name: String, text: String): File {
    val bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    canvas.drawColor(Color.WHITE)
    val paint = Paint().apply {
      color = Color.BLACK
      textSize = 28f
    }
    canvas.drawText(text, 30f, 80f, paint)

    val file = File(context.cacheDir, "${name}_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { out ->
      bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
    }
    tempFiles.add(file)
    return file
  }

  @Test
  fun testEmptyMergeHandling() {
    runBlocking {
      val result = PdfToolsEngine.mergePdfs(context, emptyList())
      assertTrue("Merging with empty list should fail", result.isFailure)
      assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
  }

  @Test
  fun testEmptyImagesToPdfHandling() {
    runBlocking {
      val result = PdfToolsEngine.imagesToPdf(context, emptyList())
      assertTrue("Converting empty images list should fail", result.isFailure)
      assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }
  }

  @Test
  fun testImagesToPdfGeneration() {
    runBlocking {
      val img1 = createSampleImageFile("img1", "Page 1 Content")
      val img2 = createSampleImageFile("img2", "Page 2 Content")
      val uris = listOf(Uri.fromFile(img1), Uri.fromFile(img2))

      val result = PdfToolsEngine.imagesToPdf(context, uris)
      assertTrue("imagesToPdf should succeed: ${result.exceptionOrNull()?.message}", result.isSuccess)

      val opResult = result.getOrThrow()
      tempFiles.add(opResult.outputFile)

      assertTrue("Output PDF must exist", opResult.outputFile.exists())
      assertTrue("Output PDF must not be empty", opResult.outputFile.length() > 200)
      assertEquals("Output should contain 2 pages", 2, opResult.pageCount)

      val headerBytes = ByteArray(4)
      opResult.outputFile.inputStream().use { it.read(headerBytes) }
      assertEquals("PDF header must start with %PDF", "%PDF", String(headerBytes))
    }
  }

  @Test
  fun testWatermarkPdf() {
    runBlocking {
      val img = createSampleImageFile("watermark_src", "Watermark Base Document")
      val basePdfResult = PdfToolsEngine.imagesToPdf(context, listOf(Uri.fromFile(img)))
      assertTrue(basePdfResult.isSuccess)
      val basePdf = basePdfResult.getOrThrow().outputFile
      tempFiles.add(basePdf)

      val watermarkResult = PdfToolsEngine.watermarkPdf(
        context = context,
        inputUri = Uri.fromFile(basePdf),
        watermarkText = "CONFIDENTIAL TEST"
      )
      assertTrue("watermarkPdf should succeed: ${watermarkResult.exceptionOrNull()?.message}", watermarkResult.isSuccess)

      val res = watermarkResult.getOrThrow()
      tempFiles.add(res.outputFile)
      assertTrue("Watermarked file must exist", res.outputFile.exists())
      assertTrue("Watermarked file must have content", res.outputFile.length() > 200)
      assertEquals(1, res.pageCount)
    }
  }

  @Test
  fun testRotatePdf() {
    runBlocking {
      val img = createSampleImageFile("rotate_src", "Rotate Base Document")
      val basePdfResult = PdfToolsEngine.imagesToPdf(context, listOf(Uri.fromFile(img)))
      assertTrue(basePdfResult.isSuccess)
      val basePdf = basePdfResult.getOrThrow().outputFile
      tempFiles.add(basePdf)

      val rotateResult = PdfToolsEngine.rotatePdf(
        context = context,
        inputUri = Uri.fromFile(basePdf),
        rotationDegrees = 90
      )
      assertTrue("rotatePdf should succeed: ${rotateResult.exceptionOrNull()?.message}", rotateResult.isSuccess)

      val res = rotateResult.getOrThrow()
      tempFiles.add(res.outputFile)
      assertTrue("Rotated file must exist", res.outputFile.exists())
      assertTrue("Rotated file must have content", res.outputFile.length() > 200)
      assertEquals(1, res.pageCount)
    }
  }

  @Test
  fun testCompressPdf() {
    runBlocking {
      val img = createSampleImageFile("compress_src", "Compress Test Document")
      val basePdfResult = PdfToolsEngine.imagesToPdf(context, listOf(Uri.fromFile(img)))
      assertTrue(basePdfResult.isSuccess)
      val basePdf = basePdfResult.getOrThrow().outputFile
      tempFiles.add(basePdf)

      val compressResult = PdfToolsEngine.compressPdf(
        context = context,
        inputUri = Uri.fromFile(basePdf),
        compressionLevel = "high"
      )
      assertTrue("compressPdf should succeed: ${compressResult.exceptionOrNull()?.message}", compressResult.isSuccess)

      val res = compressResult.getOrThrow()
      tempFiles.add(res.outputFile)
      assertTrue("Compressed file must exist", res.outputFile.exists())
      assertTrue("Compressed file must have content", res.outputFile.length() > 100)
      assertEquals(1, res.pageCount)
    }
  }

  @Test
  fun testSplitPdfAndBoundsValidation() {
    runBlocking {
      val img1 = createSampleImageFile("split_p1", "Page 1 for split")
      val img2 = createSampleImageFile("split_p2", "Page 2 for split")
      val basePdfResult = PdfToolsEngine.imagesToPdf(context, listOf(Uri.fromFile(img1), Uri.fromFile(img2)))
      assertTrue(basePdfResult.isSuccess)
      val basePdf = basePdfResult.getOrThrow().outputFile
      tempFiles.add(basePdf)

      // Test valid split extracting page 1
      val splitResult = PdfToolsEngine.splitPdf(
        context = context,
        inputUri = Uri.fromFile(basePdf),
        selectedPages = listOf(1)
      )
      assertTrue("splitPdf should succeed: ${splitResult.exceptionOrNull()?.message}", splitResult.isSuccess)
      val splitRes = splitResult.getOrThrow()
      tempFiles.add(splitRes.outputFile)
      assertTrue(splitRes.outputFile.exists())
      assertEquals(1, splitRes.pageCount)

      // Test out of bounds split (page 99)
      val outOfBoundsResult = PdfToolsEngine.splitPdf(
        context = context,
        inputUri = Uri.fromFile(basePdf),
        selectedPages = listOf(99)
      )
      assertTrue("Out of bounds split should fail", outOfBoundsResult.isFailure)
      assertTrue(outOfBoundsResult.exceptionOrNull() is IllegalArgumentException)
    }
  }
}
