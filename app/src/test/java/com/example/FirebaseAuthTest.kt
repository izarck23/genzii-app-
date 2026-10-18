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
}
