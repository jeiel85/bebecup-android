package com.bebecup.app.ui.tournament

import com.bebecup.app.ui.components.AgeBadge
import com.bebecup.app.domain.profile.BabyAge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bebecup.app.data.BabyPhoto
import com.bebecup.app.ui.components.BabyPhotoIllustration
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import kotlinx.coroutines.delay

private const val VOTE_ARM_DELAY_MS = 350L

/** One match at a time: tap the photo the family likes more. */
@Composable
fun WorldCupPlayView(viewModel: BabyCupViewModel) {
    val match = viewModel.currentMatch
    var confirmExit by rememberSaveable { mutableStateOf(false) }

    if (match == null) {
        // Bracket finished (winner screen is on its way) or never started.
        Box(Modifier.fillMaxSize())
        return
    }

    // A fast double tap must not decide the *next* match too: ignore taps until
    // the new pair has been on screen for a moment.
    var armed by remember(match) { mutableStateOf(false) }
    LaunchedEffect(match) {
        delay(VOTE_ARM_DELAY_MS)
        armed = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("world_cup_play_view")
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    viewModel.currentRoundName,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (viewModel.currentMatchLabel.isNotEmpty()) {
                    Text(
                        "${viewModel.currentMatchLabel} 경기",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            IconButton(
                onClick = { confirmExit = true },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface, CircleShape)
            ) { Icon(Icons.Default.Close, contentDescription = "월드컵 그만하기") }
        }

        Text(
            "더 마음이 가는 사진을 눌러 주세요 💕",
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.fillMaxWidth()
        )

        Contestant(
            photo = match.left,
            age = viewModel.ageOf(match.left),
            label = "A",
            testTag = "vote_left_candidate",
            modifier = Modifier.weight(1f),
            onVote = { if (armed) viewModel.vote(match, match.left) }
        )
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(40.dp)
                .background(Color(0xFFFFDB8E), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("VS", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF6B4F45))
        }
        Contestant(
            photo = match.right,
            age = viewModel.ageOf(match.right),
            label = "B",
            testTag = "vote_right_candidate",
            modifier = Modifier.weight(1f),
            onVote = { if (armed) viewModel.vote(match, match.right) }
        )
    }

    if (confirmExit) {
        AlertDialog(
            onDismissRequest = { confirmExit = false },
            title = { Text("월드컵을 그만할까요?") },
            text = { Text("진행 중인 대결은 저장되지 않아요. 보관함 사진은 그대로 남아 있어요.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmExit = false
                    viewModel.navigateHome()
                }) { Text("그만하기") }
            },
            dismissButton = { TextButton(onClick = { confirmExit = false }) { Text("계속하기") } }
        )
    }
}

@Composable
private fun Contestant(
    photo: BabyPhoto,
    age: BabyAge?,
    label: String,
    testTag: String,
    modifier: Modifier,
    onVote: () -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onVote)
            .testTag(testTag),
        shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            BabyPhotoIllustration(photo.uriString, Modifier.fillMaxSize(), isExpanded = true)
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.85f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
            if (age != null) AgeBadge(age, Modifier.align(Alignment.BottomStart).padding(6.dp))
        }
    }
}
