package com.bebecup.app.ui.photo

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

// 3. SELECTIVE BEST PHOTO PICKER (TRIGGERED BY PUSH ALERT)
@Composable
fun BestShotSelectorView(
    viewModel: BabyCupViewModel,
    allPhotos: List<BabyPhoto>
) {
    val selectedPhotoIds = remember { mutableStateListOf<Int>() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("best_shot_selector_view")
    ) {
        // Custom header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.navigateTo(UiScreen.Dashboard) },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape)
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "홈으로")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "이주의 베스트 샷 엄선",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "월드컵 풀(Pool)에 추가할 사진 고르기",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }

            Button(
                onClick = { viewModel.saveBatchBestShots(selectedPhotoIds.toSet()) },
                modifier = Modifier.testTag("confirm_best_selection_btn"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("엄선완료 (${selectedPhotoIds.size})", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF1C5).copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(imageVector = Icons.Default.Info, contentDescription = "팁", tint = Color(0xFFF9A825))
                Text(
                    text = "최근 사진들 중 이상형 월드컵에 데려갈 '핵 귀여운 순간'을 마음껏 골라주세요! 선택한 사진은 엄선 마크가 붙게 됩니다.",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = Color.DarkGray
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 60.dp)
            ) {
                items(allPhotos) { photo ->
                    val isChecked = selectedPhotoIds.contains(photo.id)
                    BabyPhotoCardItem(
                        photo = photo,
                        isSelectionMode = true,
                        isSelected = isChecked,
                        onClick = {
                            if (isChecked) {
                                selectedPhotoIds.remove(photo.id)
                            } else {
                                selectedPhotoIds.add(photo.id)
                            }
                        }
                    )
                }
            }
        }
    }
}
