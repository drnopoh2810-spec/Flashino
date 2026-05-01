package com.eduspecial

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.eduspecial.presentation.splash.LaunchIntroOverlay
import com.eduspecial.presentation.navigation.EduSpecialNavHost
import com.eduspecial.presentation.theme.EduSpecialTheme
import com.eduspecial.utils.AppLanguageManager
import com.eduspecial.utils.UserPreferencesDataStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.delay

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var prefs: UserPreferencesDataStore

    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(AppLanguageManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyTransientStatusBarBehavior()
        setContent {
            val themeMode by prefs.themeMode.collectAsState(initial = "system")
            val themePalette by prefs.themePalette.collectAsState(initial = "qusasa")
            val language by prefs.language.collectAsState(initial = AppLanguageManager.getPersistedLanguage(this))
            val darkTheme = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            var showLaunchIntro by rememberSaveable { mutableStateOf(savedInstanceState == null) }
            LaunchedEffect(showLaunchIntro) {
                if (showLaunchIntro) {
                    delay(1000)
                    showLaunchIntro = false
                }
            }
            LaunchedEffect(language) {
                AppLanguageManager.persistLanguage(this@MainActivity, language)
                if (AppLanguageManager.getCurrentResourcesLanguage(this@MainActivity) != language) {
                    recreate()
                }
            }
            EduSpecialTheme(
                darkTheme = darkTheme,
                palette = themePalette
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        EduSpecialNavHost(prefs = prefs)
                    }
                    if (showLaunchIntro) {
                        LaunchIntroOverlay()
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyTransientStatusBarBehavior()
        }
    }

    private fun applyTransientStatusBarBehavior() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.statusBars())
        }
    }
}
