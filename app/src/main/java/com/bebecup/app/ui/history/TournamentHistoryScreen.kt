package com.bebecup.app.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.R
import com.bebecup.app.data.TournamentRecord
import com.bebecup.app.domain.monthly.MonthlyCup
import com.bebecup.app.ui.components.BabyPhotoIllustration
import com.bebecup.app.ui.components.EmptyState
import com.bebecup.app.ui.components.ScreenHeader
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 우승 전당: every finished world cup, newest first. */
@Composable
fun TournamentHistoryListView(viewModel: BabyCupViewModel, tournaments: List<TournamentRecord>) {
    Column(
        Modifier
            .fillMaxSize()
            .testTag("tournament_history_view")
    ) {
        ScreenHeader(
            title = "우승 전당",
            subtitle = "지금까지 ${tournaments.size}번의 월드컵",
            onBack = { viewModel.navigateBack() }
        )
        if (tournaments.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    illustration = R.drawable.illus_winner,
                    title = "아직 우승 사진이 없어요",
                    body = "보관함 사진으로 첫 월드컵을 열어 보세요!"
                ) {
                    Button(onClick = { viewModel.openWorldCup() }) { Text("월드컵 열기") }
                }
            }
            return@Column
        }
        // A month played twice keeps one "N월의 사진" (per child with siblings): the latest cup.
        // Without siblings a child's cup and a family cup share the month, as the home card counts it.
        val perChild = viewModel.cupsPerChild
        val titleHolders = remember(tournaments, perChild) {
            MonthlyCup.titleHolders(tournaments, { it.monthKey }, { if (perChild) it.childId else null }, { it.timestamp })
                .map { it.id }.toSet()
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(tournaments, key = { it.id }) { item ->
                val date = remember(item.timestamp) {
                    SimpleDateFormat("yyyy년 M월 d일", Locale.KOREA).format(Date(item.timestamp))
                }
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BabyPhotoIllustration(item.winnerUriString, Modifier.size(72.dp))
                        Column(Modifier.padding(start = 14.dp)) {
                            // A child's cup names the child (while they are still registered).
                            val whose = viewModel.children.firstOrNull { it.id == item.childId }?.name?.let { "$it " }.orEmpty()
                            Text(
                                item.monthKey?.takeIf { item.id in titleHolders }
                                    ?.let { "👑 ${MonthlyCup.label(it, withYear = true)}의 ${whose}사진 · ${item.totalParticipants}강" }
                                    ?: "👑 $whose${item.totalParticipants}강 우승",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.testTag("cup_title_${item.id}")
                            )
                            Text(
                                date,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}
