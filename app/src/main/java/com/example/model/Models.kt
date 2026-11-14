package com.example.model

enum class FileType {
  PDF, DOCX, PPTX, TXT, IMAGE, AUDIO, OTHER
}

enum class DocumentCategory(val label: String, val colorHex: Long) {
  ACADEMIC("Academic", 0xFF6366F1),
  PERSONAL("Personal", 0xFF10B981),
  LEGAL("Legal", 0xFFF59E0B),
  RESEARCH("Research", 0xFF0284C7),
  FINANCIAL("Financial", 0xFFEC4899);

  companion object {
    val ALL_LABELS = listOf("Academic", "Personal", "Legal", "Research", "Financial")
    fun fromLabel(label: String?): DocumentCategory {
      return entries.find { it.label.equals(label, ignoreCase = true) } ?: ACADEMIC
    }
  }
}

data class DocumentItem(
  val id: String,
  val name: String,
  val sizeFormatted: String = "0 B",
  val type: FileType,
  val folder: String = "Vault",
  val category: String = "Academic",
  val updatedAt: String = "Just now",
  val originalityScore: Int? = null,
  val isEncrypted: Boolean = true,
  val content: String = "",
  val isStarred: Boolean = false,
  val thumbnailUri: String? = null,
  val isDeviceFile: Boolean = false,
  val localFilePath: String? = null,
  val uriString: String? = null,
  val pageCount: Int = 1,
  val createdAt: Long = System.currentTimeMillis()
) {
  val displayName: String
    get() {
      val trimmed = name.trim()
      val ext = if (trimmed.contains(".")) trimmed.substringAfterLast('.').lowercase() else ""
      val nameWithoutExt = if (trimmed.contains(".")) trimmed.substringBeforeLast('.') else trimmed

      // Check if name is an internal hex / hash / UUID / storage identifier
      val isHexOrHash = nameWithoutExt.length >= 16 && nameWithoutExt.all {
        it.isDigit() || it in 'a'..'f' || it in 'A'..'F' || it == '_' || it == '-'
      }
      val isRawStorageId = nameWithoutExt.startsWith("temp_", ignoreCase = true) ||
                           nameWithoutExt.startsWith("Document_", ignoreCase = true) && nameWithoutExt.removePrefix("Document_").all { it.isDigit() } ||
                           nameWithoutExt.matches(Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"))

      if (isHexOrHash || isRawStorageId) {
        val headingTitle = content.lineSequence()
          .map { it.trim().trim('#', '*', ' ', '-') }
          .firstOrNull { it.isNotBlank() && it.length in 4..40 && !it.startsWith("http") }

        if (headingTitle != null) {
          val cleanTitle = headingTitle.replace(Regex("[^a-zA-Z0-9 _-]"), "").trim()
          return if (ext.isNotBlank()) "$cleanTitle.$ext" else cleanTitle
        }

        val typePrefix = when (type) {
          FileType.PDF -> "Research_Doc"
          FileType.DOCX -> "Academic_Draft"
          FileType.PPTX -> "Lecture_Slides"
          FileType.TXT -> "Vault_Note"
          FileType.IMAGE -> "Scanned_Image"
          FileType.AUDIO -> "Lecture_Audio"
          FileType.OTHER -> "Encrypted_File"
        }
        val shortId = nameWithoutExt.take(6).uppercase()
        val finalExt = if (ext.isNotBlank()) ext else when (type) {
          FileType.PDF -> "pdf"
          FileType.DOCX -> "docx"
          FileType.IMAGE -> "jpg"
          FileType.TXT -> "txt"
          FileType.PPTX -> "pptx"
          FileType.AUDIO -> "mp3"
          else -> "bin"
        }
        return "${typePrefix}_$shortId.$finalExt"
      }
      return trimmed
    }
}

enum class DocumentSortOrder(val id: String, val displayName: String, val shortLabel: String) {
  DATE_NEWEST("date_desc", "Date Created (Newest First)", "Date (Newest)"),
  DATE_OLDEST("date_asc", "Date Created (Oldest First)", "Date (Oldest)"),
  NAME_AZ("name_asc", "Alphabetical (A–Z)", "Name (A–Z)"),
  NAME_ZA("name_desc", "Alphabetical (Z–A)", "Name (Z–A)");

  companion object {
    fun fromId(id: String?): DocumentSortOrder {
      return entries.find { it.id == id } ?: DATE_NEWEST
    }
  }
}

data class SourceMatch(
  val domain: String,
  val url: String,
  val matchPercentage: Int,
  val excerpt: String
)

data class OriginalityReport(
  val id: String,
  val documentTitle: String,
  val originalPercentage: Int,
  val similarityPercentage: Int,
  val matchesCount: Int,
  val sourcesCount: Int,
  val wordsCount: Int,
  val scanDate: String,
  val sources: List<SourceMatch>,
  val highlightedContent: String,
  val aiAnalysis: String = ""
)

data class ChatMessage(
  val id: String,
  val isUser: Boolean,
  val text: String,
  val timestamp: String,
  val bullets: List<String> = emptyList(),
  val followUpPrompt: String? = null,
  val suggestionChips: List<String> = emptyList(),
  val attachedFileName: String? = null,
  val isStreaming: Boolean = false,
  val isError: Boolean = false
)

data class NotificationItem(
  val id: String,
  val title: String,
  val description: String,
  val timestamp: String,
  val isUnread: Boolean,
  val type: String // "check", "vault", "security", "subscription"
)

enum class ThemeMode {
  LIGHT, DARK, SYSTEM
}

enum class SubscriptionTier {
  FREE, PLUS, PRO
}

enum class PremiumFeature(
  val displayName: String,
  val description: String,
  val minimumTier: SubscriptionTier,
  val tag: String
) {
  URL_CHECKER(
    "URL Originality Checker",
    "Extract publicly accessible web articles and verify matching literature against indexed academic sources.",
    SubscriptionTier.PLUS,
    "PLUS"
  ),
  ADVANCED_PLAGIARISM(
    "Advanced Plagiarism Detection",
    "Identify exact phrase matches, paraphrased sentences, and syntactic similarities with verifiable source links.",
    SubscriptionTier.PLUS,
    "PLUS"
  ),
  GEMINI_AI_ANALYSIS(
    "Gemini AI Content Analysis",
    "Deep neural analysis of AI-generated indicators, stylistic probability, and contextual reasoning.",
    SubscriptionTier.PLUS,
    "PLUS"
  ),
  MULTI_FILE_SCANNING(
    "Multiple File Scanning",
    "Batch scan PDF, DOCX, and TXT research documents simultaneously with unified cross-document matching.",
    SubscriptionTier.PRO,
    "PRO"
  ),
  DETAILED_PDF_REPORTS(
    "Detailed Evidence Reports",
    "Generate downloadable, tamper-evident PDF certificates with side-by-side highlighted source matches.",
    SubscriptionTier.PLUS,
    "PLUS"
  ),
  CITATION_CHECKER(
    "Citation & Bibliography Checker",
    "Verify citation completeness and accuracy across APA, MLA, Harvard, and Chicago formatting standards.",
    SubscriptionTier.PRO,
    "PRO"
  ),
  SMART_REWRITE(
    "Smart Rewrite Assistant",
    "Improve flagged or weak passages with academically rigorous phrasing while preserving original thesis.",
    SubscriptionTier.PLUS,
    "PLUS"
  ),
  DOCUMENT_COMPARISON(
    "Document Comparison",
    "Side-by-side comparative diff to highlight verbatim overlap and modified sections between two revisions.",
    SubscriptionTier.PRO,
    "PRO"
  ),
  SCAN_HISTORY(
    "Originality Scan History",
    "Store, filter, and reopen past originality certificates and evidence reports anytime in your private enclave.",
    SubscriptionTier.FREE,
    "FREE"
  ),
  PREMIUM_USAGE_LIMITS(
    "Premium Usage Management",
    "Up to 999 monthly originality checks, extended file sizes, and 100 GB encrypted Vault cloud enclave.",
    SubscriptionTier.PLUS,
    "PLUS"
  );

  val isPremiumOnly: Boolean
    get() = minimumTier != SubscriptionTier.FREE
}

data class PlanComparisonItem(
  val title: String,
  val free: String,
  val plus: String,
  val pro: String,
  val isHighlighted: Boolean = false
)

val GenziiPlanComparisons = listOf(
  PlanComparisonItem("Originality Checks", "10 / month", "100 / month", "999 / month", true),
  PlanComparisonItem("Input Methods", "Text & 1 Doc", "Text, Doc & URL", "Text, Multi-Doc, URL & Batch"),
  PlanComparisonItem("Encrypted Vault Storage", "5.0 GB", "50.0 GB", "100.0 GB"),
  PlanComparisonItem("Plagiarism Source Matching", "Basic", "Advanced Web & Academic", "Full Web, Journal & Deep Index", true),
  PlanComparisonItem("Gemini AI Writing Analysis", "Basic Summary", "Full Explanations & Evidence", "Deep Probabilistic Diagnostics"),
  PlanComparisonItem("Detailed PDF Reports", "Standard Export", "Certified PDF with Evidence", "Certified Institutional Dossier"),
  PlanComparisonItem("Citation Verification", "—", "APA / MLA Basic", "APA, MLA, Harvard & Chicago", true),
  PlanComparisonItem("Smart Rewrite Assistant", "—", "Included", "Priority Academic Assistant"),
  PlanComparisonItem("Document Comparison Diff", "—", "—", "Included (Side-by-Side)"),
  PlanComparisonItem("Processing Speed & Queue", "Standard", "Fast-lane", "Instant Priority Cloud")
)


data class UserProfile(
  val name: String = "Scholar",
  val email: String = "",
  val role: String = "Student / Researcher",
  val institution: String = "Academic Institution",
  val department: String = "Research & Studies",
  val studentId: String = "Member",
  val memberSince: String = "2026",
  val tier: SubscriptionTier = SubscriptionTier.FREE,
  val checksLeft: Int = 10,
  val totalChecks: Int = 10,
  val checksCount: Int = 0,
  val docsCount: Int = 0,
  val chatsCount: Int = 0,
  val usedStorageGb: Double = 0.0,
  val totalStorageGb: Double = 5.0,
  val isAppLockEnabled: Boolean = false,
  val isTwoFactorEnabled: Boolean = false,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val rewardedAdsWatched: Int = 0,
  val isCloudAiConsentGranted: Boolean = false,
  val customApiKey: String = "",
  val useStreaming: Boolean = true,
  val temperature: Float = 0.4f,
  val hasCompletedPermissionsOnboarding: Boolean = false,
  val photoUrl: String? = null
)

data class UrlScanResult(
  val url: String,
  val isSafe: Boolean,
  val noMalware: Boolean,
  val notPhishing: Boolean,
  val sslValid: Boolean,
  val originalityPercentage: Int,
  val scanDate: String
)

data class Flashcard(
  val id: String,
  val question: String,
  val answer: String,
  val category: String
)

data class QuizQuestion(
  val id: String,
  val question: String,
  val options: List<String>,
  val correctIndex: Int,
  val explanation: String
)

data class UserDocumentMetadata(
  val id: String = "",
  val userId: String = "",
  val name: String = "",
  val sizeFormatted: String = "",
  val fileType: String = "PDF",
  val folder: String = "Vault",
  val updatedAt: Long = System.currentTimeMillis(),
  val originalityScore: Int? = null,
  val isEncrypted: Boolean = true,
  val isStarred: Boolean = false,
  val wordCount: Int = 0,
  val tags: List<String> = emptyList()
) {
  fun toMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "userId" to userId,
    "name" to name,
    "sizeFormatted" to sizeFormatted,
    "fileType" to fileType,
    "folder" to folder,
    "updatedAt" to updatedAt,
    "originalityScore" to originalityScore,
    "isEncrypted" to isEncrypted,
    "isStarred" to isStarred,
    "wordCount" to wordCount,
    "tags" to tags
  )

  companion object {
    fun fromMap(id: String, map: Map<String, Any?>): UserDocumentMetadata {
      return UserDocumentMetadata(
        id = id,
        userId = map["userId"] as? String ?: "",
        name = map["name"] as? String ?: "",
        sizeFormatted = map["sizeFormatted"] as? String ?: "",
        fileType = map["fileType"] as? String ?: "PDF",
        folder = map["folder"] as? String ?: "Vault",
        updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        originalityScore = (map["originalityScore"] as? Number)?.toInt(),
        isEncrypted = map["isEncrypted"] as? Boolean ?: true,
        isStarred = map["isStarred"] as? Boolean ?: false,
        wordCount = (map["wordCount"] as? Number)?.toInt() ?: 0,
        tags = (map["tags"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
      )
    }
  }
}
