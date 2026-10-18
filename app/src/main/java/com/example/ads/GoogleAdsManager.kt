package com.example.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.GenziiRepository
import com.example.ui.theme.GenziiPurple
import com.example.ui.theme.PoppinsFontFamily
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Manages official Google Mobile Ads SDK integration using official AdMob test identifiers.
 * Rewards are granted strictly when the official AdMob rewarded callback is invoked.
 */
object GoogleAdsManager {
  private const val TAG = "GoogleAdsManager"

  // Official Google AdMob Test Ad Unit IDs
  const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
  const val BANNER_TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
  const val REWARDED_TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/5224354917"
  const val INTERSTITIAL_TEST_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

  private var isInitialized = false
  private var isInitializing = false

  private val _totalAdsWatched = MutableStateFlow(0)
  val totalAdsWatched = _totalAdsWatched.asStateFlow()

  private val _bonusScansEarned = MutableStateFlow(0)
  val bonusScansEarned = _bonusScansEarned.asStateFlow()

  /**
   * Initializes the Google Mobile Ads SDK.
   */
  fun initialize(context: Context) {
    if (isInitialized || isInitializing) return
    isInitializing = true
    try {
      MobileAds.initialize(context) { status ->
        isInitialized = true
        isInitializing = false
        Log.i(TAG, "Google Mobile Ads SDK initialized: ${status.adapterStatusMap}")
      }
    } catch (e: Throwable) {
      isInitializing = false
      Log.w(TAG, "MobileAds.initialize warning: ${e.message}")
    }
  }

  /**
   * Adds rewarded bonus scans and chat tokens only when verified by AdMob callback.
   */
  fun grantReward(scansCount: Int = 10, aiCredits: Int = 10) {
    _totalAdsWatched.update { it + 1 }
    _bonusScansEarned.update { it + scansCount }
    GenziiRepository.grantRewardedBonus(scansCount, aiCredits)
  }

  /**
   * Loads a real official Google Rewarded Ad.
   */
  fun loadRewardedAd(
    context: Context,
    onLoaded: (RewardedAd) -> Unit,
    onFailed: (String) -> Unit
  ) {
    initialize(context)
    val adRequest = AdRequest.Builder().build()
    RewardedAd.load(
      context,
      REWARDED_TEST_AD_UNIT_ID,
      adRequest,
      object : RewardedAdLoadCallback() {
        override fun onAdLoaded(ad: RewardedAd) {
          Log.i(TAG, "Real AdMob Rewarded Ad loaded successfully.")
          onLoaded(ad)
        }
        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
          val errorMsg = "AdMob error (${loadAdError.code}): ${loadAdError.message}"
          Log.w(TAG, errorMsg)
          onFailed(errorMsg)
        }
      }
    )
  }

  /**
   * Loads a real official Google Interstitial Ad.
   */
  fun loadInterstitialAd(
    context: Context,
    onLoaded: (InterstitialAd) -> Unit,
    onFailed: (String) -> Unit
  ) {
    initialize(context)
    val adRequest = AdRequest.Builder().build()
    InterstitialAd.load(
      context,
      INTERSTITIAL_TEST_AD_UNIT_ID,
      adRequest,
      object : InterstitialAdLoadCallback() {
        override fun onAdLoaded(ad: InterstitialAd) {
          Log.i(TAG, "Real AdMob Interstitial Ad loaded successfully.")
          onLoaded(ad)
        }
        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
          val errorMsg = "AdMob interstitial error (${loadAdError.code}): ${loadAdError.message}"
          Log.w(TAG, errorMsg)
          onFailed(errorMsg)
        }
      }
    )
  }
}

/**
 * Helper to safely extract Activity from Context
 */
fun Context.findActivity(): Activity? {
  var currentContext = this
  while (currentContext is ContextWrapper) {
    if (currentContext is Activity) return currentContext
    currentContext = currentContext.baseContext
  }
  return null
}

/**
 * Real Google Mobile Ads Banner using AdView and official test ad unit ID.
 */
@Composable
fun GoogleTestBannerAd(
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  LaunchedEffect(Unit) {
    GoogleAdsManager.initialize(context)
  }

  Box(
    modifier = modifier
      .fillMaxWidth()
      .wrapContentHeight()
      .testTag("google_test_banner_ad"),
    contentAlignment = Alignment.Center
  ) {
    AndroidView(
      modifier = Modifier.fillMaxWidth(),
      factory = { ctx ->
        AdView(ctx).apply {
          setAdSize(AdSize.BANNER)
          adUnitId = GoogleAdsManager.BANNER_TEST_AD_UNIT_ID
          loadAd(AdRequest.Builder().build())
        }
      }
    )
  }
}

/**
 * Real Google Mobile Ads Rewarded Ad Launcher.
 * Rewards are granted strictly when AdMob's onUserEarnedReward callback fires.
 */
@Composable
fun GoogleTestRewardedAdDialog(
  onDismiss: () -> Unit,
  onRewardEarned: (scans: Int, credits: Int) -> Unit
) {
  val context = LocalContext.current
  val activity = remember(context) { context.findActivity() }

  var isLoading by remember { mutableStateOf(true) }
  var loadedAd by remember { mutableStateOf<RewardedAd?>(null) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    GoogleAdsManager.loadRewardedAd(
      context = context,
      onLoaded = { ad ->
        loadedAd = ad
        isLoading = false
      },
      onFailed = { error ->
        errorMessage = error
        isLoading = false
      }
    )
  }

  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false)
  ) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.Black.copy(alpha = 0.85f))
        .padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .clip(RoundedCornerShape(24.dp))
          .background(Color(0xFF1E1E2E))
          .border(1.dp, Color(0xFF43435C), RoundedCornerShape(24.dp))
          .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically
        ) {
          Surface(
            shape = RoundedCornerShape(6.dp),
            color = Color(0xFFFBBC04)
          ) {
            Text(
              text = "AdMob Test",
              color = Color.Black,
              fontSize = 11.sp,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            )
          }

          IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
          }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (isLoading) {
          CircularProgressIndicator(
            color = GenziiPurple,
            modifier = Modifier.size(48.dp)
          )
          Spacer(modifier = Modifier.height(16.dp))
          Text(
            text = "Loading Google AdMob test ad...",
            color = Color.White,
            fontSize = 14.sp,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Unit ID: ${GoogleAdsManager.REWARDED_TEST_AD_UNIT_ID.takeLast(10)}",
            color = Color.White.copy(alpha = 0.5f),
            fontSize = 11.sp
          )
        } else if (errorMessage != null) {
          Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = Color(0xFFEA4335),
            modifier = Modifier.size(48.dp)
          )
          Spacer(modifier = Modifier.height(12.dp))
          Text(
            text = "Ad Failed to Load",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
          )
          Spacer(modifier = Modifier.height(6.dp))
          Text(
            text = errorMessage ?: "Unknown error",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(20.dp))
          Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF43435C)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text("Close", color = Color.White)
          }
        } else if (loadedAd != null && activity != null) {
          Text(
            text = "Official AdMob Rewarded Video Ready",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(8.dp))
          Text(
            text = "Watch this verified Google test ad to earn 10 free scans and 10 AI credits.",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 13.sp,
            textAlign = TextAlign.Center
          )
          Spacer(modifier = Modifier.height(24.dp))
          Button(
            onClick = {
              val ad = loadedAd ?: return@Button
              ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                  onDismiss()
                }
                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                  Log.w("GoogleAdsManager", "Failed to show rewarded ad: ${adError.message}")
                  onDismiss()
                }
              }
              ad.show(activity) { rewardItem ->
                // Verified reward callback strictly from Google Mobile Ads SDK
                Log.i("GoogleAdsManager", "Reward verified by AdMob: ${rewardItem.amount} ${rewardItem.type}")
                GoogleAdsManager.grantReward(10, 10)
                onRewardEarned(10, 10)
              }
            },
            colors = ButtonDefaults.buttonColors(containerColor = GenziiPurple),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .height(48.dp)
          ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Watch Ad & Earn Reward",
              fontFamily = PoppinsFontFamily,
              fontWeight = FontWeight.Bold,
              fontSize = 14.sp
            )
          }
        } else {
          Button(onClick = onDismiss) {
            Text("Dismiss")
          }
        }
      }
    }
  }
}

/**
 * Real Google Mobile Ads Interstitial Ad Launcher.
 */
@Composable
fun GoogleTestInterstitialAdDialog(
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val activity = remember(context) { context.findActivity() }

  var isLoading by remember { mutableStateOf(true) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(Unit) {
    if (activity == null) {
      onDismiss()
      return@LaunchedEffect
    }

    GoogleAdsManager.loadInterstitialAd(
      context = context,
      onLoaded = { interstitialAd ->
        isLoading = false
        interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
          override fun onAdDismissedFullScreenContent() {
            onDismiss()
          }
          override fun onAdFailedToShowFullScreenContent(adError: AdError) {
            Log.w("GoogleAdsManager", "Interstitial failed to show: ${adError.message}")
            onDismiss()
          }
        }
        interstitialAd.show(activity)
      },
      onFailed = { error ->
        isLoading = false
        errorMessage = error
      }
    )
  }

  if (isLoading) {
    Dialog(
      onDismissRequest = onDismiss,
      properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .background(Color.Black.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
      ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator(color = GenziiPurple)
          Spacer(modifier = Modifier.height(16.dp))
          Text("Loading Google Test Interstitial...", color = Color.White)
        }
      }
    }
  } else if (errorMessage != null) {
    Dialog(onDismissRequest = onDismiss) {
      Card(
        modifier = Modifier.padding(16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E2E))
      ) {
        Column(
          modifier = Modifier.padding(20.dp),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text("Interstitial Ad Error", color = Color.White, fontWeight = FontWeight.Bold)
          Spacer(modifier = Modifier.height(8.dp))
          Text(errorMessage ?: "", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
          Spacer(modifier = Modifier.height(16.dp))
          Button(onClick = onDismiss) {
            Text("Dismiss")
          }
        }
      }
    }
  }
}
