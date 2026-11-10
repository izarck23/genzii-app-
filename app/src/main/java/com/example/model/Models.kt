package com.example.model

enum class FileType {
  PDF, DOCX, PPTX, TXT, IMAGE, AUDIO, OTHER
}

data class DocumentItem(
  val id: String,
  val name: String,
  val sizeFormatted: String = "0 B",
  val type: FileType,
  val folder: String = "Vault",
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
