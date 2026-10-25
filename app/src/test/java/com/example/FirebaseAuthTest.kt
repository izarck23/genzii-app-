package com.example

import com.example.auth.FirebaseAuthManager
import com.example.data.GenziiRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirebaseAuthTest {

  @Test
  fun testFirebaseProjectConfigurationConstants() {
    assertEquals("genzii-5413c", FirebaseAuthManager.FIREBASE_PROJECT_ID)
    assertEquals("1019503501273", FirebaseAuthManager.FIREBASE_PROJECT_NUMBER)
    assertEquals("1:1019503501273:android:763697d0bb7603ad8983c8", FirebaseAuthManager.FIREBASE_APP_ID)
    assertEquals("1019503501273-973vt0brt0u26mgqsk6hli1imlsspb66.apps.googleusercontent.com", FirebaseAuthManager.FIREBASE_WEB_CLIENT_ID)
  }

  @Test
  fun testFirebaseExceptionMapping() {
    val networkException = com.google.firebase.FirebaseNetworkException("Connection timed out")
    val networkMsg = FirebaseAuthManager.mapFirebaseException(networkException)
    assertTrue(networkMsg.contains("internet connection", ignoreCase = true) || networkMsg.contains("Network", ignoreCase = true))

    val invalidUserException = com.google.firebase.auth.FirebaseAuthInvalidUserException("ERROR_USER_NOT_FOUND", "No user found")
    val invalidUserMsg = FirebaseAuthManager.mapFirebaseException(invalidUserException)
    assertTrue(invalidUserMsg.contains("No account found", ignoreCase = true))

    val weakPasswordException = com.google.firebase.auth.FirebaseAuthWeakPasswordException("WEAK_PASSWORD", "Too weak", "reason")
    val weakPasswordMsg = FirebaseAuthManager.mapFirebaseException(weakPasswordException)
    assertTrue(weakPasswordMsg.contains("weak", ignoreCase = true))
  }

  @Test
  fun testGenziiRepositoryAuthSync() {
    GenziiRepository.signIn("test.user@genzii.app", "Test User")
    val profile = GenziiRepository.userProfile.value
    assertEquals("test.user@genzii.app", profile.email)
    assertEquals("Test User", profile.name)

    GenziiRepository.updateProfile("Guest User", "guest@genzii.app", "Free Member")
    val guestProfile = GenziiRepository.userProfile.value
    assertEquals("guest@genzii.app", guestProfile.email)
  }

  @Test
  fun testCredentialRequestBuilding() {
    val serverClientId = FirebaseAuthManager.FIREBASE_WEB_CLIENT_ID
    val signInOption = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(serverClientId)
      .build()
    val googleIdOption = com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
      .setFilterByAuthorizedAccounts(false)
      .setServerClientId(serverClientId)
      .setAutoSelectEnabled(false)
      .build()

    val request = androidx.credentials.GetCredentialRequest.Builder()
      .addCredentialOption(signInOption)
      .addCredentialOption(googleIdOption)
      .build()

    assertEquals(2, request.credentialOptions.size)
  }

  @Test
  fun testFallbackOptionsIsolation() {
    val serverClientId = FirebaseAuthManager.FIREBASE_WEB_CLIENT_ID
    val signInOption = com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption.Builder(serverClientId).build()
    val googleIdOption = com.google.android.libraries.identity.googleid.GetGoogleIdOption.Builder()
      .setServerClientId(serverClientId)
      .setFilterByAuthorizedAccounts(false)
      .setAutoSelectEnabled(false)
      .build()

    val req1 = androidx.credentials.GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
    val req2 = androidx.credentials.GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()

    assertEquals(1, req1.credentialOptions.size)
    assertEquals(1, req2.credentialOptions.size)
  }

  @Test
  fun testPrivacyNoProjectLeakageInErrors() {
    val unconfiguredEx = Exception("CONFIGURATION_NOT_FOUND: Auth provider disabled")
    val msg = FirebaseAuthManager.mapFirebaseException(unconfiguredEx)
    assertTrue("Error message should be user-friendly", msg.contains("unavailable", ignoreCase = true))
    assertTrue("Error message must never expose project ID", !msg.contains("genzii-5413c"))
    assertTrue("Error message must not expose Firebase internals", !msg.contains("Firebase"))

    val apiKeyEx = Exception("API key not valid for project")
    val keyMsg = FirebaseAuthManager.mapFirebaseException(apiKeyEx)
    assertTrue("Key error message must not expose project ID", !keyMsg.contains("genzii-5413c"))
    assertTrue("Key error message must not expose Firebase internals", !keyMsg.contains("Firebase"))
  }
}
