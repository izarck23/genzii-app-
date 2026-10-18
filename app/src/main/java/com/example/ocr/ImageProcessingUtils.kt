package com.example.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF

object ImageProcessingUtils {

  /**
   * Rotates a bitmap by a specified angle (e.g. 90, 180, 270 degrees).
   */
  fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
    if (degrees % 360 == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
  }

  /**
   * Crops a bitmap using normalized coordinates (0f..1f).
   */
  fun cropBitmap(bitmap: Bitmap, cropRectRatio: RectF): Bitmap {
    val left = (cropRectRatio.left.coerceIn(0f, 1f) * bitmap.width).toInt()
    val top = (cropRectRatio.top.coerceIn(0f, 1f) * bitmap.height).toInt()
    val right = (cropRectRatio.right.coerceIn(0f, 1f) * bitmap.width).toInt()
    val bottom = (cropRectRatio.bottom.coerceIn(0f, 1f) * bitmap.height).toInt()

    val width = (right - left).coerceAtLeast(10)
    val height = (bottom - top).coerceAtLeast(10)

    val validLeft = left.coerceIn(0, bitmap.width - width)
    val validTop = top.coerceIn(0, bitmap.height - height)

    return Bitmap.createBitmap(bitmap, validLeft, validTop, width, height)
  }

  /**
   * Automatically detects document edges using luminosity gradient analysis.
   * Returns a normalized RectF with the detected page boundaries.
   */
  fun detectDocumentEdges(bitmap: Bitmap): RectF {
    val sampleWidth = 200
    val sampleHeight = (sampleWidth * (bitmap.height.toFloat() / bitmap.width.toFloat())).toInt().coerceIn(100, 400)
    val scaled = Bitmap.createScaledBitmap(bitmap, sampleWidth, sampleHeight, false)

    var minX = sampleWidth
    var maxX = 0
    var minY = sampleHeight
    var maxY = 0

    // Sample background luminance from the 4 outer corners
    val cornerPixels = listOf(
      scaled.getPixel(2, 2),
      scaled.getPixel(sampleWidth - 3, 2),
      scaled.getPixel(2, sampleHeight - 3),
      scaled.getPixel(sampleWidth - 3, sampleHeight - 3)
    )
    val bgLuminance = cornerPixels.map { Color.luminance(it) }.average().toFloat()

    // Threshold to detect document page boundaries
    val threshold = 0.18f

    for (y in 0 until sampleHeight step 4) {
      for (x in 0 until sampleWidth step 4) {
        val pixel = scaled.getPixel(x, y)
        val lum = Color.luminance(pixel)
        if (Math.abs(lum - bgLuminance) > threshold) {
          if (x < minX) minX = x
          if (x > maxX) maxX = x
          if (y < minY) minY = y
          if (y > maxY) maxY = y
        }
      }
    }

    if (scaled != bitmap) {
      scaled.recycle()
    }

    // If no distinct edge was detected, provide a safe 5% inset
    return if (maxX - minX > sampleWidth * 0.3 && maxY - minY > sampleHeight * 0.3) {
      RectF(
        (minX.toFloat() / sampleWidth).coerceIn(0f, 0.25f),
        (minY.toFloat() / sampleHeight).coerceIn(0f, 0.25f),
        (maxX.toFloat() / sampleWidth).coerceIn(0.75f, 1f),
        (maxY.toFloat() / sampleHeight).coerceIn(0.75f, 1f)
      )
    } else {
      RectF(0.04f, 0.04f, 0.96f, 0.96f)
    }
  }

  /**
   * Applies image enhancement filters (Magic Color, B&W, Grayscale, High Contrast).
   */
  fun applyFilter(bitmap: Bitmap, filter: ScanFilter): Bitmap {
    return when (filter) {
      ScanFilter.ORIGINAL -> bitmap
      ScanFilter.GRAYSCALE -> applyGrayscale(bitmap)
      ScanFilter.MAGIC_COLOR -> applyMagicColor(bitmap)
      ScanFilter.B_AND_W -> applyBlackAndWhite(bitmap)
      ScanFilter.HIGH_CONTRAST -> applyHighContrast(bitmap)
    }
  }

  /**
   * CamScanner-style Magic Color: boosts contrast and saturation, brightens background paper.
   */
  private fun applyMagicColor(src: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Contrast boost + Saturation enhancement
    val contrastMatrix = ColorMatrix().apply {
      val scale = 1.35f
      val translate = (-0.5f * scale + 0.5f) * 255f + 15f
      set(floatArrayOf(
        scale, 0f, 0f, 0f, translate,
        0f, scale, 0f, 0f, translate,
        0f, 0f, scale, 0f, translate,
        0f, 0f, 0f, 1f, 0f
      ))
    }

    val saturationMatrix = ColorMatrix().apply { setSaturation(1.25f) }
    contrastMatrix.postConcat(saturationMatrix)

    paint.colorFilter = ColorMatrixColorFilter(contrastMatrix)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return result
  }

  /**
   * Smooth standard Grayscale conversion.
   */
  private fun applyGrayscale(src: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val colorMatrix = ColorMatrix().apply { setSaturation(0f) }
    paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return result
  }

  /**
   * High-contrast monochrome black and white binarization for clean printed documents.
   */
  private fun applyBlackAndWhite(src: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // Very steep contrast curve to binarize text vs paper
    val bwMatrix = ColorMatrix().apply {
      setSaturation(0f)
      val scale = 2.8f
      val translate = -140f
      val m = floatArrayOf(
        scale, 0f, 0f, 0f, translate,
        0f, scale, 0f, 0f, translate,
        0f, 0f, scale, 0f, translate,
        0f, 0f, 0f, 1f, 0f
      )
      postConcat(ColorMatrix(m))
    }

    paint.colorFilter = ColorMatrixColorFilter(bwMatrix)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return result
  }

  /**
   * High Contrast enhancement preserving subtle color tints while making ink crisp.
   */
  private fun applyHighContrast(src: Bitmap): Bitmap {
    val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(result)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val scale = 1.7f
    val translate = (-0.5f * scale + 0.5f) * 255f
    val matrix = ColorMatrix(floatArrayOf(
      scale, 0f, 0f, 0f, translate,
      0f, scale, 0f, 0f, translate,
      0f, 0f, scale, 0f, translate,
      0f, 0f, 0f, 1f, 0f
    ))
    paint.colorFilter = ColorMatrixColorFilter(matrix)
    canvas.drawBitmap(src, 0f, 0f, paint)
    return result
  }

  /**
   * Reprocesses a page from its pristine original bitmap by applying crop, rotation, and filter.
   */
  fun reprocessPage(page: ScannedPage): Bitmap {
    var bmp = page.originalBitmap

    // Step 1: Crop
    if (page.cropRectRatio.left > 0f || page.cropRectRatio.top > 0f ||
        page.cropRectRatio.right < 1f || page.cropRectRatio.bottom < 1f) {
      bmp = cropBitmap(bmp, page.cropRectRatio)
    }

    // Step 2: Rotate
    if (page.rotationDegrees % 360 != 0) {
      bmp = rotateBitmap(bmp, page.rotationDegrees)
    }

    // Step 3: Apply Filter
    if (page.filterMode != ScanFilter.ORIGINAL) {
      bmp = applyFilter(bmp, page.filterMode)
    }

    page.currentBitmap = bmp
    return bmp
  }
}
