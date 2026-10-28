package com.example.data

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.DocumentItem
import com.example.model.FileType
import com.example.model.UserDocumentMetadata
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "genzii_document_cache")

/**
 * Local DataStore-based caching layer for offline document persistence.
 * Ensures all documents scanned via ML Kit and retrieved from Firebase Firestore
 * are reliably accessible offline even across device reboots or network loss.
 */
class DocumentLocalCacheManager private constructor(private val context: Context) {

  private val moshi = Moshi.Builder()
    .addLast(KotlinJsonAdapterFactory())
    .build()

  private val listType = Types.newParameterizedType(List::class.java, CachedDocumentDto::class.java)
  private val adapter = moshi.adapter<List<CachedDocumentDto>>(listType)

  private val documentsCacheDir: File by lazy {
    File(context.filesDir, "cached_documents").apply {
      if (!exists()) mkdirs()
    }
  }

  /**
   * Observes cached documents as a continuous Flow.
   */
  fun observeCachedDocuments(): Flow<List<DocumentItem>> {
    return context.dataStore.data
      .catch { exception ->
        if (exception is IOException) {
          Log.e(TAG, "Error reading documents from DataStore: ${exception.message}")
          emit(emptyPreferences())
        } else {
          throw exception
        }
      }
      .map { preferences ->
        val json = preferences[KEY_CACHED_DOCUMENTS]
        if (json.isNullOrBlank()) {
          emptyList()
        } else {
          try {
            val dtoList = adapter.fromJson(json) ?: emptyList()
            dtoList.map { dto ->
              val restoredContent = readContentIfExternal(dto.id, dto.content)
              dto.toDocumentItem(restoredContent)
            }
          } catch (e: Exception) {
            Log.e(TAG, "Failed to parse cached documents JSON: ${e.message}")
            emptyList()
          }
        }
      }
  }

  /**
   * Fetches the current cached documents list synchronously/suspending.
   */
  suspend fun getCachedDocuments(): List<DocumentItem> = withContext(Dispatchers.IO) {
    try {
      val preferences = context.dataStore.data.first()
      val json = preferences[KEY_CACHED_DOCUMENTS]
      if (json.isNullOrBlank()) {
        emptyList()
      } else {
        val dtoList = adapter.fromJson(json) ?: emptyList()
        dtoList.map { dto ->
          val restoredContent = readContentIfExternal(dto.id, dto.content)
          dto.toDocumentItem(restoredContent)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to load cached documents: ${e.message}")
      emptyList()
    }
  }

  /**
   * Caches a single document (e.g. freshly scanned via ML Kit or modified).
   */
  suspend fun cacheDocument(doc: DocumentItem) = withContext(Dispatchers.IO) {
    val currentDocs = getCachedDocuments().toMutableList()
    val index = currentDocs.indexOfFirst { it.id == doc.id }
    if (index >= 0) {
      currentDocs[index] = doc
    } else {
      currentDocs.add(0, doc)
    }
    saveDocumentList(currentDocs)
  }

  /**
   * Caches a list of documents in batch.
   */
  suspend fun cacheDocuments(docs: List<DocumentItem>) = withContext(Dispatchers.IO) {
    val currentDocs = getCachedDocuments().toMutableList()
    val docMap = currentDocs.associateBy { it.id }.toMutableMap()
    docs.forEach { doc -> docMap[doc.id] = doc }
    saveDocumentList(docMap.values.toList())
  }

  /**
   * Merges documents fetched from Firestore into the local cache.
   * If a document exists locally with full OCR text, it keeps the full text while updating metadata.
   */
  suspend fun cacheFirestoreMetadata(metadataList: List<UserDocumentMetadata>) = withContext(Dispatchers.IO) {
    val currentDocs = getCachedDocuments().toMutableList()
    val localMap = currentDocs.associateBy { it.id }.toMutableMap()

    metadataList.forEach { meta ->
      val existing = localMap[meta.id]
      val type = try {
        FileType.valueOf(meta.fileType.uppercase())
      } catch (_: Exception) {
        FileType.PDF
      }

      val merged = existing?.copy(
        name = meta.name.ifBlank { existing.name },
        sizeFormatted = meta.sizeFormatted.ifBlank { existing.sizeFormatted },
        type = type,
        folder = meta.folder.ifBlank { existing.folder },
        originalityScore = meta.originalityScore ?: existing.originalityScore,
        isStarred = meta.isStarred,
        isEncrypted = meta.isEncrypted
      ) ?: DocumentItem(
        id = meta.id,
        name = meta.name,
        sizeFormatted = meta.sizeFormatted.ifBlank { "120 KB" },
        type = type,
        folder = meta.folder.ifBlank { "Vault" },
        updatedAt = "Cloud Synced",
        originalityScore = meta.originalityScore,
        isEncrypted = meta.isEncrypted,
        content = "[Offline cached metadata from Firestore. Content available when synced.]",
        isStarred = meta.isStarred
      )

      localMap[meta.id] = merged
    }

    saveDocumentList(localMap.values.toList())
  }

  /**
   * Deletes a document from the local cache.
   */
  suspend fun deleteCachedDocument(id: String) = withContext(Dispatchers.IO) {
    val currentDocs = getCachedDocuments().filterNot { it.id == id }
    deleteExternalFile(id)
    saveDocumentList(currentDocs)
  }

  /**
   * Clears all local document cache.
   */
  suspend fun clearAllCache() = withContext(Dispatchers.IO) {
    context.dataStore.edit { preferences ->
      preferences.remove(KEY_CACHED_DOCUMENTS)
    }
    documentsCacheDir.deleteRecursively()
    documentsCacheDir.mkdirs()
  }

  private suspend fun saveDocumentList(docs: List<DocumentItem>) {
    val dtoList = docs.map { doc ->
      val optimizedContent = if (doc.content.length > 5000) {
        saveContentToFile(doc.id, doc.content)
        "EXTERNAL_FILE:${doc.id}"
      } else {
        doc.content
      }
      CachedDocumentDto.fromDocumentItem(doc, optimizedContent)
    }

    val json = adapter.toJson(dtoList)
    context.dataStore.edit { preferences ->
      preferences[KEY_CACHED_DOCUMENTS] = json
    }
    Log.d(TAG, "Cached ${docs.size} documents successfully in DataStore.")
  }

  private fun saveContentToFile(id: String, content: String) {
    try {
      val file = File(documentsCacheDir, "$id.txt")
      file.writeText(content, Charsets.UTF_8)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to write document content file $id: ${e.message}")
    }
  }

  private fun readContentIfExternal(id: String, content: String): String {
    return if (content.startsWith("EXTERNAL_FILE:")) {
      try {
        val file = File(documentsCacheDir, "$id.txt")
        if (file.exists()) file.readText(Charsets.UTF_8) else "[Cached Content]"
      } catch (e: Exception) {
        "[Cached Content]"
      }
    } else {
      content
    }
  }

  private fun deleteExternalFile(id: String) {
    try {
      val file = File(documentsCacheDir, "$id.txt")
      if (file.exists()) file.delete()
    } catch (_: Exception) {}
  }

  companion object {
    private const val TAG = "DocumentCacheManager"
    private val KEY_CACHED_DOCUMENTS = stringPreferencesKey("key_cached_documents_json")

    @Volatile
    private var INSTANCE: DocumentLocalCacheManager? = null

    fun getInstance(context: Context): DocumentLocalCacheManager {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: DocumentLocalCacheManager(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
}

/**
 * Data Transfer Object for JSON serialization via Moshi.
 */
data class CachedDocumentDto(
  val id: String,
  val name: String,
  val sizeFormatted: String,
  val typeName: String,
  val folder: String,
  val updatedAt: String,
  val originalityScore: Int? = null,
  val isEncrypted: Boolean = true,
  val content: String = "",
  val isStarred: Boolean = false,
  val thumbnailUri: String? = null,
  val isDeviceFile: Boolean = false,
  val localFilePath: String? = null,
  val uriString: String? = null,
  val pageCount: Int = 1
) {
  fun toDocumentItem(restoredContent: String = content): DocumentItem {
    val fileType = try {
      FileType.valueOf(typeName)
    } catch (_: Exception) {
      FileType.PDF
    }
    return DocumentItem(
      id = id,
      name = name,
      sizeFormatted = sizeFormatted,
      type = fileType,
      folder = folder,
      updatedAt = updatedAt,
      originalityScore = originalityScore,
      isEncrypted = isEncrypted,
      content = restoredContent,
      isStarred = isStarred,
      thumbnailUri = thumbnailUri,
      isDeviceFile = isDeviceFile,
      localFilePath = localFilePath,
      uriString = uriString,
      pageCount = pageCount
    )
  }

  companion object {
    fun fromDocumentItem(doc: DocumentItem, contentToSave: String = doc.content): CachedDocumentDto {
      return CachedDocumentDto(
        id = doc.id,
        name = doc.name,
        sizeFormatted = doc.sizeFormatted,
        typeName = doc.type.name,
        folder = doc.folder,
        updatedAt = doc.updatedAt,
        originalityScore = doc.originalityScore,
        isEncrypted = doc.isEncrypted,
        content = contentToSave,
        isStarred = doc.isStarred,
        thumbnailUri = doc.thumbnailUri,
        isDeviceFile = doc.isDeviceFile,
        localFilePath = doc.localFilePath,
        uriString = doc.uriString,
        pageCount = doc.pageCount
      )
    }
  }
}
