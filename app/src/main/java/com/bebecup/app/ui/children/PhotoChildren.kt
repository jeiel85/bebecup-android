package com.bebecup.app.ui.children

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.data.children.ChildEntity
import com.bebecup.app.data.children.FaceInPhoto
import com.bebecup.app.data.children.FaceState
import com.bebecup.app.ui.components.ChildProfileDialog
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.FaceIdState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The children found in a photo, as a small label laid over it ("하린 · 도윤"). */
@Composable
fun ChildNamesBadge(children: List<ChildEntity>, modifier: Modifier = Modifier) {
    if (children.isEmpty()) return
    Text(
        children.joinToString(" · ") { it.name },
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        maxLines = 1,
        modifier = modifier
            .padding(6.dp)
            .background(Color(0x99000000), RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
            .testTag("child_names_badge")
    )
}

/**
 * "누구 사진이에요?": each face found in [photo], and whose it is. Picking a name
 * fixes it (the app never changes it again) and teaches the app that face.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoChildrenDialog(viewModel: BabyCupViewModel, photo: BabyPhoto, onDismiss: () -> Unit) {
    var faces by remember { mutableStateOf<List<FaceInPhoto>?>(null) }
    var addingFor by remember { mutableStateOf<Long?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(photo.id) { faces = viewModel.facesIn(photo) }
    // The change itself runs in the ViewModel, so closing the dialog cannot cut it short.
    fun after(change: Job) {
        scope.launch {
            change.join()
            faces = viewModel.facesIn(photo)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("누구 사진이에요?") },
        text = {
            Column(
                Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                val found = faces
                when {
                    found == null -> CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                    found.isEmpty() -> Text(
                        when {
                            viewModel.faceIdState != FaceIdState.READY ->
                                "설정에서 '아이 얼굴 구분'을 켜면 사진마다 어느 아이인지 나눠 드려요."
                            photo.faceIdVersion == 0 -> "아직 이 사진의 얼굴을 살펴보지 못했어요. 잠시 뒤 다시 열어 주세요."
                            else -> "얼굴을 또렷하게 찾지 못한 사진이에요. 옆모습이거나 얼굴이 작으면 그럴 수 있어요."
                        },
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                    else -> found.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FaceCrop(item.uriString, item.face, 56.dp)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                val decided = item.face.state != FaceState.UNKNOWN
                                viewModel.children.forEach { child ->
                                    FilterChip(
                                        selected = decided && item.face.childId == child.id,
                                        onClick = { after(viewModel.nameFaces(listOf(item.face.id), child.id)) },
                                        label = { Text(child.name) },
                                        modifier = Modifier.testTag("face_${item.face.id}_child_${child.id}")
                                    )
                                }
                                FilterChip(
                                    selected = false,
                                    onClick = { addingFor = item.face.id },
                                    label = { Text("새 아이") }
                                )
                                FilterChip(
                                    selected = decided && item.face.childId == null,
                                    onClick = { after(viewModel.nameFaces(listOf(item.face.id), null)) },
                                    label = { Text("아이 아님") },
                                    modifier = Modifier.testTag("face_${item.face.id}_not_child")
                                )
                            }
                        }
                    }
                }
                if (!found.isNullOrEmpty()) {
                    Text(
                        "고른 이름은 베베컵이 바꾸지 않아요. 이 얼굴로 다른 사진도 다시 나눠 드려요.",
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } }
    )

    addingFor?.let { faceId ->
        ChildProfileDialog(
            title = "아이 추가",
            initialName = "",
            initialBirthDate = "",
            onSave = { name, birth ->
                after(viewModel.addChild(name, birth, listOf(faceId)))
                addingFor = null
            },
            onDismiss = { addingFor = null }
        )
    }
}
