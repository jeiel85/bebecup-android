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
import com.bebecup.app.domain.profile.CalendarDate
import com.bebecup.app.ui.components.BabyProfileDialog
import com.bebecup.app.ui.ai.ToggleRow
import com.bebecup.app.ui.components.ScreenHeader
import com.bebecup.app.ui.components.rememberNudgeOptIn
import com.bebecup.app.ui.viewmodel.BabyCupViewModel

@Composable
fun SettingsScreen(viewModel: BabyCupViewModel) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf(false) }
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
            SettingsCard("아기 정보") {
                val birth = CalendarDate.parse(viewModel.babyBirthDate)
                Caption(
                    if (birth == null) "생일을 알려 주시면 보관함·월드컵·인화함 사진마다 몇 개월 때인지 보여 드려요."
                    else buildString {
                        if (viewModel.babyName.isNotBlank()) append("${viewModel.babyName} · ")
                        append("${birth.year}년 ${birth.month}월 ${birth.day}일생")
                        viewModel.ageToday()?.let { append(" · 오늘 ${it.longLabel}") }
                    }
                )
                OutlinedButton(
                    onClick = { editingProfile = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .testTag("settings_baby_profile_btn"),
                    shape = RoundedCornerShape(14.dp)
                ) { Text(if (birth == null) "아기 정보 입력" else "아기 정보 고치기", fontSize = 13.sp) }
                if (birth != null) {
                    TextButton(
                        onClick = { viewModel.updateBabyProfile("", null) },
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) { Text("아기 정보 지우기", fontSize = 12.sp) }
                }
                Caption("이 기기 안에만 저장되고, 휴대폰의 Google 백업에 다른 앱 데이터와 함께 포함될 수 있어요.")
            }

            SettingsCard("정리 알림") {
                ToggleRow("사진이 쌓이면·새 달이 되면 알려주기", viewModel.nudgeEnabled) { enable ->
                    if (enable) optIn.request() else viewModel.updateNudgeEnabled(false)
                }
                Caption(
                    if (optIn.declined.value) "알림 권한이 있어야 켤 수 있어요. 휴대폰 설정 > 앱 > 베베컵에서 허용해 주세요."
                    else "확인 안 한 사진이 ${NudgePolicy.UNSORTED_THRESHOLD}장 넘게 쌓이면 하루 한 번 살펴보고, " +
                        "${NudgePolicy.MIN_INTERVAL_DAYS}일에 한 번까지만 알려드려요. 매달 초에는 지난달 보관 사진이 2장 이상이면 " +
                        "'이달의 월드컵'을 한 번 알려드려요. 사진 개수만 세고, 사진을 열어 보지 않아요. " +
                        "사진 접근이 '선택한 사진만'이면 쌓인 사진은 셀 수 없어 이달의 월드컵만 알려드려요."
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
