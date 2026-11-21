package com.bebecup.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "사진첩에서 지워진 사진이 N장 있어요": kept photos whose file is gone show
 * only a placeholder. Removing them takes them out of the 보관함 and 인화함;
 * the photos themselves are already gone, so nothing else is lost.
 */
@Composable
fun MissingPhotosCard(count: Int, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .testTag("missing_photos_card")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("사진첩에서 지워진 사진이 ${count}장 있어요", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("빈 칸으로만 보여서 목록에서 빼는 게 좋아요", fontSize = 12.sp)
            }
            TextButton(onClick = onRemove, modifier = Modifier.testTag("remove_missing_photos")) {
                Text("목록에서 빼기", fontSize = 13.sp)
            }
        }
    }
}
