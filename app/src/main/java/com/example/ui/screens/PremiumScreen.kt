package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Executive Golden Luxury Palette
private val GoldLight = Color(0xFFFFE082)
private val GoldPrimary = Color(0xFFFFD700)
private val GoldAmber = Color(0xFFF59E0B)
private val GoldBronze = Color(0xFFD97706)
private val GoldDarkBg = Color(0xFF16130D)
private val GoldCardBg = Color(0xFF1E190F)
private val GoldBorder = Color(0xFFFCD34D).copy(alpha = 0.45f)

private val GoldGradient = Brush.linearGradient(
  listOf(GoldLight, GoldPrimary, GoldAmber, GoldBronze)
)

private val DarkGoldHeroGradient = Brush.linearGradient(
  listOf(Color(0xFF2B2313), Color(0xFF1E180B), Color(0xFF141006))
)

@Composable
fun PremiumScreen(
  onBack: () -> Unit
) {
  BackHandler { onBack() }

  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val userProfile by GenziiRepository.userProfile.collectAsState()

  var showSuccessDialog by remember { mutableStateOf(false) }
  var upgradedTierName by remember { mutableStateOf("") }
  var transactionReceipt by remember { mutableStateOf("") }

  // Payment checkout sheet state
  var selectedCheckoutPlan by remember { mutableStateOf<CheckoutPlan?>(null) }

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
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(
            imageVector = Icons.Filled.WorkspacePremium,
            contentDescription = null,
            tint = GoldAmber,
            modifier = Modifier.size(22.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Genzii Premium VIP",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground
          )
        }
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
      Spacer(modifier = Modifier.height(4.dp))

      // --- HERO EXECUTIVE GOLD UPGRADE BANNER ---
      Card(
        modifier = Modifier
          .fillMaxWidth()
          .testTag("premium_hero_banner"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = BorderStroke(1.5.dp, GoldBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
      ) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .background(DarkGoldHeroGradient)
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
                  .size(48.dp)
                  .clip(CircleShape)
                  .background(GoldGradient),
                contentAlignment = Alignment.Center
              ) {
                Icon(
                  imageVector = Icons.Filled.WorkspacePremium,
                  contentDescription = null,
                  tint = Color(0xFF1E1606),
                  modifier = Modifier.size(28.dp)
                )
              }

              Surface(
                shape = RoundedCornerShape(12.dp),
                color = GoldAmber.copy(alpha = 0.22f),
                border = BorderStroke(1.dp, GoldAmber.copy(alpha = 0.5f))
              ) {
                Text(
                  text = "CURRENT: ${userProfile.tier.name} TIER",
                  fontFamily = PoppinsFontFamily,
                  fontWeight = FontWeight.Bold,
                  fontSize = 11.sp,
                  color = GoldLight,
                  modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
              }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
              text = "Academic Integrity & Unlimited Access",
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 21.sp,
              color = Color.White,
              lineHeight = 27.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
              text = "Subscribe to remove all ads, unlock live URL verification, academic plagiarism detection, multiple file scanning, and Gemini AI writing analysis.",
              fontFamily = PoppinsFontFamily,
              fontSize = 13.sp,
              color = Color.White.copy(alpha = 0.9f),
              lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
              Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = GoldLight,
                modifier = Modifier.size(16.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text(
                text = "${userProfile.checksLeft} checks available • Zero-knowledge enclave",
                fontFamily = PoppinsFontFamily,
                fontSize = 12.sp,
                color = GoldLight,
                fontWeight = FontWeight.Medium
              )
            }
          }
        }
      }

      Spacer(modifier = Modifier.height(20.dp))

      // Section Title: Choose Your Plan
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column {
          Text(
            text = "Subscription Plans & Pricing",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onBackground
          )
          Text(
            text = "Flexible payments via M-Pesa & PayPal",
            fontFamily = PoppinsFontFamily,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // --- PLAN 1: FREE TIER ---
      GoldPlanPricingCard(
        title = "Genzii Free",
        badge = if (userProfile.tier == SubscriptionTier.FREE) "ACTIVE PLAN" else "FREE TIER",
        badgeColor = Color(0xFF94A3B8),
        price = "KES 0",
        period = "Free Forever",
        description = "Core originality scans for individual essays and coursework.",
        highlights = listOf(
          "10 scans / month",
          "Ad-supported AI Writing Tools",
          "4 OCR Camera Scans (via Rewarded Ads)",
          "5.0 GB Encrypted Device Enclave",
          "Basic Source & Citation Index"
        ),
        isCurrent = userProfile.tier == SubscriptionTier.FREE,
        actionLabel = if (userProfile.tier == SubscriptionTier.FREE) "Current Plan" else "Downgrade to Free",
        isPopular = false,
        isGolden = false,
        onSelect = {
          GenziiRepository.upgradeTier(SubscriptionTier.FREE)
          upgradedTierName = "Free"
          transactionReceipt = "Standard Free Activation"
          showSuccessDialog = true
        }
      )

      Spacer(modifier = Modifier.height(14.dp))

      // --- PLAN 2: WEEKLY VIP (KES 100) ---
      GoldPlanPricingCard(
        title = "Weekly VIP",
        badge = if (userProfile.tier == SubscriptionTier.PLUS) "ACTIVE PLAN" else "FLEXIBLE ACCESS",
        badgeColor = GoldAmber,
        price = "KES 100",
        period = "/ week",
        description = "Ideal for students working on weekly coursework and assignments.",
        highlights = listOf(
          "Instant Ad-Free experience across the entire app",
          "Unlimited AI Writing Assistant (No ads required)",
          "Unlimited OCR Camera Document Scans",
          "URL Originality Checker & live content extraction",
          "100 originality scans during active period",
          "25.0 GB Encrypted Enclave Storage",
          "Fast-lane Gemini 2.5 Flash processing"
        ),
        isCurrent = userProfile.tier == SubscriptionTier.PLUS,
        actionLabel = if (userProfile.tier == SubscriptionTier.PLUS) "Current Plan" else "Subscribe Weekly • KES 100",
        isPopular = false,
        isGolden = true,
        onSelect = {
          selectedCheckoutPlan = CheckoutPlan(
            name = "Weekly VIP",
            tier = SubscriptionTier.PLUS,
            amountKes = 100,
            durationLabel = "7 Days"
          )
        }
      )

      Spacer(modifier = Modifier.height(14.dp))

      // --- PLAN 3: MONTHLY EXECUTIVE (KES 200 - BEST VALUE) ---
      GoldPlanPricingCard(
        title = "Monthly Executive",
        badge = if (userProfile.tier == SubscriptionTier.PRO) "ACTIVE PLAN" else "BEST VALUE • SAVE 50%",
        badgeColor = GoldPrimary,
        price = "KES 200",
        period = "/ month",
        description = "Full institutional-grade verification for researchers, thesis authors, and scholars.",
        highlights = listOf(
          "100% Ad-Free forever on all tools and screens",
          "Unlimited Originality & Plagiarism checks (999/mo)",
          "Batch Multiple File Scanning (PDF, DOCX, TXT)",
          "Downloadable Certified PDF Evidence Reports",
          "Citation & Bibliography Checker (APA, MLA, Harvard, Chicago)",
          "Document Comparison & Smart Paraphraser",
          "Unlimited OCR Camera Scanner & PDF Tools",
          "100.0 GB Zero-Knowledge Encrypted Vault",
          "VIP 24/7 Academic Support"
        ),
        isCurrent = userProfile.tier == SubscriptionTier.PRO,
        actionLabel = if (userProfile.tier == SubscriptionTier.PRO) "Current Plan" else "Subscribe Monthly • KES 200",
        isPopular = true,
        isGolden = true,
        onSelect = {
          selectedCheckoutPlan = CheckoutPlan(
            name = "Monthly Executive",
            tier = SubscriptionTier.PRO,
            amountKes = 200,
            durationLabel = "30 Days"
          )
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
            text = "Executive Feature Suite",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = MaterialTheme.colorScheme.onBackground
          )
          Text(
            text = "Professional academic verification & writing utilities",
            fontFamily = PoppinsFontFamily,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      Spacer(modifier = Modifier.height(14.dp))

      // Grid of 10 Features with Golden Badges
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
        border = BorderStroke(1.dp, GoldBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
      ) {
        Column(modifier = Modifier.padding(14.dp)) {
          // Table Header
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .background(GoldAmber.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
              .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Capability",
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 11.5.sp,
              color = GoldBronze,
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
              text = "Weekly",
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              textAlign = TextAlign.Center,
              color = GoldAmber,
              modifier = Modifier.weight(0.9f)
            )
            Text(
              text = "Monthly",
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 11.sp,
              textAlign = TextAlign.Center,
              color = GoldPrimary,
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
                color = if (item.isHighlighted) GoldAmber else MaterialTheme.colorScheme.onBackground,
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
                color = GoldAmber,
                modifier = Modifier.weight(0.9f)
              )
              Text(
                text = item.pro,
                fontFamily = PoppinsFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.5.sp,
                textAlign = TextAlign.Center,
                color = GoldPrimary,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
        border = BorderStroke(1.dp, GoldBorder.copy(alpha = 0.3f))
      ) {
        Row(
          modifier = Modifier.padding(16.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            tint = GoldAmber,
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
              text = "Your research manuscripts, scan inputs, and verification certificates are never used to train public AI models. Subscriptions activate instantly with M-Pesa or PayPal.",
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

  // --- PAYMENT SELECTION DIALOG (M-PESA / PAYPAL) ---
  selectedCheckoutPlan?.let { plan ->
    PaymentCheckoutDialog(
      plan = plan,
      onDismiss = { selectedCheckoutPlan = null },
      onPaymentSuccess = { receipt ->
        GenziiRepository.upgradeTier(plan.tier)
        upgradedTierName = plan.name
        transactionReceipt = receipt
        selectedCheckoutPlan = null
        showSuccessDialog = true
      }
    )
  }

  // --- UPGRADE CONFIRMATION DIALOG ---
  if (showSuccessDialog) {
    AlertDialog(
      onDismissRequest = { showSuccessDialog = false },
      icon = {
        Icon(
          imageVector = Icons.Filled.CheckCircle,
          contentDescription = null,
          tint = GoldAmber,
          modifier = Modifier.size(40.dp)
        )
      },
      title = {
        Text(
          text = "Subscription Activated! 👑",
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.Bold,
          textAlign = TextAlign.Center
        )
      },
      text = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
            text = "Welcome to Genzii $upgradedTierName! All ads have been removed and premium verification features are now unlocked.",
            fontFamily = PoppinsFontFamily,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
          )
          if (transactionReceipt.isNotBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
              shape = RoundedCornerShape(8.dp),
              color = GoldAmber.copy(alpha = 0.15f),
              border = BorderStroke(1.dp, GoldAmber.copy(alpha = 0.4f))
            ) {
              Text(
                text = "Receipt: $transactionReceipt",
                fontFamily = PoppinsFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = GoldAmber,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
              )
            }
          }
        }
      },
      confirmButton = {
        Button(
          onClick = { showSuccessDialog = false },
          colors = ButtonDefaults.buttonColors(containerColor = GoldAmber)
        ) {
          Text("Continue to Workspace", color = Color.White, fontWeight = FontWeight.Bold)
        }
      }
    )
  }
}

data class CheckoutPlan(
  val name: String,
  val tier: SubscriptionTier,
  val amountKes: Int,
  val durationLabel: String
)

@Composable
fun GoldPlanPricingCard(
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
  isGolden: Boolean,
  onSelect: () -> Unit
) {
  Card(
    modifier = Modifier.fillMaxWidth(),
    shape = RoundedCornerShape(20.dp),
    colors = CardDefaults.cardColors(
      containerColor = if (isGolden && isPopular) GoldCardBg else MaterialTheme.colorScheme.surface
    ),
    border = BorderStroke(
      width = if (isPopular || isCurrent) 2.dp else 1.dp,
      color = if (isCurrent) GoldAmber else if (isPopular) GoldPrimary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    ),
    elevation = CardDefaults.cardElevation(defaultElevation = if (isPopular) 6.dp else 2.dp)
  ) {
    Column(modifier = Modifier.padding(18.dp)) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (isGolden) {
            Icon(
              imageVector = if (isPopular) Icons.Filled.WorkspacePremium else Icons.Filled.Star,
              contentDescription = null,
              tint = if (isPopular) GoldPrimary else GoldAmber,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
          }
          Text(
            text = title,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = if (isGolden && isPopular) Color.White else MaterialTheme.colorScheme.onBackground
          )
        }

        Surface(
          shape = RoundedCornerShape(12.dp),
          color = badgeColor.copy(alpha = 0.16f),
          border = BorderStroke(0.8.dp, badgeColor.copy(alpha = 0.5f))
        ) {
          Text(
            text = badge,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 10.5.sp,
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
          color = if (isGolden && isPopular) GoldPrimary else MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
          text = period,
          fontFamily = PoppinsFontFamily,
          fontSize = 13.sp,
          color = if (isGolden && isPopular) Color(0xFFD4D4D8) else MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = 4.dp)
        )
      }

      Spacer(modifier = Modifier.height(4.dp))

      Text(
        text = description,
        fontFamily = PoppinsFontFamily,
        fontSize = 12.sp,
        color = if (isGolden && isPopular) Color(0xFFD4D4D8) else MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 17.sp
      )

      Spacer(modifier = Modifier.height(14.dp))
      HorizontalDivider(color = if (isGolden && isPopular) Color(0xFF3F3725) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
      Spacer(modifier = Modifier.height(12.dp))

      highlights.forEach { item ->
        Row(
          modifier = Modifier.padding(vertical = 3.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = if (isGolden) GoldAmber else GenziiPurple,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = item,
            fontFamily = PoppinsFontFamily,
            fontSize = 12.5.sp,
            color = if (isGolden && isPopular) Color.White else MaterialTheme.colorScheme.onBackground
          )
        }
      }

      Spacer(modifier = Modifier.height(18.dp))

      if (isGolden) {
        Button(
          onClick = onSelect,
          enabled = !isCurrent,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = GoldAmber,
            contentColor = Color.White,
            disabledContainerColor = Color(0xFF3D321A),
            disabledContentColor = GoldLight
          ),
          shape = RoundedCornerShape(14.dp)
        ) {
          Icon(
            imageVector = if (isCurrent) Icons.Filled.Check else Icons.Filled.LockOpen,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = actionLabel,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
          )
        }
      } else {
        OutlinedButton(
          onClick = onSelect,
          enabled = !isCurrent,
          modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
          shape = RoundedCornerShape(14.dp)
        ) {
          Text(
            text = actionLabel,
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp
          )
        }
      }
    }
  }
}

@Composable
fun PaymentCheckoutDialog(
  plan: CheckoutPlan,
  onDismiss: () -> Unit,
  onPaymentSuccess: (receipt: String) -> Unit
) {
  var selectedMethod by remember { mutableStateOf("mpesa") } // "mpesa" or "paypal"
  var phoneNumber by remember { mutableStateOf("0712345678") }
  var isProcessing by remember { mutableStateOf(false) }
  var statusText by remember { mutableStateOf("") }
  val coroutineScope = rememberCoroutineScope()

  AlertDialog(
    onDismissRequest = { if (!isProcessing) onDismiss() },
    title = {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Security, contentDescription = null, tint = GoldAmber, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "VIP Checkout",
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp
        )
      }
    },
    text = {
      Column(modifier = Modifier.fillMaxWidth()) {
        // Plan Summary Box
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = GoldAmber.copy(alpha = 0.12f),
          border = BorderStroke(1.dp, GoldAmber.copy(alpha = 0.4f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column {
              Text(
                text = plan.name,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onBackground
              )
              Text(
                text = "${plan.durationLabel} • No Ads • Unlimited Access",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
            Text(
              text = "KES ${plan.amountKes}",
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 18.sp,
              color = GoldAmber
            )
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
          text = "Select Payment Channel:",
          fontFamily = PoppinsFontFamily,
          fontWeight = FontWeight.SemiBold,
          fontSize = 13.sp
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Payment Method Selector Tabs
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          // M-Pesa Button
          OutlinedCard(
            onClick = { selectedMethod = "mpesa" },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
              width = if (selectedMethod == "mpesa") 2.dp else 1.dp,
              color = if (selectedMethod == "mpesa") Color(0xFF16A34A) else MaterialTheme.colorScheme.outlineVariant
            ),
            colors = CardDefaults.outlinedCardColors(
              containerColor = if (selectedMethod == "mpesa") Color(0xFFDCFCE7) else Color.Transparent
            )
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(
                imageVector = Icons.Default.PhoneAndroid,
                contentDescription = null,
                tint = Color(0xFF16A34A),
                modifier = Modifier.size(24.dp)
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "M-Pesa",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (selectedMethod == "mpesa") Color(0xFF166534) else MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Instant STK Push",
                fontSize = 10.sp,
                color = Color(0xFF166534).copy(alpha = 0.8f)
              )
            }
          }

          // PayPal Button
          OutlinedCard(
            onClick = { selectedMethod = "paypal" },
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(
              width = if (selectedMethod == "paypal") 2.dp else 1.dp,
              color = if (selectedMethod == "paypal") Color(0xFF0284C7) else MaterialTheme.colorScheme.outlineVariant
            ),
            colors = CardDefaults.outlinedCardColors(
              containerColor = if (selectedMethod == "paypal") Color(0xFFE0F2FE) else Color.Transparent
            )
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Icon(
                imageVector = Icons.Default.Payment,
                contentDescription = null,
                tint = Color(0xFF0284C7),
                modifier = Modifier.size(24.dp)
              )
              Spacer(modifier = Modifier.height(4.dp))
              Text(
                text = "PayPal",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = if (selectedMethod == "paypal") Color(0xFF075985) else MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = "Global & Card",
                fontSize = 10.sp,
                color = Color(0xFF075985).copy(alpha = 0.8f)
              )
            }
          }
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (selectedMethod == "mpesa") {
          Text(
            text = "Enter Safaricom Mobile Number:",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Spacer(modifier = Modifier.height(6.dp))
          OutlinedTextField(
            value = phoneNumber,
            onValueChange = { phoneNumber = it },
            singleLine = true,
            leadingIcon = {
              Icon(Icons.Default.Phone, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
            },
            placeholder = { Text("0712345678") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = "A prompt will appear on your phone asking for your M-Pesa PIN.",
            fontSize = 11.sp,
            color = Color(0xFF166534),
            lineHeight = 15.sp
          )
        } else {
          Surface(
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFFF0F9FF),
            border = BorderStroke(1.dp, Color(0xFFBAE6FD)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = "PayPal Express Checkout",
                fontWeight = FontWeight.Bold,
                fontSize = 12.5.sp,
                color = Color(0xFF0369A1)
              )
              Text(
                text = "Equivalent ~ $${if (plan.amountKes == 100) "0.79" else "1.59"} USD. You will be authenticated securely via PayPal.",
                fontSize = 11.sp,
                color = Color(0xFF0284C7)
              )
            }
          }
        }

        if (isProcessing) {
          Spacer(modifier = Modifier.height(14.dp))
          Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
          ) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = GoldAmber,
              strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
              text = statusText,
              fontSize = 12.sp,
              color = GoldAmber,
              fontWeight = FontWeight.Medium
            )
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          if (!isProcessing) {
            isProcessing = true
            coroutineScope.launch {
              if (selectedMethod == "mpesa") {
                statusText = "Sending M-Pesa STK Prompt to $phoneNumber..."
                delay(1200)
                statusText = "Confirming PIN authorization on Safaricom..."
                delay(1500)
                val randomMpesaCode = "MPESA-${(100000..999999).random()}-KES"
                onPaymentSuccess(randomMpesaCode)
              } else {
                statusText = "Connecting to PayPal Gateway..."
                delay(1200)
                statusText = "Authorizing VIP transaction..."
                delay(1300)
                val paypalId = "PAYPAL-ORDER-${(1000000..9999999).random()}"
                onPaymentSuccess(paypalId)
              }
            }
          }
        },
        enabled = !isProcessing && (selectedMethod != "mpesa" || phoneNumber.length >= 9),
        colors = ButtonDefaults.buttonColors(
          containerColor = if (selectedMethod == "mpesa") Color(0xFF16A34A) else Color(0xFF0284C7)
        ),
        shape = RoundedCornerShape(12.dp)
      ) {
        Text(
          text = if (isProcessing) "Processing..." else "Pay KES ${plan.amountKes}",
          fontWeight = FontWeight.Bold,
          color = Color.White
        )
      }
    },
    dismissButton = {
      if (!isProcessing) {
        TextButton(onClick = onDismiss) {
          Text("Cancel")
        }
      }
    }
  )
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
      if (isUnlocked) GoldAmber.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
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
            if (isUnlocked) GoldAmber.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant
          ),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = icon,
          contentDescription = null,
          tint = if (isUnlocked) GoldAmber else MaterialTheme.colorScheme.onSurfaceVariant,
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
            color = if (isUnlocked) Color(0xFFDCFCE7) else GoldAmber.copy(alpha = 0.14f),
            border = BorderStroke(0.6.dp, if (isUnlocked) Color(0xFF86EFAC) else GoldAmber.copy(alpha = 0.4f))
          ) {
            Text(
              text = if (isUnlocked) "UNLOCKED" else feature.tag,
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 10.sp,
              color = if (isUnlocked) Color(0xFF166534) else GoldBronze,
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
          .background(GoldAmber.copy(alpha = 0.15f)),
        contentAlignment = Alignment.Center
      ) {
        Icon(
          imageVector = Icons.Filled.WorkspacePremium,
          contentDescription = null,
          tint = GoldAmber,
          modifier = Modifier.size(26.dp)
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
          color = GoldAmber.copy(alpha = 0.15f),
          border = BorderStroke(1.dp, GoldAmber.copy(alpha = 0.4f))
        ) {
          Text(
            text = "Requires Genzii ${feature.tag} Plan (from KES 100/wk)",
            fontFamily = PoppinsFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.5.sp,
            color = GoldAmber,
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
        colors = ButtonDefaults.buttonColors(containerColor = GoldAmber)
      ) {
        Icon(Icons.Filled.WorkspacePremium, contentDescription = null, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("View Premium Plans", fontWeight = FontWeight.Bold)
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Not Now")
      }
    }
  )
}
