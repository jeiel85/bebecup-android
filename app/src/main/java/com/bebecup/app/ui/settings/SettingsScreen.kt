package com.bebecup.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.BuildConfig
import com.bebecup.app.domain.nudge.NudgePolicy
import com.bebecup.app.ui.ai.ToggleRow
import com.bebecup.app.ui.components.ScreenHeader
import com.bebecup.app.ui.components.rememberNudgeOptIn
import com.bebecup.app.ui.viewmodel.BabyCupViewModel

@Composable
fun SettingsScreen(viewModel: BabyCupViewModel) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }
    val optIn = rememberNudgeOptIn(viewModel)

    Column(
        Modifier
            .fillMaxSize()
            .testTag("settings_view")
    ) {
        ScreenHeader(title = "설정", onBack = { viewModel.navigateBack() })
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SettingsCard("정리 알림") {
                ToggleRow("사진이 쌓이면 알려주기", viewModel.nudgeEnabled) { enable ->
                    if (enable) optIn.request() else viewModel.updateNudgeEnabled(false)
                }
                Caption(
                    if (optIn.declined.value) "사진 또는 알림 권한이 없어 켤 수 없어요. 휴대폰 설정 > 앱 > 베베컵에서 권한을 허용해 주세요."
                    else "확인 안 한 사진이 ${NudgePolicy.UNSORTED_THRESHOLD}장 넘게 쌓이면 하루 한 번 살펴보고, " +
                        "${NudgePolicy.MIN_INTERVAL_DAYS}일에 한 번까지만 알려드려요. 사진 개수만 세고, 사진을 열어 보지 않아요."
                )
            }

            SettingsCard("베스트 고르기 기본값") {
                ToggleRow("자는 사진도 좋은 사진으로 포함", viewModel.aiSleepingModeEnabled) {
                    viewModel.updateSleepingMode(it)
                }
                ToggleRow("비슷한 사진은 한 장만 추천", viewModel.aiSimilarOnly) { viewModel.updateSimilarOnly(it) }
                ToggleRow("흔들린 사진은 추천에서 빼기", viewModel.aiExcludeBlurry) { viewModel.updateExcludeBlurry(it) }
            }

            SettingsCard("개인정보") {
                Caption("사진 분석은 모두 이 기기 안에서만 해요. 광고·분석·추적 도구는 쓰지 않아요.")
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .testTag("settings_delete_data_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("기기에 저장된 분석 데이터 전체 삭제", fontSize = 13.sp) }
                if (deleted) Caption("분석 데이터를 모두 삭제했어요.")
            }

            Text(
                "베베컵 ${BuildConfig.VERSION_NAME}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(8.dp)
            )
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("분석 데이터 삭제") },
            text = { Text("기기에 저장된 분석 결과·묶음·세션 기록을 모두 지워요. 사진 원본과 보관함은 그대로예요.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteAllAnalysisData()
                    showDeleteConfirm = false
                    deleted = true
                }) { Text("삭제") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("취소") } }
        )
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(8.dp)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(8.dp)
            )
            content()
        }
    }
}

@Composable
private fun Caption(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    )
}
