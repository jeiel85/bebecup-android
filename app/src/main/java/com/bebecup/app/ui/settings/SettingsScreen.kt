package com.bebecup.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.BuildConfig
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.HqModelUiState
import com.bebecup.app.ui.viewmodel.UiScreen

// Settings: AI curation defaults + privacy controls (spec §9).
@Composable
fun SettingsScreen(viewModel: BabyCupViewModel) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_view")
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { viewModel.navigateTo(UiScreen.Dashboard) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape)
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text("설정", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        // --- 베베컵 엄선 기본값 ---
        SectionTitle("베베컵 엄선 기본값")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("기본 스캔 기간", fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    listOf(1 to "오늘", 7 to "7일", 30 to "30일").forEach { (days, label) ->
                        val selected = viewModel.aiScanRangeDays == days
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { viewModel.updateScanRangeDays(days) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                ToggleRow("자는 사진도 좋은 사진으로 포함", viewModel.aiSleepingModeEnabled) { viewModel.updateSleepingMode(it) }
                ToggleRow("비슷한 사진은 한 장만 추천", viewModel.aiSimilarOnly) { viewModel.updateSimilarOnly(it) }
                ToggleRow("흔들린 사진은 자동 제외", viewModel.aiExcludeBlurry) { viewModel.updateExcludeBlurry(it) }
            }
        }

        // --- 개인정보 ---
        SectionTitle("개인정보 보호")
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "베베컵은 사진을 기기 안에서만 살펴봐요. 사진은 외부 서버로 올라가지 않고, " +
                        "부모님이 승인하지 않은 사진은 공유되지 않아요.",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.fillMaxWidth().testTag("settings_delete_data_btn")
                ) {
                    Text("기기에 저장된 분석 데이터 전체 삭제", fontSize = 12.sp)
                }
                if (deleted) {
                    Text("분석 데이터를 모두 삭제했어요.", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // --- 고화질 엄선 모델 ---
        // Visible once a model is actually hosted (configured) or already on the
        // device. Stays hidden in builds where no model is wired yet, so users
        // never see a download that does nothing. Debug builds always show it.
        val showHqModel = BuildConfig.DEBUG ||
            viewModel.hqModelConfigured ||
            viewModel.hqModelState is HqModelUiState.Installed
        if (showHqModel) {
            SectionTitle("고화질 엄선 모델")
            HqModelCard(viewModel)
        }

        // --- 앱 정보 ---
        SectionTitle("앱 정보")
        Text(
            "베베컵 v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("분석 데이터 삭제") },
            text = { Text("기기에 저장된 분석 결과·묶음·세션 기록을 모두 지웁니다. 사진 원본은 삭제되지 않아요.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteAllAnalysisData()
                    showDeleteConfirm = false
                    deleted = true
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("취소") }
            }
        )
    }
}

@Composable
private fun HqModelCard(viewModel: BabyCupViewModel) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "더 똑똑한 엄선을 위해 고화질 분석 모델을 한 번만 내려받아요. " +
                    "받은 모델도 기기 안에서만 동작하고, 사진은 여전히 밖으로 나가지 않아요.",
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            val sizeLabel = viewModel.hqModelApproxBytes
                .takeIf { it > 0 }
                ?.let { " (약 ${it / (1024 * 1024)}MB · Wi-Fi 권장)" }
                ?: ""

            when (val state = viewModel.hqModelState) {
                is HqModelUiState.NotInstalled -> {
                    Button(
                        onClick = { viewModel.downloadHqModel() },
                        enabled = viewModel.hqModelConfigured,
                        modifier = Modifier.fillMaxWidth().testTag("hq_model_download_btn")
                    ) { Text("고화질 모델 받기$sizeLabel", fontSize = 13.sp) }
                    if (!viewModel.hqModelConfigured) {
                        Text(
                            "아직 모델이 준비되지 않았어요 (개발 중).",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }
                is HqModelUiState.Downloading -> {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("내려받는 중… ${(state.progress * 100).toInt()}%", fontSize = 11.sp, color = Color.Gray)
                }
                is HqModelUiState.Installed -> {
                    Text("고화질 모델이 설치됐어요.", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                    OutlinedButton(
                        onClick = { viewModel.deleteHqModel() },
                        modifier = Modifier.fillMaxWidth().testTag("hq_model_delete_btn")
                    ) { Text("모델 삭제", fontSize = 12.sp) }
                }
                is HqModelUiState.Failed -> {
                    Text("받지 못했어요: ${state.message}", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    Button(
                        onClick = { viewModel.downloadHqModel() },
                        enabled = viewModel.hqModelConfigured,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("다시 시도", fontSize = 13.sp) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
