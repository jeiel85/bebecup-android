package com.bebecup.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.data.TournamentRecord
import com.bebecup.app.ui.components.BabyPhotoCardItem
import com.bebecup.app.ui.components.BabyPhotoIllustration
import com.bebecup.app.ui.theme.MyApplicationTheme
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainContentScreen()
            }
        }
    }
}

@Composable
fun MainContentScreen(
    viewModel: BabyCupViewModel = viewModel()
) {
    val allPhotos by viewModel.allPhotos.collectAsState()
    val bestPhotos by viewModel.bestPhotos.collectAsState()
    val printCartPhotos by viewModel.printCartPhotos.collectAsState()
    val tournaments by viewModel.tournaments.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = viewModel.currentScreen,
                transitionSpec = {
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "screen_transition"
            ) { screen ->
                when (screen) {
                    is UiScreen.Dashboard -> DashboardView(
                        viewModel = viewModel,
                        allPhotos = allPhotos,
                        bestPhotos = bestPhotos,
                        printCartPhotos = printCartPhotos
                    )
                    is UiScreen.PhotoManager -> PhotoManagerView(
                        viewModel = viewModel,
                        allPhotos = allPhotos
                    )
                    is UiScreen.BestShotSelector -> BestShotSelectorView(
                        viewModel = viewModel,
                        allPhotos = allPhotos
                    )
                    is UiScreen.WorldCupSetup -> WorldCupSetupView(
                        viewModel = viewModel,
                        bestPhotos = bestPhotos,
                        allPhotos = allPhotos
                    )
                    is UiScreen.WorldCupPlay -> WorldCupPlayView(
                        viewModel = viewModel
                    )
                    is UiScreen.WorldCupWinner -> WorldCupWinnerCelebrationView(
                        viewModel = viewModel,
                        winner = screen.winner,
                        bracketSize = screen.bracketSize
                    )
                    is UiScreen.PrintCart -> PrintCartView(
                        viewModel = viewModel,
                        cartPhotos = printCartPhotos
                    )
                    is UiScreen.TournamentHistoryList -> TournamentHistoryListView(
                        viewModel = viewModel,
                        tournaments = tournaments
                    )
                }
            }

            // ZZIXX order handoff overlay
            if (viewModel.isSendingToZzixx) {
                ZzixxTransferingOverlay()
            }

            // ZZIXX order handoff dialog
            if (viewModel.showZzixxSuccessDialog) {
                ZzixxSuccessDialog(
                    onDismiss = { viewModel.showZzixxSuccessDialog = false },
                    cartPhotos = printCartPhotos
                )
            }
        }
    }
}

// 1. DASHBOARD VIEW (HOME)
@Composable
fun DashboardView(
    viewModel: BabyCupViewModel,
    allPhotos: List<BabyPhoto>,
    bestPhotos: List<BabyPhoto>,
    printCartPhotos: List<BabyPhoto>
) {
    val context = LocalContext.current
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // App Header Branding
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "베베컵 🧡",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "아기사진 이상형 월드컵 & 인화 선택기",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                IconButton(
                    onClick = { viewModel.triggerBestShotReminder() },
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surface, CircleShape)
                        .shadow(1.dp, CircleShape)
                ) {
                    BadgedBox(
                        badge = {
                            if (viewModel.showNotificationAlert) {
                                Badge(containerColor = MaterialTheme.colorScheme.primary)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "베스트 샷 알림",
                            tint = if (viewModel.showNotificationAlert) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Parenting selective push notice simulator block
        if (viewModel.showNotificationAlert) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(20.dp))
                        .testTag("push_alert_banner"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = "Notification icon",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "엄빠를 위한 정기 알림 🔔",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "지금 방금",
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.dismissNotificationAlert() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "닫기",
                                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Text(
                            text = "최근 3일간 촬영된 이달의 아기 사진이 쌓여있네요! 늦기 전에 이번 주 베스트 샷들을 후다닥 상자 속에 엄선해두고 이상형 월드컵 준비를 끝냅시다 ✨",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f)
                        )

                        Button(
                            onClick = { viewModel.navigateTo(UiScreen.BestShotSelector) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("select_now_button"),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("🧡 이번 주 베스트 사진 엄선하기", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Primary World Cup start controller
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("world_cup_launcher_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "Trophy icon",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "아기사진 이상형 월드컵 🏆",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "엄선된 베스트 사진 중 최고의 사진을 선출해요",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }

                    // Stat summary
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                                RoundedCornerShape(14.dp)
                            )
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "엄선 보관된 베스트", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text(text = "${bestPhotos.size}장", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                        Divider(
                            modifier = Modifier
                                .height(32.dp)
                                .width(1.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "총 인화 예정", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            Text(text = "${printCartPhotos.size}장", fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    Button(
                        onClick = { viewModel.navigateTo(UiScreen.WorldCupSetup) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("start_worldcup_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Play icon", tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("대회 개막하기! (이상형 월드컵)", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        // Secondary quick navigation actions row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cart print bucket button
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.navigateTo(UiScreen.PrintCart) }
                        .testTag("goto_cart_button"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ShoppingCart,
                            contentDescription = "Cart print",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("인화 인화함", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("${printCartPhotos.size}장 보관 중", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }

                // Match Tournament records history
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { viewModel.navigateTo(UiScreen.TournamentHistoryList) }
                        .testTag("goto_history_button"),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "History list",
                            tint = Color(0xFFFFD97D)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("우승 전당", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("역대 기록 보기", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }
        }

        // Photos repository view tag summary
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "우리 아기 갤러리 👶 (${allPhotos.size}장)",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                TextButton(
                    onClick = { viewModel.navigateTo(UiScreen.PhotoManager) },
                    modifier = Modifier.testTag("manage_gallery_button")
                ) {
                    Text("관리 / 등록하기", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 4 Columns layout listing items
        if (allPhotos.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 30.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "등록된 아기 사진이 없습니다.", fontSize = 12.sp, color = Color.Gray)
                }
            }
        } else {
            item {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(allPhotos.take(4)) { photo ->
                        BabyPhotoCardItem(
                            photo = photo,
                            onClick = { viewModel.toggleBestShotSelectionInDb(photo) }
                        )
                    }
                }
            }
        }
    }
}

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

// 4. WORLD CUP STUDY & PREPARATION CARD SETUP
@Composable
fun WorldCupSetupView(
    viewModel: BabyCupViewModel,
    bestPhotos: List<BabyPhoto>,
    allPhotos: List<BabyPhoto>
) {
    var sizePreference by remember { mutableIntStateOf(4) }
    
    // Total contest pool counts (best photos + others if fallback is needed)
    val totalAvailablePhotos = allPhotos.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("world_cup_setup_view")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateTo(UiScreen.Dashboard) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape)
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "월드컵 대진 설정",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "대진 규모 선택",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val scaleSizes = listOf(4, 8, 16)
                    scaleSizes.forEach { n ->
                        val isSel = sizePreference == n
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { sizePreference = n }
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${n}강전",
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) Color.White else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = "대진 보강 룰 설명 💡",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "현재 엄마 아빠가 하트(🧡) 표시로 엄선한 베스트 샷은 총 ${bestPhotos.size}장 있습니다.\n\n선택하신 ${sizePreference}강전을 채우기에 베스트 사진 수가 모자라면, 사진첩에 등록된 다른 실제 사진으로만 대진표를 보강합니다.",
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = { viewModel.startWorldCup(allPhotos, sizePreference) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("initiate_cup_play_btn"),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("위 설정으로 월드컵 개막하기 🏆", fontWeight = FontWeight.ExtraBold)
        }
    }
}

// 5. WORLD CUP ACTIVE TOURNAMENT GAMEPLAY MATCH
@Composable
fun WorldCupPlayView(
    viewModel: BabyCupViewModel
) {
    val left = viewModel.leftCandidate
    val right = viewModel.rightCandidate

    if (left == null || right == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("world_cup_play_view")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Progress text
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = viewModel.currentRoundName,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "매치 ${viewModel.currentMatchIndex + 1}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray
            )
        }

        Text(
            text = "두 사진 중 더 가슴을 울리고 인화하고 싶은 아기 최고의 순간을 아래에서 선택해 주세요! 💕",
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.fillMaxWidth()
        )

        // Comparison Stack Panels
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Left contestant
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { viewModel.submitMatchVote(left) }
                    .testTag("vote_left_candidate"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    BabyPhotoIllustration(
                        uriString = left.uriString,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        isExpanded = true
                    )
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "후보 A",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = left.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = left.description,
                            fontSize = 10.sp,
                            color = Color.Gray,
                            lineHeight = 14.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Central VS visual accent badge
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(32.dp)
                    .background(Color(0xFFFFD97D), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "VS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF5D4037))
            }

            // Right contestant
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { viewModel.submitMatchVote(right) }
                    .testTag("vote_right_candidate"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxSize()
                ) {
                    BabyPhotoIllustration(
                        uriString = right.uriString,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        isExpanded = true
                    )
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "후보 B",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = right.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = right.description,
                            fontSize = 10.sp,
                            color = Color.Gray,
                            lineHeight = 14.sp,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { viewModel.navigateTo(UiScreen.Dashboard) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray),
                modifier = Modifier.testTag("match_exit_btn")
            ) {
                Text("월드컵 기권 (홈으로)", color = Color.DarkGray, fontSize = 11.sp)
            }
            Text(
                text = "탭하여 표 투표하기",
                fontSize = 11.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

// 6. WINNER CELEBRATION TROPHY SHOWCASE
@Composable
fun WorldCupWinnerCelebrationView(
    viewModel: BabyCupViewModel,
    winner: BabyPhoto,
    bracketSize: Int
) {
    val scale = remember { Animatable(0.5f) }
    
    // Scale animation upon entry
    LaunchedEffect(key1 = true) {
        scale.animateTo(
            targetValue = 1.0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("world_cup_winner_view")
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "🎉 월드컵 최종 우승! 🏅",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "엄빠 피셜 오늘의 베스트 레전드 샷 결정 완료",
            fontSize = 12.sp,
            color = Color.Gray
        )

        // Center Champion illustration card
        Card(
            modifier = Modifier
                .size(260.dp)
                .shadow(8.dp, RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                BabyPhotoIllustration(
                    uriString = winner.uriString,
                    modifier = Modifier.fillMaxSize(),
                    isExpanded = true
                )

                // Top golden Crown decoration
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .background(Color(0xFFFFD97D), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "👑 CHAMPION", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF5D4037))
                }
            }
        }

        Text(
            text = winner.title,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = winner.description,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Stats card details
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("대회 규모", fontSize = 11.sp, color = Color.Gray)
                    Text("${bracketSize}강 토너먼트", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("누적 우승 기록", fontSize = 11.sp, color = Color.Gray)
                    Text("${winner.winsCount}회 트로피", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Actions
        Button(
            onClick = {
                viewModel.addWinnerToPrintCart(winner)
                viewModel.navigateTo(UiScreen.PrintCart)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("add_winner_to_cart_btn"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.ShoppingCart, contentDescription = "Cart")
                Spacer(modifier = Modifier.width(8.dp))
                Text("인화 장바구니에 담고 바로 주문하기", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.navigateTo(UiScreen.Dashboard) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("winner_exit_home_btn"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("우승 완료! 홈으로", fontSize = 11.sp)
            }

            OutlinedButton(
                onClick = { viewModel.navigateTo(UiScreen.WorldCupSetup) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("winner_restart_cup_btn"),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("다른 한 판 더 하기", fontSize = 11.sp)
            }
        }
    }
}

// 7. MY PRINT CART BOX (인화 바구니 연동 및 찍스)
@Composable
fun PrintCartView(
    viewModel: BabyCupViewModel,
    cartPhotos: List<BabyPhoto>
) {
    val sizePricing = mapOf("3x5" to 110, "4x6" to 140, "D4" to 170, "Wallet" to 180)
    
    // Calculate total pricing based on size definitions
    val totalPrice = cartPhotos.sumOf { item ->
        val unitPrice = sizePricing[item.printSize] ?: 140
        unitPrice * item.printQuantity
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("print_cart_view")
    ) {
        // App header
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
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = "내 인화 보관함", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(text = "선택된 우승 사진 목록", fontSize = 11.sp, color = Color.Gray)
                }
            }

            if (cartPhotos.isNotEmpty()) {
                TextButton(
                    onClick = { viewModel.clearPrintCart() },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red),
                    modifier = Modifier.testTag("clear_cart_btn")
                ) {
                    Text("전체 비우기", fontSize = 12.sp)
                }
            }
        }

        if (cartPhotos.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = "Empty list",
                        tint = Color.LightGray,
                        modifier = Modifier.size(64.dp)
                    )
                    Text("아직 인화 바구니에 담긴 사진이 없습니다.", fontSize = 13.sp, color = Color.Gray)
                    Text("월드컵 우승 사진이나 전체 리스트에서 추가해보세요!", fontSize = 11.sp, color = Color.Gray)
                    Button(onClick = { viewModel.navigateTo(UiScreen.Dashboard) }) {
                        Text("메인 대진으로 이동", fontSize = 11.sp)
                    }
                }
            }
        } else {
            Column(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(cartPhotos) { item ->
                        var isExpandedDropdown by remember { mutableStateOf(false) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cart_item_${item.id}"),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BabyPhotoIllustration(
                                    uriString = item.uriString,
                                    modifier = Modifier.size(72.dp)
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    // Custom Size layout picker dropdown inside cart
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { isExpandedDropdown = true }
                                    ) {
                                        Text(
                                            text = "사이즈: ${item.printSize} (장당 ${sizePricing[item.printSize]}원)",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = "dropdown", modifier = Modifier.size(16.dp))

                                        DropdownMenu(
                                            expanded = isExpandedDropdown,
                                            onDismissRequest = { isExpandedDropdown = false }
                                        ) {
                                            sizePricing.keys.forEach { sz ->
                                                DropdownMenuItem(
                                                    text = { Text("$sz 사이즈") },
                                                    onClick = {
                                                        viewModel.updatePrintCartItem(item, sz, item.printQuantity)
                                                        isExpandedDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Stepper quantity control details (+ / -)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconButton(
                                        onClick = {
                                            if (item.printQuantity > 1) {
                                                viewModel.updatePrintCartItem(item, item.printSize, item.printQuantity - 1)
                                            } else {
                                                viewModel.removePhotoFromPrintCart(item)
                                            }
                                        },
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                    ) {
                                        Text("-", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    Text(
                                        text = "${item.printQuantity}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )

                                    IconButton(
                                        onClick = {
                                            viewModel.updatePrintCartItem(item, item.printSize, item.printQuantity + 1)
                                        },
                                        modifier = Modifier
                                            .size(24.dp)
                                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
                                    ) {
                                        Text("+", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Billing checkout action summary
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("총 파일 수량", fontSize = 13.sp)
                            Text("${cartPhotos.sumOf { it.printQuantity }}장", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("예상 인화 비용 (찍스 할인가)", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("${totalPrice}원", fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Button(
                            onClick = { viewModel.simulateTransferToZzixx() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("zzixx_checkout_btn"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                Icon(imageVector = Icons.Default.Check, contentDescription = "Order")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("찍스(ZZIXX) 주문 페이지 준비하기", fontWeight = FontWeight.ExtraBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// 8. TOURNAMENT RUN WINNERS HISTORIC HALL LIST
@Composable
fun TournamentHistoryListView(
    viewModel: BabyCupViewModel,
    tournaments: List<TournamentRecord>
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("tournament_history_view")
    ) {
        // App Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.navigateTo(UiScreen.Dashboard) },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape)
            ) {
                Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "홈으로가기")
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "명예의 전당 (우승 이력)",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
        }

        if (tournaments.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(text = "아직 완료된 월드컵 경기가 없습니다.", color = Color.Gray, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tournaments) { item ->
                    val dateFormatted = remember(item.timestamp) {
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        sdf.format(Date(item.timestamp))
                    }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            BabyPhotoIllustration(
                                uriString = item.winnerUriString,
                                modifier = Modifier.size(56.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = item.winnerTitle,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = dateFormatted,
                                        fontSize = 10.sp,
                                        color = Color.Gray
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${item.totalParticipants}강 월드컵 챔피언",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.DarkGray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

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
