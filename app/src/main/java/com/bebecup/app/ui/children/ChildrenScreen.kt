package com.bebecup.app.ui.children

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.data.children.ChildEntity
import com.bebecup.app.data.children.FaceInPhoto
import com.bebecup.app.data.children.FamilyFaces
import com.bebecup.app.domain.profile.CalendarDate
import com.bebecup.app.ui.components.ChildProfileDialog
import com.bebecup.app.ui.components.ScreenHeader
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.FaceIdState

/**
 * 우리 아이들: each child's name and birthday, and the faces 베베컵 found but
 * cannot put a name to yet. Naming a group teaches it that child's face.
 */
@Composable
fun ChildrenScreen(viewModel: BabyCupViewModel, bestPhotos: List<BabyPhoto>) {
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ChildEntity?>(null) }
    /** Faces to name as the child being added (from a group's "새 아이"). */
    var addingFor by remember { mutableStateOf<List<Long>>(emptyList()) }
    LaunchedEffect(Unit) { viewModel.refreshFaceGroups() }

    Column(
        Modifier
            .fillMaxSize()
            .testTag("children_view")
    ) {
        ScreenHeader(
            title = "우리 아이들",
            subtitle = if (viewModel.children.isEmpty()) null else "${viewModel.children.size}명",
            onBack = { viewModel.navigateBack() }
        )
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (viewModel.faceIdState != FaceIdState.READY || viewModel.faceIdBusy) {
                item(key = "face_id") { FaceIdCard(viewModel) }
            }

            items(viewModel.children, key = { "child_${it.id}" }) { child ->
                ChildRow(
                    viewModel = viewModel,
                    child = child,
                    photoCount = viewModel.photosOf(bestPhotos, child.id).size,
                    onEdit = { editing = child }
                )
            }
            item(key = "add") {
                OutlinedButton(
                    onClick = { addingFor = emptyList(); adding = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("add_child_btn")
                ) { Text(if (viewModel.children.isEmpty()) "아이 추가하기" else "아이 한 명 더 추가하기") }
            }

            val groups = viewModel.faceGroups
            if (groups.isNotEmpty()) {
                item(key = "groups_title") {
                    Column(Modifier.padding(top = 8.dp)) {
                        Text("누구인지 알려 주세요", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                        Text(
                            "베베컵이 사진에서 찾은 얼굴이에요. 한 번 알려 주시면 다음부터는 알아서 나눠요.",
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                        )
                    }
                }
                items(groups, key = { "group_${it.faceIds.first()}" }) { group ->
                    FaceGroupCard(
                        group = group,
                        children = viewModel.children,
                        onChild = { viewModel.nameFaces(group.faceIds, it.id) },
                        onNewChild = { addingFor = group.faceIds; adding = true },
                        onNotChild = { viewModel.nameFaces(group.faceIds, null) }
                    )
                }
            } else if (viewModel.faceIdState == FaceIdState.READY && !viewModel.faceIdBusy) {
                item(key = "groups_empty") {
                    Text(
                        "지금은 물어볼 얼굴이 없어요. 새 사진을 정리하다가 모르는 얼굴이 보이면 여기서 여쭤볼게요.",
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
            if (viewModel.faceIdState == FaceIdState.READY && !viewModel.faceIdBusy) {
                item(key = "face_id_ready") { FaceIdCard(viewModel) }
            }
        }
    }

    if (adding) {
        ChildProfileDialog(
            title = "아이 추가",
            initialName = "",
            initialBirthDate = "",
            onSave = { name, birth ->
                viewModel.addChild(name, birth, addingFor)
                adding = false
            },
            onDismiss = { adding = false }
        )
    }
    editing?.let { child ->
        ChildProfileDialog(
            title = "아이 정보",
            initialName = child.name,
            initialBirthDate = child.birthDate,
            onSave = { name, birth ->
                viewModel.updateChild(child.id, name, birth)
                editing = null
            },
            onDismiss = { editing = null },
            onDelete = {
                viewModel.deleteChild(child.id)
                editing = null
            }
        )
    }
}

@Composable
private fun ChildRow(viewModel: BabyCupViewModel, child: ChildEntity, photoCount: Int, onEdit: () -> Unit) {
    val face by produceState<FaceInPhoto?>(null, child.id, viewModel.photoChildren) { value = viewModel.faceOf(child) }
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("child_row_${child.id}")
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val shown = face
            if (shown != null) FaceCrop(shown.uriString, shown.face, 52.dp) else InitialAvatar(child.name, 52.dp)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(child.name, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                val birth = CalendarDate.parse(child.birthDate)
                Text(
                    buildString {
                        if (birth != null) append("${birth.year}년 ${birth.month}월 ${birth.day}일생 · ")
                        append("보관한 사진 ${photoCount}장")
                    },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                )
            }
            IconButton(onClick = onEdit, modifier = Modifier.testTag("child_edit_${child.id}")) {
                Icon(Icons.Default.Edit, contentDescription = "${child.name} 정보 고치기")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FaceGroupCard(
    group: FamilyFaces.Group,
    children: List<ChildEntity>,
    onChild: (ChildEntity) -> Unit,
    onNewChild: () -> Unit,
    onNotChild: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("face_group")
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                group.faces.take(MAX_FACES_SHOWN).forEach { FaceCrop(it.uriString, it.face, 48.dp) }
                if (group.faces.size > MAX_FACES_SHOWN) {
                    Text("+${group.faces.size - MAX_FACES_SHOWN}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
            Text("사진 ${group.photoCount}장에 나온 얼굴이에요. 누구예요?", fontSize = 13.sp)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                children.forEach { child ->
                    AssistChip(
                        onClick = { onChild(child) },
                        label = { Text(child.name, fontWeight = FontWeight.Bold) },
                        modifier = Modifier.testTag("group_child_${child.id}")
                    )
                }
                AssistChip(
                    onClick = onNewChild,
                    label = { Text(if (children.isEmpty()) "우리 아이예요 (이름 붙이기)" else "다른 아이예요") },
                    modifier = Modifier.testTag("group_new_child")
                )
                AssistChip(
                    onClick = onNotChild,
                    label = { Text("아이가 아니에요") },
                    modifier = Modifier.testTag("group_not_child")
                )
            }
        }
    }
}

private const val MAX_FACES_SHOWN = 5
