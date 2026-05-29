package com.bebecup.app.ui.tournament

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen

// 4. WORLD CUP STUDY & PREPARATION CARD SETUP
@Composable
fun WorldCupSetupView(
    viewModel: BabyCupViewModel,
    bestPhotos: List<BabyPhoto>,
    allPhotos: List<BabyPhoto>
) {
    var sizePreference by remember { mutableIntStateOf(4) }

    // Total contest pool counts (best photos + others if fallback is needed)
    val totalAvailablePhotos = allPhotos.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("world_cup_setup_view")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateTo(UiScreen.Dashboard) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape)
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "월드컵 대진 설정",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "대진 규모 선택",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val scaleSizes = listOf(4, 8, 16)
                    scaleSizes.forEach { n ->
                        val isSel = sizePreference == n
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { sizePreference = n }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${n}강전",
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color.White else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "대진 보강 룰 설명 💡",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "현재 엄마 아빠가 하트(🧡) 표시로 엄선한 베스트 샷은 총 ${bestPhotos.size}장 있습니다.\n\n선택하신 ${sizePreference}강전을 채우기에 베스트 사진 수가 모자라면, 사진첩에 등록된 다른 실제 사진으로만 대진표를 보강합니다.",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.startWorldCup(allPhotos, sizePreference) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("initiate_cup_play_btn"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("위 설정으로 월드컵 개막하기 🏆", fontWeight = FontWeight.ExtraBold)
        }
    }
}
