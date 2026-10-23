package com.example.auth

import android.content.Context
import android.util.Log
import com.example.data.GenziiRepository
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthActionCodeException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class AuthResult {
  data class Success(val user: FirebaseUser) : AuthResult()
  object SuccessReset : AuthResult()
  data class Error(val message: String, val exception: Exception? = null) : AuthResult()
  object Cancelled : AuthResult()
}

object FirebaseAuthManager {
  private const val TAG = "FirebaseAuthManager"
  const val FIREBASE_PROJECT_ID = "genzii-5413c"
  const val FIREBASE_PROJECT_NUMBER = "1019503501273"
  const val FIREBASE_APP_ID = "1:1019503501273:android:763697d0bb7603ad8983c8"
  const val FIREBASE_WEB_CLIENT_ID = "1019503501273-973vt0brt0u26mgqsk6hli1imlsspb66.apps.googleusercontent.com"

  private val _currentUser = MutableStateFlow<FirebaseUser?>(null)
  val currentUser: StateFlow<FirebaseUser?> = _currentUser.asStateFlow()

  private val _isUserLoggedIn = MutableStateFlow(false)
  val isUserLoggedIn: StateFlow<Boolean> = _isUserLoggedIn.asStateFlow()

  private val _authError = MutableStateFlow<String?>(null)
  val authError: StateFlow<String?> = _authError.asStateFlow()

  private val auth: FirebaseAuth?
    get() = try {
      FirebaseAuth.getInstance()
    } catch (e: Exception) {
      Log.e(TAG, "FirebaseAuth initialization error: ${e.message}", e)
      null
    }

  init {
    try {
      auth?.addAuthStateListener { firebaseAuth ->
        val user = firebaseAuth.currentUser
        _currentUser.value = user
        _isUserLoggedIn.value = user != null
        if (user != null) {
          val displayName = user.displayName?.takeIf { it.isNotBlank() }
            ?: user.email?.substringBefore("@")
            ?: "Genzii Member"
          val email = user.email ?: ""
          GenziiRepository.signIn(email, displayName)
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "Failed to register authStateListener: ${e.message}", e)
    }
  }

  fun getCurrentlySignedInUser(): FirebaseUser? {
    return auth?.currentUser
  }

  suspend fun signInWithEmail(email: String, password: String): AuthResult {
    val cleanEmail = email.trim()
    if (cleanEmail.isEmpty()) {
      return AuthResult.Error("Please enter your email address.")
    }
    if (password.isEmpty()) {
      return AuthResult.Error("Please enter your password.")
    }

    val firebaseAuth = auth ?: return AuthResult.Error("Firebase Auth is not available. Please check configuration.")

    return try {
      val authResult = firebaseAuth.signInWithEmailAndPassword(cleanEmail, password).awaitTask()
      val user = authResult.user ?: return AuthResult.Error("Sign in completed but user profile was not returned.")
      _currentUser.value = user
      _isUserLoggedIn.value = true

      val displayName = user.displayName?.takeIf { it.isNotBlank() }
        ?: cleanEmail.substringBefore("@").replace(".", " ")
          .split(" ")
          .filter { it.isNotBlank() }
          .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
      GenziiRepository.signIn(cleanEmail, displayName)

      AuthResult.Success(user)
    } catch (e: Exception) {
      Log.e(TAG, "signInWithEmail failed: ${e.message}", e)
      val friendlyMessage = mapFirebaseException(e)
      _authError.value = friendlyMessage
      AuthResult.Error(friendlyMessage, e)
    }
  }

  suspend fun signUpWithEmail(fullName: String, email: String, password: String): AuthResult {
    val cleanName = fullName.trim()
    val cleanEmail = email.trim()

    if (cleanName.isEmpty()) {
      return AuthResult.Error("Please enter your full name.")
    }
    if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
      return AuthResult.Error("Please enter a valid email address.")
    }
    if (password.length < 6) {
      return AuthResult.Error("Password must be at least 6 characters.")
    }

    val firebaseAuth = auth ?: return AuthResult.Error("Firebase Auth is not available. Please check configuration.")

    return try {
      val authResult = firebaseAuth.createUserWithEmailAndPassword(cleanEmail, password).awaitTask()
      val user = authResult.user ?: return AuthResult.Error("Account creation failed: no user object returned.")

      // Update user display name in Firebase Auth
      try {
        val profileUpdates = UserProfileChangeRequest.Builder()
          .setDisplayName(cleanName)
          .build()
        user.updateProfile(profileUpdates).awaitTask()
      } catch (e: Exception) {
        Log.w(TAG, "Failed to update Firebase profile display name: ${e.message}")
      }

      // Send email verification
      try {
        user.sendEmailVerification().awaitTask()
        Log.d(TAG, "Sent verification email to $cleanEmail")
      } catch (e: Exception) {
        Log.w(TAG, "Failed to send email verification: ${e.message}")
      }

      _currentUser.value = user
      _isUserLoggedIn.value = true
      GenziiRepository.signIn(cleanEmail, cleanName)

      AuthResult.Success(user)
    } catch (e: Exception) {
      Log.e(TAG, "signUpWithEmail failed: ${e.message}", e)
      val friendlyMessage = mapFirebaseException(e)
      _authError.value = friendlyMessage
      AuthResult.Error(friendlyMessage, e)
    }
  }

  suspend fun sendPasswordReset(email: String): AuthResult {
    val cleanEmail = email.trim()
    if (cleanEmail.isEmpty() || !cleanEmail.contains("@")) {
      return AuthResult.Error("Please enter a valid registered email address.")
    }

    val firebaseAuth = auth ?: return AuthResult.Error("Firebase Auth is not available. Please check configuration.")

    return try {
      firebaseAuth.sendPasswordResetEmail(cleanEmail).awaitTask()
      Log.d(TAG, "Password reset email sent to $cleanEmail")
      AuthResult.SuccessReset
    } catch (e: Exception) {
      Log.e(TAG, "sendPasswordReset failed: ${e.message}", e)
      val friendlyMessage = mapFirebaseException(e)
      _authError.value = friendlyMessage
      AuthResult.Error(friendlyMessage, e)
    }
  }

  suspend fun resendVerificationEmail(): AuthResult {
    val user = auth?.currentUser ?: return AuthResult.Error("No user is currently signed in.")
    return try {
      user.sendEmailVerification().awaitTask()
      AuthResult.Success(user)
    } catch (e: Exception) {
      AuthResult.Error(mapFirebaseException(e), e)
    }
  }

  suspend fun checkEmailVerified(): Boolean {
    val user = auth?.currentUser ?: return false
    return try {
      user.reload().awaitTask()
      user.isEmailVerified
    } catch (e: Exception) {
      Log.w(TAG, "Failed to reload user for email verification check: ${e.message}")
      user.isEmailVerified
    }
  }

  private fun findActivity(context: Context): android.app.Activity? {
    var ctx = context
    while (ctx is android.content.ContextWrapper) {
      if (ctx is android.app.Activity) return ctx
      ctx = ctx.baseContext
    }
    return null
  }

  suspend fun signInWithGoogle(context: Context): AuthResult {
    val firebaseAuth = auth ?: return AuthResult.Error("Firebase Auth is not available. Please check configuration.")

    return try {
      val activityContext = findActivity(context) ?: context
      val credentialManager = CredentialManager.create(activityContext)

      // Look up server client ID or use default Google Cloud project number clientId
      val webClientId = try {
        val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        if (resId != 0) context.getString(resId) else FIREBASE_WEB_CLIENT_ID
      } catch (_: Exception) {
        FIREBASE_WEB_CLIENT_ID
      }

      val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(webClientId)
        .build()

      val googleIdOption = GetGoogleIdOption.Builder()
        .setFilterByAuthorizedAccounts(false)
        .setServerClientId(webClientId)
        .setAutoSelectEnabled(false)
        .build()

      val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInWithGoogleOption)
        .addCredentialOption(googleIdOption)
        .build()

      val result = credentialManager.getCredential(context = activityContext, request = request)
      val credential = result.credential

      if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
      ) {
        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
        val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
        val authResult = firebaseAuth.signInWithCredential(authCredential).awaitTask()
        val user = authResult.user ?: return AuthResult.Error("Google sign-in completed but no Firebase user returned.")

        _currentUser.value = user
        _isUserLoggedIn.value = true

        val name = user.displayName ?: "Google User"
        val email = user.email ?: ""
        GenziiRepository.signIn(email, name)

        AuthResult.Success(user)
      } else {
        AuthResult.Error("Unsupported credential type returned from Google.")
      }
    } catch (e: GetCredentialCancellationException) {
      AuthResult.Cancelled
    } catch (e: NoCredentialException) {
      Log.w(TAG, "No Google accounts available or matching in CredentialManager: ${e.message}")
      val friendly = "No matching Google account credentials found. Please verify that a Google account is active in your phone settings, or sign in with your email and password."
      _authError.value = friendly
      AuthResult.Error(friendly, e)
    } catch (e: Exception) {
      val msg = e.message ?: ""
      if (msg.contains("No credential", ignoreCase = true) || msg.contains("16:")) {
        Log.i(TAG, "Google CredentialManager notification: $msg")
      } else {
        Log.e(TAG, "Google Sign-In failed: $msg", e)
      }
      val friendly = mapFirebaseException(e)
      _authError.value = friendly
      AuthResult.Error(friendly, e)
    }
  }

  fun signOut() {
    try {
      auth?.signOut()
      _currentUser.value = null
      _isUserLoggedIn.value = false
      _authError.value = null
      GenziiRepository.updateProfile(
        name = "Guest User",
        email = "guest@genzii.app",
        role = "Free Member"
      )
    } catch (e: Exception) {
      Log.e(TAG, "signOut failed: ${e.message}", e)
    }
  }

  fun mapFirebaseException(e: Exception): String {
    return when (e) {
      is FirebaseAuthInvalidUserException -> {
        "No account found with this email. Please verify your email or create a new account."
      }
      is FirebaseAuthWeakPasswordException -> {
        "Password is too weak. Please use at least 6 characters (mix of letters, numbers, and symbols)."
      }
      is FirebaseAuthInvalidCredentialsException -> {
        "Invalid credentials. Please verify your password and email address."
      }
      is FirebaseAuthUserCollisionException -> {
        "An account already exists with this email address. Please log in instead."
      }
      is FirebaseAuthActionCodeException -> {
        "The verification link or action code is invalid or has expired."
      }
      is FirebaseNetworkException -> {
        "Network connection failed. Please check your internet connection."
      }
      else -> {
        val message = e.localizedMessage ?: "Authentication operation failed."
        when {
          message.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ||
            message.contains("OPERATION_NOT_ALLOWED", ignoreCase = true) -> {
            "Firebase Auth provider is not enabled. Please enable 'Email/Password' in the Firebase Console under Authentication > Sign-in method for project $FIREBASE_PROJECT_ID."
          }
          message.contains("API key not valid", ignoreCase = true) -> {
            "Firebase API key for project $FIREBASE_PROJECT_ID requires verification. You can also proceed in test mode."
          }
          message.contains("16:") || message.contains("No credential", ignoreCase = true) -> {
            "No Google accounts found on this device or Google Play Services was cancelled."
          }
          else -> message
        }
      }
    }
  }
}

/**
 * Extension function to safely await any Google Play Services / Firebase Task
 * without crashing or hanging coroutine.
 */
suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
  addOnSuccessListener { result ->
    if (continuation.isActive) {
      continuation.resume(result)
    }
  }
  addOnFailureListener { exception ->
    if (continuation.isActive) {
      continuation.resumeWithException(exception)
    }
  }
  addOnCanceledListener {
    if (continuation.isActive) {
      continuation.cancel()
    }
  }
}
