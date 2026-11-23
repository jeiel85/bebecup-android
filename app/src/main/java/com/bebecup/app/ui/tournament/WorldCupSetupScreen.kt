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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
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
import com.bebecup.app.domain.monthly.MonthlyCup
import com.bebecup.app.domain.tournament.TournamentEngine
import com.bebecup.app.ui.components.EmptyState
import com.bebecup.app.ui.components.Illustration
import com.bebecup.app.ui.components.ScreenHeader
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen

/**
 * Pick which photos compete and the bracket size. Entrants are the most
 * recently saved photos in the 보관함, or — for a 이달의 월드컵 — the saved
 * photos taken in one month, whose winner is kept as that month's photo.
 * With siblings every cup is one child's ([BabyCupViewModel.cupsPerChild]).
 */
@Composable
fun WorldCupSetupView(viewModel: BabyCupViewModel, bestPhotos: List<BabyPhoto>) {
    val tournaments by viewModel.tournaments.collectAsState()
    // On a cold start (e.g. from a reminder) whether there are siblings is not known yet.
    if (!viewModel.childrenLoaded) {
        Box(Modifier.fillMaxSize())
        return
    }
    val perChild = viewModel.cupsPerChild
    // Whose cup: only children with at least two saved photos can have one.
    val childChoices = viewModel.children
        .map { it to viewModel.photosOf(bestPhotos, it.id).size }
        .filter { it.second >= 2 }
    val childId = viewModel.worldCupChildId?.takeIf { id -> childChoices.any { it.first.id == id } }
        ?: viewModel.defaultCupChild(bestPhotos, viewModel.worldCupMonth, tournaments)
    // Keep the child the screen opened on: picking a month later must not switch whose cup it is.
    LaunchedEffect(perChild, childId) {
        if (perChild && childId != null && viewModel.worldCupChildId != childId) viewModel.selectWorldCupChild(childId)
    }
    val childName = childChoices.firstOrNull { it.first.id == childId }?.first?.name
    val childPhotos = viewModel.photosOf(bestPhotos, childId)
    val months = remember(childPhotos) { MonthlyCup.months(childPhotos.map { it.takenDate }) }
    // A month that no longer has enough photos (e.g. from an old reminder) falls back to "최근".
    val month = viewModel.worldCupMonth?.takeIf { key -> months.any { it.key == key } }
    val pool = remember(childPhotos, month) { viewModel.worldCupPool(childPhotos, month, childId) }
    val sizes = TournamentEngine.availableSizes(pool.size)
    var selected by rememberSaveable(sizes) { mutableIntStateOf(sizes.lastOrNull() ?: 0) }

    Column(
        Modifier
            .fillMaxSize()
            .testTag("world_cup_setup_view")
    ) {
        ScreenHeader(
            title = childName?.let { "$it 이상형 월드컵" } ?: if (perChild) "이상형 월드컵" else "가족 이상형 월드컵",
            onBack = { viewModel.navigateBack() }
        )

        if (perChild && childId == null) {
            // Siblings, but no child has two saved photos known to be theirs yet.
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    illustration = R.drawable.illus_worldcup,
                    title = "아이별로 월드컵을 열어요",
                    body = "형제끼리 겨루지 않도록 아이마다 따로 월드컵을 해요. " +
                        "'아이 얼굴 구분'을 켜고 누구 사진인지 알려 주시면, 아이마다 보관한 사진이 2장 이상일 때 열 수 있어요."
                ) {
                    Button(
                        onClick = { viewModel.navigateTo(UiScreen.Children) },
                        modifier = Modifier.testTag("cup_needs_children")
                    ) { Text("우리 아이들") }
                }
            }
            return
        }

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Illustration(R.drawable.illus_worldcup, size = 200.dp)
            val label = month?.let { viewModel.monthLabel(it) }
            val whose = childName?.let { "$it 사진" } ?: "사진"
            Text(
                if (label == null) "보관함의 최근 $whose ${selected}장이 1:1로 겨뤄요.\n가족과 함께 골라 최고의 한 장을 뽑아 보세요!"
                else "${label}에 찍은 $whose ${selected}장이 1:1로 겨뤄요.\n우승 사진은 ${label}의 ${whose}으로 우승 전당에 남아요!",
                fontSize = 14.sp,
                lineHeight = 21.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                modifier = Modifier.testTag("world_cup_setup_description")
            )
            if (perChild || (childChoices.isNotEmpty() && childChoices.single().second < bestPhotos.size)) {
                Spacer(Modifier.height(2.dp))
                Text("누구의 월드컵인가요?", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    // One child: their cup or the family's over every photo. Siblings: no shared cup.
                    if (!perChild) item {
                        Choice(
                            text = "모두 · ${bestPhotos.size}장",
                            selected = childId == null,
                            testTag = "cup_child_all"
                        ) { viewModel.selectWorldCupChild(null) }
                    }
                    items(childChoices, key = { it.first.id }) { (child, count) ->
                        Choice(
                            text = "${child.name} · ${count}장",
                            selected = childId == child.id,
                            testTag = "cup_child_${child.id}"
                        ) { viewModel.selectWorldCupChild(child.id) }
                    }
                }
            }
            if (months.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text("어떤 사진으로 할까요?", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    item {
                        Choice(
                            text = "최근 보관",
                            selected = month == null,
                            testTag = "cup_month_recent"
                        ) { viewModel.selectWorldCupMonth(null) }
                    }
                    items(months, key = { it.key }) { m ->
                        Choice(
                            text = "${viewModel.monthLabel(m.key)} · ${m.photoCount}장",
                            selected = month == m.key,
                            testTag = "cup_month_${m.key}"
                        ) { viewModel.selectWorldCupMonth(m.key) }
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Text("몇 강으로 할까요?", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                sizes.forEach { size ->
                    Choice(
                        text = "${size}강",
                        selected = size == selected,
                        testTag = "bracket_size_$size",
                        modifier = Modifier.weight(1f)
                    ) { selected = size }
                }
            }
        }

        Button(
            onClick = { viewModel.startWorldCup(selected, month, childId) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .height(54.dp)
                .testTag("start_worldcup_button"),
            shape = RoundedCornerShape(18.dp)
        ) { Text("월드컵 시작!", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold) }
    }
}

@Composable
private fun Choice(
    text: String,
    selected: Boolean,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontWeight = FontWeight.ExtraBold,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
        )
    }
}
