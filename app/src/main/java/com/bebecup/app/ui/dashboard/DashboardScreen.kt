package com.bebecup.app.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.R
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.data.TournamentRecord
import com.bebecup.app.ui.components.BabyPhotoIllustration
import com.bebecup.app.ui.components.BabyProfileDialog
import com.bebecup.app.ui.components.Illustration
import com.bebecup.app.ui.components.UpdateBannerContent
import com.bebecup.app.ui.components.rememberStoreUpdate
import com.bebecup.app.ui.components.rememberNudgeOptIn
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen
import com.bebecup.app.update.StoreUpdates

/**
 * Home. Mirrors the app's journey: the top card says how many photos are
 * waiting to be sorted (the nudge), then ① 보관함 ② 월드컵 ③ 인화함.
 */
@Composable
fun DashboardView(
    viewModel: BabyCupViewModel,
    bestPhotos: List<BabyPhoto>,
    printCartPhotos: List<BabyPhoto>,
    tournaments: List<TournamentRecord>
) {
    val context = LocalContext.current
    val storeUpdate = rememberStoreUpdate()
    var editingProfile by remember { mutableStateOf(false) }
    val ageToday = viewModel.ageToday()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("dashboard_view"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (storeUpdate != null) {
            item { UpdateBannerContent(onClick = { StoreUpdates.openStore(context) }) }
        }
        item {
            HomeHeader(
                ageLine = ageToday?.let { age ->
                    val who = viewModel.babyName.ifBlank { "우리 아기" }
                    "오늘 $who ${age.longLabel}"
                },
                onSettings = { viewModel.navigateTo(UiScreen.Settings) }
            )
        }
        item { NudgeHeroCard(viewModel) }
        viewModel.pendingMonthlyCup(bestPhotos, tournaments)?.let { due ->
            item(key = "monthly_cup") {
                MonthlyCupCard(
                    label = viewModel.monthLabel(due.key),
                    photoCount = due.photoCount,
                    onOpen = { viewModel.openWorldCup(due.key) },
                    onSkip = { viewModel.skipMonthlyCup(due.key) }
                )
            }
        }
        item {
            JourneyRow(
                bestCount = bestPhotos.size,
                cupCount = tournaments.size,
                printCount = printCartPhotos.size,
                onBest = { viewModel.navigateTo(UiScreen.BestBox) },
                onCup = { viewModel.openWorldCup() },
                onPrint = { viewModel.navigateTo(UiScreen.PrintCart) }
            )
        }
        if (viewModel.babyBirthDate.isBlank() && !viewModel.babyProfilePromptDismissed && bestPhotos.isNotEmpty()) {
            item {
                BabyProfilePromptCard(
                    onEnter = { editingProfile = true },
                    onLater = { viewModel.dismissBabyProfilePrompt() }
                )
            }
        }
        item { BestBoxPreview(bestPhotos, onOpen = { viewModel.navigateTo(UiScreen.BestBox) }) }
        item {
            FeatureCard(
                illustration = R.drawable.illus_worldcup,
                title = "가족 이상형 월드컵",
                body = if (bestPhotos.size >= 2) "보관한 사진 ${bestPhotos.size}장 중 최고의 한 장을 가족이 함께 뽑아요"
                else "보관함에 사진이 2장 이상 모이면 시작할 수 있어요",
                actionLabel = "월드컵 열기",
                actionEnabled = bestPhotos.size >= 2,
                testTag = "goto_worldcup_button",
                onAction = { viewModel.openWorldCup() }
            )
        }
        item {
            FeatureCard(
                illustration = R.drawable.illus_print,
                title = "인화함 ${printCartPhotos.size}장",
                body = if (printCartPhotos.isEmpty()) "우승 사진이나 보관함 사진을 담아 두면 나중에 한 번에 인화할 수 있어요"
                else "담아 둔 사진을 인화 앱이나 사이트로 한 번에 보낼 수 있어요",
                actionLabel = "인화함 열기",
                actionEnabled = true,
                testTag = "goto_print_button",
                onAction = { viewModel.navigateTo(UiScreen.PrintCart) }
            )
        }
        item {
            TextButton(
                onClick = { viewModel.navigateTo(UiScreen.TournamentHistoryList) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("🏆 우승 전당 · 지금까지 ${tournaments.size}번의 월드컵", fontSize = 13.sp)
            }
        }
    }

    if (editingProfile) {
        BabyProfileDialog(
            initialName = viewModel.babyName,
            initialBirthDate = viewModel.babyBirthDate,
            onSave = { name, birthDate ->
                viewModel.updateBabyProfile(name, birthDate)
                editingProfile = false
            },
            onDismiss = { editingProfile = false }
        )
    }
}

/** 이달의 월드컵: last month's saved photos are waiting for their cup. */
@Composable
private fun MonthlyCupCard(label: String, photoCount: Int, onOpen: () -> Unit, onSkip: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("monthly_cup_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("🏆 ${label}의 월드컵", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    "${label}에 찍어 보관한 사진 ${photoCount}장 중 가족이 함께 ${label}의 사진을 뽑아요. 우승 사진은 우승 전당에 남아요.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = onSkip, modifier = Modifier.testTag("monthly_cup_skip")) { Text("건너뛰기") }
                    Button(
                        onClick = onOpen,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("monthly_cup_open")
                    ) { Text("${label} 월드컵 열기", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable
private fun BabyProfilePromptCard(onEnter: () -> Unit, onLater: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("baby_profile_prompt"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🎂 아기 생일을 알려 주세요", fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(
                "사진마다 몇 개월 때인지 보여 드려요. 생일은 이 기기 안에만 저장돼요.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onLater) { Text("다음에") }
                TextButton(onClick = onEnter, modifier = Modifier.testTag("baby_profile_prompt_enter")) {
                    Text("입력하기", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(ageLine: String?, onSettings: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(
                "베베컵",
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                ageLine ?: "쌓인 아기 사진, 베스트만 골라 두기",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.testTag("home_subtitle")
            )
        }
        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, CircleShape)
                .testTag("goto_settings_button")
        ) {
            Icon(Icons.Default.Settings, contentDescription = "설정")
        }
    }
}

@Composable
private fun NudgeHeroCard(viewModel: BabyCupViewModel) {
    val unsorted = viewModel.unsortedCount
    val canRead = viewModel.canReadPhotos
    val daysSince = viewModel.daysSinceReview()
    val optIn = rememberNudgeOptIn(viewModel)

    val allSorted = canRead && unsorted == 0
    val title = when {
        !canRead -> "쌓인 아기 사진, 베베컵이 먼저 추려 드릴게요"
        unsorted == null -> "최근 사진을 세는 중이에요…"
        unsorted == 0 -> "모두 정리했어요! 🎉"
        else -> "확인 안 한 사진이 ${unsorted}장 쌓였어요"
    }
    val body = when {
        !canRead -> "갤러리를 볼 수 있게 허용하면 정리 안 한 사진이 몇 장인지 알려드려요. 사진은 기기 밖으로 나가지 않아요."
        allSorted && daysSince != null -> "마지막 정리 ${if (daysSince == 0L) "오늘" else "${daysSince}일 전"} · 새 사진이 쌓이면 다시 알려드릴게요"
        daysSince != null -> "마지막 정리 ${if (daysSince == 0L) "오늘" else "${daysSince}일 전"} · 5분이면 베스트를 골라 둘 수 있어요"
        else -> "최근 2주 사진이에요. 5분이면 베스트를 골라 둘 수 있어요"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("nudge_hero_card"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(title, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 25.sp)
                    Text(
                        body,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                }
                Illustration(
                    if (allSorted) R.drawable.illus_curate else R.drawable.illus_nudge,
                    size = 112.dp
                )
            }
            if (allSorted) {
                OutlinedButton(
                    onClick = { viewModel.navigateTo(UiScreen.AiScanSetup) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("goto_ai_curation_button"),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("그래도 다시 골라보기", fontWeight = FontWeight.Bold) }
            } else {
                Button(
                    onClick = { viewModel.navigateTo(UiScreen.AiScanSetup) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("goto_ai_curation_button"),
                    shape = RoundedCornerShape(16.dp)
                ) { Text("지금 베스트 골라보기", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }
            }
            if (!viewModel.nudgeEnabled) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (optIn.declined.value) "알림 권한이 있어야 알려드릴 수 있어요."
                        else "사진이 쌓이면 알려드릴까요?",
                        fontSize = 12.sp,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                    )
                    TextButton(onClick = optIn.request, modifier = Modifier.testTag("nudge_opt_in_button")) {
                        Text("알림 켜기", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun JourneyRow(
    bestCount: Int,
    cupCount: Int,
    printCount: Int,
    onBest: () -> Unit,
    onCup: () -> Unit,
    onPrint: () -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        JourneyStep("① 보관함", "${bestCount}장", Modifier.weight(1f), onBest)
        JourneyStep("② 월드컵", "${cupCount}회", Modifier.weight(1f), onCup)
        JourneyStep("③ 인화함", "${printCount}장", Modifier.weight(1f), onPrint)
    }
}

@Composable
private fun JourneyStep(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun BestBoxPreview(bestPhotos: List<BabyPhoto>, onOpen: () -> Unit) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "베스트 보관함",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onOpen, modifier = Modifier.testTag("goto_best_box_button")) {
                    Text("전체 보기")
                }
            }
            if (bestPhotos.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Illustration(R.drawable.illus_empty, size = 72.dp)
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "아직 보관한 사진이 없어요. 베베컵 추천에서 고르거나 사진첩에서 직접 담아 보세요.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                    )
                }
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(bestPhotos.take(12), key = { it.id }) { photo ->
                        BabyPhotoIllustration(
                            uriString = photo.uriString,
                            modifier = Modifier
                                .size(84.dp)
                                .clickable(onClick = onOpen)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeatureCard(
    illustration: Int,
    title: String,
    body: String,
    actionLabel: String,
    actionEnabled: Boolean,
    testTag: String,
    onAction: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Illustration(illustration, size = 88.dp)
            }
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    body,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
                Button(
                    onClick = onAction,
                    enabled = actionEnabled,
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(disabledContainerColor = Color.LightGray.copy(alpha = 0.4f)),
                    modifier = Modifier.testTag(testTag)
                ) { Text(actionLabel, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}
