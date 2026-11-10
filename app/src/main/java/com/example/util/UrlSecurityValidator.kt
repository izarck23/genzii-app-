package com.example.util

import java.net.URI
import java.util.Locale

/**
 * Result of URL security and accessibility validation.
 */
sealed class UrlValidationResult {
  data class Valid(
    val normalizedUrl: String,
    val host: String,
    val scheme: String,
    val isHttps: Boolean
  ) : UrlValidationResult()

  data class Invalid(
    val reason: String,
    val errorCode: ErrorCode
  ) : UrlValidationResult()

  enum class ErrorCode {
    EMPTY_URL,
    INVALID_SCHEME,
    INVALID_FORMAT,
    PRIVATE_NETWORK_BLOCKED,
    LOOPBACK_BLOCKED,
    LOCAL_HOST_BLOCKED,
    DISALLOWED_TLD
  }
}

/**
 * Validates URLs for the Genzii Originality Checker.
 * Prevents SSRF, private-network scanning, and unsupported protocols.
 */
object UrlSecurityValidator {
  private val PRIVATE_IP_REGEX = Regex(
    "^(10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|" +
    "172\\.(1[6-9]|2[0-9]|3[0-1])\\.\\d{1,3}\\.\\d{1,3}|" +
    "192\\.168\\.\\d{1,3}\\.\\d{1,3}|" +
    "127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}|" +
    "169\\.254\\.\\d{1,3}\\.\\d{1,3}|" +
    "0\\.0\\.0\\.0)$"
  )

  private val BLOCKED_HOSTS = setOf(
    "localhost", "broadcasthost", "local", "internal", "lan", "home.arpa"
  )

  /**
   * Validates and normalizes raw input URLs.
   */
  fun validateUrl(rawUrl: String): UrlValidationResult {
    val trimmed = rawUrl.trim()
    if (trimmed.isBlank()) {
      return UrlValidationResult.Invalid("URL cannot be empty.", UrlValidationResult.ErrorCode.EMPTY_URL)
    }

    val colonIdx = trimmed.indexOf(":")
    val slashIdx = trimmed.indexOf("/")
    val hasExplicitScheme = colonIdx != -1 && (slashIdx == -1 || colonIdx < slashIdx)

    if (hasExplicitScheme) {
      val candidateScheme = trimmed.substring(0, colonIdx).lowercase(Locale.ROOT)
      if (candidateScheme != "http" && candidateScheme != "https") {
        return UrlValidationResult.Invalid(
          "Only HTTP and HTTPS URLs are supported. Non-web schemes (e.g. file:, ftp:, javascript:, data:) are strictly blocked for security.",
          UrlValidationResult.ErrorCode.INVALID_SCHEME
        )
      }
    }

    val urlWithScheme = if (!hasExplicitScheme) {
      "https://$trimmed"
    } else {
      trimmed
    }

    val uri = try {
      URI(urlWithScheme)
    } catch (e: Exception) {
      return UrlValidationResult.Invalid(
        "Malformed URL format: ${e.message}",
        UrlValidationResult.ErrorCode.INVALID_FORMAT
      )
    }

    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: ""
    if (scheme != "http" && scheme != "https") {
      return UrlValidationResult.Invalid(
        "Unsupported scheme '$scheme'. Only http:// and https:// protocols are permitted.",
        UrlValidationResult.ErrorCode.INVALID_SCHEME
      )
    }

    val rawHost = uri.host ?: uri.authority?.substringBefore(":")?.substringAfter("@")
    val host = rawHost?.lowercase(Locale.ROOT) ?: ""
    if (host.isBlank()) {
      return UrlValidationResult.Invalid(
        "Hostname is missing or invalid in '$urlWithScheme'.",
        UrlValidationResult.ErrorCode.INVALID_FORMAT
      )
    }


    // SSRF & private network checks
    if (host in BLOCKED_HOSTS || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) {
      return UrlValidationResult.Invalid(
        "Access to local and loopback addresses ('$host') is strictly blocked for security.",
        UrlValidationResult.ErrorCode.LOCAL_HOST_BLOCKED
      )
    }

    if (PRIVATE_IP_REGEX.matches(host)) {
      return UrlValidationResult.Invalid(
        "Private network IP addresses ($host) cannot be accessed. Only publicly accessible web URLs can be scanned.",
        UrlValidationResult.ErrorCode.PRIVATE_NETWORK_BLOCKED
      )
    }

    // IPv6 loopback / private link-local checks
    if (host == "::1" || host == "[::1]" || host.startsWith("fe80:") || host.startsWith("[fe80:")) {
      return UrlValidationResult.Invalid(
        "IPv6 loopback and link-local addresses are blocked for security.",
        UrlValidationResult.ErrorCode.LOOPBACK_BLOCKED
      )
    }

    // Host must contain at least one dot (domain.tld) unless it's a valid public IP
    if (!host.contains(".") && !host.contains(":")) {
      return UrlValidationResult.Invalid(
        "Invalid domain name '$host'. Domain must include a valid public extension.",
        UrlValidationResult.ErrorCode.DISALLOWED_TLD
      )
    }

    return UrlValidationResult.Valid(
      normalizedUrl = uri.toASCIIString(),
      host = host,
      scheme = scheme,
      isHttps = scheme == "https"
    )
  }
}
