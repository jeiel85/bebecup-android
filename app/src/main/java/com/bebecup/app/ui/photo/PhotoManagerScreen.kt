package com.bebecup.app.ui.photo

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.ui.components.BabyPhotoCardItem
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 2. PHOTO GALLERY MANAGER VIEW
@Composable
fun PhotoManagerView(
    viewModel: BabyCupViewModel,
    allPhotos: List<BabyPhoto>
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val galleryPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                selectedPhotoUri = uri
                showAddDialog = true
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("photo_manager_view")
    ) {
        // Simple Edge Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateTo(UiScreen.Dashboard) },
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.surface, CircleShape)
                    .size(40.dp)
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back home")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "전체 사진첩",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Option command buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    galleryPickerLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("gallery_picker_btn"),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "사진 추가", modifier = Modifier.size(16.dp))
                    Text("내 사진 등록", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Button(
                onClick = { viewModel.navigateTo(UiScreen.BestShotSelector) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("best_selector_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD97D)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "베스트 선택", tint = Color(0xFF5D4037), modifier = Modifier.size(16.dp))
                    Text("베스트 엄선", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5D4037))
                }
            }
        }

        Divider(modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

        Text(
            text = "사진을 클릭하시면 베스트 샷 상자(🧡 표시)에 등록하거나 제외할 수 있습니다. 베스트 샷에 담긴 사진들이 월드컵 경기에 참가하게 됩니다.",
            fontSize = 11.sp,
            lineHeight = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 2.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            items(allPhotos) { photo ->
                BabyPhotoCardItem(
                    photo = photo,
                    onClick = { viewModel.toggleBestShotSelectionInDb(photo) },
                    modifier = Modifier.padding(4.dp),
                    isSelected = photo.isSelectedAsBest
                )
            }
        }
    }

    if (showAddDialog && selectedPhotoUri != null) {
        var picTitle by remember { mutableStateOf("") }
        var picDesc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                selectedPhotoUri = null
            },
            title = { Text("사진 기록 등록", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextField(
                        value = picTitle,
                        onValueChange = { picTitle = it },
                        label = { Text("기록할 제목 (예: 생일 미소)") },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("방긋 웃는 이쁜 얼굴") }
                    )

                    TextField(
                        value = picDesc,
                        onValueChange = { picDesc = it },
                        label = { Text("기억 요약 (예: 삼촌 선물받고 좋아함)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        viewModel.addNewPhoto(
                            uriString = selectedPhotoUri.toString(),
                            title = picTitle,
                            description = picDesc,
                            date = today
                        )
                        showAddDialog = false
                        selectedPhotoUri = null
                    },
                    modifier = Modifier.testTag("add_photo_confirm")
                ) {
                    Text("등록하기", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    selectedPhotoUri = null
                }) {
                    Text("취소")
                }
            }
        )
    }
}
