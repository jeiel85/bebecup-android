package com.bebecup.app.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import kotlinx.coroutines.launch

/**
 * First-launch onboarding (spec §11.0). Three pages, told in 베베컵's own warm
 * first-person voice so the AI feels like a friendly helper rather than a faceless
 * algorithm. Page 2 is the non-negotiable privacy promise: photos never leave the
 * phone without consent. Shown once, then [BabyCupViewModel.completeOnboarding]
 * persists the flag and routes home.
 */
private data class OnboardingPage(
    val emoji: String,
    val title: String,
    val body: String
)

private val onboardingPages = listOf(
    OnboardingPage(
        emoji = "🧡",
        title = "안녕하세요, 베베컵이에요",
        body = "매주 쏟아지는 아기 사진,\n흔들리거나 비슷한 컷은 빼고\n베베컵이 베스트만 쏙 골라드릴게요."
    ),
    OnboardingPage(
        emoji = "🔒",
        title = "사진은 늘 폰 안에만 있어요",
        body = "베베컵은 모든 분석을 이 기기 안에서만 해요.\n소중한 아기 사진은 부모님이 동의하지 않는 한\n절대 밖으로 나가지 않아요."
    ),
    OnboardingPage(
        emoji = "📷",
        title = "이제 시작해볼까요?",
        body = "최근 사진을 살펴보려면\n사진 접근 권한이 필요해요.\n원하지 않으면 사진첩에서 직접 골라도 좋아요."
    )
)

@Composable
fun OnboardingScreen(viewModel: BabyCupViewModel) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == onboardingPages.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("onboarding_view")
            .padding(24.dp)
    ) {
        // Skip — always available, respects parents who want to dive straight in.
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            TextButton(
                onClick = { viewModel.completeOnboarding() },
                modifier = Modifier.testTag("onboarding_skip_button")
            ) {
                Text("건너뛰기", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { page ->
            val item = onboardingPages[page]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(128.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(item.emoji, fontSize = 64.sp)
                }
                Spacer(modifier = Modifier.height(36.dp))
                Text(
                    text = item.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = item.body,
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
        }

        // Page indicator dots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(onboardingPages.size) { index ->
                val selected = pagerState.currentPage == index
                val width by animateDpAsState(if (selected) 22.dp else 8.dp, label = "dot_width")
                val color by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    label = "dot_color"
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(8.dp)
                        .width(width)
                        .background(color, CircleShape)
                )
            }
        }

        Button(
            onClick = {
                if (isLastPage) {
                    viewModel.completeOnboarding()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag(if (isLastPage) "onboarding_start_button" else "onboarding_next_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                if (isLastPage) "베베컵 시작하기" else "다음",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 15.sp,
                color = Color.White
            )
        }
    }
}
