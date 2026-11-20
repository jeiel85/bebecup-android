package com.bebecup.app.ui.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.R
import com.bebecup.app.ui.components.Illustration
import com.bebecup.app.ui.viewmodel.BabyCupViewModel

@Composable
fun AiScanProgressScreen(viewModel: BabyCupViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_scan_progress_view")
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        if (viewModel.aiScanFailed) {
            Illustration(R.drawable.illus_empty, size = 200.dp)
            val modelMissing = viewModel.aiFaceModelMissing
            Text(
                if (modelMissing) "얼굴 인식 기능을 받지 못했어요" else "사진을 살펴보다 멈췄어요",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (modelMissing) {
                    "처음 한 번은 Google Play 서비스에서 얼굴 인식 기능을 받아야 해요. " +
                        "인터넷에 연결한 뒤 다시 시도해 주세요. Google Play 서비스가 없는 기기에서는 쓸 수 없어요."
                } else {
                    "잠시 후 다시 시도해 주세요. 계속 안 되면 사진 접근 권한을 확인해 주세요."
                },
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Button(onClick = { viewModel.startAiCuration() }) { Text("다시 시도") }
            TextButton(onClick = { viewModel.navigateHome() }) { Text("홈으로") }
            return@Column
        }

        Illustration(R.drawable.illus_curate, size = 220.dp)
        Text(
            viewModel.aiProgressStep.ifBlank { "준비 중이에요" },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )
        val total = viewModel.aiTotalCount
        val modelProgress = viewModel.aiFaceModelProgress
        if (viewModel.aiProgressStep == BabyCupViewModel.STEP_FACE_MODEL) {
            if (modelProgress != null) {
                LinearProgressIndicator(progress = { modelProgress }, modifier = Modifier.fillMaxWidth(0.8f))
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.8f))
            }
            Text(
                "처음 한 번만 받아요. 사진은 기기 밖으로 나가지 않아요.",
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        } else if (total > 0) {
            LinearProgressIndicator(
                progress = { (viewModel.aiAnalyzedCount.toFloat() / total).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(0.8f)
            )
            Text(
                "${viewModel.aiAnalyzedCount} / ${total}장 확인",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.8f))
        }

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    "사진은 밖으로 나가지 않고, 이 기기 안에서만 살펴보고 있어요.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }
        }
    }
}
