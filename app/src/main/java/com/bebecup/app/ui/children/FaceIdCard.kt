package com.bebecup.app.ui.children

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.bebecup.app.ai.faceid.FaceIdModelStore
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.FaceIdState
import java.util.Locale

/** "9.9MB" for the face-ID model download. */
val FACE_ID_MODEL_SIZE_LABEL: String =
    String.format(Locale.ROOT, "%.1fMB", FaceIdModelStore.SIZE_BYTES / 1_000_000.0)

/**
 * 아이 얼굴 구분: whether the model is here, the download, and turning it off.
 * Shown in 설정 and on 우리 아이들.
 */
@Composable
fun FaceIdCard(viewModel: BabyCupViewModel, modifier: Modifier = Modifier) {
    var confirmingDelete by remember { mutableStateOf(false) }
    val state = viewModel.faceIdState
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .testTag("face_id_card")
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("아이 얼굴 구분", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            val status = when (state) {
                FaceIdState.NOT_INSTALLED ->
                    "아이가 둘 이상이면 켜 주세요. 사진마다 어느 아이인지 나눠서, 아이별로 월드컵을 열 수 있어요. " +
                        "켜면 얼굴 구분 파일(${FACE_ID_MODEL_SIZE_LABEL})을 한 번 받아요."
                FaceIdState.DOWNLOADING -> viewModel.faceIdProgress
                    ?.let { "받는 중이에요 ${(it * 100).toInt()}%" } ?: "받는 중이에요"
                FaceIdState.READY -> if (viewModel.faceIdBusy) "보관한 사진에서 얼굴을 살펴보는 중이에요" else "켜져 있어요."
                FaceIdState.FAILED -> "받지 못했어요. 인터넷에 연결한 뒤 다시 받아 주세요."
                FaceIdState.UNAVAILABLE ->
                    "이 기기에서는 얼굴 구분을 실행하지 못했어요. Google Play 서비스를 업데이트한 뒤 다시 시도해 주세요."
            }
            Text(status, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.testTag("face_id_status"))
            if (state == FaceIdState.DOWNLOADING) {
                val progress = viewModel.faceIdProgress
                if (progress != null) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (state == FaceIdState.READY && viewModel.faceIdBusy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                when (state) {
                    FaceIdState.NOT_INSTALLED, FaceIdState.FAILED -> Button(
                        onClick = { viewModel.downloadFaceIdModel() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("face_id_download_btn")
                    ) { Text(if (state == FaceIdState.FAILED) "다시 받기" else "켜기 (${FACE_ID_MODEL_SIZE_LABEL} 받기)") }
                    FaceIdState.UNAVAILABLE -> {
                        TextButton(
                            onClick = { confirmingDelete = true },
                            enabled = !viewModel.faceIdBusy && !viewModel.aiIsScanning
                        ) { Text("받은 파일 지우기", fontSize = 12.sp) }
                        Button(
                            onClick = { viewModel.retryFaceId() },
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("다시 시도") }
                    }
                    FaceIdState.READY -> TextButton(
                        onClick = { confirmingDelete = true },
                        enabled = !viewModel.faceIdBusy && !viewModel.aiIsScanning,
                        modifier = Modifier.testTag("face_id_delete_btn")
                    ) { Text("끄고 받은 파일 지우기", fontSize = 12.sp) }
                    FaceIdState.DOWNLOADING -> Unit
                }
            }
            Text(
                "얼굴 구분은 이 기기 안에서만 해요. 사진과 얼굴 정보는 밖으로 보내지 않아요. " +
                    "받는 파일은 OpenCV의 SFace 얼굴 구분 모델이에요. 아기와 닮은 형제자매는 헷갈릴 수 있어서, " +
                    "확실하지 않은 얼굴은 누구인지 물어봐요.",
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
            )
        }
    }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("아이 얼굴 구분을 끌까요?") },
            text = {
                Text(
                    "받은 파일(${FACE_ID_MODEL_SIZE_LABEL})을 지워요. 지금까지 나눈 사진과 아이 이름은 그대로 남고, " +
                        "새 사진은 다시 켤 때까지 나누지 않아요. 얼굴 정보까지 지우려면 설정 > 개인정보에서 분석 데이터를 지워 주세요."
                )
            },
            confirmButton = {
                Button(onClick = { confirmingDelete = false; viewModel.deleteFaceIdModel() }) { Text("끄기") }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("취소") } }
        )
    }
}
