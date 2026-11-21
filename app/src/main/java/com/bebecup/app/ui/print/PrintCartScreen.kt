package com.bebecup.app.ui.print

import com.bebecup.app.domain.profile.BabyAge
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.ui.components.BabyPhotoIllustration
import com.bebecup.app.ui.components.EmptyState
import com.bebecup.app.ui.components.ScreenHeader
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen
import kotlinx.coroutines.launch

private val PRINT_SIZES = listOf("3x5", "4x6", "5x7", "8x10")
private const val ZZIXX_URL = "https://www.zzixx.com/"

/**
 * 인화함: photos kept for printing later. "인화하러 보내기" opens the system
 * share sheet so the parent picks the print app/site; BebeCup itself uploads
 * nothing.
 */
@Composable
fun PrintCartView(viewModel: BabyCupViewModel, cartPhotos: List<BabyPhoto>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var preparing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .testTag("print_cart_view")
    ) {
        ScreenHeader(
            title = "인화함",
            subtitle = "${cartPhotos.size}장 · 총 ${cartPhotos.sumOf { it.printQuantity }}매",
            onBack = { viewModel.navigateBack() },
            trailing = {
                if (cartPhotos.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) { Text("비우기") }
                }
            }
        )

        if (cartPhotos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    illustration = R.drawable.illus_print,
                    title = "인화함이 비어 있어요",
                    body = "월드컵 우승 사진이나 보관함 사진을 담아 두면\n나중에 한 번에 인화할 수 있어요."
                ) {
                    Button(onClick = { viewModel.navigateTo(UiScreen.BestBox) }) { Text("보관함에서 고르기") }
                }
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(cartPhotos, key = { it.id }) { photo ->
                PrintItemCard(
                    photo = photo,
                    age = viewModel.ageOf(photo),
                    onSize = { viewModel.updatePrintCartItem(photo, it, photo.printQuantity) },
                    onQuantity = { viewModel.updatePrintCartItem(photo, photo.printSize, it) },
                    onRemove = { viewModel.removeFromPrintCart(photo) }
                )
            }
            item {
                TextButton(
                    onClick = {
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(ZZIXX_URL)))
                        } catch (e: ActivityNotFoundException) {
                            message = "브라우저를 열 수 없어요."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("인화 사이트 바로가기 (찍스)", fontSize = 12.sp) }
            }
        }

        Surface(shadowElevation = 8.dp) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = {
                        preparing = true
                        scope.launch {
                            val prepared = PrintShare.prepare(context, cartPhotos)
                            preparing = false
                            if (prepared.uris.isEmpty()) {
                                message = "사진을 열 수 없어 보낼 수 없어요. 사진첩에서 지워졌는지 확인해 주세요."
                                return@launch
                            }
                            if (prepared.skipped > 0) {
                                message = "열 수 없는 사진 ${prepared.skipped}장은 빼고 보냈어요."
                            }
                            try {
                                context.startActivity(
                                    PrintShare.shareIntent(prepared.uris, PrintShare.summary(prepared.photos))
                                )
                            } catch (e: ActivityNotFoundException) {
                                message = "사진을 받을 수 있는 앱이 없어요."
                            }
                        }
                    },
                    enabled = !preparing,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("share_print_button"),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (preparing) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("인화하러 보내기", fontWeight = FontWeight.ExtraBold)
                    }
                }
                Text(
                    "인화 앱·사이트·메신저 중 원하는 곳을 고르면 사진이 그곳으로 전달돼요. 베베컵이 직접 업로드하지는 않아요.",
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }

    message?.let {
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(it) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("확인") } }
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("인화함을 비울까요?") },
            text = { Text("인화함 목록만 비워요. 사진과 보관함은 그대로예요.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearPrintCart()
                    confirmClear = false
                }) { Text("비우기") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("취소") } }
        )
    }
}

@Composable
private fun PrintItemCard(
    photo: BabyPhoto,
    age: BabyAge?,
    onSize: (String) -> Unit,
    onQuantity: (Int) -> Unit,
    onRemove: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            BabyPhotoIllustration(photo.uriString, Modifier.size(84.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (age != null) {
                    Text(
                        age.longLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("print_item_age")
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PRINT_SIZES.forEach { size ->
                        val selected = size == photo.printSize
                        Text(
                            size,
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onSize(size) }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StepButton("−", enabled = photo.printQuantity > 1) { onQuantity(photo.printQuantity - 1) }
                    Text(
                        "${photo.printQuantity}매",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                    StepButton("+", enabled = photo.printQuantity < 99) { onQuantity(photo.printQuantity + 1) }
                }
            }
            IconButton(onClick = onRemove) { Icon(Icons.Default.Close, contentDescription = "인화함에서 빼기") }
        }
    }
}

@Composable
private fun StepButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(30.dp)
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (enabled) 0.8f else 0.3f),
                CircleShape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(label, fontWeight = FontWeight.Bold) }
}

