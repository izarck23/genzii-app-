package com.example.model

enum class FileType {
  PDF, DOCX, PPTX, TXT, IMAGE
}

data class DocumentItem(
  val id: String,
  val name: String,
  val sizeFormatted: String,
  val type: FileType,
  val folder: String,
  val updatedAt: String,
  val originalityScore: Int? = null,
  val isEncrypted: Boolean = true,
  val content: String = "",
  val isStarred: Boolean = false
)

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
  val highlightedContent: String
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
  val name: String = "John Doe",
  val email: String = "john.doe@example.com",
  val role: String = "Research Fellow & Graduate Student",
  val institution: String = "Stanford University",
  val department: String = "Computer Science & Artificial Intelligence",
  val studentId: String = "GZ-92041",
  val memberSince: String = "September 2026",
  val tier: SubscriptionTier = SubscriptionTier.FREE,
  val checksLeft: Int = 20,
  val totalChecks: Int = 20,
  val checksCount: Int = 12,
  val docsCount: Int = 8,
  val chatsCount: Int = 3,
  val usedStorageGb: Double = 4.2,
  val totalStorageGb: Double = 10.0,
  val isAppLockEnabled: Boolean = false,
  val isTwoFactorEnabled: Boolean = true,
  val themeMode: ThemeMode = ThemeMode.SYSTEM,
  val rewardedAdsWatched: Int = 0,
  val isCloudAiConsentGranted: Boolean = false,
  val customApiKey: String = "",
  val useStreaming: Boolean = true,
  val temperature: Float = 0.4f,
  val hasCompletedPermissionsOnboarding: Boolean = false
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
