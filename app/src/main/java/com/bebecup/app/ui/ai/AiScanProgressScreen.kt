package com.bebecup.app.ui.ai

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.ui.viewmodel.BabyCupViewModel

// AI Scan Progress (spec §11.2) — make the scan feel safe and transparent.
@Composable
fun AiScanProgressScreen(viewModel: BabyCupViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("ai_scan_progress_view")
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(56.dp))
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = viewModel.aiProgressStep.ifBlank { "준비 중이에요" },
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.primary
        )

        if (viewModel.aiTotalCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${viewModel.aiAnalyzedCount} / ${viewModel.aiTotalCount}장 확인",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Local-only reassurance (spec §11.2)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "사진을 외부로 보내지 않고 기기 안에서만 분석 중이에요.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = Color.DarkGray
                )
            }
        }
    }
}
