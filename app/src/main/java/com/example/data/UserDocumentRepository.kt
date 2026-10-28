package com.example.data

import android.content.Context
import android.util.Log
import com.example.auth.FirebaseAuthManager
import com.example.auth.awaitTask
import com.example.model.DocumentItem
import com.example.model.FileType
import com.example.model.UserDocumentMetadata
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Repository managing user document metadata persistence in Firebase Firestore.
 * Documents are organized hierarchically under users/{userId}/documents/{documentId}
 * adhering to zero-trust per-user security boundaries.
 */
class UserDocumentRepository(
  private val db: FirebaseFirestore,
  private val context: Context? = null
) {

  constructor(context: Context) : this(
    resolveFirestoreInstance(context),
    context.applicationContext
  )

  private val auth: FirebaseAuth
    get() = FirebaseAuth.getInstance()

  /**
   * Retrieves current authenticated user ID, throwing an IllegalStateException
   * if operations are attempted without authentication.
   */
  fun requireUserId(): String {
    val uid = auth.currentUser?.uid
    check(!uid.isNullOrBlank()) {
      "User must be authenticated with Firebase Auth before accessing Firestore document metadata."
    }
    return uid
  }

  /**
   * Returns current user ID or null if unauthenticated.
   */
  fun currentUserIdOrNull(): String? = auth.currentUser?.uid

  /**
   * Saves or updates a document metadata record in Firestore.
   */
  suspend fun saveDocumentMetadata(metadata: UserDocumentMetadata): Result<Unit> {
    return try {
      val userId = if (metadata.userId.isNotBlank()) metadata.userId else requireUserId()
      val docId = if (metadata.id.isNotBlank()) metadata.id else UUID.randomUUID().toString()
      val finalMetadata = metadata.copy(id = docId, userId = userId, updatedAt = System.currentTimeMillis())

      db.collection(COLLECTION_USERS)
        .document(userId)
        .collection(COLLECTION_DOCUMENTS)
        .document(docId)
        .set(finalMetadata.toMap())
        .awaitTask()

      Log.d(TAG, "Successfully saved metadata for document $docId (user: $userId)")
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to save document metadata: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Fetches a specific document metadata by its ID.
   */
  suspend fun getDocumentMetadata(documentId: String, userId: String? = null): Result<UserDocumentMetadata?> {
    return try {
      val uid = userId ?: requireUserId()
      val snapshot = db.collection(COLLECTION_USERS)
        .document(uid)
        .collection(COLLECTION_DOCUMENTS)
        .document(documentId)
        .get()
        .awaitTask()

      if (snapshot.exists()) {
        val data = snapshot.data ?: emptyMap<String, Any?>()
        Result.success(UserDocumentMetadata.fromMap(snapshot.id, data))
      } else {
        Result.success(null)
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to get document metadata $documentId: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Observes all document metadata records for a given user in real time.
   * Emits updates whenever documents are added, updated, or removed.
   */
  fun observeUserDocuments(userId: String): Flow<List<UserDocumentMetadata>> = callbackFlow {
    if (userId.isBlank()) {
      trySend(emptyList())
      close()
      return@callbackFlow
    }

    val query = db.collection(COLLECTION_USERS)
      .document(userId)
      .collection(COLLECTION_DOCUMENTS)
      .orderBy("updatedAt", Query.Direction.DESCENDING)

    val listenerRegistration = query.addSnapshotListener { snapshot, error ->
      if (error != null) {
        Log.e(TAG, "Firestore snapshot listener error for user $userId: ${error.message}", error)
        // Fallback to offline cached metadata
        context?.let { ctx ->
          kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
              val cached = DocumentLocalCacheManager.getInstance(ctx).getCachedDocuments()
              val metaList = cached.map { doc ->
                UserDocumentMetadata(
                  id = doc.id,
                  userId = userId,
                  name = doc.name,
                  sizeFormatted = doc.sizeFormatted,
                  fileType = doc.type.name,
                  folder = doc.folder,
                  updatedAt = System.currentTimeMillis(),
                  originalityScore = doc.originalityScore,
                  isEncrypted = doc.isEncrypted,
                  isStarred = doc.isStarred
                )
              }
              trySend(metaList)
            } catch (_: Exception) {
              trySend(emptyList())
            }
          }
        } ?: trySend(emptyList())
        return@addSnapshotListener
      }

      if (snapshot != null) {
        val documents = snapshot.documents.map { doc ->
          UserDocumentMetadata.fromMap(doc.id, doc.data ?: emptyMap())
        }
        context?.let { ctx ->
          kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
              DocumentLocalCacheManager.getInstance(ctx).cacheFirestoreMetadata(documents)
            } catch (_: Exception) {}
          }
        }
        trySend(documents)
      }
    }

    awaitClose {
      Log.d(TAG, "Closing document snapshot listener for user $userId")
      listenerRegistration.remove()
    }
  }

  /**
   * Deletes a document metadata record from Firestore.
   */
  suspend fun deleteDocumentMetadata(documentId: String, userId: String? = null): Result<Unit> {
    return try {
      val uid = userId ?: requireUserId()
      db.collection(COLLECTION_USERS)
        .document(uid)
        .collection(COLLECTION_DOCUMENTS)
        .document(documentId)
        .delete()
        .awaitTask()

      Log.d(TAG, "Successfully deleted metadata for document $documentId")
      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to delete document metadata: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Updates partial metadata fields (e.g. isStarred, folder, originalityScore).
   */
  suspend fun updateDocumentFields(
    documentId: String,
    updates: Map<String, Any>,
    userId: String? = null
  ): Result<Unit> {
    return try {
      val uid = userId ?: requireUserId()
      val fieldsWithTimestamp = updates.toMutableMap<String, Any>().apply {
        put("updatedAt", System.currentTimeMillis())
      }

      db.collection(COLLECTION_USERS)
        .document(uid)
        .collection(COLLECTION_DOCUMENTS)
        .document(documentId)
        .update(fieldsWithTimestamp)
        .awaitTask()

      Result.success(Unit)
    } catch (e: Exception) {
      Log.e(TAG, "Failed to update document fields: ${e.message}", e)
      Result.failure(e)
    }
  }

  /**
   * Convenience helper to bridge a local vault DocumentItem into Firestore metadata.
   */
  suspend fun syncLocalDocument(doc: DocumentItem): Result<Unit> {
    val uid = currentUserIdOrNull() ?: return Result.failure(IllegalStateException("Not authenticated"))
    val metadata = UserDocumentMetadata(
      id = doc.id,
      userId = uid,
      name = doc.name,
      sizeFormatted = doc.sizeFormatted,
      fileType = doc.type.name,
      folder = doc.folder,
      updatedAt = System.currentTimeMillis(),
      originalityScore = doc.originalityScore,
      isEncrypted = doc.isEncrypted,
      isStarred = doc.isStarred,
      wordCount = doc.content.split("\\s+".toRegex()).filter { it.isNotBlank() }.size
    )
    return saveDocumentMetadata(metadata)
  }

  companion object {
    private const val TAG = "UserDocumentRepository"
    const val COLLECTION_USERS = "users"
    const val COLLECTION_DOCUMENTS = "documents"

    @Volatile
    private var INSTANCE: UserDocumentRepository? = null

    fun getInstance(context: Context): UserDocumentRepository {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: UserDocumentRepository(context.applicationContext).also { INSTANCE = it }
      }
    }

    private fun resolveFirestoreInstance(context: Context): FirebaseFirestore {
      return try {
        val appContext = context.applicationContext
        val resId = appContext.resources.getIdentifier("firestore_database_id", "string", appContext.packageName)
        if (resId != 0) {
          val dbId = appContext.getString(resId)
          if (dbId.isNotBlank() && dbId != "(default)") {
            FirebaseFirestore.getInstance(dbId)
          } else {
            FirebaseFirestore.getInstance()
          }
        } else {
          FirebaseFirestore.getInstance()
        }
      } catch (e: Exception) {
        Log.w(TAG, "Failed to read named database ID resource, falling back to default instance: ${e.message}")
        FirebaseFirestore.getInstance()
      }
    }
  }
}
