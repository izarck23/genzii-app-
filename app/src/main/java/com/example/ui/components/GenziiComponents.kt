package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.FileType
import com.example.ui.theme.*

@Composable
fun GenziiLogo(
  modifier: Modifier = Modifier,
  isWhite: Boolean = false,
  showTagline: Boolean = false,
  logoSize: Dp = 32.dp
) {
  val primaryColor = if (isWhite) GenziiWhite else GenziiPurple
  val secondaryColor = if (isWhite) GenziiPurpleSoft else GenziiPurpleLight

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Box(
        modifier = Modifier
          .size(logoSize)
          .clip(CircleShape)
          .background(primaryColor),
        contentAlignment = Alignment.Center
      ) {
        // Monogram stylized g
        Text(
          text = "g",
          color = if (isWhite) GenziiPurple else GenziiWhite,
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = (logoSize.value * 0.65).sp,
          lineHeight = (logoSize.value * 0.65).sp,
          modifier = Modifier.offset(y = (-2).dp)
        )
        // Accent dot
        Box(
          modifier = Modifier
            .size((logoSize.value * 0.28).dp)
            .align(Alignment.TopEnd)
            .offset(x = (-2).dp, y = (2).dp)
            .clip(CircleShape)
            .border(1.5.dp, if (isWhite) GenziiPurple else GenziiWhite, CircleShape)
            .background(secondaryColor)
        )
      }

      Spacer(modifier = Modifier.width(8.dp))

      Text(
        text = "genzii",
        color = primaryColor,
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = (logoSize.value * 0.72).sp,
        letterSpacing = (-0.5).sp
      )
    }

    if (showTagline) {
      Spacer(modifier = Modifier.height(12.dp))
      Text(
        text = "Think smarter. Write originally. Keep everything secure.",
        color = if (isWhite) GenziiPurpleSoft else GenziiTextSecondary,
        fontFamily = PoppinsFontFamily,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(horizontal = 24.dp)
      )
    }
  }
}

@Composable
fun GenziiTopBar(
  title: String? = null,
  showBack: Boolean = false,
  onBack: () -> Unit = {},
  unreadCount: Int = 0,
  onNotificationsClick: () -> Unit = {},
  onProfileClick: () -> Unit = {}
) {
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .statusBarsPadding()
      .padding(horizontal = 16.dp, vertical = 12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    if (showBack) {
      IconButton(
        onClick = onBack,
        modifier = Modifier.testTag("top_bar_back_button")
      ) {
        Icon(
          imageVector = Icons.Default.ArrowBack,
          contentDescription = "Back",
          tint = MaterialTheme.colorScheme.onBackground
        )
      }
    } else {
      GenziiLogo(logoSize = 30.dp)
    }

    if (title != null && showBack) {
      Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
      )
    } else {
      Spacer(modifier = Modifier.weight(1f))
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
      // Notification bell
      Box(
        modifier = Modifier
          .size(40.dp)
          .clip(CircleShape)
          .clickable(onClick = onNotificationsClick)
          .testTag("notifications_icon_button"),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Outlined.Notifications,
          contentDescription = "Notifications",
          tint = MaterialTheme.colorScheme.onBackground
        )
        if (unreadCount > 0) {
          Box(
            modifier = Modifier
              .size(8.dp)
              .align(Alignment.TopEnd)
              .offset(x = (-8).dp, y = 8.dp)
              .clip(CircleShape)
              .background(GenziiError)
          )
        }
      }

      Spacer(modifier = Modifier.width(8.dp))

      // User avatar
      Box(
        modifier = Modifier
          .size(38.dp)
          .clip(CircleShape)
          .background(GenziiPurpleContainer)
          .border(1.5.dp, GenziiPurple, CircleShape)
          .clickable(onClick = onProfileClick)
          .testTag("avatar_button"),
        contentAlignment = Alignment.Center
      ) {
        Text(
          text = "JD",
          color = GenziiPurple,
          fontWeight = FontWeight.Bold,
          fontSize = 13.sp,
          fontFamily = PoppinsFontFamily
        )
      }
    }
  }
}

enum class NavigationTab(val label: String, val iconSelected: ImageVector, val iconUnselected: ImageVector) {
  HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
  AI("AI", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome),
  CHECK("Check", Icons.Filled.VerifiedUser, Icons.Outlined.VerifiedUser),
  VAULT("Vault", Icons.Filled.Lock, Icons.Outlined.Lock),
  PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

@Composable
fun GenziiBottomNavBar(
  currentTab: NavigationTab,
  onTabSelected: (NavigationTab) -> Unit
) {
  Surface(
    modifier = Modifier
      .fillMaxWidth()
      .navigationBarsPadding(),
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 8.dp,
    shadowElevation = 16.dp
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 8.dp, horizontal = 12.dp),
      horizontalArrangement = Arrangement.SpaceAround,
      verticalAlignment = Alignment.CenterVertically
    ) {
      NavigationTab.values().forEach { tab ->
        val isSelected = currentTab == tab
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { onTabSelected(tab) }
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .testTag("nav_tab_${tab.name.lowercase()}")
        ) {
          Box(
            modifier = Modifier
              .size(32.dp)
              .clip(CircleShape)
              .background(if (isSelected) GenziiPurpleContainer else Color.Transparent),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = if (isSelected) tab.iconSelected else tab.iconUnselected,
              contentDescription = tab.label,
              tint = if (isSelected) GenziiPurple else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
              modifier = Modifier.size(22.dp)
            )
          }

          Spacer(modifier = Modifier.height(2.dp))

          Text(
            text = tab.label,
            fontFamily = PoppinsFontFamily,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) GenziiPurple else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
          )
        }
      }
    }
  }
}

@Composable
fun OriginalityGauge(
  originalPercentage: Int,
  similarityPercentage: Int,
  modifier: Modifier = Modifier,
  size: Dp = 190.dp
) {
  val animatedOriginal = animateFloatAsState(
    targetValue = originalPercentage / 100f,
    animationSpec = tween(durationMillis = 1000),
    label = "originalGauge"
  )

  Column(
    modifier = modifier,
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    Box(
      modifier = Modifier.size(size),
      contentAlignment = Alignment.Center
    ) {
      Canvas(modifier = Modifier.fillMaxSize()) {
        val strokeWidth = 14.dp.toPx()
        val diameter = size.toPx() - strokeWidth
        val topLeft = strokeWidth / 2f

        // Background track (subtle light track)
        drawArc(
          color = Color(0xFFEFEFF7),
          startAngle = -90f,
          sweepAngle = 360f,
          useCenter = false,
          topLeft = androidx.compose.ui.geometry.Offset(topLeft, topLeft),
          size = androidx.compose.ui.geometry.Size(diameter, diameter),
          style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Active original sweep
        drawArc(
          brush = Brush.sweepGradient(
            colors = listOf(
              GenziiPurple,
              GenziiPurpleLight,
              GenziiPurple
            )
          ),
          startAngle = -90f,
          sweepAngle = 360f * animatedOriginal.value,
          useCenter = false,
          topLeft = androidx.compose.ui.geometry.Offset(topLeft, topLeft),
          size = androidx.compose.ui.geometry.Size(diameter, diameter),
          style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
      }

      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = "$originalPercentage%",
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 36.sp,
          color = MaterialTheme.colorScheme.onBackground
        )
        Text(
          text = "Original",
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.Medium,
          fontSize = 14.sp,
          color = GenziiPurple
        )
      }
    }

    Spacer(modifier = Modifier.height(10.dp))

    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      Box(
        modifier = Modifier
          .size(8.dp)
          .clip(CircleShape)
          .background(GenziiPurple)
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = "$originalPercentage% Original",
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground
      )
      Spacer(modifier = Modifier.width(16.dp))
      Box(
        modifier = Modifier
          .size(8.dp)
          .clip(CircleShape)
          .background(Color(0xFFE0E0E0))
      )
      Spacer(modifier = Modifier.width(6.dp))
      Text(
        text = "$similarityPercentage% Similarity",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun PlanBannerCard(
  checksLeft: Int,
  totalChecks: Int,
  tier: String,
  onUpgradeClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    shape = RoundedCornerShape(18.dp),
    colors = CardDefaults.cardColors(
      containerColor = GenziiPurple
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
  ) {
    Column(
      modifier = Modifier.padding(18.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Box(
            modifier = Modifier
              .size(36.dp)
              .clip(CircleShape)
              .background(GenziiWhite.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
          ) {
            Icon(
              imageVector = Icons.Filled.WorkspacePremium,
              contentDescription = null,
              tint = GenziiWhite,
              modifier = Modifier.size(20.dp)
            )
          }
          Spacer(modifier = Modifier.width(10.dp))
          Column {
            Text(
              text = if (tier == "FREE") "Free Plan" else "$tier Plan",
              color = GenziiWhite,
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 16.sp
            )
            Text(
              text = "Get higher limits & advanced features",
              color = GenziiPurpleSoft,
              fontSize = 12.sp
            )
          }
        }

        Button(
          onClick = onUpgradeClick,
          colors = ButtonDefaults.buttonColors(
            containerColor = GenziiWhite,
            contentColor = GenziiPurple
          ),
          shape = RoundedCornerShape(20.dp),
          contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
          modifier = Modifier.testTag("upgrade_plan_button")
        ) {
          Text(
            text = "Upgrade",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Text(
          text = "Checks left: $checksLeft / $totalChecks",
          color = GenziiWhite,
          fontSize = 12.sp,
          fontWeight = FontWeight.Medium
        )
        Text(
          text = "${((checksLeft.toFloat() / totalChecks) * 100).toInt()}%",
          color = GenziiPurpleSoft,
          fontSize = 12.sp
        )
      }

      Spacer(modifier = Modifier.height(6.dp))

      LinearProgressIndicator(
        progress = { checksLeft.toFloat() / totalChecks },
        modifier = Modifier
          .fillMaxWidth()
          .height(6.dp)
          .clip(RoundedCornerShape(4.dp)),
        color = GenziiWhite,
        trackColor = GenziiWhite.copy(alpha = 0.25f),
      )
    }
  }
}

@Composable
fun StatCard(
  value: String,
  label: String,
  modifier: Modifier = Modifier,
  icon: ImageVector? = null
) {
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 14.dp, horizontal = 12.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      if (icon != null) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = GenziiPurple,
          modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
      }
      Text(
        text = value,
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        color = GenziiPurple
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = label,
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
fun FileTypeIcon(
  type: FileType,
  modifier: Modifier = Modifier
) {
  val (bgColor, iconVector) = when (type) {
    FileType.PDF -> Color(0xFFFFECEB) to Icons.Default.PictureAsPdf
    FileType.DOCX -> Color(0xFFEBF3FF) to Icons.Default.Description
    FileType.PPTX -> Color(0xFFFFF0EB) to Icons.Default.Slideshow
    FileType.TXT -> Color(0xFFEBFBF3) to Icons.Default.Article
    FileType.IMAGE -> Color(0xFFF3EBFF) to Icons.Default.Image
    FileType.AUDIO -> Color(0xFFFFF8E1) to Icons.Default.Audiotrack
    FileType.OTHER -> Color(0xFFEDE7F6) to Icons.Default.InsertDriveFile
  }
  val iconTint = when (type) {
    FileType.PDF -> Color(0xFFE53935)
    FileType.DOCX -> Color(0xFF1E88E5)
    FileType.PPTX -> Color(0xFFFB8C00)
    FileType.TXT -> Color(0xFF43A047)
    FileType.IMAGE -> GenziiPurple
    FileType.AUDIO -> Color(0xFFF57F17)
    FileType.OTHER -> Color(0xFF7E57C2)
  }

  Box(
    modifier = modifier
      .size(42.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(bgColor),
    contentAlignment = Alignment.Center
  ) {
    Icon(
      imageVector = iconVector,
      contentDescription = type.name,
      tint = iconTint,
      modifier = Modifier.size(22.dp)
    )
  }
}
