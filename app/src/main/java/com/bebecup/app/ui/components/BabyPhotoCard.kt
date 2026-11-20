package com.bebecup.app.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bebecup.app.R

/**
 * A parent's photo, cropped to fill. When the photo can no longer be opened
 * (deleted from the gallery, access revoked) a soft placeholder is shown
 * instead of a blank card.
 */
@Composable
fun BabyPhotoIllustration(
    uriString: String,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false
) {
    var isError by remember(uriString) { mutableStateOf(false) }
    Box(
        modifier = modifier
            .testTag("baby_photo_illustration_${uriString}")
            .clip(RoundedCornerShape(if (isExpanded) 28.dp else 18.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (isError) {
            Image(
                painter = painterResource(R.drawable.illus_empty),
                contentDescription = "열 수 없는 사진",
                modifier = Modifier.fillMaxSize().padding(8.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            AsyncImage(
                model = uriString,
                contentDescription = "아기 사진",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { isError = true }
            )
        }
    }
}
