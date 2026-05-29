package com.bebecup.app.ui.tournament

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.ui.components.BabyPhotoIllustration
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen

// 5. WORLD CUP ACTIVE TOURNAMENT GAMEPLAY MATCH
@Composable
fun WorldCupPlayView(
    viewModel: BabyCupViewModel
) {
    val left = viewModel.leftCandidate
    val right = viewModel.rightCandidate

    if (left == null || right == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("world_cup_play_view")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Progress text
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = viewModel.currentRoundName,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "매치 ${viewModel.currentMatchIndex + 1}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
        }

        Text(
            text = "두 사진 중 더 가슴을 울리고 인화하고 싶은 아기 최고의 순간을 아래에서 선택해 주세요! 💕",
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        )

        // Comparison Stack Panels
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left contestant
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { viewModel.submitMatchVote(left) }
                    .testTag("vote_left_candidate"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    BabyPhotoIllustration(
                        uriString = left.uriString,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        isExpanded = true
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "후보 A",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = left.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = left.description,
                            fontSize = 10.sp,
                            color = Color.Gray,
                            lineHeight = 14.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Central VS visual accent badge
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(32.dp)
                    .background(Color(0xFFFFD97D), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "VS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5D4037))
            }

            // Right contestant
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { viewModel.submitMatchVote(right) }
                    .testTag("vote_right_candidate"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    BabyPhotoIllustration(
                        uriString = right.uriString,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        isExpanded = true
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "후보 B",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = right.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = right.description,
                            fontSize = 10.sp,
                            color = Color.Gray,
                            lineHeight = 14.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.navigateTo(UiScreen.Dashboard) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray),
                modifier = Modifier.testTag("match_exit_btn")
            ) {
                Text("월드컵 기권 (홈으로)", color = Color.DarkGray, fontSize = 11.sp)
            }
            Text(
                text = "탭하여 표 투표하기",
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
