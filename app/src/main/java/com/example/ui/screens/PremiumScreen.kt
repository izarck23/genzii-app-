package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.GenziiRepository
import com.example.model.*
import com.example.ui.theme.*

@Composable
fun PremiumScreen(
  onBack: () -> Unit
) {
  BackHandler { onBack() }

  val context = LocalContext.current
  val userProfile by GenziiRepository.userProfile.collectAsState()
  var isAnnualBilling by remember { mutableStateOf(false) }
  var showSuccessDialog by remember { mutableStateOf(false) }
  var upgradedTierName by remember { mutableStateOf("") }

  Scaffold(
    topBar = {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .statusBarsPadding()
          .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        IconButton(
          onClick = onBack,
          modifier = Modifier.testTag("premium_back_button")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onBackground
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Genzii Premium",
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = MaterialTheme.colorScheme.onBackground
        )
      }
    },
    containerColor = MaterialTheme.colorScheme.background
  ) { padding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(padding)
        .padding(horizontal = 16.dp)
        .verticalScroll(rememberScrollState()),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Spacer(modifier = Modifier.height(6.dp))

      // --- HERO UPGRADE BANNER ---
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("premium_hero_banner"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(
              Brush.linearGradient(
                colors = listOf(
                  GenziiPurple,
                  Color(0xFF7C3AED),
                  Color(0xFF4C1D95)
                )
              )
            )
            .padding(22.dp)
        ) {
          Column {
            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.Top
            ) {
              Box(
                modifier = Modifier
                  .size(46.dp)
                  .clip(CircleShape)
                  .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.WorkspacePremium,
                  contentDescription = null,
                  tint = Color.White,
                  modifier = Modifier.size(26.dp)
                )
              }

              Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.22f)
              ) {
                Text(
                  text = "CURRENT: ${userProfile.tier.name}",
                  fontFamily = PoppinsFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 11.sp,
                  color = Color.White,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
              text = "Academic Integrity Without Limits",
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 20.sp,
              color = Color.White,
              lineHeight = 26.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
              text = "Unlock live URL verification, deep academic literature matching, batch file scanning, and Gemini AI writing analysis.",
              fontFamily = PoppinsFontFamily,
              fontSize = 13.sp,
              color = Color.White.copy(alpha = 0.88f),
              lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = Color(0xFF86EFAC),
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "${userProfile.checksLeft} checks available • Zero-knowledge enclave",
                fontFamily = PoppinsFontFamily,
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.95f),
                fontWeight = FontWeight.Medium
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // --- BILLING PERIOD SELECTOR ---
      Row(
        modifier = Modifier
          .clip(RoundedCornerShape(16.dp))
          .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
          .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        BillingCycleButton(
          text = "Monthly Billing",
          isSelected = !isAnnualBilling,
          onClick = { isAnnualBilling = false },
          modifier = Modifier.weight(1f)
        )
        BillingCycleButton(
          text = "Annual (Save 20%)",
          isSelected = isAnnualBilling,
          onClick = { isAnnualBilling = true },
          modifier = Modifier.weight(1f)
        )
      }

      Spacer(modifier = Modifier.height(20.dp))

      // --- SUBSCRIPTION PLAN CARDS ---
      // 1. FREE TIER
      PlanPricingCard(
        title = "Genzii Free",
        badge = if (userProfile.tier == SubscriptionTier.FREE) "ACTIVE PLAN" else "FREE",
        badgeColor = Color(0xFF64748B),
        price = "$0",
        period = "forever",
        description = "Core originality scans for individual assignments and essays.",
        highlights = listOf(
          "10 scans / month",
          "Text & Single Document uploads (.txt, .docx, .pdf)",
          "5.0 GB Encrypted Device Enclave",
          "Basic Source & Citation Index"
        ),
        isCurrent = userProfile.tier == SubscriptionTier.FREE,
        actionLabel = if (userProfile.tier == SubscriptionTier.FREE) "Current Plan" else "Downgrade to Free",
        isPopular = false,
        onSelect = {
          GenziiRepository.upgradeTier(SubscriptionTier.FREE)
          upgradedTierName = "Free"
          showSuccessDialog = true
        }
      )

      Spacer(modifier = Modifier.height(14.dp))

      // 2. PLUS TIER (MOST POPULAR)
      PlanPricingCard(
        title = "Genzii Plus",
        badge = if (userProfile.tier == SubscriptionTier.PLUS) "ACTIVE PLAN" else "MOST POPULAR",
        badgeColor = GenziiPurple,
        price = if (isAnnualBilling) "$3.99" else "$4.99",
        period = if (isAnnualBilling) "/mo (billed $47.88/yr)" else "/month",
        description = "Designed for university students and independent scholars.",
        highlights = listOf(
          "100 originality scans / month",
          "URL Originality Checker (Public web articles)",
          "Advanced Plagiarism Detection & source links",
          "Gemini AI Content Analysis & explanations",
          "Certified PDF Evidence Reports with highlights",
          "Smart Rewrite Assistant",
          "50.0 GB Encrypted Enclave Storage"
        ),
        isCurrent = userProfile.tier == SubscriptionTier.PLUS,
        actionLabel = if (userProfile.tier == SubscriptionTier.PLUS) "Current Plan" else "Upgrade to Plus",
        isPopular = true,
        onSelect = {
          GenziiRepository.upgradeTier(SubscriptionTier.PLUS)
          upgradedTierName = "Plus"
          showSuccessDialog = true
        }
      )

      Spacer(modifier = Modifier.height(14.dp))

      // 3. PRO TIER (RESEARCHERS)
      PlanPricingCard(
        title = "Genzii Pro",
        badge = if (userProfile.tier == SubscriptionTier.PRO) "ACTIVE PLAN" else "FOR RESEARCHERS",
        badgeColor = Color(0xFF7C3AED),
        price = if (isAnnualBilling) "$7.99" else "$9.99",
        period = if (isAnnualBilling) "/mo (billed $95.88/yr)" else "/month",
        description = "Institutional-grade verification for thesis authors and academic labs.",
        highlights = listOf(
          "999 originality scans / month (Unlimited Fair-Use)",
          "Multiple File Scanning (Batch PDF, DOCX, TXT)",
          "Citation & Format Checker (APA, MLA, Harvard, Chicago)",
          "Side-by-Side Document Comparison Diff",
          "Deep Journal & Repository Cross-Referencing",
          "Fast-lane Neural Processing Cloud",
          "100.0 GB Encrypted Vault Storage"
        ),
        isCurrent = userProfile.tier == SubscriptionTier.PRO,
        actionLabel = if (userProfile.tier == SubscriptionTier.PRO) "Current Plan" else "Upgrade to Pro",
        isPopular = false,
        onSelect = {
          GenziiRepository.upgradeTier(SubscriptionTier.PRO)
          upgradedTierName = "Pro"
          showSuccessDialog = true
        }
      )

      Spacer(modifier = Modifier.height(28.dp))

      // --- SECTION: 10 RECOMMENDED PREMIUM FEATURES ---
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Premium Verification Suite",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onBackground
          )
          Text(
            text = "Professional tools to inspect, verify, and cite research",
            fontFamily = PoppinsFontFamily,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Grid of 10 Features
      PremiumFeature.entries.forEach { feature ->
        FeatureDetailCard(feature = feature, userTier = userProfile.tier)
        Spacer(modifier = Modifier.height(10.dp))
      }

      Spacer(modifier = Modifier.height(24.dp))

      // --- PLAN COMPARISON TABLE ---
      Text(
        text = "Feature Matrix Comparison",
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxWidth()
      )
      Spacer(modifier = Modifier.height(12.dp))

      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          // Table Header
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(GenziiPurpleContainer.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
              .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Capability",
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 11.5.sp,
              color = GenziiPurple,
              modifier = Modifier.weight(1.3f)
            )
            Text(
              text = "Free",
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.SemiBold,
              fontSize = 11.sp,
              textAlign = TextAlign.Center,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(0.9f)
            )
            Text(
              text = "Plus",
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              textAlign = TextAlign.Center,
              color = GenziiPurple,
              modifier = Modifier.weight(0.9f)
            )
            Text(
              text = "Pro",
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              textAlign = TextAlign.Center,
              color = Color(0xFF7C3AED),
              modifier = Modifier.weight(0.9f)
            )
          }

          Spacer(modifier = Modifier.height(6.dp))

          GenziiPlanComparisons.forEachIndexed { index, item ->
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 7.dp),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Text(
                text = item.title,
                fontFamily = PoppinsFontFamily,
                fontWeight = if (item.isHighlighted) FontWeight.SemiBold else FontWeight.Normal,
                fontSize = 11.5.sp,
                color = if (item.isHighlighted) GenziiPurple else MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1.3f)
              )
              Text(
                text = item.free,
                fontFamily = PoppinsFontFamily,
                fontSize = 10.5.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(0.9f)
              )
              Text(
                text = item.plus,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.5.sp,
                textAlign = TextAlign.Center,
                color = GenziiPurple,
                modifier = Modifier.weight(0.9f)
              )
              Text(
                text = item.pro,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.5.sp,
                textAlign = TextAlign.Center,
                color = Color(0xFF7C3AED),
                modifier = Modifier.weight(0.9f)
              )
            }
            if (index < GenziiPlanComparisons.lastIndex) {
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(28.dp))

      // --- GUARANTEE & ENCLAVE SECURITY ---
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            tint = GenziiPurple,
            modifier = Modifier.size(28.dp)
          )
          Spacer(modifier = Modifier.width(12.dp))
          Column {
            Text(
              text = "Zero-Knowledge Enclave Security",
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 13.sp,
              color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
              text = "Your research manuscripts, scan inputs, and verification certificates are never used to train public AI models. Entitlements persist across app launches.",
              fontFamily = PoppinsFontFamily,
              fontSize = 11.5.sp,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              lineHeight = 16.sp
            )
          }
        }
      }

      Spacer(modifier = Modifier.height(36.dp))
    }
  }

  // --- UPGRADE CONFIRMATION DIALOG ---
  if (showSuccessDialog) {
    AlertDialog(
      onDismissRequest = { showSuccessDialog = false },
      icon = {
        Icon(
          imageVector = Icons.Filled.CheckCircle,
          contentDescription = null,
          tint = GenziiPurple,
          modifier = Modifier.size(36.dp)
        )
      },
      title = {
        Text(
          text = "Subscription Updated",
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.Bold
        )
      },
      text = {
        Text(
          text = "You are now on the Genzii $upgradedTierName plan! Your quotas, storage allocation, and unlocked features have been activated.",
          fontFamily = PoppinsFontFamily,
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      },
      confirmButton = {
        Button(
          onClick = { showSuccessDialog = false },
          colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
        ) {
          Text("Continue")
        }
      }
    )
  }
}

@Composable
fun BillingCycleButton(
  text: String,
  isSelected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  Box(
    modifier = modifier
      .clip(RoundedCornerShape(12.dp))
      .background(if (isSelected) GenziiPurple else Color.Transparent)
      .clickable(onClick = onClick)
      .padding(vertical = 9.dp),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = text,
      fontFamily = PoppinsFontFamily,
      fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
      color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
      fontSize = 13.sp
    )
  }
}

@Composable
fun PlanPricingCard(
  title: String,
  badge: String,
  badgeColor: Color,
  price: String,
  period: String,
  description: String,
  highlights: List<String>,
  isCurrent: Boolean,
  actionLabel: String,
  isPopular: Boolean,
  onSelect: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isPopular) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface
    ),
    border = BorderStroke(
      width = if (isPopular || isCurrent) 2.dp else 1.dp,
      color = if (isCurrent) GenziiPurple else if (isPopular) GenziiPurple.copy(alpha = 0.6f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = if (isPopular) 4.dp else 1.dp)
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = title,
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp,
          color = MaterialTheme.colorScheme.onBackground
        )

        Surface(
          shape = RoundedCornerShape(12.dp),
          color = badgeColor.copy(alpha = 0.14f)
        ) {
          Text(
            text = badge,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = badgeColor,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
          )
        }
      }

      Spacer(modifier = Modifier.height(10.dp))

      Row(verticalAlignment = Alignment.Bottom) {
        Text(
          text = price,
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 28.sp,
          color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
          text = period,
          fontFamily = PoppinsFontFamily,
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 4.dp)
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = description,
        fontFamily = PoppinsFontFamily,
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 17.sp
      )

      Spacer(modifier = Modifier.height(14.dp))
      HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(12.dp))

      highlights.forEach { item ->
        Row(
          modifier = Modifier.padding(vertical = 3.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = GenziiPurple,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = item,
            fontFamily = PoppinsFontFamily,
            fontSize = 12.5.sp,
            color = MaterialTheme.colorScheme.onBackground
          )
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      Button(
        onClick = onSelect,
        enabled = !isCurrent,
        modifier = Modifier
          .fillMaxWidth()
          .height(48.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = if (isPopular) GenziiPurple else MaterialTheme.colorScheme.surfaceVariant,
          contentColor = if (isPopular) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
          disabledContainerColor = GenziiPurpleContainer.copy(alpha = 0.6f),
          disabledContentColor = GenziiPurple
        ),
        shape = RoundedCornerShape(14.dp)
      ) {
        Text(
          text = actionLabel,
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 14.sp
        )
      }
    }
  }
}

@Composable
fun FeatureDetailCard(
  feature: PremiumFeature,
  userTier: SubscriptionTier
) {
  val isUnlocked = when (feature.minimumTier) {
    SubscriptionTier.FREE -> true
    SubscriptionTier.PLUS -> userTier == SubscriptionTier.PLUS || userTier == SubscriptionTier.PRO
    SubscriptionTier.PRO -> userTier == SubscriptionTier.PRO
  }

  val icon = when (feature) {
    PremiumFeature.URL_CHECKER -> Icons.Default.Link
    PremiumFeature.ADVANCED_PLAGIARISM -> Icons.Default.FactCheck
    PremiumFeature.GEMINI_AI_ANALYSIS -> Icons.Filled.AutoAwesome
    PremiumFeature.MULTI_FILE_SCANNING -> Icons.Default.FolderCopy
    PremiumFeature.DETAILED_PDF_REPORTS -> Icons.Default.PictureAsPdf
    PremiumFeature.CITATION_CHECKER -> Icons.Default.FormatQuote
    PremiumFeature.SMART_REWRITE -> Icons.Default.EditNote
    PremiumFeature.DOCUMENT_COMPARISON -> Icons.Default.CompareArrows
    PremiumFeature.SCAN_HISTORY -> Icons.Default.History
    PremiumFeature.PREMIUM_USAGE_LIMITS -> Icons.Default.Speed
  }

  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surface
    ),
    border = BorderStroke(
      1.dp,
      if (isUnlocked) GenziiPurple.copy(alpha = 0.3f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier
          .size(42.dp)
          .clip(CircleShape)
          .background(
            if (isUnlocked) GenziiPurpleContainer else MaterialTheme.colorScheme.surfaceVariant
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (isUnlocked) GenziiPurple else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.size(20.dp)
        )
      }

      Spacer(modifier = Modifier.width(12.dp))

      Column(modifier = Modifier.weight(1f)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Text(
            text = feature.displayName,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.5.sp,
            color = MaterialTheme.colorScheme.onBackground
          )

          Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isUnlocked) Color(0xFFDCFCE7) else GenziiPurpleContainer.copy(alpha = 0.6f)
          ) {
            Text(
              text = if (isUnlocked) "UNLOCKED" else feature.tag,
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 10.sp,
              color = if (isUnlocked) Color(0xFF166534) else GenziiPurple,
              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
          }
        }

        Spacer(modifier = Modifier.height(3.dp))

        Text(
          text = feature.description,
          fontFamily = PoppinsFontFamily,
          fontSize = 11.5.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          lineHeight = 16.sp
        )
      }
    }
  }
}

/**
 * Reusable dialog shown when a user attempts to access a locked Premium or Pro feature.
 */
@Composable
fun PremiumUpgradeDialog(
  feature: PremiumFeature,
  onDismiss: () -> Unit,
  onNavigateToUpgrade: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    icon = {
      Box(
        modifier = Modifier
          .size(48.dp)
          .clip(CircleShape)
          .background(GenziiPurpleContainer),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Filled.Lock,
          contentDescription = null,
          tint = GenziiPurple,
          modifier = Modifier.size(24.dp)
        )
      }
    },
    title = {
      Text(
        text = feature.displayName,
        fontFamily = PoppinsFontFamily,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )
    },
    text = {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
          text = feature.description,
          fontFamily = PoppinsFontFamily,
          fontSize = 13.sp,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
          shape = RoundedCornerShape(10.dp),
          color = GenziiPurpleContainer
        ) {
          Text(
            text = "Requires Genzii ${feature.tag} Plan",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            color = GenziiPurple,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onDismiss()
          onNavigateToUpgrade()
        },
        colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple)
      ) {
        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("View Premium Plans")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Not Now")
      }
    }
  )
}
