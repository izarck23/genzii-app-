package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.example.data.GenziiRepository
import com.example.model.ThemeMode
import com.example.navigation.GenziiBottomNavigationBar
import com.example.navigation.GenziiNavGraph
import com.example.ui.theme.MyApplicationTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      val userProfile by GenziiRepository.userProfile.collectAsState()
      val isSystemDark = isSystemInDarkTheme()
      val isDark = when (userProfile.themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemDark
      }

      MyApplicationTheme(darkTheme = isDark) {
        GenziiNavigationApp()
      }
    }
  }
}

@Composable
fun GenziiNavigationApp() {
  val navController = rememberNavController()

  Scaffold(
    bottomBar = {
      GenziiBottomNavigationBar(navController = navController)
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(bottom = paddingValues.calculateBottomPadding())
    ) {
      GenziiNavGraph(navController = navController)
    }
  }
}
