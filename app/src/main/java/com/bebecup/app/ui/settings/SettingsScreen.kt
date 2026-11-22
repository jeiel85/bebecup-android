package com.bebecup.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
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
import com.bebecup.app.ui.theme.ThemeMode
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.FaceModelState
import com.bebecup.app.update.UpdateChannel
import com.bebecup.app.update.UpdateStatus
import com.bebecup.app.update.VersionParticles
import kotlinx.coroutines.launch

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

            SettingsCard("화면 테마") {
                ThemeModeRow(viewModel.themeMode) { viewModel.updateThemeMode(it) }
            }

            FaceModelCard(viewModel)

            AppUpdateCard()

            SettingsCard("개인정보") {
                Caption(
                    "사진 분석은 모두 이 기기 안에서만 해요. 사진과 분석 결과는 밖으로 나가지 않고, 광고·추적 도구도 쓰지 않아요. " +
                        "얼굴 인식에 쓰는 Google ML Kit은 기능 사용 지표(기기 모델, 앱 버전 등)를 Google에 보낼 수 있어요."
                )
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
                "베베컵 ${BuildConfig.VERSION_NAME} · ${UpdateChannel.LABEL} 배포본",
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
private fun ThemeModeRow(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        ThemeMode.entries.forEach { mode ->
            val isSelected = mode == selected
            Surface(
                onClick = { onSelect(mode) },
                shape = RoundedCornerShape(14.dp),
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .weight(1f)
                    .semantics { this.selected = isSelected }
                    .testTag("settings_theme_${mode.storageKey}")
            ) {
                Text(
                    mode.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                )
            }
        }
    }
}

/**
 * Play build: the face model behind "베베컵에게 맡기기" comes from Google Play
 * services the first time, so show whether it is here and let the parent fetch
 * it now (e.g. on Wi-Fi). GitHub build: the model is inside the app.
 */
@Composable
private fun FaceModelCard(viewModel: BabyCupViewModel) {
    LaunchedEffect(Unit) { viewModel.refreshFaceModelState() }
    val state = viewModel.faceModelState
    val bundled = viewModel.faceModelBundled
    SettingsCard("얼굴 인식 기능") {
        val status = when (state) {
            FaceModelState.UNKNOWN, FaceModelState.CHECKING -> "확인하는 중이에요"
            FaceModelState.READY -> if (bundled) "앱에 들어 있어요. 따로 받을 필요가 없어요." else "준비됐어요."
            FaceModelState.NOT_INSTALLED -> "아직 받지 않았어요. 처음 '베베컵에게 맡기기'를 할 때 자동으로 받아요."
            FaceModelState.DOWNLOADING -> viewModel.faceModelProgress
                ?.let { "받는 중이에요 ${(it * 100).toInt()}%" } ?: "받는 중이에요"
            FaceModelState.FAILED -> "받지 못했어요. 인터넷에 연결한 뒤 다시 받아 주세요."
            FaceModelState.UNAVAILABLE -> "Google Play 서비스를 확인할 수 없어요. Play 서비스가 없거나 꺼진 기기에서는 쓸 수 없어요."
        }
        Text(
            status,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("settings_face_model_status")
        )
        if (state == FaceModelState.DOWNLOADING) {
            val progress = viewModel.faceModelProgress
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(8.dp))
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(8.dp))
            }
        }
        if (state == FaceModelState.NOT_INSTALLED || state == FaceModelState.FAILED || state == FaceModelState.UNAVAILABLE) {
            OutlinedButton(
                onClick = {
                    if (state == FaceModelState.UNAVAILABLE) viewModel.refreshFaceModelState()
                    else viewModel.installFaceModelNow()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .testTag("settings_face_model_btn"),
                shape = RoundedCornerShape(14.dp)
            ) { Text(if (state == FaceModelState.UNAVAILABLE) "다시 확인" else "지금 받기", fontSize = 13.sp) }
        }
        Caption(
            if (bundled) {
                "Google ML Kit 얼굴 인식이에요. 흔들림·밝기·눈 감김·표정을 기기 안에서 판단하고, 사진은 밖으로 보내지 않아요."
            } else {
                "Google ML Kit 얼굴 인식이에요. 앱을 작게 유지하려고 이 기능만 Google Play 서비스에서 한 번 받아요. " +
                    "흔들림·밝기·눈 감김·표정을 기기 안에서 판단하고, 사진은 밖으로 보내지 않아요."
            }
        )
    }
}

@Composable
private fun AppUpdateCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val status = UpdateChannel.rememberStatus()
    val isPlay = UpdateChannel.LABEL == "Google Play"
    SettingsCard("앱 업데이트") {
        val line = when (status) {
            UpdateStatus.Unknown -> "지금 베베컵 ${BuildConfig.VERSION_NAME}을 쓰고 있어요."
            UpdateStatus.Checking -> "새 버전이 있는지 확인하는 중이에요"
            UpdateStatus.UpToDate -> "최신 버전(${BuildConfig.VERSION_NAME})을 쓰고 있어요."
            is UpdateStatus.CheckFailed -> status.reason
            is UpdateStatus.Available -> status.versionName?.let { "새 버전 ${VersionParticles.subject(it)} 나왔어요." } ?: "새 버전이 나왔어요."
            is UpdateStatus.Downloading -> status.progress?.let { "받는 중이에요 ${(it * 100).toInt()}%" } ?: "받는 중이에요"
            is UpdateStatus.ReadyToInstall -> "다 받았어요. '설치'를 눌러 주세요."
            is UpdateStatus.Installing -> "설치하는 중이에요. Android가 물어보면 '업데이트'를 눌러 주세요."
            is UpdateStatus.DownloadFailed -> status.reason
        }
        Text(
            line,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("settings_update_status")
        )
        when (status) {
            is UpdateStatus.Available, is UpdateStatus.DownloadFailed, is UpdateStatus.ReadyToInstall -> Button(
                onClick = { UpdateChannel.startUpdate(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .testTag("settings_update_btn"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text(
                    when {
                        status is UpdateStatus.ReadyToInstall -> "설치"
                        isPlay -> "Play 스토어에서 업데이트"
                        else -> "업데이트 받기"
                    },
                    fontSize = 13.sp
                )
            }
            is UpdateStatus.Installing -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(8.dp))
            is UpdateStatus.Downloading -> {
                val progress = status.progress
                if (progress != null) {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(8.dp))
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(8.dp))
                }
            }
            else -> OutlinedButton(
                onClick = { scope.launch { UpdateChannel.checkNow(context) } },
                enabled = status != UpdateStatus.Checking,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
                    .testTag("settings_update_check_btn"),
                shape = RoundedCornerShape(14.dp)
            ) { Text("업데이트 확인", fontSize = 13.sp) }
        }
        UpdateChannel.AutoUpdateOptions()
        Caption(UpdateChannel.SETTINGS_CAPTION)
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
