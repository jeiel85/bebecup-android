package com.bebecup.app.ui.ai

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import com.bebecup.app.data.media.PhotoAccess
import com.bebecup.app.data.prefs.BebecupPrefs
import com.bebecup.app.ui.components.Illustration
import com.bebecup.app.ui.components.ScreenHeader
import com.bebecup.app.ui.viewmodel.BabyCupViewModel

/** ① 정리하기: choose which photos BebeCup looks at, then start the on-device pass. */
@Composable
fun AiScanSetupScreen(viewModel: BabyCupViewModel) {
    val context = LocalContext.current
    var showPermissionRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (PhotoAccess.canReadPhotos(context)) viewModel.startAiCuration()
        else showPermissionRationale = true
    }

    fun launchScan() {
        if (PhotoAccess.canReadPhotos(context)) viewModel.startAiCuration()
        else permissionLauncher.launch(PhotoAccess.requestPermissions)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_scan_setup_view")
    ) {
        ScreenHeader(title = "베스트 골라보기", onBack = { viewModel.navigateBack() })

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "흔들리거나 눈 감은 사진은 걸러 내고, 베스트 후보를 먼저 추려 드릴게요. 마지막 선택은 부모님 몫이에요.",
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.weight(1f)
                )
                Illustration(R.drawable.illus_curate, size = 108.dp)
            }

            Text("어느 사진을 볼까요?", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            val sinceLabel = if (viewModel.lastReviewedAtMillis > 0L) "지난 정리 이후" else "최근 2주"
            val ranges = listOf(
                BebecupPrefs.SCAN_SINCE_LAST_REVIEW to sinceLabel,
                7 to "최근 7일",
                30 to "최근 30일"
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                ranges.forEach { (days, label) ->
                    val selected = viewModel.aiScanRangeDays == days
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { viewModel.updateScanRangeDays(days) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    ToggleRow("자는 사진도 좋은 사진으로 포함", viewModel.aiSleepingModeEnabled) {
                        viewModel.updateSleepingMode(it)
                    }
                    ToggleRow("비슷한 사진은 한 장만 추천", viewModel.aiSimilarOnly) {
                        viewModel.updateSimilarOnly(it)
                    }
                    ToggleRow("흔들린 사진은 추천에서 빼기", viewModel.aiExcludeBlurry) {
                        viewModel.updateExcludeBlurry(it)
                    }
                }
            }

            Text(
                "베베컵은 사진을 기기 안에서만 살펴봐요. 사진은 밖으로 나가지 않아요.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
        }

        Button(
            onClick = { launchScan() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(54.dp)
                .testTag("start_ai_curation_btn"),
            shape = RoundedCornerShape(18.dp),
            enabled = !viewModel.aiIsScanning
        ) {
            Text("베베컵에게 맡기기", fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
        }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("사진 접근 권한이 필요해요") },
            text = {
                Text(
                    "베베컵은 최근 사진을 기기 안에서만 살펴봐요. 사진은 밖으로 나가지 않아요. " +
                        "권한 없이도 보관함의 '사진첩에서 담기'로 직접 고를 수 있어요."
                )
            },
            confirmButton = {
                TextButton(onClick = { showPermissionRationale = false }) { Text("확인") }
            }
        )
    }
}

@Composable
internal fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
