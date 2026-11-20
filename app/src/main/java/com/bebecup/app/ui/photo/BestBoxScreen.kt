package com.bebecup.app.ui.photo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.R
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.ui.components.BabyPhotoIllustration
import com.bebecup.app.ui.components.EmptyState
import com.bebecup.app.ui.components.ScreenHeader
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen

/**
 * 베스트 보관함: every photo the parent decided to keep — approved from a
 * BebeCup round or added by hand with the Photo Picker. World cups draw from
 * here; any photo can be sent to the 인화함.
 */
@Composable
fun BestBoxScreen(
    viewModel: BabyCupViewModel,
    bestPhotos: List<BabyPhoto>,
    printCartPhotos: List<BabyPhoto>
) {
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_PICK)
    ) { uris -> viewModel.addPickedPhotos(uris) }
    val printIds = printCartPhotos.map { it.id }.toSet()

    Column(
        Modifier
            .fillMaxSize()
            .testTag("best_box_view")
    ) {
        ScreenHeader(
            title = "베스트 보관함",
            subtitle = "${bestPhotos.size}장 보관 중",
            onBack = { viewModel.navigateBack() }
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("pick_photos_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("사진첩에서 담기", fontSize = 13.sp)
            }
            Button(
                onClick = { viewModel.navigateTo(UiScreen.WorldCupSetup) },
                enabled = bestPhotos.size >= 2,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp)
            ) { Text("월드컵 하기", fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        }

        if (bestPhotos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    illustration = R.drawable.illus_empty,
                    title = "아직 보관한 사진이 없어요",
                    body = "베베컵이 고른 추천에서 마음에 드는 사진을 보관하거나,\n사진첩에서 직접 담아 보세요."
                ) {
                    Button(onClick = { viewModel.navigateTo(UiScreen.AiScanSetup) }) {
                        Text("베베컵 추천 받기")
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(bestPhotos, key = { it.id }) { photo ->
                    BestTile(
                        photo = photo,
                        inPrintCart = photo.id in printIds,
                        onAddToPrint = { viewModel.addToPrintCart(photo) },
                        onRemove = { viewModel.removeFromBest(photo) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BestTile(
    photo: BabyPhoto,
    inPrintCart: Boolean,
    onAddToPrint: () -> Unit,
    onRemove: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable { menuOpen = true }
    ) {
        BabyPhotoIllustration(photo.uriString, Modifier.fillMaxSize())
        if (photo.winsCount > 0) {
            Text(
                "👑 ${photo.winsCount}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .background(Color(0x99FFB300), RoundedCornerShape(10.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(6.dp)
                .size(28.dp)
                .background(Color.White.copy(alpha = 0.85f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (inPrintCart) Icons.Default.Check else Icons.Default.MoreVert,
                contentDescription = if (inPrintCart) "인화함에 담김" else "메뉴",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (inPrintCart) "인화함에 담겨 있어요" else "인화함에 담기") },
                enabled = !inPrintCart,
                onClick = {
                    onAddToPrint()
                    menuOpen = false
                }
            )
            DropdownMenuItem(
                text = { Text("보관함에서 빼기 (사진은 그대로)") },
                onClick = {
                    onRemove()
                    menuOpen = false
                }
            )
        }
    }
}

private const val MAX_PICK = 30
