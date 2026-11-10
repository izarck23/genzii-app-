package com.example

import com.example.data.GenziiRepository
import com.example.model.GenziiPlanComparisons
import com.example.model.PremiumFeature
import com.example.model.SubscriptionTier
import com.example.util.UrlSecurityValidator
import com.example.util.UrlValidationResult
import org.junit.Assert.*
import org.junit.Test

class OriginalityCheckerPremiumPhase1Test {

  @Test
  fun testValidPublicUrlsPassSecurityValidation() {
    val result1 = UrlSecurityValidator.validateUrl("https://arxiv.org/abs/1706.03762")
    assertTrue(result1 is UrlValidationResult.Valid)
    val valid1 = result1 as UrlValidationResult.Valid
    assertEquals("arxiv.org", valid1.host)
    assertEquals("https", valid1.scheme)
    assertTrue(valid1.isHttps)

    val result2 = UrlSecurityValidator.validateUrl("nature.com/articles/d41586-024-00000-0")
    assertTrue(result2 is UrlValidationResult.Valid)
    val valid2 = result2 as UrlValidationResult.Valid
    assertEquals("nature.com", valid2.host)
    assertTrue(valid2.isHttps)
    assertEquals("https://nature.com/articles/d41586-024-00000-0", valid2.normalizedUrl)

    val result3 = UrlSecurityValidator.validateUrl("http://public-academic-press.org/index.html")
    assertTrue(result3 is UrlValidationResult.Valid)
    val valid3 = result3 as UrlValidationResult.Valid
    assertEquals("public-academic-press.org", valid3.host)
    assertFalse(valid3.isHttps)
  }

  @Test
  fun testPrivateNetworkAndSsrfVectorsAreBlocked() {
    // Localhost
    val resLocalhost = UrlSecurityValidator.validateUrl("http://localhost/admin")
    assertTrue(resLocalhost is UrlValidationResult.Invalid)
    assertEquals(
      UrlValidationResult.ErrorCode.LOCAL_HOST_BLOCKED,
      (resLocalhost as UrlValidationResult.Invalid).errorCode
    )

    // Loopback 127.0.0.1
    val resLoopback = UrlSecurityValidator.validateUrl("http://127.0.0.1:8080/secret")
    assertTrue(resLoopback is UrlValidationResult.Invalid)
    assertEquals(
      UrlValidationResult.ErrorCode.PRIVATE_NETWORK_BLOCKED,
      (resLoopback as UrlValidationResult.Invalid).errorCode
    )

    // Class A Private (10.x.x.x)
    val resClassA = UrlSecurityValidator.validateUrl("https://10.0.0.1/intranet")
    assertTrue(resClassA is UrlValidationResult.Invalid)
    assertEquals(
      UrlValidationResult.ErrorCode.PRIVATE_NETWORK_BLOCKED,
      (resClassA as UrlValidationResult.Invalid).errorCode
    )

    // Class B Private (172.16.x.x)
    val resClassB = UrlSecurityValidator.validateUrl("http://172.16.10.5/api")
    assertTrue(resClassB is UrlValidationResult.Invalid)
    assertEquals(
      UrlValidationResult.ErrorCode.PRIVATE_NETWORK_BLOCKED,
      (resClassB as UrlValidationResult.Invalid).errorCode
    )

    // Class C Private (192.168.x.x)
    val resClassC = UrlSecurityValidator.validateUrl("http://192.168.1.1/router")
    assertTrue(resClassC is UrlValidationResult.Invalid)
    assertEquals(
      UrlValidationResult.ErrorCode.PRIVATE_NETWORK_BLOCKED,
      (resClassC as UrlValidationResult.Invalid).errorCode
    )

    // Cloud Metadata Link-Local (169.254.169.254)
    val resMetadata = UrlSecurityValidator.validateUrl("http://169.254.169.254/latest/meta-data/")
    assertTrue(resMetadata is UrlValidationResult.Invalid)
    assertEquals(
      UrlValidationResult.ErrorCode.PRIVATE_NETWORK_BLOCKED,
      (resMetadata as UrlValidationResult.Invalid).errorCode
    )

    // IPv6 Loopback [::1]
    val resIpv6 = UrlSecurityValidator.validateUrl("http://[::1]:9000")
    assertTrue(resIpv6 is UrlValidationResult.Invalid)
    assertEquals(
      UrlValidationResult.ErrorCode.LOOPBACK_BLOCKED,
      (resIpv6 as UrlValidationResult.Invalid).errorCode
    )
  }

  @Test
  fun testMaliciousAndUnsupportedSchemesAreBlocked() {
    val resFile = UrlSecurityValidator.validateUrl("file:///etc/passwd")
    assertTrue(resFile is UrlValidationResult.Invalid)
    assertEquals(
      UrlValidationResult.ErrorCode.INVALID_SCHEME,
      (resFile as UrlValidationResult.Invalid).errorCode
    )

    val resFtp = UrlSecurityValidator.validateUrl("ftp://archive.org/file.pdf")
    assertTrue(resFtp is UrlValidationResult.Invalid)
    assertEquals(
      UrlValidationResult.ErrorCode.INVALID_SCHEME,
      (resFtp as UrlValidationResult.Invalid).errorCode
    )

    val resJs = UrlSecurityValidator.validateUrl("javascript:alert(1)")
    assertTrue(resJs is UrlValidationResult.Invalid)
    assertEquals(
      UrlValidationResult.ErrorCode.INVALID_SCHEME,
      (resJs as UrlValidationResult.Invalid).errorCode
    )
  }

  @Test
  fun testEmptyAndMalformedUrlsAreHandledGracefully() {
    val resEmpty = UrlSecurityValidator.validateUrl("   ")
    assertTrue(resEmpty is UrlValidationResult.Invalid)
    assertEquals(
      UrlValidationResult.ErrorCode.EMPTY_URL,
      (resEmpty as UrlValidationResult.Invalid).errorCode
    )

    val resNoDot = UrlSecurityValidator.validateUrl("https://intranet-server-no-tld")
    assertTrue(resNoDot is UrlValidationResult.Invalid)
    assertEquals(
      UrlValidationResult.ErrorCode.DISALLOWED_TLD,
      (resNoDot as UrlValidationResult.Invalid).errorCode
    )
  }

  @Test
  fun testFreeTierEntitlementsAndAccessControl() {
    GenziiRepository.upgradeTier(SubscriptionTier.FREE)
    val profile = GenziiRepository.userProfile.value
    assertEquals(SubscriptionTier.FREE, profile.tier)
    assertEquals(10, profile.checksLeft)
    assertEquals(5.0, profile.totalStorageGb, 0.01)

    // Free tier access control checks
    assertTrue(GenziiRepository.isFeatureEntitled(PremiumFeature.SCAN_HISTORY))
    assertFalse(GenziiRepository.isFeatureEntitled(PremiumFeature.URL_CHECKER))
    assertFalse(GenziiRepository.isFeatureEntitled(PremiumFeature.ADVANCED_PLAGIARISM))
    assertFalse(GenziiRepository.isFeatureEntitled(PremiumFeature.GEMINI_AI_ANALYSIS))
    assertFalse(GenziiRepository.isFeatureEntitled(PremiumFeature.MULTI_FILE_SCANNING))
    assertFalse(GenziiRepository.isFeatureEntitled(PremiumFeature.CITATION_CHECKER))
    assertFalse(GenziiRepository.isFeatureEntitled(PremiumFeature.DOCUMENT_COMPARISON))
  }

  @Test
  fun testPlusTierEntitlementsAndAccessControl() {
    GenziiRepository.upgradeTier(SubscriptionTier.PLUS)
    val profile = GenziiRepository.userProfile.value
    assertEquals(SubscriptionTier.PLUS, profile.tier)
    assertEquals(100, profile.checksLeft)
    assertEquals(50.0, profile.totalStorageGb, 0.01)

    // Plus tier unlocks core premium features
    assertTrue(GenziiRepository.isFeatureEntitled(PremiumFeature.URL_CHECKER))
    assertTrue(GenziiRepository.isFeatureEntitled(PremiumFeature.ADVANCED_PLAGIARISM))
    assertTrue(GenziiRepository.isFeatureEntitled(PremiumFeature.GEMINI_AI_ANALYSIS))
    assertTrue(GenziiRepository.isFeatureEntitled(PremiumFeature.DETAILED_PDF_REPORTS))
    assertTrue(GenziiRepository.isFeatureEntitled(PremiumFeature.SMART_REWRITE))
    assertTrue(GenziiRepository.isFeatureEntitled(PremiumFeature.PREMIUM_USAGE_LIMITS))

    // But Pro-only features remain gated
    assertFalse(GenziiRepository.isFeatureEntitled(PremiumFeature.MULTI_FILE_SCANNING))
    assertFalse(GenziiRepository.isFeatureEntitled(PremiumFeature.CITATION_CHECKER))
    assertFalse(GenziiRepository.isFeatureEntitled(PremiumFeature.DOCUMENT_COMPARISON))
  }

  @Test
  fun testProTierEntitlementsAndAccessControl() {
    GenziiRepository.upgradeTier(SubscriptionTier.PRO)
    val profile = GenziiRepository.userProfile.value
    assertEquals(SubscriptionTier.PRO, profile.tier)
    assertEquals(999, profile.checksLeft)
    assertEquals(100.0, profile.totalStorageGb, 0.01)

    // Pro tier unlocks all 10 features
    PremiumFeature.entries.forEach { feature ->
      assertTrue("Pro tier must be entitled to ${feature.name}", GenziiRepository.isFeatureEntitled(feature))
    }
  }

  @Test
  fun testScanUrlIntegrationWithUrlSecurityValidator() {
    val validUrl = "https://scholar.google.com/citations?user=123"
    val safeResult = GenziiRepository.scanUrl(validUrl)
    assertTrue(safeResult.isSafe)
    assertTrue(safeResult.sslValid)
    assertEquals(validUrl, safeResult.url)

    val blockedPrivateUrl = "http://192.168.0.1/confidential.pdf"
    val unsafeResult = GenziiRepository.scanUrl(blockedPrivateUrl)
    assertFalse(unsafeResult.isSafe)
    assertFalse(unsafeResult.sslValid)
  }

  @Test
  fun testGenziiPlanComparisonsMatrixCompleteness() {
    assertEquals(10, GenziiPlanComparisons.size)
    val titles = GenziiPlanComparisons.map { it.title }
    assertTrue(titles.contains("Originality Checks"))
    assertTrue(titles.contains("Input Methods"))
    assertTrue(titles.contains("Encrypted Vault Storage"))
    assertTrue(titles.contains("Plagiarism Source Matching"))
    assertTrue(titles.contains("Gemini AI Writing Analysis"))
    assertTrue(titles.contains("Citation Verification"))
  }
}
