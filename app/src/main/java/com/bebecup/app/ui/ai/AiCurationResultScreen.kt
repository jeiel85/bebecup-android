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
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.data.children.ChildEntity
import com.bebecup.app.domain.model.PhotoQualityGrade
import com.bebecup.app.ui.children.PhotoChildrenDialog
import com.bebecup.app.ui.viewmodel.FaceIdState
import androidx.compose.runtime.remember
import com.bebecup.app.domain.model.ShortlistItem
import com.bebecup.app.R
import com.bebecup.app.ui.components.BabyPhotoIllustration
import com.bebecup.app.ui.components.EmptyState
import com.bebecup.app.ui.components.ScreenHeader
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen

// AI Curation Result (spec §11.3, §11.4)
@Composable
fun AiCurationResultScreen(viewModel: BabyCupViewModel) {
    val shortlist = viewModel.aiShortlist
    val approvedCount = viewModel.aiApprovedIds.size
    var showRejected by rememberSaveable { mutableStateOf(false) }
    var whoPhoto by remember { mutableStateOf<BabyPhoto?>(null) }
    val showNames = viewModel.children.size >= 2 || viewModel.faceIdState == FaceIdState.READY

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_curation_result_view")
    ) {
        ScreenHeader(
            title = "베베컵 추천 결과",
            subtitle = "총 ${viewModel.aiTotalCount}장 중 ${shortlist.size}장을 추천했어요",
            onBack = { viewModel.navigateHome() }
        )

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

        if (viewModel.faceGroups.isNotEmpty()) {
            Card(
                onClick = { viewModel.navigateTo(UiScreen.Children) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 10.dp)
                    .testTag("result_face_naming")
            ) {
                Text(
                    "얼굴 ${viewModel.faceGroups.size}명이 누구인지 알려 주세요. 알려 주시면 아이별로 나눠 드려요.",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (viewModel.aiClusterCount > 0) {
            Text(
                "비슷한 사진 묶음 ${viewModel.aiClusterCount}개에서 대표 한 장씩만 추천했어요",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
        }

        val rejected = viewModel.aiRejectedItems
        if (shortlist.isEmpty() && rejected.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(
                    illustration = R.drawable.illus_empty,
                    title = if (viewModel.aiTotalCount == 0) "새로 쌓인 사진이 없어요" else "추천할 만한 사진을 찾지 못했어요",
                    body = if (viewModel.aiTotalCount == 0) "이 기간에는 정리할 사진이 없어요. 기간을 넓혀 볼 수도 있어요."
                    else "얼굴이 잘 보이는 사진이 적었어요. 기간을 넓혀 다시 골라 보세요."
                ) {
                    Button(onClick = { viewModel.navigateTo(UiScreen.AiScanSetup) }) { Text("기간 바꿔 다시 골라보기") }
                    TextButton(onClick = { viewModel.saveCurationRound(thenPlay = false) }) { Text("이번 정리는 끝내기") }
                }
            }
        } else {
            Text(
                if (shortlist.isEmpty()) "추천 기준을 넘은 사진은 없었어요. 아래 아쉬운 후보 중 마음에 드는 사진을 담아 보세요."
                else "베베컵이 고른 후보예요. 빼고 싶은 사진은 '추천 유지'를 눌러 제외해 주세요.",
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
                        onToggle = { viewModel.toggleShortlistApproval(item.photo.id) },
                        children = viewModel.childrenIn(item.photo),
                        onWho = if (showNames) ({ whoPhoto = item.photo }) else null
                    )
                }

                // "아쉬운 후보(제외)" section — gently phrased, never deleted (spec §11.5, §15.3).
                // With an empty shortlist it is the only content, so it is always open.
                val rejectedOpen = showRejected || shortlist.isEmpty()
                if (rejected.isNotEmpty()) {
                    if (shortlist.isNotEmpty()) item {
                        TextButton(onClick = { showRejected = !showRejected }) {
                            Text(
                                if (showRejected) "아쉬운 후보 접기"
                                else "아쉬운 후보 ${rejected.size}장 보기",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (rejectedOpen) {
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
                                onToggle = { viewModel.toggleShortlistApproval(item.photo.id) },
                                children = viewModel.childrenIn(item.photo),
                                onWho = if (showNames) ({ whoPhoto = item.photo }) else null
                            )
                        }
                    }
                }
            }

            // Save the round: approved photos go to the 보관함 (and optionally straight into a world cup).
            Surface(shadowElevation = 8.dp) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { viewModel.saveCurationRound(thenPlay = true) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("start_worldcup_from_ai_btn"),
                        shape = RoundedCornerShape(16.dp),
                        enabled = approvedCount >= 2
                    ) {
                        Text(
                            if (approvedCount >= 2) "${approvedCount}장 보관하고 바로 월드컵"
                            else "월드컵은 2장 이상부터 할 수 있어요",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 15.sp
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.saveCurationRound(thenPlay = false) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("save_round_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            if (approvedCount > 0) "${approvedCount}장 보관함에만 담기" else "담지 않고 정리 끝내기",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    whoPhoto?.let { photo ->
        PhotoChildrenDialog(viewModel = viewModel, photo = photo, onDismiss = { whoPhoto = null })
    }
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
private fun ShortlistCard(
    item: ShortlistItem,
    approved: Boolean,
    onToggle: () -> Unit,
    children: List<ChildEntity> = emptyList(),
    onWho: (() -> Unit)? = null
) {
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
                if (onWho != null) {
                    TextButton(
                        onClick = onWho,
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(28.dp)
                            .testTag("who_${item.photo.id}")
                    ) {
                        Text(
                            if (children.isEmpty()) "누구 사진이에요?" else children.joinToString(" · ") { it.name },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
