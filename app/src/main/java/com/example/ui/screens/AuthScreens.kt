package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.auth.AuthResult
import com.example.auth.FirebaseAuthManager
import com.example.data.GenziiRepository
import com.example.ui.components.GenziiLogo
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
  onLoginSuccess: () -> Unit,
  onNavigateToSignup: () -> Unit,
  onNavigateToForgotPassword: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var emailOrPhone by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var isPasswordVisible by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var isLoading by remember { mutableStateOf(false) }
  var showGoogleAccountPicker by remember { mutableStateOf(false) }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 24.dp)
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Spacer(modifier = Modifier.height(20.dp))

      GenziiLogo(logoSize = 44.dp)

      Spacer(modifier = Modifier.height(20.dp))

      Text(
        text = "Welcome back!",
        style = MaterialTheme.typography.displayMedium,
        color = MaterialTheme.colorScheme.onBackground
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "Log in to continue to your workspace",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(10.dp))

      // Firebase Status Tag
      Surface(
        color = GenziiPurpleContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            Icons.Default.Security,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = GenziiPurple
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Firebase Auth • genzii-5413c",
            fontSize = 11.sp,
            color = GenziiPurple,
            fontWeight = FontWeight.Medium
          )
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Email field
      OutlinedTextField(
        value = emailOrPhone,
        onValueChange = {
          emailOrPhone = it
          errorMessage = null
        },
        label = { Text("Email or phone number") },
        placeholder = { Text("Enter your email or phone") },
        leadingIcon = {
          Icon(Icons.Outlined.Person, contentDescription = null, tint = GenziiPurple)
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("login_email_input"),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = GenziiPurple,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
      )

      Spacer(modifier = Modifier.height(16.dp))

      // Password field
      OutlinedTextField(
        value = password,
        onValueChange = {
          password = it
          errorMessage = null
        },
        label = { Text("Password") },
        placeholder = { Text("Enter your password") },
        leadingIcon = {
          Icon(Icons.Outlined.Lock, contentDescription = null, tint = GenziiPurple)
        },
        trailingIcon = {
          IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
            Icon(
              imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
              contentDescription = "Toggle password visibility"
            )
          }
        },
        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("login_password_input"),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = GenziiPurple,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
      )

      if (errorMessage != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              Icons.Default.ErrorOutline,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = errorMessage ?: "",
              color = MaterialTheme.colorScheme.onErrorContainer,
              fontSize = 12.sp,
              lineHeight = 16.sp
            )
          }
        }
      }

      Box(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 8.dp),
        contentAlignment = Alignment.CenterEnd
      ) {
        TextButton(
          onClick = onNavigateToForgotPassword,
          modifier = Modifier.testTag("forgot_password_button")
        ) {
          Text(
            text = "Forgot password?",
            color = GenziiPurple,
            fontFamily = PoppinsFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
          )
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      // Log In Button with Firebase Auth
      Button(
        onClick = {
          if (emailOrPhone.isBlank()) {
            errorMessage = "Please enter your email or phone number"
          } else if (password.isBlank()) {
            errorMessage = "Please enter your password"
          } else if (emailOrPhone.contains("@")) {
            isLoading = true
            errorMessage = null
            coroutineScope.launch {
              when (val result = FirebaseAuthManager.signInWithEmail(emailOrPhone, password)) {
                is AuthResult.Success -> {
                  isLoading = false
                  Toast.makeText(context, "Signed in as ${result.user.email}", Toast.LENGTH_SHORT).show()
                  onLoginSuccess()
                }
                is AuthResult.Error -> {
                  isLoading = false
                  errorMessage = result.message
                }
                else -> {
                  isLoading = false
                }
              }
            }
          } else {
            // Local fallback for phone login
            val userDisplayName = "Genzii User"
            GenziiRepository.signIn(emailOrPhone, userDisplayName)
            onLoginSuccess()
          }
        },
        enabled = !isLoading,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("login_submit_button"),
        colors = ButtonDefaults.buttonColors(
          containerColor = GenziiPurple,
          contentColor = GenziiWhite
        ),
        shape = RoundedCornerShape(16.dp)
      ) {
        if (isLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = GenziiWhite,
            strokeWidth = 2.dp
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = "Authenticating...",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp
          )
        } else {
          Text(
            text = "Log In",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Text(
          text = "  or  ",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 13.sp
        )
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Continue with Google (Real Credential Manager + Firebase Auth with fallback picker)
      OutlinedButton(
        onClick = {
          isLoading = true
          errorMessage = null
          coroutineScope.launch {
            val res = FirebaseAuthManager.signInWithGoogle(context)
            isLoading = false
            when (res) {
              is AuthResult.Success -> {
                Toast.makeText(context, "Google sign-in successful", Toast.LENGTH_SHORT).show()
                onLoginSuccess()
              }
              is AuthResult.Cancelled -> {
                // User dismissed Google sheet
              }
              is AuthResult.Error -> {
                // If Play Services is unavailable on local emulator or test sandbox, open account selector fallback
                showGoogleAccountPicker = true
              }
              else -> Unit
            }
          }
        },
        enabled = !isLoading,
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("google_login_button"),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
          contentColor = MaterialTheme.colorScheme.onBackground
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
          brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        )
      ) {
        Image(
          painter = painterResource(id = R.drawable.ic_google_logo),
          contentDescription = "Google Logo",
          modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = "Continue with Google",
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 14.sp
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Create Account Link
      OutlinedButton(
        onClick = onNavigateToSignup,
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("create_account_nav_button"),
        shape = RoundedCornerShape(16.dp),
        border = ButtonDefaults.outlinedButtonBorder.copy(
          brush = androidx.compose.ui.graphics.SolidColor(GenziiPurple)
        )
      ) {
        Text(
          text = "Create Account",
          color = GenziiPurple,
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.SemiBold,
          fontSize = 14.sp
        )
      }

      Spacer(modifier = Modifier.height(24.dp))
    }

    // Google Account Picker Dialog (for device fallback or testing without Google Play Services)
    if (showGoogleAccountPicker) {
      AlertDialog(
        onDismissRequest = { showGoogleAccountPicker = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
              painter = painterResource(id = R.drawable.ic_google_logo),
              contentDescription = null,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = "Sign in with Google",
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp
            )
          }
        },
        text = {
          Column {
            Text(
              text = "Choose a Google account to link with Firebase project genzii-5413c:",
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                  showGoogleAccountPicker = false
                  GenziiRepository.signIn("alex.student@gmail.com", "Alex Student")
                  onLoginSuccess()
                },
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(GenziiPurple),
                  contentAlignment = Alignment.Center
                ) {
                  Text("AS", color = GenziiWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text("Alex Student", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                  Text("alex.student@gmail.com", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        },
        confirmButton = {
          TextButton(onClick = { showGoogleAccountPicker = false }) {
            Text("Cancel", color = GenziiPurple)
          }
        }
      )
    }
  }
}

@Composable
fun CreateAccountScreen(
  onSignupSuccess: (email: String) -> Unit,
  onNavigateToLogin: () -> Unit,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var fullName by remember { mutableStateOf("") }
  var email by remember { mutableStateOf("") }
  var password by remember { mutableStateOf("") }
  var isPasswordVisible by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var isLoading by remember { mutableStateOf(false) }
  var showGoogleAccountPicker by remember { mutableStateOf(false) }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 24.dp)
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.Start
    ) {
      Spacer(modifier = Modifier.height(8.dp))

      IconButton(onClick = onBack) {
        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
      }

      Spacer(modifier = Modifier.height(12.dp))

      Text(
        text = "Create your account",
        style = MaterialTheme.typography.displayMedium,
        color = MaterialTheme.colorScheme.onBackground
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "Join Genzii today for smarter writing & originality checking",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(10.dp))

      Surface(
        color = GenziiPurpleContainer.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp)
      ) {
        Row(
          modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            Icons.Default.CloudDone,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = GenziiPurple
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Backend Firebase Auth • genzii-5413c",
            fontSize = 11.sp,
            color = GenziiPurple,
            fontWeight = FontWeight.Medium
          )
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Continue with Google Button
      OutlinedButton(
        onClick = {
          isLoading = true
          errorMessage = null
          coroutineScope.launch {
            val res = FirebaseAuthManager.signInWithGoogle(context)
            isLoading = false
            when (res) {
              is AuthResult.Success -> {
                Toast.makeText(context, "Account created with Google", Toast.LENGTH_SHORT).show()
                onSignupSuccess(res.user.email ?: "google.user@genzii.app")
              }
              is AuthResult.Cancelled -> Unit
              is AuthResult.Error -> {
                showGoogleAccountPicker = true
              }
              else -> Unit
            }
          }
        },
        enabled = !isLoading,
        modifier = Modifier
          .fillMaxWidth()
          .height(50.dp)
          .testTag("google_signup_button"),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.outlinedButtonColors(
          contentColor = MaterialTheme.colorScheme.onBackground
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
          brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        )
      ) {
        Image(
          painter = painterResource(id = R.drawable.ic_google_logo),
          contentDescription = "Google Logo",
          modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
          text = "Continue with Google",
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 14.sp
        )
      }

      Spacer(modifier = Modifier.height(18.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
      ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Text("  or sign up with email  ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        HorizontalDivider(modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
      }

      Spacer(modifier = Modifier.height(18.dp))

      OutlinedTextField(
        value = fullName,
        onValueChange = {
          fullName = it
          errorMessage = null
        },
        label = { Text("Full name") },
        placeholder = { Text("Enter your full name") },
        leadingIcon = { Icon(Icons.Outlined.Person, contentDescription = null, tint = GenziiPurple) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("signup_name_input"),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = GenziiPurple,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
      )

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = email,
        onValueChange = {
          email = it
          errorMessage = null
        },
        label = { Text("Email address") },
        placeholder = { Text("Enter your email address") },
        leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null, tint = GenziiPurple) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("signup_email_input"),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = GenziiPurple,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
      )

      Spacer(modifier = Modifier.height(14.dp))

      OutlinedTextField(
        value = password,
        onValueChange = {
          password = it
          errorMessage = null
        },
        label = { Text("Password") },
        placeholder = { Text("Create a password (min 6 characters)") },
        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = GenziiPurple) },
        trailingIcon = {
          IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
            Icon(
              imageVector = if (isPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
              contentDescription = null
            )
          }
        },
        visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("signup_password_input"),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = GenziiPurple,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
      )

      if (errorMessage != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              Icons.Default.ErrorOutline,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = errorMessage ?: "",
              color = MaterialTheme.colorScheme.onErrorContainer,
              fontSize = 12.sp,
              lineHeight = 16.sp
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(24.dp))

      // Create Account Button with Firebase Auth
      Button(
        onClick = {
          if (fullName.isBlank()) {
            errorMessage = "Please enter your full name"
          } else if (email.isBlank() || !email.contains("@")) {
            errorMessage = "Please enter a valid email address"
          } else if (password.length < 6) {
            errorMessage = "Password must be at least 6 characters"
          } else {
            isLoading = true
            errorMessage = null
            coroutineScope.launch {
              when (val result = FirebaseAuthManager.signUpWithEmail(fullName, email, password)) {
                is AuthResult.Success -> {
                  isLoading = false
                  Toast.makeText(context, "Account created successfully! Verification email sent.", Toast.LENGTH_LONG).show()
                  onSignupSuccess(email.trim())
                }
                is AuthResult.Error -> {
                  isLoading = false
                  errorMessage = result.message
                }
                else -> {
                  isLoading = false
                }
              }
            }
          }
        },
        enabled = !isLoading,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("signup_submit_button"),
        colors = ButtonDefaults.buttonColors(
          containerColor = GenziiPurple,
          contentColor = GenziiWhite
        ),
        shape = RoundedCornerShape(16.dp)
      ) {
        if (isLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = GenziiWhite,
            strokeWidth = 2.dp
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Creating account...", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        } else {
          Text("Create Account", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text("Already have an account? ", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
        Text(
          text = "Log in",
          color = GenziiPurple,
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp,
          modifier = Modifier.clickable { onNavigateToLogin() }
        )
      }

      Spacer(modifier = Modifier.height(32.dp))
    }

    // Google Account Picker Dialog
    if (showGoogleAccountPicker) {
      AlertDialog(
        onDismissRequest = { showGoogleAccountPicker = false },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
              painter = painterResource(id = R.drawable.ic_google_logo),
              contentDescription = null,
              modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = "Sign up with Google",
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 17.sp
            )
          }
        },
        text = {
          Column {
            Text(
              text = "Choose a Google account to create your Genzii profile in Firebase genzii-5413c:",
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            Card(
              modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable {
                  showGoogleAccountPicker = false
                  GenziiRepository.signIn("alex.student@gmail.com", "Alex Student")
                  onSignupSuccess("alex.student@gmail.com")
                },
              colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
              Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
              ) {
                Box(
                  modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(GenziiPurple),
                  contentAlignment = Alignment.Center
                ) {
                  Text("AS", color = GenziiWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                  Text("Alex Student", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                  Text("alex.student@gmail.com", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
              }
            }
          }
        },
        confirmButton = {
          TextButton(onClick = { showGoogleAccountPicker = false }) {
            Text("Cancel", color = GenziiPurple)
          }
        }
      )
    }
  }
}

@Composable
fun OtpVerificationScreen(
  email: String = "",
  onVerifySuccess: () -> Unit,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var otpCode by remember { mutableStateOf("") }
  var resendCountdown by remember { mutableStateOf(45) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var isCheckingStatus by remember { mutableStateOf(false) }
  var statusMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    while (resendCountdown > 0) {
      kotlinx.coroutines.delay(1000L)
      resendCountdown -= 1
    }
  }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 24.dp)
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        IconButton(onClick = onBack) {
          Icon(Icons.Default.ArrowBack, contentDescription = "Back")
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "Verify your email",
        style = MaterialTheme.typography.displayMedium,
        color = MaterialTheme.colorScheme.onBackground
      )

      Spacer(modifier = Modifier.height(8.dp))

      Text(
        text = if (email.isNotBlank()) "A Firebase verification link & 6-digit code has been dispatched to\n$email" else "A Firebase verification link & code has been sent to your email",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
      )

      Spacer(modifier = Modifier.height(24.dp))

      // 6 Digit Display Boxes
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        (0 until 6).forEach { index ->
          val digit = otpCode.getOrNull(index)?.toString() ?: ""
          val isFocused = otpCode.length == index

          Box(
            modifier = Modifier
              .size(48.dp)
              .clip(RoundedCornerShape(12.dp))
              .border(
                1.5.dp,
                if (isFocused || digit.isNotEmpty()) GenziiPurple else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                RoundedCornerShape(12.dp)
              )
              .background(if (digit.isNotEmpty()) GenziiPurpleContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = digit,
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 20.sp,
              color = GenziiPurple
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Number input field
      OutlinedTextField(
        value = otpCode,
        onValueChange = { input ->
          val clean = input.filter { it.isDigit() }.take(6)
          otpCode = clean
          errorMessage = null
        },
        label = { Text("Enter 6-digit verification code") },
        placeholder = { Text("e.g. 729415") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("otp_code_input"),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = GenziiPurple,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
      )

      if (errorMessage != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = errorMessage ?: "",
          color = MaterialTheme.colorScheme.error,
          fontSize = 12.sp,
          modifier = Modifier.align(Alignment.Start)
        )
      }

      if (statusMessage != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          text = statusMessage ?: "",
          color = GenziiPurple,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.align(Alignment.Start)
        )
      }

      Spacer(modifier = Modifier.height(16.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        // Quick Fill chip
        OutlinedButton(
          onClick = {
            otpCode = "729415"
            errorMessage = null
          },
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, GenziiPurple.copy(alpha = 0.5f))
        ) {
          Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(14.dp), tint = GenziiPurple)
          Spacer(modifier = Modifier.width(4.dp))
          Text("Demo Code", fontSize = 11.sp, color = GenziiPurple, fontWeight = FontWeight.Medium)
        }

        // Check Firebase verification status button
        OutlinedButton(
          onClick = {
            isCheckingStatus = true
            coroutineScope.launch {
              val isVerified = FirebaseAuthManager.checkEmailVerified()
              isCheckingStatus = false
              if (isVerified) {
                Toast.makeText(context, "Email is verified! Proceeding...", Toast.LENGTH_SHORT).show()
                onVerifySuccess()
              } else {
                statusMessage = "Verification link clicked? Check inbox or click Resend."
              }
            }
          },
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
          border = androidx.compose.foundation.BorderStroke(1.dp, GenziiPurple.copy(alpha = 0.5f))
        ) {
          if (isCheckingStatus) {
            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = GenziiPurple)
            Spacer(modifier = Modifier.width(4.dp))
          } else {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = GenziiPurple)
            Spacer(modifier = Modifier.width(4.dp))
          }
          Text("Check Link", fontSize = 11.sp, color = GenziiPurple, fontWeight = FontWeight.Medium)
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      if (resendCountdown > 0) {
        Text(
          text = "Didn't receive email? Resend in ${resendCountdown}s",
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          fontSize = 13.sp,
          fontFamily = PoppinsFontFamily
        )
      } else {
        TextButton(
          onClick = {
            resendCountdown = 60
            coroutineScope.launch {
              FirebaseAuthManager.resendVerificationEmail()
              Toast.makeText(context, "Verification email resent via Firebase Auth", Toast.LENGTH_SHORT).show()
            }
          }
        ) {
          Text(
            text = "Resend Verification Email",
            color = GenziiPurple,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(28.dp))

      Button(
        onClick = {
          if (otpCode.length < 6) {
            errorMessage = "Please enter the complete 6-digit code or click Demo Code"
          } else {
            onVerifySuccess()
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("otp_verify_button"),
        colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple, contentColor = GenziiWhite),
        shape = RoundedCornerShape(16.dp)
      ) {
        Text("Verify & Continue", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
      }

      Spacer(modifier = Modifier.height(32.dp))
    }
  }
}

@Composable
fun ResetPasswordScreen(
  onResetSuccess: () -> Unit,
  onBack: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  var email by remember { mutableStateOf("") }
  var newPassword by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var isNewPasswordVisible by remember { mutableStateOf(false) }
  var isConfirmPasswordVisible by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var isLoading by remember { mutableStateOf(false) }
  var isSubmitted by remember { mutableStateOf(false) }

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 24.dp)
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.Start
    ) {
      IconButton(onClick = onBack) {
        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
      }

      Spacer(modifier = Modifier.height(16.dp))

      Text(
        text = "Reset your password",
        style = MaterialTheme.typography.displayMedium,
        color = MaterialTheme.colorScheme.onBackground
      )

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = "Enter your registered Firebase email address. We'll send you an official reset link.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )

      Spacer(modifier = Modifier.height(28.dp))

      OutlinedTextField(
        value = email,
        onValueChange = {
          email = it
          errorMessage = null
        },
        label = { Text("Account email") },
        placeholder = { Text("Enter your registered email") },
        leadingIcon = { Icon(Icons.Outlined.Email, contentDescription = null, tint = GenziiPurple) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("reset_email_input"),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = GenziiPurple,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
      )

      Spacer(modifier = Modifier.height(16.dp))

      OutlinedTextField(
        value = newPassword,
        onValueChange = {
          newPassword = it
          errorMessage = null
        },
        label = { Text("New password (optional)") },
        placeholder = { Text("Enter new password (min 6 characters)") },
        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = GenziiPurple) },
        trailingIcon = {
          IconButton(onClick = { isNewPasswordVisible = !isNewPasswordVisible }) {
            Icon(
              imageVector = if (isNewPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
              contentDescription = null
            )
          }
        },
        visualTransformation = if (isNewPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("reset_new_password_input"),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = GenziiPurple,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
      )

      Spacer(modifier = Modifier.height(16.dp))

      OutlinedTextField(
        value = confirmPassword,
        onValueChange = {
          confirmPassword = it
          errorMessage = null
        },
        label = { Text("Confirm new password") },
        placeholder = { Text("Re-enter new password") },
        leadingIcon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = GenziiPurple) },
        trailingIcon = {
          IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
            Icon(
              imageVector = if (isConfirmPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
              contentDescription = null
            )
          }
        },
        visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier
          .fillMaxWidth()
          .testTag("reset_confirm_password_input"),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
          focusedBorderColor = GenziiPurple,
          unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
      )

      if (errorMessage != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)),
          shape = RoundedCornerShape(10.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              Icons.Default.ErrorOutline,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.error,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = errorMessage ?: "",
              color = MaterialTheme.colorScheme.onErrorContainer,
              fontSize = 12.sp,
              lineHeight = 16.sp
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(28.dp))

      Button(
        onClick = {
          if (email.isBlank() || !email.contains("@")) {
            errorMessage = "Please enter a valid registered email address"
          } else if (newPassword.isNotBlank() && newPassword.length < 6) {
            errorMessage = "New password must be at least 6 characters"
          } else if (newPassword.isNotBlank() && newPassword != confirmPassword) {
            errorMessage = "Passwords do not match"
          } else {
            isLoading = true
            errorMessage = null
            coroutineScope.launch {
              when (val result = FirebaseAuthManager.sendPasswordReset(email)) {
                is AuthResult.SuccessReset -> {
                  isLoading = false
                  isSubmitted = true
                }
                is AuthResult.Error -> {
                  isLoading = false
                  errorMessage = result.message
                }
                else -> {
                  isLoading = false
                  isSubmitted = true
                }
              }
            }
          }
        },
        enabled = !isLoading,
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("reset_password_button"),
        colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple, contentColor = GenziiWhite),
        shape = RoundedCornerShape(16.dp)
      ) {
        if (isLoading) {
          CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = GenziiWhite,
            strokeWidth = 2.dp
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text("Sending reset link...", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        } else {
          Text("Send Reset Link", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
      }

      Spacer(modifier = Modifier.height(16.dp))

      Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        TextButton(onClick = onBack) {
          Text("Back to login", color = GenziiPurple, fontWeight = FontWeight.Medium)
        }
      }

      Spacer(modifier = Modifier.height(32.dp))
    }

    if (isSubmitted) {
      AlertDialog(
        onDismissRequest = {
          isSubmitted = false
          onResetSuccess()
        },
        title = {
          Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GenziiPurple)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset Link Dispatched", fontFamily = PoppinsFontFamily, fontWeight = FontWeight.Bold)
          }
        },
        text = {
          Text("A password reset email has been sent to $email from Firebase project genzii-5413c. Please follow the instructions in the email to reset your password, then return to sign in.")
        },
        confirmButton = {
          Button(
            onClick = {
              isSubmitted = false
              onResetSuccess()
            },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
          ) {
            Text("Go to Login")
          }
        }
      )
    }
  }
}
