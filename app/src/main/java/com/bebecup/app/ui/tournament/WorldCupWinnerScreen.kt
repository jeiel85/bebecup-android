package com.bebecup.app.ui.tournament

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.ui.components.BabyPhotoIllustration
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen

// 6. WINNER CELEBRATION TROPHY SHOWCASE
@Composable
fun WorldCupWinnerCelebrationView(
    viewModel: BabyCupViewModel,
    winner: BabyPhoto,
    bracketSize: Int
) {
    val scale = remember { Animatable(0.5f) }

    // Scale animation upon entry
    LaunchedEffect(key1 = true) {
        scale.animateTo(
            targetValue = 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("world_cup_winner_view")
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "🎉 월드컵 최종 우승! 🏅",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "엄빠 피셜 오늘의 베스트 레전드 샷 결정 완료",
            fontSize = 12.sp,
            color = Color.Gray
        )

        // Center Champion illustration card
        Card(
            modifier = Modifier
                .size(260.dp)
                .shadow(8.dp, RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                BabyPhotoIllustration(
                    uriString = winner.uriString,
                    modifier = Modifier.fillMaxSize(),
                    isExpanded = true
                )

                // Top golden Crown decoration
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .background(Color(0xFFFFD97D), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "👑 CHAMPION", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF5D4037))
                }
            }
        }

        Text(
            text = winner.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = winner.description,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Stats card details
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("대회 규모", fontSize = 11.sp, color = Color.Gray)
                    Text("${bracketSize}강 토너먼트", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("누적 우승 기록", fontSize = 11.sp, color = Color.Gray)
                    Text("${winner.winsCount}회 트로피", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // AI vs parent pick comparison (spec §11.7) — only when AI-sourced.
        val aiMatched = viewModel.aiMatchForWinner(winner.id)
        if (aiMatched != null) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("ai_match_card"),
                colors = CardDefaults.cardColors(
                    containerColor = if (aiMatched) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (aiMatched) "🤝 AI도 같은 사진을 1위로 골랐어요" else "🙂 AI는 다른 사진을 1위로 봤어요",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (aiMatched) {
                            "선명도와 표정 기준에서도 부모님 선택과 일치했어요."
                        } else {
                            "AI는 선명도·시선 기준으로 \"${viewModel.aiTopPickTitle ?: "다른 사진"}\"을 골랐지만, 최종 베스트는 언제나 부모님의 선택이에요."
                        },
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Actions
        Button(
            onClick = {
                viewModel.addWinnerToPrintCart(winner)
                viewModel.navigateTo(UiScreen.PrintCart)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("add_winner_to_cart_btn"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = "Cart")
                Spacer(modifier = Modifier.width(8.dp))
                Text("인화 장바구니에 담고 바로 주문하기", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.navigateTo(UiScreen.Dashboard) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("winner_exit_home_btn"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("우승 완료! 홈으로", fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = { viewModel.navigateTo(UiScreen.WorldCupSetup) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("winner_restart_cup_btn"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("다른 한 판 더 하기", fontSize = 11.sp)
            }
        }
    }
}
