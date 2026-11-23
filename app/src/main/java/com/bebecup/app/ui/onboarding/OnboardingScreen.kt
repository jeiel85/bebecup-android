package com.bebecup.app.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.R
import com.bebecup.app.ui.children.FACE_ID_MODEL_SIZE_LABEL
import com.bebecup.app.ui.components.Illustration
import com.bebecup.app.ui.components.rememberNudgeOptIn
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import kotlinx.coroutines.launch

/**
 * First launch: the four-step journey in BebeCup's own warm voice, ending with
 * the opt-in for "사진이 쌓이면 알려드려요". Shown once.
 */
private data class OnboardingPage(
    @param:DrawableRes val illustration: Int,
    val title: String,
    val body: String
)

private val onboardingPages = listOf(
    OnboardingPage(
        R.drawable.illus_nudge,
        "사진이 쌓이면 살짝 알려드려요",
        "매일 쏟아지는 아기 사진, 정리할 틈이 없죠.\n확인 안 한 사진이 쌓이면 베베컵이 먼저 알려드릴게요."
    ),
    OnboardingPage(
        R.drawable.illus_curate,
        "베스트 후보는 베베컵이 먼저",
        "흔들리거나 눈 감은 컷은 빼고 후보를 추려 드려요.\n분석은 이 기기 안에서만 하고, 사진은 밖으로 나가지 않아요."
    ),
    OnboardingPage(
        R.drawable.illus_worldcup,
        "가족이 함께 고르는 이상형 월드컵",
        "보관한 사진끼리 1:1로 겨뤄\n우리 가족이 뽑은 최고의 한 장을 정해요."
    ),
    OnboardingPage(
        R.drawable.illus_print,
        "골라 둔 사진은 나중에 인화",
        "마음에 든 사진을 인화함에 모아 두었다가\n원하는 인화 앱이나 사이트로 한 번에 보내요."
    )
)

@Composable
fun OnboardingScreen(viewModel: BabyCupViewModel) {
    val pagerState = rememberPagerState(pageCount = { onboardingPages.size })
    val scope = rememberCoroutineScope()
    // 아이 얼굴 구분 is a ~10 MB download, so it is off unless the parent turns it on here.
    var faceId by rememberSaveable { mutableStateOf(false) }
    val optIn = rememberNudgeOptIn(viewModel) { viewModel.completeOnboarding(downloadFaceId = faceId) }
    val isLastPage = pagerState.currentPage == onboardingPages.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("onboarding_view")
            .padding(20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                onClick = { viewModel.completeOnboarding(downloadFaceId = faceId) },
                modifier = Modifier.testTag("onboarding_skip_button")
            ) {
                Text("건너뛰기", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
            }
        }

        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val item = onboardingPages[page]
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Illustration(item.illustration, size = 260.dp)
                Spacer(Modifier.height(24.dp))
                Text(
                    item.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    item.body,
                    fontSize = 15.sp,
                    lineHeight = 23.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            repeat(onboardingPages.size) { index ->
                val selected = index == pagerState.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = if (selected) 22.dp else 8.dp, height = 8.dp)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                            CircleShape
                        )
                )
            }
        }

        if (isLastPage) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text("아이가 둘 이상이에요", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "얼굴로 아이를 구분해 아이별로 월드컵을 열어요. 얼굴 구분 파일(${FACE_ID_MODEL_SIZE_LABEL})을 받아요. 나중에 설정에서도 켤 수 있어요.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
                Switch(
                    checked = faceId,
                    onCheckedChange = { faceId = it },
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .testTag("onboarding_face_id_switch")
                )
            }
            Button(
                onClick = optIn.request,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("onboarding_start_button"),
                shape = RoundedCornerShape(18.dp)
            ) { Text("알림 받고 시작하기", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
            OutlinedButton(
                onClick = { viewModel.completeOnboarding(downloadFaceId = faceId) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .testTag("onboarding_start_without_nudge_button"),
                shape = RoundedCornerShape(18.dp)
            ) { Text("알림 없이 시작하기") }
        } else {
            Button(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("onboarding_next_button"),
                shape = RoundedCornerShape(18.dp)
            ) { Text("다음", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
