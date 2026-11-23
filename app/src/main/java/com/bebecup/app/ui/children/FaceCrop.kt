package com.bebecup.app.ui.children

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation
import com.bebecup.app.data.children.PhotoFaceEntity
import kotlin.math.roundToInt

/** One face cut out of its photo, round. */
@Composable
fun FaceCrop(uriString: String, face: PhotoFaceEntity, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(uriString, face.id) {
        ImageRequest.Builder(context)
            .data(uriString)
            // Decode big enough that the face itself still has pixels after the crop.
            .size(DECODE_SIDE)
            .transformations(FaceCropTransformation(face.boxLeft, face.boxTop, face.boxRight, face.boxBottom))
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = "얼굴",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
    )
}

/** A round initial for a child without a face to show yet. */
@Composable
fun InitialAvatar(name: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            name.take(1),
            fontSize = (size.value * 0.4f).sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

private const val DECODE_SIDE = 768

/**
 * Crops the face box (0..1 of the upright photo) with some room around it, as a
 * square. Coil hands over the photo already turned upright by its EXIF tag,
 * like the analysis decode the box came from.
 */
private class FaceCropTransformation(
    private val left: Float,
    private val top: Float,
    private val right: Float,
    private val bottom: Float
) : Transformation {
    override val cacheKey: String = "face_crop_${left}_${top}_${right}_${bottom}"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        val cx = (left + right) / 2f * w
        val cy = (top + bottom) / 2f * h
        val side = (maxOf((right - left) * w, (bottom - top) * h) * MARGIN).coerceAtMost(minOf(w, h).toFloat())
        val x = (cx - side / 2f).coerceIn(0f, w - side).roundToInt()
        val y = (cy - side / 2f).coerceIn(0f, h - side).roundToInt()
        val s = side.roundToInt().coerceAtLeast(1).coerceAtMost(minOf(w - x, h - y))
        return Bitmap.createBitmap(input, x, y, s, s)
    }

    private companion object {
        const val MARGIN = 1.5f
    }
}
