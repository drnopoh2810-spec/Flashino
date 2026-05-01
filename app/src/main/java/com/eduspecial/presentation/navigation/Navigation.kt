package com.eduspecial.presentation.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.eduspecial.R
import com.eduspecial.domain.model.FlashcardCategory
import com.eduspecial.presentation.auth.AuthScreen
import com.eduspecial.presentation.bookmarks.BookmarksScreen
import com.eduspecial.presentation.common.rememberResponsiveLayoutInfo
import com.eduspecial.presentation.flashcards.FlashcardsScreen
import com.eduspecial.presentation.flashcards.StudyScreen
import com.eduspecial.presentation.home.HomeScreen
import com.eduspecial.presentation.leaderboard.LeaderboardScreen
import com.eduspecial.presentation.onboarding.OnboardingScreen
import com.eduspecial.presentation.permissions.PermissionRequestScreen
import com.eduspecial.presentation.profile.ProfileScreen
import com.eduspecial.presentation.qa.QAScreen
import com.eduspecial.presentation.search.SearchScreen
import com.eduspecial.ui.profile.AccountSecurityScreen
import com.eduspecial.ui.profile.ProfileSettingsScreen
import com.eduspecial.update.UpdateDialogHost
import com.eduspecial.update.UpdateViewModel
import com.eduspecial.utils.ApiHealthMonitor
import com.eduspecial.utils.UserPreferencesDataStore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.first

// ─── Transition constants ──────────────────────────────────────────────────────
private const val TRANSITION_DURATION = 300

private val enterTransition = fadeIn(tween(TRANSITION_DURATION)) +
        slideInHorizontally(tween(TRANSITION_DURATION)) { -it / 6 }

private val exitTransition = fadeOut(tween(TRANSITION_DURATION)) +
        slideOutHorizontally(tween(TRANSITION_DURATION)) { it / 6 }

private val popEnterTransition = fadeIn(tween(TRANSITION_DURATION)) +
        slideInHorizontally(tween(TRANSITION_DURATION)) { it / 6 }

private val popExitTransition = fadeOut(tween(TRANSITION_DURATION)) +
        slideOutHorizontally(tween(TRANSITION_DURATION)) { -it / 6 }

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Auth        : Screen("auth",         "تسجيل الدخول", Icons.Default.Lock)
    object Home        : Screen("home",         "الرئيسية",     Icons.Default.Home)
    object Flashcards  : Screen("flashcards",   "البطاقات",     Icons.Default.Style)
    object FlashcardsCompose : Screen("flashcards_compose", "إنشاء بطاقة", Icons.Default.Add)
    object Study       : Screen("study",        "المراجعة",     Icons.Default.School)
    object QA          : Screen("qa",           "الأسئلة",      Icons.Default.QuestionAnswer)
    object QACompose   : Screen("qa_compose",   "طرح سؤال",     Icons.Default.AddComment)
    object Search      : Screen("search",       "البحث",        Icons.Default.Search)
    object Profile     : Screen("profile",      "حسابي",        Icons.Default.Person)
    object Onboarding  : Screen("onboarding",   "مرحباً",       Icons.Default.AutoAwesome)
    object Bookmarks   : Screen("bookmarks",    "المحفوظات",    Icons.Default.Bookmark)
    object Leaderboard : Screen("leaderboard",  "المتصدرون",    Icons.Default.EmojiEvents)
    object Permissions : Screen("permissions",  "الأذونات",     Icons.Default.Security)
    object ProfileSettings : Screen("profile_settings", "الإعدادات", Icons.Default.Settings)
    object AccountSecurity : Screen("account_security", "الأمان", Icons.Default.VerifiedUser)
    object FlashcardsCategory : Screen("flashcards_category/{category}", "البطاقات", Icons.Default.Style) {
        fun createRoute(category: String): String = "flashcards_category/$category"
    }
    object FlashcardsFocus : Screen("flashcards_focus/{flashcardId}", "البطاقات", Icons.Default.Style) {
        fun createRoute(flashcardId: String): String = "flashcards_focus/$flashcardId"
    }
    object QAFocus : Screen("qa_focus/{questionId}", "الأسئلة", Icons.Default.QuestionAnswer) {
        fun createRoute(questionId: String): String = "qa_focus/$questionId"
    }
}

// Study is accessed from Flashcards screen, not a standalone bottom nav item
val bottomNavItems = listOf(
    Screen.Home,
    Screen.Flashcards,
    Screen.QA,
    Screen.Search,
    Screen.Profile
)

@Composable
private fun Screen.localizedBottomNavLabel(): String = when (this) {
    Screen.Home -> stringResource(R.string.nav_home)
    Screen.Flashcards -> stringResource(R.string.nav_flashcards)
    Screen.QA -> stringResource(R.string.nav_qa)
    Screen.Search -> stringResource(R.string.nav_search)
    Screen.Profile -> stringResource(R.string.nav_profile)
    else -> label
}

private val noBottomBarRoutes = setOf(
    Screen.Auth.route,
    Screen.Study.route,
    Screen.Onboarding.route,
    Screen.Leaderboard.route,
    Screen.Bookmarks.route,
    Screen.Permissions.route,
    Screen.ProfileSettings.route,
    Screen.AccountSecurity.route,
    Screen.FlashcardsCompose.route,
    Screen.QACompose.route,
    Screen.FlashcardsCategory.route,
    Screen.FlashcardsFocus.route,
    Screen.QAFocus.route
)

private val fullBleedRoutes = setOf(
    Screen.Auth.route,
    Screen.Onboarding.route,
    Screen.Permissions.route
)

private val darkStatusBarBackgroundRoutes = setOf(
    Screen.Auth.route,
    Screen.Home.route
)

@Composable
fun EduSpecialNavHost(prefs: UserPreferencesDataStore) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val authToken by prefs.authToken.collectAsState(initial = null)
    val hasFirebaseSession = remember { FirebaseAuth.getInstance() }.currentUser != null
    val hasSession = hasFirebaseSession || !authToken.isNullOrBlank()

    // Determine start destination asynchronously to avoid blocking the main thread.
    // Show a blank loading screen until the check completes (typically < 50ms).
    var startDestination by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val permissionsDone = prefs.isPermissionsDone.first()

        // Show permissions screen on very first launch (before auth/onboarding)
        if (!permissionsDone) {
            startDestination = Screen.Permissions.route
            return@LaunchedEffect
        }

        val storedAuthToken = prefs.authToken.first()
        val hasStoredSession = FirebaseAuth.getInstance().currentUser != null || !storedAuthToken.isNullOrBlank()

        startDestination = if (!hasStoredSession) {
            Screen.Auth.route
        } else {
            val onboardingDone = prefs.isOnboardingDone.first()
            if (onboardingDone) Screen.Home.route else Screen.Onboarding.route
        }
    }

    // Show a minimal loading state while resolving the start destination.
    if (startDestination == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                strokeWidth = 2.5.dp
            )
        }
        return
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        FixedTransparentStatusBar(currentRoute = currentRoute ?: startDestination)
        // Update check runs once after the start destination is resolved.
        val updateViewModel: UpdateViewModel = hiltViewModel()
        LaunchedEffect(startDestination) {
            updateViewModel.checkForUpdate(autoInstall = true)
        }
        // Global update dialog — overlays any screen
        UpdateDialogHost(viewModel = updateViewModel)

        val responsive = rememberResponsiveLayoutInfo()
        val showMainNavigation = currentRoute != null && currentRoute !in noBottomBarRoutes
        val useNavigationRail = showMainNavigation && responsive.useNavigationRail
        val constrainContentPane = currentRoute !in fullBleedRoutes

        Scaffold(
            contentWindowInsets = WindowInsets(0),
            topBar = {
                // API health banner — shown when backend is degraded or unavailable
                ApiStatusBanner()
            },
            bottomBar = {
                if (showMainNavigation && !useNavigationRail) {
                    MainNavigationBar(
                        currentDestination = navBackStackEntry?.destination,
                        onNavigate = { screen ->
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                if (useNavigationRail) {
                    MainNavigationRail(
                        currentDestination = navBackStackEntry?.destination,
                        onNavigate = { screen ->
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    val navHostModifier = if (constrainContentPane) {
                        Modifier
                            .widthIn(max = responsive.contentMaxWidth)
                            .fillMaxWidth()
                            .fillMaxHeight()
                    } else {
                        Modifier.fillMaxSize()
                    }

                    NavHost(
                        navController = navController,
                        startDestination = startDestination!!,
                        modifier = navHostModifier,
                        enterTransition = { enterTransition },
                        exitTransition = { exitTransition },
                        popEnterTransition = { popEnterTransition },
                        popExitTransition = { popExitTransition }
                    ) {
                composable(Screen.Auth.route)        { AuthScreen(navController) }
                composable(Screen.Home.route)        { HomeScreen(navController, innerPadding) }
                composable(Screen.Flashcards.route)  { FlashcardsScreen(navController, innerPadding) }
                composable(Screen.FlashcardsCompose.route)  {
                    FlashcardsScreen(
                        navController = navController,
                        innerPadding = innerPadding,
                        showAddDialogOnStart = true,
                        showBackButton = true
                    )
                }
                composable(Screen.Study.route)       { StudyScreen(navController) }
                composable(Screen.QA.route)          { QAScreen(navController, innerPadding) }
                composable(Screen.QACompose.route)   {
                    QAScreen(
                        navController = navController,
                        innerPadding = innerPadding,
                        showAddDialogOnStart = true,
                        showBackButton = true
                    )
                }
                composable(Screen.Search.route)      { SearchScreen(navController, innerPadding) }
                composable(Screen.Profile.route) {
                    ProfileScreen(
                        navController = navController,
                        innerPadding = innerPadding,
                        updateViewModel = updateViewModel
                    )
                }
                composable(Screen.ProfileSettings.route) {
                    ProfileSettingsScreen(
                        onNavigateBack = { navController.popBackStack() },
                        onNavigateToSecurity = { navController.navigate(Screen.AccountSecurity.route) },
                        onAccountDeleted = {
                            navController.navigate(Screen.Auth.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Screen.AccountSecurity.route) {
                    AccountSecurityScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                composable(
                    route = Screen.FlashcardsCategory.route,
                    arguments = listOf(navArgument("category") { defaultValue = FlashcardCategory.ABA_THERAPY.name })
                ) { backStackEntry ->
                    val categoryArg = backStackEntry.arguments?.getString("category")
                    val initialCategory = categoryArg?.let {
                        runCatching { FlashcardCategory.valueOf(it) }.getOrNull()
                    }
                    FlashcardsScreen(
                        navController = navController,
                        innerPadding = innerPadding,
                        initialCategory = initialCategory,
                        showBackButton = true
                    )
                }
                composable(
                    route = Screen.FlashcardsFocus.route,
                    arguments = listOf(navArgument("flashcardId") { defaultValue = "" })
                ) { backStackEntry ->
                    FlashcardsScreen(
                        navController = navController,
                        innerPadding = innerPadding,
                        focusFlashcardId = backStackEntry.arguments?.getString("flashcardId"),
                        showBackButton = true
                    )
                }
                composable(
                    route = Screen.QAFocus.route,
                    arguments = listOf(navArgument("questionId") { defaultValue = "" })
                ) { backStackEntry ->
                    QAScreen(
                        navController = navController,
                        innerPadding = innerPadding,
                        focusQuestionId = backStackEntry.arguments?.getString("questionId"),
                        showBackButton = true
                    )
                }
                composable(Screen.Onboarding.route)  { OnboardingScreen(navController) }
                composable(Screen.Bookmarks.route)   { BookmarksScreen(navController, innerPadding) }
                composable(Screen.Leaderboard.route) { LeaderboardScreen(navController, innerPadding) }
                composable(Screen.Permissions.route) {
                    // After permissions: go to Auth if not logged in, Onboarding if first time, else Home
                    val nextRoute = if (!hasSession) {
                        Screen.Auth.route
                    } else {
                        // Will be resolved by the normal flow — go to Onboarding check
                        Screen.Onboarding.route
                    }
                    PermissionRequestScreen(
                        navController = navController,
                        nextRoute = nextRoute
                    )
                }
                    }
                }
            }
        }
    }
}

/**
 * Shows a colored banner at the top of the screen when the API is degraded or unavailable.
 * Injected via Hilt — no need to pass it down the composable tree.
 */
/** Main destination navigation for compact screens. */
@Composable
private fun MainNavigationBar(
    currentDestination: NavDestination?,
    onNavigate: (Screen) -> Unit
) {
    NavigationBar(
        tonalElevation = 3.dp,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        bottomNavItems.forEach { screen ->
            val selected = currentDestination?.hierarchy
                ?.any { it.route == screen.route } == true
            MainNavigationBarItem(
                screen = screen,
                selected = selected,
                onClick = { onNavigate(screen) }
            )
        }
    }
}

@Composable
private fun RowScope.MainNavigationBarItem(
    screen: Screen,
    selected: Boolean,
    onClick: () -> Unit
) {
    val label = screen.localizedBottomNavLabel()
    val interactionSource = remember(screen.route) { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val iconScale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.86f
            selected -> 1.08f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "bottom_nav_icon_scale_${screen.route}"
    )
    val iconTranslationY by animateFloatAsState(
        targetValue = if (pressed) 2f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 700f),
        label = "bottom_nav_icon_translation_${screen.route}"
    )

    NavigationBarItem(
        icon = {
            Icon(
                screen.icon,
                contentDescription = label,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                        translationY = iconTranslationY
                    }
            )
        },
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        },
        selected = selected,
        alwaysShowLabel = true,
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        interactionSource = interactionSource,
        onClick = onClick
    )
}

@Composable
private fun MainNavigationRail(
    currentDestination: NavDestination?,
    onNavigate: (Screen) -> Unit
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Spacer(Modifier.height(8.dp))
        bottomNavItems.forEach { screen ->
            val selected = currentDestination?.hierarchy
                ?.any { it.route == screen.route } == true
            MainNavigationRailItem(
                screen = screen,
                selected = selected,
                onClick = { onNavigate(screen) }
            )
        }
    }
}

@Composable
private fun MainNavigationRailItem(
    screen: Screen,
    selected: Boolean,
    onClick: () -> Unit
) {
    val label = screen.localizedBottomNavLabel()
    val interactionSource = remember(screen.route) { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val iconScale by animateFloatAsState(
        targetValue = when {
            pressed -> 0.86f
            selected -> 1.08f
            else -> 1f
        },
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "rail_nav_icon_scale_${screen.route}"
    )

    NavigationRailItem(
        icon = {
            Icon(
                screen.icon,
                contentDescription = label,
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
        },
        label = {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1
            )
        },
        selected = selected,
        alwaysShowLabel = true,
        colors = NavigationRailItemDefaults.colors(
            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        interactionSource = interactionSource,
        onClick = onClick
    )
}

/**
 * Shows a colored banner at the top of the screen when the API is degraded or unavailable.
 */
@Composable
private fun ApiStatusBanner(
    healthMonitor: ApiHealthMonitor = hiltViewModel<ApiStatusViewModel>().healthMonitor
) {
    val status by healthMonitor.status.collectAsState()

    data class BannerConfig(val message: String, val color: Color, val icon: androidx.compose.ui.graphics.vector.ImageVector)

    val config: BannerConfig? = when (status) {
        ApiHealthMonitor.ApiStatus.OFFLINE ->
            BannerConfig("لا يوجد اتصال بالإنترنت — وضع عدم الاتصال", Color(0xFF424242), Icons.Default.WifiOff)
        ApiHealthMonitor.ApiStatus.UNAVAILABLE ->
            BannerConfig("الخادم غير متاح مؤقتاً — البيانات من الذاكرة المحلية", Color(0xFFB71C1C), Icons.Default.CloudOff)
        ApiHealthMonitor.ApiStatus.DEGRADED ->
            BannerConfig("أداء الخادم بطيء — قد يستغرق التحميل وقتاً أطول", Color(0xFFE65100), Icons.Default.SignalWifiStatusbarConnectedNoInternet4)
        ApiHealthMonitor.ApiStatus.HEALTHY -> null
    }

    AnimatedVisibility(
        visible = status != ApiHealthMonitor.ApiStatus.HEALTHY,
        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
    ) {
        config?.let { cfg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(cfg.color)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .semantics { contentDescription = cfg.message },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = cfg.icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = cfg.message,
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Thin ViewModel just to get ApiHealthMonitor injected into a Composable */
@dagger.hilt.android.lifecycle.HiltViewModel
class ApiStatusViewModel @javax.inject.Inject constructor(
    val healthMonitor: ApiHealthMonitor
) : androidx.lifecycle.ViewModel()

@Composable
private fun FixedTransparentStatusBar(currentRoute: String?) {
    val context = LocalContext.current
    val useDarkIcons = currentRoute !in darkStatusBarBackgroundRoutes

    SideEffect {
        val activity = context.findActivity() ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        activity.window.statusBarColor = AndroidColor.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            activity.window.isStatusBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            show(WindowInsetsCompat.Type.statusBars())
            isAppearanceLightStatusBars = useDarkIcons
        }
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
