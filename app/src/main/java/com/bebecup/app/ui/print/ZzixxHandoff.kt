package com.bebecup.app.ui.print

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.data.BabyPhoto

// Prepare an external order-page handoff. No files are uploaded by the app.
@Composable
fun ZzixxTransferingOverlay() {
    val infinite = rememberInfiniteTransition(label = "trans")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotate"
    )

    // Simulating visual stage details
    var currentProgressStateText by remember { mutableStateOf("1단계: 선택한 인화 목록을 확인 중...") }

    LaunchedEffect(key1 = true) {
        kotlinx.coroutines.delay(1000)
        currentProgressStateText = "2단계: 수량과 인화 사이즈를 정리 중..."
        kotlinx.coroutines.delay(500)
        currentProgressStateText = "3단계: 외부 주문 페이지로 이동할 준비 중..."
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            .testTag("zzixx_transfer_overlay"),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(300.dp)
                .padding(20.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .rotate(rotation)
                        .border(4.dp, MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Loading spinner",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp)
                    )
                }

                Text(
                    text = "주문 페이지 준비 중",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )

                Text(
                    text = currentProgressStateText,
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )
            }
        }
    }
}

// SIMULATOR COMPONENT: COMPLETE DIALOG
@Composable
fun ZzixxSuccessDialog(
    onDismiss: () -> Unit,
    cartPhotos: List<BabyPhoto>
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Success check",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
            )
        },
        title = {
            Text("주문 준비 완료", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, textAlign = TextAlign.Center)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "선택하신 사진과 수량을 확인했습니다. 실제 주문은 찍스 웹사이트에서 직접 진행합니다.",
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    color = Color.Gray
                )

                Text(
                    text = "인화 예정 수량: 총 ${cartPhotos.sumOf { it.printQuantity }}장",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "아래 버튼을 누르면 찍스(ZZIXX) 공식 모바일 사진 인화 사이트로 이동합니다. 사진 업로드와 결제는 해당 사이트에서 진행합니다.",
                    fontSize = 10.sp,
                    color = Color.DarkGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    // Open Zzixx print mobile homepage nicely!
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.zzixx.com/"))
                    context.startActivity(webIntent)
                },
                modifier = Modifier.fillMaxWidth().testTag("open_zzixx_external_btn")
            ) {
                Text("찍스(ZZIXX) 웹사이트 열기", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("닫기 (주문 완료)")
            }
        }
    )
}
