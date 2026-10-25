package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GenziiRepository
import com.example.ui.components.GenziiLogo
import com.example.ui.components.NavigationTab
import com.example.ui.theme.*

@Composable
fun WelcomeBackScreen(
  onContinue: () -> Unit,
  onQuickAction: (NavigationTab) -> Unit,
  onUpgrade: () -> Unit
) {
  val userProfile by GenziiRepository.userProfile.collectAsState()

  Scaffold(
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .statusBarsPadding()
        .navigationBarsPadding()
        .padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.SpaceBetween
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        GenziiLogo(logoSize = 40.dp)

        Spacer(modifier = Modifier.height(30.dp))

        val firstName = androidx.compose.runtime.remember(userProfile.name) {
          userProfile.name.split(" ").firstOrNull()?.takeIf { it.isNotBlank() } ?: "Scholar"
        }
        Text(
          text = "Hi, $firstName 👋",
          style = MaterialTheme.typography.displayMedium,
          color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
          text = "Let's make today productive.",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      // Workspace Highlights Card (Production Preview - No Paywall)
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = GenziiPurple),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Box(
            modifier = Modifier
              .size(48.dp)
              .clip(RoundedCornerShape(14.dp))
              .background(GenziiWhite.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Filled.VerifiedUser,
              contentDescription = null,
              tint = GenziiWhite,
              modifier = Modifier.size(28.dp)
            )
          }

          Spacer(modifier = Modifier.height(14.dp))

          Text(
            text = "Workspace Ready",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = GenziiWhite
          )

          Spacer(modifier = Modifier.height(4.dp))

          Text(
            text = "All originality scanning, AI writing assistants, and encrypted document storage are ready for your production session.",
            color = GenziiPurpleSoft,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
          )

          Spacer(modifier = Modifier.height(16.dp))

          Button(
            onClick = onContinue,
            colors = ButtonDefaults.buttonColors(
              containerColor = GenziiWhite,
              contentColor = GenziiPurple
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
          ) {
            Text(
              text = "Launch Workspace",
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 15.sp
            )
          }
        }
      }

      // Quick Actions Row
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceAround
        ) {
          QuickActionButton(label = "AI Writer", icon = Icons.Filled.EditNote) { onQuickAction(NavigationTab.AI) }
          QuickActionButton(label = "Check", icon = Icons.Filled.VerifiedUser) { onQuickAction(NavigationTab.CHECK) }
          QuickActionButton(label = "Vault", icon = Icons.Filled.Lock) { onQuickAction(NavigationTab.VAULT) }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
          onClick = onContinue,
          modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag("welcome_continue_button"),
          colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple),
          shape = RoundedCornerShape(16.dp)
        ) {
          Text(
            text = "Go to Workspace",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
          )
        }
      }
    }
  }
}
