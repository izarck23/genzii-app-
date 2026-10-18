package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.GenziiLogo
import com.example.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
  onFinishSplash: () -> Unit
) {
  LaunchedEffect(Unit) {
    delay(2000)
    onFinishSplash()
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(GenziiPurple)
      .clickable { onFinishSplash() }
      .testTag("splash_screen"),
    contentAlignment = Alignment.Center
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      GenziiLogo(
        isWhite = true,
        showTagline = true,
        logoSize = 56.dp
      )
    }

    Text(
      text = "Version 1.3.0 (Build 4) • Smart AI & Permissions Release",
      color = GenziiPurpleSoft.copy(alpha = 0.8f),
      fontSize = 11.sp,
      fontFamily = PoppinsFontFamily,
      modifier = Modifier
        .align(Alignment.BottomCenter)
        .navigationBarsPadding()
        .padding(bottom = 24.dp)
    )
  }
}

data class OnboardingStep(
  val title: String,
  val subtitle: String,
  val imageRes: Int,
  val bullets: List<String>
)

@Composable
fun OnboardingScreen(
  onFinishOnboarding: () -> Unit
) {
  var currentStep by remember { mutableStateOf(0) }

  val steps = listOf(
    OnboardingStep(
      title = "Smart AI",
      subtitle = "Write, improve, summarize and get things done faster.",
      imageRes = R.drawable.img_onboarding_1,
      bullets = listOf(
        "AI writing assistant",
        "Rewrite & improve",
        "Summarize content",
        "Generate ideas"
      )
    ),
    OnboardingStep(
      title = "Originality Checker",
      subtitle = "Check for similarity and find trusted sources.",
      imageRes = R.drawable.img_onboarding_2,
      bullets = listOf(
        "Detect matching content",
        "View source links",
        "Highlight similarities",
        "Improve your writing"
      )
    ),
    OnboardingStep(
      title = "Secure Vault",
      subtitle = "Keep your files, notes and conversations safe.",
      imageRes = R.drawable.img_onboarding_3,
      bullets = listOf(
        "Secure storage",
        "Organize with folders",
        "Access across devices",
        "You're in control"
      )
    )
  )

  val step = steps[currentStep]

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(horizontal = 24.dp, vertical = 16.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      // Top row with skip
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        GenziiLogo(logoSize = 26.dp)

        TextButton(
          onClick = onFinishOnboarding,
          modifier = Modifier.testTag("onboarding_skip_button")
        ) {
          Text(
            text = "Skip",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
          )
        }
      }

      // Illustration Area
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .weight(1f)
          .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
      ) {
        Card(
          shape = RoundedCornerShape(24.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
          elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
        ) {
          Image(
            painter = painterResource(id = step.imageRes),
            contentDescription = step.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
          )
        }
      }

      // Content Area: Title, Subtitle, Checkmarks
      Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
      ) {
        Text(
          text = step.title,
          style = MaterialTheme.typography.displayMedium,
          color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = step.subtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Bullet checklist
        step.bullets.forEach { bullet ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Box(
              modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(GenziiPurpleContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = GenziiPurple,
                modifier = Modifier.size(13.dp)
              )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = bullet,
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Medium,
              fontSize = 14.sp,
              color = MaterialTheme.colorScheme.onBackground
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Dots Indicator
      Row(
        modifier = Modifier.padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.Center
      ) {
        repeat(steps.size) { index ->
          val isSelected = index == currentStep
          Box(
            modifier = Modifier
              .padding(horizontal = 4.dp)
              .height(8.dp)
              .width(if (isSelected) 24.dp else 8.dp)
              .clip(CircleShape)
              .background(if (isSelected) GenziiPurple else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
          )
        }
      }

      // Bottom Action Button
      Button(
        onClick = {
          if (currentStep < steps.size - 1) {
            currentStep++
          } else {
            onFinishOnboarding()
          }
        },
        modifier = Modifier
          .fillMaxWidth()
          .height(52.dp)
          .testTag("onboarding_primary_button"),
        colors = ButtonDefaults.buttonColors(
          containerColor = GenziiPurple,
          contentColor = GenziiWhite
        ),
        shape = RoundedCornerShape(16.dp)
      ) {
        Text(
          text = if (currentStep == steps.size - 1) "Get Started" else "Next",
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.SemiBold,
          fontSize = 16.sp
        )
      }
    }
  }
}
