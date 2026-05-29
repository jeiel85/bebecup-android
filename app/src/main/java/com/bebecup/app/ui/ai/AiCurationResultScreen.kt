package com.bebecup.app.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.domain.model.PhotoQualityGrade
import com.bebecup.app.domain.model.ShortlistItem
import com.bebecup.app.ui.components.BabyPhotoIllustration
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen

// AI Curation Result (spec §11.3, §11.4)
@Composable
fun AiCurationResultScreen(viewModel: BabyCupViewModel) {
    val shortlist = viewModel.aiShortlist
    val approvedCount = viewModel.aiApprovedIds.size
    var showRejected by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_curation_result_view")
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateTo(UiScreen.Dashboard) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape)
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "홈으로")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text("AI 엄선 결과", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "총 ${viewModel.aiTotalCount}장 중 ${shortlist.size}장을 추천했어요",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }

        // Stats summary
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatChip("추천", "${shortlist.size}장", Modifier.weight(1f))
            StatChip("승인", "${approvedCount}장", Modifier.weight(1f))
            StatChip("제외", "${viewModel.aiRejectedCount}장", Modifier.weight(1f))
        }

        if (viewModel.aiClusterCount > 0) {
            Text(
                "비슷한 사진 묶음 ${viewModel.aiClusterCount}개에서 대표 한 장씩만 추천했어요",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        if (shortlist.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("추천할 만한 사진을 찾지 못했어요.", fontSize = 13.sp, color = Color.Gray)
                    Text("기간을 넓혀 다시 시도해보세요.", fontSize = 11.sp, color = Color.Gray)
                    Button(onClick = { viewModel.navigateTo(UiScreen.AiScanSetup) }) {
                        Text("다시 엄선하기", fontSize = 12.sp)
                    }
                }
            }
        } else {
            Text(
                "AI 추천 베스트 — 추천에서 빼고 싶은 사진은 '제외'를 눌러주세요.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(16.dp)
            )
            LazyColumn(
                modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(shortlist) { item ->
                    ShortlistCard(
                        item = item,
                        approved = viewModel.aiApprovedIds.contains(item.photo.id),
                        onToggle = { viewModel.toggleShortlistApproval(item.photo.id) }
                    )
                }

                // "아쉬운 후보(제외)" section — gently phrased, never deleted (spec §11.5, §15.3).
                val rejected = viewModel.aiRejectedItems
                if (rejected.isNotEmpty()) {
                    item {
                        TextButton(onClick = { showRejected = !showRejected }) {
                            Text(
                                if (showRejected) "아쉬운 후보 접기"
                                else "아쉬운 후보 ${rejected.size}장 보기",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (showRejected) {
                        item {
                            Text(
                                "제외된 사진도 삭제되지 않아요. 마음에 들면 '추천 유지'로 다시 담을 수 있어요.",
                                fontSize = 10.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        items(rejected) { item ->
                            ShortlistCard(
                                item = item,
                                approved = viewModel.aiApprovedIds.contains(item.photo.id),
                                onToggle = { viewModel.toggleShortlistApproval(item.photo.id) }
                            )
                        }
                    }
                }
            }

            // Start tournament from approved AI shortlist (default flow, spec §11.6)
            Surface(shadowElevation = 8.dp) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    val bracket = pickBracketSize(approvedCount)
                    Button(
                        onClick = { viewModel.startWorldCupFromShortlist(bracket) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("start_worldcup_from_ai_btn"),
                        shape = RoundedCornerShape(14.dp),
                        enabled = approvedCount >= 2
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (approvedCount >= 2) "AI 추천 사진으로 월드컵 시작 (${bracket}강)"
                            else "최소 2장을 승인해주세요",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

private fun pickBracketSize(approvedCount: Int): Int = when {
    approvedCount >= 16 -> 16
    approvedCount >= 8 -> 8
    else -> 4
}

@Composable
private fun StatChip(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 10.sp, color = Color.Gray)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ShortlistCard(item: ShortlistItem, approved: Boolean, onToggle: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box {
                BabyPhotoIllustration(uriString = item.photo.uriString, modifier = Modifier.size(80.dp))
                GradeBadge(item.grade, modifier = Modifier.align(Alignment.TopStart).padding(4.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.photo.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                // Natural-language summary (top photos), else reason chips.
                val aiReason = item.aiReason
                if (!aiReason.isNullOrBlank()) {
                    Text(
                        aiReason,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        lineHeight = 15.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                val reasons = item.positiveReasons.ifEmpty { item.rejectReasons }
                reasons.take(2).forEach { reason ->
                    Text("· $reason", fontSize = 10.sp, color = Color.DarkGray, lineHeight = 14.sp)
                }
            }

            // Parent control: keep / exclude
            OutlinedButton(
                onClick = onToggle,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                colors = if (approved) ButtonDefaults.outlinedButtonColors()
                else ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray)
            ) {
                Text(if (approved) "추천 유지" else "제외됨", fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun GradeBadge(grade: PhotoQualityGrade, modifier: Modifier = Modifier) {
    val color = when (grade) {
        PhotoQualityGrade.S -> Color(0xFFFFB300)
        PhotoQualityGrade.A -> Color(0xFF66BB6A)
        PhotoQualityGrade.B -> Color(0xFF42A5F5)
        else -> Color(0xFFBDBDBD)
    }
    Box(
        modifier = modifier
            .background(color, CircleShape)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(grade.label, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
    }
}
