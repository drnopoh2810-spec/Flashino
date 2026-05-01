package com.eduspecial.presentation.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.airbnb.lottie.compose.*
import com.eduspecial.R
import com.eduspecial.presentation.common.rememberResponsiveLayoutInfo
import com.eduspecial.presentation.navigation.Screen

private data class OnboardingPageData(
    val lottieRes: Int,
    val title: String,
    val description: String
)

private val onboardingPages = listOf(
    OnboardingPageData(
        lottieRes = R.raw.lottie_flashcards,
        title = "بطاقات تعليمية تفاعلية",
        description = "تعلّم مصطلحات ABA والتعليم الخاص من خلال بطاقات تعليمية غنية بالوسائط المتعددة"
    ),
    OnboardingPageData(
        lottieRes = R.raw.lottie_study,
        title = "نظام المراجعة الذكية",
        description = "يعتمد التطبيق على خوارزمية التكرار المتباعد (SRS) لمساعدتك على الاحتفاظ بالمعلومات لفترة أطول"
    ),
    OnboardingPageData(
        lottieRes = R.raw.lottie_qa,
        title = "منتدى الأسئلة والأجوبة",
        description = "اطرح أسئلتك واحصل على إجابات من مجتمع متخصصي ABA والتعليم الخاص"
    )
)

@Composable
fun OnboardingScreen(
    navController: NavController,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val responsive = rememberResponsiveLayoutInfo()
    val currentPage by viewModel.currentPage.collectAsState()
    val isComplete by viewModel.isComplete.collectAsState()
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })

    // Sync pager with ViewModel page
    LaunchedEffect(currentPage) {
        pagerState.animateScrollToPage(currentPage)
    }

    // Navigate to Home when complete
    LaunchedEffect(isComplete) {
        if (isComplete) {
            navController.navigate(Screen.Home.route) {
                popUpTo(Screen.Onboarding.route) { inclusive = true }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Skip button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { viewModel.skip() }) {
                Text("تخطي", style = MaterialTheme.typography.bodyMedium)
            }
        }

        // Pager
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f),
            userScrollEnabled = true
        ) { page ->
            OnboardingPage(data = onboardingPages[page])
        }

        // Page indicator dots
        PageIndicatorRow(
            pageCount = onboardingPages.size,
            currentPage = pagerState.currentPage
        )

        Spacer(Modifier.height(24.dp))

        // Navigation buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            val isLastPage = pagerState.currentPage == onboardingPages.size - 1
            com.eduspecial.presentation.common.PrimaryButton(
                text = if (isLastPage) "ابدأ الآن" else "التالي",
                onClick = {
                    if (isLastPage) viewModel.complete()
                    else viewModel.nextPage()
                },
                modifier = Modifier
                    .widthIn(max = responsive.formMaxWidth)
                    .fillMaxWidth(0.7f),
                contentDesc = if (isLastPage) "ابدأ استخدام التطبيق" else "الصفحة التالية"
            )
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun OnboardingPage(data: OnboardingPageData) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(data.lottieRes))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = Modifier.size(240.dp)
        )
        Spacer(Modifier.height(32.dp))
        Text(
            text = data.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = data.description,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PageIndicatorRow(
    pageCount: Int,
    currentPage: Int
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == currentPage) 12.dp else 8.dp)
                    .clip(CircleShape)
                    .background(
                        if (index == currentPage)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
            )
        }
    }
}
