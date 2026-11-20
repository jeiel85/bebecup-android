package com.bebecup.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.bebecup.app.update.StoreUpdates

/**
 * The newer versionCode Google Play offers, or null. Asks Play whenever the
 * caller comes to the foreground, at most every few hours. Home shows
 * [UpdateBannerContent] while it is non-null; never a dialog, because updating
 * is not why the user opened the app.
 */
@Composable
fun rememberStoreUpdate(): Int? {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { StoreUpdates.checkIfStale(context) }
    }
    val available by StoreUpdates.available.collectAsState()
    return available
}

@Composable
fun UpdateBannerContent(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier
            .fillMaxWidth()
            .testTag("update_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("✨", fontSize = 20.sp, modifier = Modifier.padding(end = 12.dp))
            Column(Modifier.weight(1f)) {
                Text("새 버전이 나왔어요", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("눌러서 Play 스토어에서 업데이트하세요", fontSize = 12.sp)
            }
            Text("업데이트", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}
