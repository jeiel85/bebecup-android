package com.bebecup.app.ui.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen

private val photoPermission: String
    get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

// AI Scan Setup (spec §11.1)
@Composable
fun AiScanSetupScreen(viewModel: BabyCupViewModel) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showPermissionRationale by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startAiCuration()
        } else {
            showPermissionRationale = true
        }
    }

    fun launchScan() {
        val alreadyGranted = ContextCompat.checkSelfPermission(context, photoPermission) ==
            PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            viewModel.startAiCuration()
        } else {
            permissionLauncher.launch(photoPermission)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_scan_setup_view")
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
            Text("AI 아기사진 엄선", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            text = "AI가 이번 주 베스트 아기사진을 골라드릴게요",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // Scan range chips
        Text("어느 기간의 사진을 볼까요?", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            val ranges = listOf(1 to "오늘", 7 to "최근 7일", 30 to "최근 30일")
            ranges.forEach { (days, label) ->
                val selected = viewModel.aiScanRangeDays == days
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { viewModel.updateScanRangeDays(days) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selected) Color.White else MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        // Toggles
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                ToggleRow("자는 사진도 좋은 사진으로 포함", viewModel.aiSleepingModeEnabled) {
                    viewModel.updateSleepingMode(it)
                }
                ToggleRow("비슷한 사진은 한 장만 추천", viewModel.aiSimilarOnly) {
                    viewModel.updateSimilarOnly(it)
                }
                ToggleRow("흔들린 사진은 자동 제외", viewModel.aiExcludeBlurry) {
                    viewModel.updateExcludeBlurry(it)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Privacy trust copy (spec §9.1)
        Text(
            text = "사진은 외부로 전송되지 않고 기기 안에서만 분석돼요.\nAI 추천은 참고용이며, 최종 선택은 언제나 부모님이 결정해요.",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )

        Button(
            onClick = { launchScan() },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("start_ai_curation_btn"),
            shape = RoundedCornerShape(14.dp),
            enabled = !viewModel.aiIsScanning
        ) {
            Text("AI 엄선 시작", fontWeight = FontWeight.ExtraBold)
        }

        TextButton(
            onClick = { showDeleteConfirm = true },
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("기기에 저장된 AI 분석 데이터 전체 삭제", fontSize = 11.sp, color = Color.Gray)
        }
    }

    if (showPermissionRationale) {
        AlertDialog(
            onDismissRequest = { showPermissionRationale = false },
            title = { Text("사진 접근 권한이 필요해요") },
            text = {
                Text(
                    "AI 엄선은 최근 사진을 기기 안에서만 분석합니다. 사진은 외부로 전송되지 않아요. " +
                        "권한 없이 진행하려면 사진첩에서 직접 골라 월드컵을 시작할 수도 있어요."
                )
            },
            confirmButton = {
                TextButton(onClick = { showPermissionRationale = false }) { Text("확인") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("AI 분석 데이터 삭제") },
            text = { Text("기기에 저장된 분석 결과·묶음·세션 기록을 모두 지웁니다. 사진 원본은 삭제되지 않아요.") },
            confirmButton = {
                Button(onClick = {
                    viewModel.deleteAllAnalysisData()
                    showDeleteConfirm = false
                }) { Text("삭제") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("취소") }
            }
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
