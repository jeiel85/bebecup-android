package com.bebecup.app.ui.tournament

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.R
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.domain.tournament.TournamentEngine
import com.bebecup.app.ui.components.EmptyState
import com.bebecup.app.ui.components.Illustration
import com.bebecup.app.ui.components.ScreenHeader
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen

/** Pick a bracket size; entrants are the most recently saved photos in the 보관함. */
@Composable
fun WorldCupSetupView(viewModel: BabyCupViewModel, bestPhotos: List<BabyPhoto>) {
    val sizes = TournamentEngine.availableSizes(bestPhotos.size)
    var selected by rememberSaveable(sizes) { mutableIntStateOf(sizes.lastOrNull() ?: 0) }

    Column(
        Modifier
            .fillMaxSize()
            .testTag("world_cup_setup_view")
    ) {
        ScreenHeader(title = "가족 이상형 월드컵", onBack = { viewModel.navigateBack() })

        if (sizes.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    illustration = R.drawable.illus_worldcup,
                    title = "보관함에 사진이 2장 이상 필요해요",
                    body = "베베컵 추천에서 사진을 보관하거나 사진첩에서 직접 담은 뒤 다시 와 주세요."
                ) {
                    Button(onClick = { viewModel.navigateTo(UiScreen.AiScanSetup) }) { Text("베스트 골라보기") }
                }
            }
            return
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Illustration(R.drawable.illus_worldcup, size = 220.dp)
            Text(
                "보관함의 최근 사진 ${selected}장이 1:1로 겨뤄요.\n가족과 함께 골라 최고의 한 장을 뽑아 보세요!",
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(4.dp))
            Text("몇 강으로 할까요?", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                sizes.forEach { size ->
                    val isSelected = size == selected
                    Box(
                        Modifier
                            .weight(1f)
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { selected = size }
                            .padding(vertical = 14.dp)
                            .testTag("bracket_size_$size"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${size}강",
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Button(
            onClick = { viewModel.startWorldCup(selected) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .height(54.dp)
                .testTag("start_worldcup_button"),
            shape = RoundedCornerShape(18.dp)
        ) { Text("월드컵 시작!", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
    }
}
