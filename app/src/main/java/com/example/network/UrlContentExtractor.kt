package com.example.network

import android.util.Log
import com.example.util.UrlSecurityValidator
import com.example.util.UrlValidationResult
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Result of web content extraction.
 */
data class ExtractedWebContent(
  val url: String,
  val host: String,
  val title: String,
  val extractedText: String,
  val wordCount: Int,
  val fetchTimeMs: Long
)

/**
 * Robust, SSRF-secure web crawler and content extractor for Genzii Originality Checker.
 * Extracts publicly accessible web articles and strips HTML boilerplates.
 * Enforces strict SSRF protection on initial request and every redirect hop.
 */
object UrlContentExtractor {
  private const val TAG = "UrlContentExtractor"
  private const val MAX_REDIRECTS = 5
  private const val MAX_RESPONSE_BYTES = 2L * 1024L * 1024L // 2MB max payload
  private const val MIN_WORD_COUNT = 30

  private val okHttpClient = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS)
    .followRedirects(false)
    .followSslRedirects(false)
    .build()

  /**
   * Fetches public web page, validates SSRF security on all hops, extracts article content.
   */
  suspend fun extractText(rawUrl: String): Result<ExtractedWebContent> = withContext(Dispatchers.IO) {
    val startTime = System.currentTimeMillis()

    var currentUrl = rawUrl.trim()
    var redirectCount = 0
    var finalResponse: Response? = null

    try {
      while (redirectCount <= MAX_REDIRECTS) {
        // Validate URL security before making any network connection
        val validation = UrlSecurityValidator.validateUrl(currentUrl)
        if (validation !is UrlValidationResult.Valid) {
          val reason = (validation as? UrlValidationResult.Invalid)?.reason ?: "Security validation failed for '$currentUrl'."
          return@withContext Result.failure(SecurityException("SSRF Security Violation: $reason"))
        }

        val request = Request.Builder()
          .url(validation.normalizedUrl)
          .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 GenziiOriginality/1.3")
          .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,text/plain;q=0.8,*/*;q=0.5")
          .header("Accept-Language", "en-US,en;q=0.9")
          .get()
          .build()

        val response = okHttpClient.newCall(request).execute()

        if (response.isRedirect) {
          val location = response.header("Location")
          response.close()
          if (location.isNullOrBlank()) {
            return@withContext Result.failure(IllegalStateException("Target server returned redirect status ${response.code} without Location header."))
          }
          // Resolve relative redirect
          currentUrl = try {
            URI(currentUrl).resolve(location).toString()
          } catch (e: Exception) {
            location
          }
          redirectCount++
          if (redirectCount > MAX_REDIRECTS) {
            return@withContext Result.failure(IllegalStateException("Too many redirects (limit: $MAX_REDIRECTS) while resolving '$rawUrl'."))
          }
          continue
        }

        finalResponse = response
        break
      }

      val response = finalResponse ?: return@withContext Result.failure(IllegalStateException("Failed to establish connection to '$rawUrl'."))

      response.use { resp ->
        // Handle HTTP response codes with honest explanations
        if (!resp.isSuccessful) {
          val errorMessage = when (resp.code) {
            401, 403 -> "Access denied (HTTP ${resp.code}). The webpage is protected by a login paywall, CAPTCHA, or anti-bot defense."
            404 -> "Page not found (HTTP 404). Please verify that the URL is public and typed correctly."
            410 -> "Page no longer exists (HTTP 410 Gone)."
            429 -> "Rate limited by website (HTTP 429). The target host is throttling automated requests. Please try again later."
            500, 502, 503, 504 -> "Target website server error (HTTP ${resp.code}). The external server is experiencing technical downtime."
            else -> "Website request failed with HTTP ${resp.code} (${resp.message})."
          }
          return@withContext Result.failure(Exception(errorMessage))
        }

        val contentType = resp.header("Content-Type")?.lowercase() ?: ""
        if (contentType.isNotBlank() &&
            !contentType.contains("text/html") &&
            !contentType.contains("text/plain") &&
            !contentType.contains("application/xhtml") &&
            !contentType.contains("application/xml")
        ) {
          return@withContext Result.failure(
            IllegalArgumentException("Unsupported content type '$contentType'. URL Originality Checker only scans HTML and text web articles.")
          )
        }

        val body = resp.body ?: return@withContext Result.failure(IllegalStateException("Empty response body received from website."))
        val rawHtml = body.string()

        if (rawHtml.isBlank()) {
          return@withContext Result.failure(IllegalStateException("Target webpage returned an empty response."))
        }

        val title = extractTitle(rawHtml, currentUrl)
        val cleanText = extractReadableText(rawHtml)
        val words = cleanText.split(Regex("\\s+")).filter { it.isNotBlank() }
        val wordCount = words.size

        if (wordCount < MIN_WORD_COUNT) {
          return@withContext Result.failure(
            IllegalStateException(
              "The webpage contains insufficient textual content ($wordCount words) for originality verification. Single-page apps rendered with client-side JavaScript, image-only pages, or password-protected drafts cannot be processed."
            )
          )
        }

        val fetchTime = System.currentTimeMillis() - startTime
        val host = try { URI(currentUrl).host ?: currentUrl } catch (_: Exception) { currentUrl }

        Result.success(
          ExtractedWebContent(
            url = currentUrl,
            host = host,
            title = title,
            extractedText = cleanText,
            wordCount = wordCount,
            fetchTimeMs = fetchTime
          )
        )
      }
    } catch (e: Exception) {
      Log.e(TAG, "Extraction failed for $rawUrl", e)
      Result.failure(e)
    }
  }

  /**
   * Extracts page title from HTML header or OpenGraph tags.
   */
  private fun extractTitle(html: String, fallbackUrl: String): String {
    // 1. Check <title>
    val titleMatcher = Pattern.compile("<title[^>]*>(.*?)</title>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
    if (titleMatcher.find()) {
      val raw = titleMatcher.group(1)?.let { unescapeHtml(it) }?.trim() ?: ""
      if (raw.isNotBlank()) return cleanTitle(raw)
    }

    // 2. Check OpenGraph title
    val ogMatcher = Pattern.compile("<meta\\s+property=[\"']og:title[\"']\\s+content=[\"'](.*?)[\"']", Pattern.CASE_INSENSITIVE).matcher(html)
    if (ogMatcher.find()) {
      val raw = ogMatcher.group(1)?.let { unescapeHtml(it) }?.trim() ?: ""
      if (raw.isNotBlank()) return cleanTitle(raw)
    }

    // 3. Check <h1>
    val h1Matcher = Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL).matcher(html)
    if (h1Matcher.find()) {
      val raw = h1Matcher.group(1)?.replace(Regex("<[^>]+>"), "")?.let { unescapeHtml(it) }?.trim() ?: ""
      if (raw.isNotBlank()) return cleanTitle(raw)
    }

    return try {
      val host = URI(fallbackUrl).host ?: fallbackUrl
      "Article from $host"
    } catch (_: Exception) {
      "Scanned Web Document"
    }
  }

  private fun cleanTitle(title: String): String {
    return title.replace(Regex("\\s+"), " ")
      .take(120)
      .trim()
  }

  /**
   * Strips boilerplates, navigation, scripts, styles and extracts readable article text.
   */
  fun extractReadableText(html: String): String {
    var working = html

    // Remove scripts, styles, noscript, svg, nav, footer, header, aside, head
    val tagsToRemove = listOf("script", "style", "noscript", "svg", "nav", "footer", "header", "aside", "head", "iframe")
    for (tag in tagsToRemove) {
      val pattern = Pattern.compile("<$tag\\b[^<]*(?:(?!<\\/$tag>)<[^<]*)*<\\/$tag>", Pattern.CASE_INSENSITIVE or Pattern.DOTALL)
      working = pattern.matcher(working).replaceAll(" ")
    }

    // Remove comments
    working = working.replace(Regex("<!--[\\s\\S]*?-->"), " ")

    // If <article> or <main> tag is present, prioritize inner content
    val articleMatcher = Pattern.compile("<article[^>]*>([\\s\\S]*?)</article>", Pattern.CASE_INSENSITIVE).matcher(working)
    if (articleMatcher.find()) {
      val articleContent = articleMatcher.group(1)
      if (!articleContent.isNullOrBlank() && articleContent.length > 200) {
        working = articleContent
      }
    } else {
      val mainMatcher = Pattern.compile("<main[^>]*>([\\s\\S]*?)</main>", Pattern.CASE_INSENSITIVE).matcher(working)
      if (mainMatcher.find()) {
        val mainContent = mainMatcher.group(1)
        if (!mainContent.isNullOrBlank() && mainContent.length > 200) {
          working = mainContent
        }
      }
    }

    // Convert block-level elements to line breaks
    working = working.replace(Regex("(?i)</?(p|div|h[1-6]|li|blockquote|section|tr|td|br|hr)[^>]*>"), "\n")

    // Strip all remaining HTML tags
    working = working.replace(Regex("<[^>]+>"), " ")

    // Decode HTML entities
    working = unescapeHtml(working)

    // Normalize whitespace: trim lines and remove excessive blank lines
    val lines = working.lines().map { it.trim() }.filter { it.isNotBlank() }
    return lines.joinToString("\n\n")
  }

  /**
   * Decodes common HTML entities to standard Unicode characters.
   */
  fun unescapeHtml(text: String): String {
    var s = text
      .replace("&amp;", "&")
      .replace("&quot;", "\"")
      .replace("&apos;", "'")
      .replace("&#39;", "'")
      .replace("&#x27;", "'")
      .replace("&lt;", "<")
      .replace("&gt;", ">")
      .replace("&nbsp;", " ")
      .replace("&copy;", "©")
      .replace("&reg;", "®")
      .replace("&trade;", "™")
      .replace("&mdash;", "—")
      .replace("&#x2014;", "—")
      .replace("&ndash;", "–")
      .replace("&#x2013;", "–")
      .replace("&bull;", "•")
      .replace("&hellip;", "…")

    // Handle decimal character entities &#123;
    s = Pattern.compile("&#(\\d+);").matcher(s).replaceAll { mr ->
      try {
        val code = mr.group(1)?.toInt() ?: return@replaceAll mr.group(0) ?: ""
        if (code in 32..65535) Character.toString(code) else " "
      } catch (_: Exception) {
        " "
      }
    }

    // Handle hex character entities &#x1F44D;
    s = Pattern.compile("&#x([0-9a-fA-F]+);").matcher(s).replaceAll { mr ->
      try {
        val code = mr.group(1)?.toInt(16) ?: return@replaceAll mr.group(0) ?: ""
        if (code in 32..65535) Character.toString(code) else " "
      } catch (_: Exception) {
        " "
      }
    }

    return s
  }
}
