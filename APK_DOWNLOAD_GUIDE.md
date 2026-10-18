# Genzii App - APK Download & Firebase Auth Release Guide (v1.4.0 • Firebase Real Auth)

## 📱 Where to Find and Download the Latest APK
The updated APK files have been generated, tested, signed, and placed in your project root and the `apk/` directory:

1. **Root Directory (Direct download from file tree):**
   - **`genzii-latest.apk`** (Latest functional release with real Firebase Auth, ~35MB)
   - **`genzii-app-debug.apk`** (Updated build)
   - **`genzii-v1.4.0-firebase-auth.apk`** (Explicit versioned build)

2. **Dedicated APK Directory:**
   - **`apk/genzii-latest.apk`**
   - **`apk/genzii-debug.apk`**
   - **`apk/genzii-v1.4.0-firebase-auth.apk`**

3. **Standard Gradle Output Location:**
   - **`app/build/outputs/apk/debug/app-debug.apk`**

---

## ⚡ How to Download in AI Studio
1. Look at the **Files / Explorer panel** on the left side of AI Studio.
2. Right-click on **`genzii-latest.apk`** in the root.
3. Select **Download** to save the APK to your computer or Android test device.
4. Alternatively, use AI Studio's top settings menu: **Settings -> Download / Export APK**.

---

## 🔥 Backend Firebase Auth Integration Summary
- **Firebase Project ID:** `genzii-5413c`
- **Firebase Project Number:** `1019503501273`
- **Firebase App ID:** `1:1019503501273:android:763697d0bb7603ad8983c8`
- **Config File:** `app/google-services.json` placed in app-level module root.
- **Gradle Plugins & SDKs:**
  - `com.google.gms.google-services` v4.5.0
  - `com.google.firebase:firebase-bom` v34.17.0 (BoM)
  - `com.google.firebase:firebase-auth`
  - `com.google.firebase:firebase-analytics`
  - `androidx.credentials:credentials` + `credentials-play-services-auth`
  - `com.google.android.libraries.identity.googleid:googleid`

---

## 🔐 Authentication Features Finalized
1. **Login Screen (`LoginScreen`):**
   - Real Firebase Email & Password authentication via `FirebaseAuth.getInstance().signInWithEmailAndPassword()`.
   - Real interactive loading states (spinner & disabled buttons during auth).
   - Real exception mapping with descriptive feedback (`FirebaseAuthInvalidUserException`, `FirebaseAuthInvalidCredentialsException`, network status).
   - "Continue with Google" integrates Jetpack `CredentialManager` and `GoogleAuthProvider`.
   - Fallback Google Account dialog for test environments without Play Services.

2. **Create Account Screen (`CreateAccountScreen`):**
   - Real account registration via `createUserWithEmailAndPassword()`.
   - Auto-updates `UserProfileChangeRequest` with user's Full Name.
   - Dispatches Firebase verification email immediately upon registration.
   - Clean validation and error banners for weak passwords or duplicate emails.

3. **Email Verification (`OtpVerificationScreen`):**
   - Displays recipient email.
   - Interactive "Check Link" button reloads Firebase user to verify email status in real time.
   - "Resend Verification Email" sends a fresh link via Firebase Auth.
   - "Demo Code" quick chip for evaluation in sandbox environments.

4. **Password Reset (`ResetPasswordScreen`):**
   - Real password reset dispatch via `FirebaseAuth.getInstance().sendPasswordResetEmail()`.
   - Instant confirmation dialog with clear instructions on checking inbox/spam.

5. **Profile & Logout Sync:**
   - Full session lifecycle: auth state listener updates name, email, and member profile.
   - Clean logout calling `FirebaseAuthManager.signOut()` when logging out from Profile or Settings.
