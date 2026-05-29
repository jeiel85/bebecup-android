package com.bebecup.app.ui.print

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.ui.components.BabyPhotoIllustration
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen

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
