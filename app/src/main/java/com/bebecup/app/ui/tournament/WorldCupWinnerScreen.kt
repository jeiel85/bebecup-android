package com.bebecup.app.ui.tournament

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.R
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.ui.components.BabyPhotoIllustration
import com.bebecup.app.ui.components.Illustration
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen

@Composable
fun WorldCupWinnerCelebrationView(
    viewModel: BabyCupViewModel,
    winner: BabyPhoto,
    bracketSize: Int,
    monthKey: String?,
    inPrintCart: Boolean
) {
    val aiMatched = viewModel.aiMatchForWinner(winner.id)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .testTag("world_cup_winner_view")
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            monthKey?.let { "${viewModel.monthLabel(it)}의 사진!" } ?: "${bracketSize}강 우승!",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.testTag("winner_title")
        )
        Card(
            shape = RoundedCornerShape(32.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            modifier = Modifier.size(280.dp)
        ) {
            Box {
                BabyPhotoIllustration(winner.uriString, Modifier.fillMaxSize(), isExpanded = true)
                // Small crowned mascot cheering in the corner, never covering the photo's centre.
                Illustration(
                    R.drawable.illus_winner,
                    size = 84.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                )
            }
        }
        viewModel.ageOf(winner)?.let { age ->
            Text(
                "${age.longLabel} 때 사진",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("winner_age")
            )
        }
        Text(
            "우리 가족이 뽑은 최고의 순간이에요",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        if (aiMatched != null) {
            Text(
                if (aiMatched) "베베컵 추천 1위와 같은 사진을 골랐어요! 👀✨"
                else "베베컵 추천 1위와는 다른 사진이에요. 역시 가족의 눈이 제일이죠 💕",
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
        Button(
            onClick = { viewModel.addToPrintCart(winner) },
            enabled = !inPrintCart,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("winner_add_to_print_button"),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                if (inPrintCart) "인화함에 담았어요 ✓" else "인화함에 담기",
                fontWeight = FontWeight.ExtraBold
            )
        }
        OutlinedButton(
            onClick = { viewModel.navigateTo(UiScreen.PrintCart) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) { Text("인화함 보기") }
        TextButton(onClick = { viewModel.navigateHome() }) { Text("홈으로") }
    }
}
