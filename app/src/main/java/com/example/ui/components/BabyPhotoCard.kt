package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BabyPhoto
import kotlin.math.sin

@Composable
fun BabyPhotoIllustration(
    uriString: String,
    modifier: Modifier = Modifier,
    isExpanded: Boolean = false
) {
    Box(
        modifier = modifier
            .testTag("baby_photo_illustration_${uriString}")
            .clip(RoundedCornerShape(if (isExpanded) 28.dp else 18.dp))
    ) {
        // Base illustration according to preset identifier
        if (uriString.startsWith("content://") || uriString.startsWith("file://") || uriString.startsWith("http")) {
            var isError by remember { mutableStateOf(false) }
            if (isError) {
                CustomBabyIllustration(uriString, isExpanded)
            } else {
                AsyncImage(
                    model = uriString,
                    contentDescription = "아기 사진",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onError = { isError = true }
                )
            }
        } else {
            when {
                uriString.startsWith("preset:smile") -> SmileBabyIllustration(isExpanded)
                uriString.startsWith("preset:sleep") -> SleepBabyIllustration(isExpanded)
                uriString.startsWith("preset:cry") -> CryBabyIllustration(isExpanded)
                uriString.startsWith("preset:eat") -> EatBabyIllustration(isExpanded)
                uriString.startsWith("preset:crawl") -> CrawlBabyIllustration(isExpanded)
                uriString.startsWith("preset:peek") -> PeekBabyIllustration(isExpanded)
                uriString.startsWith("preset:bath") -> BathBabyIllustration(isExpanded)
                uriString.startsWith("preset:toy") -> ToyBabyIllustration(isExpanded)
                else -> CustomBabyIllustration(uriString, isExpanded)
            }
        }
    }
}

@Composable
fun SmileBabyIllustration(isExpanded: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "smile")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = EaseInOutBack),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heartScale"
    )

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF0F3))
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2
        val r = if (isExpanded) w * 0.28f else w * 0.24f

        // Draw soft glow
        drawCircle(
            color = Color(0xFFFFD1D6).copy(alpha = 0.5f),
            radius = r * 1.3f,
            center = Offset(cx, cy)
        )

        // Draw Baby Face
        drawCircle(
            color = Color(0xFFFDE1D3),
            radius = r,
            center = Offset(cx, cy)
        )

        // Draw rosy cheeks
        drawCircle(
            color = Color(0xFFFFB3C1).copy(alpha = 0.8f),
            radius = r * 0.22f,
            center = Offset(cx - r * 0.55f, cy + r * 0.15f)
        )
        drawCircle(
            color = Color(0xFFFFB3C1).copy(alpha = 0.8f),
            radius = r * 0.22f,
            center = Offset(cx + r * 0.55f, cy + r * 0.15f)
        )

        // Draw smiling closed eyes as arcs
        val eyeWidth = r * 0.25f
        val strokeW = if (isExpanded) 8f else 5f
        
        // Left eye
        val leftEyePath = Path().apply {
            moveTo(cx - r * 0.45f - eyeWidth/2, cy - r * 0.15f)
            quadraticTo(cx - r * 0.45f, cy - r * 0.25f, cx - r * 0.45f + eyeWidth/2, cy - r * 0.15f)
        }
        drawPath(leftEyePath, Color(0xFF5D4037), style = Stroke(strokeW, cap = StrokeCap.Round))

        // Right eye
        val rightEyePath = Path().apply {
            moveTo(cx + r * 0.45f - eyeWidth/2, cy - r * 0.15f)
            quadraticTo(cx + r * 0.45f, cy - r * 0.25f, cx + r * 0.45f + eyeWidth/2, cy - r * 0.15f)
        }
        drawPath(rightEyePath, Color(0xFF5D4037), style = Stroke(strokeW, cap = StrokeCap.Round))

        // Big smile mouth
        val mouthPath = Path().apply {
            moveTo(cx - r * 0.25f, cy + r * 0.25f)
            quadraticTo(cx, cy + r * 0.5f, cx + r * 0.25f, cy + r * 0.25f)
            quadraticTo(cx, cy + r * 0.28f, cx - r * 0.25f, cy + r * 0.25f)
        }
        drawPath(mouthPath, Color(0xFFFC8B9C))
        drawPath(mouthPath, Color(0xFF5D4037), style = Stroke(strokeW/2, cap = StrokeCap.Round))

        // Top cute single hair strand curly
        val hairPath = Path().apply {
            moveTo(cx, cy - r)
            quadraticTo(cx - r * 0.15f, cy - r * 1.25f, cx + r * 0.1f, cy - r * 1.35f)
        }
        drawPath(hairPath, Color(0xFF8D6E63), style = Stroke(strokeW, cap = StrokeCap.Round))

        // Floating dynamic mini hearts
        if (isExpanded) {
            val heartOffset1 = Offset(cx - r * 1.1f, cy - r * 0.7f)
            drawCircle(Color(0xFFFC8B9C), radius = 18f * scale, center = heartOffset1)
            val heartOffset2 = Offset(cx + r * 1.1f, cy + r * 0.6f)
            drawCircle(Color(0xFFFC8B9C), radius = 14f * scale, center = heartOffset2)
        }
    }
}

@Composable
fun SleepBabyIllustration(isExpanded: Boolean) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEEEAF7))
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2
        val r = if (isExpanded) w * 0.28f else w * 0.24f

        // Sleepy moon background
        drawCircle(
            color = Color(0xFFFFF7C4),
            radius = r * 1.4f,
            center = Offset(cx + r * 0.3f, cy - r * 0.3f)
        )
        drawCircle(
            color = Color(0xFFEEEAF7), // Masking to make crescent moon
            radius = r * 1.4f,
            center = Offset(cx + r * 0.1f, cy - r * 0.4f)
        )

        // Draw Baby Face
        drawCircle(
            color = Color(0xFFFEE6DB),
            radius = r,
            center = Offset(cx, cy + r * 0.2f)
        )

        // Sleeping eyes (downward curves)
        val strokeW = if (isExpanded) 8f else 5f
        val eyeWidth = r * 0.22f
        val leftEyePath = Path().apply {
            moveTo(cx - r * 0.45f - eyeWidth/2, cy + r * 0.05f)
            quadraticTo(cx - r * 0.45f, cy + r * 0.15f, cx - r * 0.45f + eyeWidth/2, cy + r * 0.05f)
        }
        drawPath(leftEyePath, Color(0xFF6C5B7B), style = Stroke(strokeW, cap = StrokeCap.Round))

        val rightEyePath = Path().apply {
            moveTo(cx + r * 0.45f - eyeWidth/2, cy + r * 0.05f)
            quadraticTo(cx + r * 0.45f, cy + r * 0.15f, cx + r * 0.45f + eyeWidth/2, cy + r * 0.05f)
        }
        drawPath(rightEyePath, Color(0xFF6C5B7B), style = Stroke(strokeW, cap = StrokeCap.Round))

        // Cute pacifier (dummy) in mouth
        drawCircle(
            color = Color(0xFFFFCC33),
            radius = r * 0.25f,
            center = Offset(cx, cy + r * 0.42f)
        )
        drawCircle(
            color = Color(0xFF5CC0C7),
            radius = r * 0.16f,
            center = Offset(cx, cy + r * 0.42f)
        )

        // Gentle pink blush
        drawCircle(
            color = Color(0xFFFFA69E).copy(alpha = 0.5f),
            radius = r * 0.18f,
            center = Offset(cx - r * 0.5f, cy + r * 0.3f)
        )
        drawCircle(
            color = Color(0xFFFFA69E).copy(alpha = 0.5f),
            radius = r * 0.18f,
            center = Offset(cx + r * 0.5f, cy + r * 0.3f)
        )

        // Soft yellow sleeping cap
        val capPath = Path().apply {
            moveTo(cx - r * 0.85f, cy - r * 0.3f)
            quadraticTo(cx, cy - r * 1.5f, cx + r * 0.3f, cy - r * 0.7f)
            quadraticTo(cx + r * 0.85f, cy - r * 0.1f, cx + r * 0.85f, cy - r * 0.1f)
            quadraticTo(cx, cy - r * 0.4f, cx - r * 0.85f, cy - r * 0.3f)
        }
        drawPath(capPath, Color(0xFFFFD166))
        
        // Pom pom on cap end
        drawCircle(
            color = Color.White,
            radius = r * 0.15f,
            center = Offset(cx + r * 0.3f, cy - r * 1.4f)
        )
    }
}

@Composable
fun CryBabyIllustration(isExpanded: Boolean) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE4F0F6))
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2
        val r = if (isExpanded) w * 0.28f else w * 0.24f

        // Face
        drawCircle(
            color = Color(0xFFFDE1D3),
            radius = r,
            center = Offset(cx, cy)
        )

        val strokeW = if (isExpanded) 8f else 5f

        // Crying squeezed closed eyes (Chevron / V-shape)
        // Left eye
        val lp = Path().apply {
            moveTo(cx - r * 0.55f, cy - r * 0.12f)
            lineTo(cx - r * 0.35f, cy - r * 0.02f)
            lineTo(cx - r * 0.55f, cy + r * 0.08f)
        }
        drawPath(lp, Color(0xFF457B9D), style = Stroke(strokeW, cap = StrokeCap.Round))

        // Right eye
        val rp = Path().apply {
            moveTo(cx + r * 0.55f, cy - r * 0.12f)
            lineTo(cx + r * 0.35f, cy - r * 0.02f)
            lineTo(cx + r * 0.55f, cy + r * 0.08f)
        }
        drawPath(rp, Color(0xFF457B9D), style = Stroke(strokeW, cap = StrokeCap.Round))

        // Large shouting round mouth
        drawCircle(
            color = Color(0xFFEF476F),
            radius = r * 0.28f,
            center = Offset(cx, cy + r * 0.35f)
        )
        // Tiny baby teeth inside mouth
        drawRect(
            color = Color.White,
            topLeft = Offset(cx - r * 0.08f, cy + r * 0.12f),
            size = Size(r * 0.16f, r * 0.08f)
        )

        // Splash style big tear droplets
        val tearPathLeft = Path().apply {
            moveTo(cx - r * 0.7f, cy + r * 0.15f)
            quadraticTo(cx - r * 0.9f, cy + r * 0.45f, cx - r * 0.75f, cy + r * 0.55f)
            quadraticTo(cx - r * 0.62f, cy + r * 0.45f, cx - r * 0.7f, cy + r * 0.15f)
        }
        drawPath(tearPathLeft, Color(0xFF00B4D8))

        val tearPathRight = Path().apply {
            moveTo(cx + r * 0.7f, cy + r * 0.15f)
            quadraticTo(cx + r * 0.9f, cy + r * 0.45f, cx + r * 0.75f, cy + r * 0.55f)
            quadraticTo(cx + r * 0.62f, cy + r * 0.45f, cx + r * 0.7f, cy + r * 0.15f)
        }
        drawPath(tearPathRight, Color(0xFF00B4D8))
    }
}

@Composable
fun EatBabyIllustration(isExpanded: Boolean) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF6E5))
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2
        val r = if (isExpanded) w * 0.28f else w * 0.24f

        // Face
        drawCircle(
            color = Color(0xFFFDE1D3),
            radius = r,
            center = Offset(cx, cy)
        )

        // Rosy cheeks
        drawCircle(
            color = Color(0xFFFFA69E),
            radius = r * 0.18f,
            center = Offset(cx - r * 0.52f, cy + r * 0.2f)
        )
        drawCircle(
            color = Color(0xFFFFA69E),
            radius = r * 0.18f,
            center = Offset(cx + r * 0.52f, cy + r * 0.2f)
        )

        // Messy yellow food stains on cheeks!
        drawCircle(
            color = Color(0xFFFFC658).copy(alpha = 0.8f),
            radius = r * 0.08f,
            center = Offset(cx - r * 0.48f, cy + r * 0.24f)
        )
        drawCircle(
            color = Color(0xFFFFC658).copy(alpha = 0.8f),
            radius = r * 0.10f,
            center = Offset(cx + r * 0.45f, cy + r * 0.15f)
        )
        drawCircle(
            color = Color(0xFFFFC658).copy(alpha = 0.8f),
            radius = r * 0.06f,
            center = Offset(cx - r * 0.1f, cy + r * 0.52f)
        )

        // Simple dot eyes
        drawCircle(color = Color(0xFF5D4037), radius = r * 0.08f, center = Offset(cx - r * 0.4f, cy - r * 0.1f))
        drawCircle(color = Color(0xFF5D4037), radius = r * 0.08f, center = Offset(cx + r * 0.4f, cy - r * 0.1f))

        // Messy happy smile
        val strokeW = if (isExpanded) 8f else 5f
        val smilePath = Path().apply {
            moveTo(cx - r * 0.18f, cy + r * 0.22f)
            quadraticTo(cx, cy + r * 0.38f, cx + r * 0.18f, cy + r * 0.22f)
        }
        drawPath(smilePath, Color(0xFF5D4037), style = Stroke(strokeW, cap = StrokeCap.Round))

        // Cute baby bib (bottom collar)
        val bibPath = Path().apply {
            moveTo(cx - r * 0.7f, cy + r * 0.7f)
            quadraticTo(cx, cy + r * 1.2f, cx + r * 0.7f, cy + r * 0.7f)
            lineTo(cx + r * 0.3f, cy + r * 1.5f)
            lineTo(cx - r * 0.3f, cy + r * 1.5f)
            close()
        }
        drawPath(bibPath, Color(0xFF5CC0C7))
    }
}

@Composable
fun CrawlBabyIllustration(isExpanded: Boolean) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8F5E9))
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2
        val r = if (isExpanded) w * 0.28f else w * 0.24f

        // Draw some crawling dashed movement lines or stars
        val strokeW = if (isExpanded) 6f else 4f
        drawCircle(
            color = Color(0xFFC8E6C9),
            radius = r * 1.3f,
            center = Offset(cx, cy),
            style = Stroke(strokeW, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f))
        )

        // Draw Baby crawling back/bum silhouette
        // Baby head
        drawCircle(
            color = Color(0xFFFDE1D3),
            radius = r * 0.7f,
            center = Offset(cx, cy - r * 0.4f)
        )
        // Baby body (cute plump diapered torso)
        drawCircle(
            color = Color.White, // White diaper
            radius = r * 0.85f,
            center = Offset(cx, cy + r * 0.5f)
        )
        drawCircle(
            color = Color(0xFFFDE1D3), // fleshy back showing inside diaper
            radius = r * 0.5f,
            center = Offset(cx, cy + r * 0.2f)
        )

        // Plump crawling legs
        drawCircle(Color(0xFFFDE1D3), radius = r * 0.28f, center = Offset(cx - r * 0.6f, cy + r * 0.78f))
        drawCircle(Color(0xFFFDE1D3), radius = r * 0.28f, center = Offset(cx + r * 0.6f, cy + r * 0.78f))

        // Cute mini hair tuft
        val hairPath = Path().apply {
            moveTo(cx, cy - r * 1.1f)
            quadraticTo(cx + r * 0.15f, cy - r * 1.3f, cx + r * 0.3f, cy - r * 1.1f)
        }
        drawPath(hairPath, Color(0xFF8D6E63), style = Stroke(strokeW, cap = StrokeCap.Round))
    }
}

@Composable
fun PeekBabyIllustration(isExpanded: Boolean) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFFF2D4))
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2
        val r = if (isExpanded) w * 0.28f else w * 0.24f

        // Background decorative splash
        drawCircle(color = Color(0xFFFFE082).copy(alpha = 0.5f), radius = r * 1.4f, center = Offset(cx, cy))

        // Baby head peeking from the bottom edge
        drawCircle(
            color = Color(0xFFFDE1D3),
            radius = r * 1.1f,
            center = Offset(cx, cy + r * 0.6f)
        )

        val strokeW = if (isExpanded) 8f else 5f

        // Two big shining round baby eyes with white sparkles
        val eyeRadius = r * 0.2f
        drawCircle(color = Color(0xFF5D4037), radius = eyeRadius, center = Offset(cx - r * 0.45f, cy + r * 0.2f))
        drawCircle(color = Color(0xFF5D4037), radius = eyeRadius, center = Offset(cx + r * 0.45f, cy + r * 0.2f))
        
        // Sparkle points inside eyes
        drawCircle(color = Color.White, radius = eyeRadius * 0.35f, center = Offset(cx - r * 0.52f, cy + r * 0.12f))
        drawCircle(color = Color.White, radius = eyeRadius * 0.35f, center = Offset(cx + r * 0.38f, cy + r * 0.12f))

        // Squeezed tiny nose button
        drawCircle(color = Color(0xFFFFA69E), radius = r * 0.08f, center = Offset(cx, cy + r * 0.45f))

        // Curved happy baby eyebrows
        val leftEyebrow = Path().apply {
            moveTo(cx - r * 0.65f, cy - r * 0.08f)
            quadraticTo(cx - r * 0.45f, cy - r * 0.18f, cx - r * 0.25f, cy - r * 0.08f)
        }
        drawPath(leftEyebrow, Color(0xFF8D6E63), style = Stroke(strokeW * 0.7f, cap = StrokeCap.Round))

        val rightEyebrow = Path().apply {
            moveTo(cx + r * 0.25f, cy - r * 0.08f)
            quadraticTo(cx + r * 0.45f, cy - r * 0.18f, cx + r * 0.65f, cy - r * 0.08f)
        }
        drawPath(rightEyebrow, Color(0xFF8D6E63), style = Stroke(strokeW * 0.7f, cap = StrokeCap.Round))
    }
}

@Composable
fun BathBabyIllustration(isExpanded: Boolean) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE0F7FA))
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2
        val r = if (isExpanded) w * 0.28f else w * 0.24f

        // Water levels base
        drawRect(
            color = Color(0xFF4DD0E1).copy(alpha = 0.5f),
            topLeft = Offset(0f, cy + r * 0.1f),
            size = Size(w, h - (cy + r * 0.1f))
        )

        // Water waves
        val wavePath = Path().apply {
            moveTo(0f, cy + r * 0.1f)
            quadraticTo(w * 0.25f, cy, w * 0.5f, cy + r * 0.1f)
            quadraticTo(w * 0.75f, cy + r * 0.2f, w, cy + r * 0.1f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(wavePath, Color(0xFF00ACC1).copy(alpha = 0.4f))

        // Cute baby face in tub
        drawCircle(
            color = Color(0xFFFDE1D3),
            radius = r,
            center = Offset(cx, cy - r * 0.1f)
        )

        // Cute hair suds (soap bubble hat)
        drawCircle(color = Color.White, radius = r * 0.24f, center = Offset(cx, cy - r * 1.1f))
        drawCircle(color = Color.White, radius = r * 0.18f, center = Offset(cx - r * 0.2f, cy - r * 1.0f))
        drawCircle(color = Color.White, radius = r * 0.18f, center = Offset(cx + r * 0.2f, cy - r * 1.0f))

        // Smiling dot eyes & cute face lines
        drawCircle(color = Color(0xFF5D4037), radius = r * 0.08f, center = Offset(cx - r * 0.4f, cy - r * 0.2f))
        drawCircle(color = Color(0xFF5D4037), radius = r * 0.08f, center = Offset(cx + r * 0.4f, cy - r * 0.2f))

        val strokeW = if (isExpanded) 8f else 5f
        val smilePath = Path().apply {
            moveTo(cx - r * 0.25f, cy + r * 0.18f)
            quadraticTo(cx, cy + r * 0.42f, cx + r * 0.25f, cy + r * 0.18f)
        }
        drawPath(smilePath, Color(0xFF5D4037), style = Stroke(strokeW, cap = StrokeCap.Round))

        // Floating yellow bath toy duck
        if (isExpanded) {
            val duckX = cx + r * 0.9f
            val duckY = cy + r * 0.5f
            drawCircle(color = Color(0xFFFFEB3B), radius = 25f, center = Offset(duckX, duckY))
            drawCircle(color = Color(0xFFFFEB3B), radius = 15f, center = Offset(duckX + 18f, duckY - 14f))
            drawRect(color = Color(0xFFFF5722), topLeft = Offset(duckX + 30f, duckY - 17f), size = Size(10f, 6f)) // orange beak
        }
    }
}

@Composable
fun ToyBabyIllustration(isExpanded: Boolean) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFEDE7F6))
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2
        val r = if (isExpanded) w * 0.28f else w * 0.24f

        // Soft circular target back
        drawCircle(color = Color(0xFFD1C4E9).copy(alpha = 0.5f), radius = r * 1.3f, center = Offset(cx, cy))

        // Face
        drawCircle(
            color = Color(0xFFFDE1D3),
            radius = r,
            center = Offset(cx - r * 0.15f, cy - r * 0.1f)
        )

        // Left eye closed winking, right eye open sparkle
        // Right eye (open)
        drawCircle(color = Color(0xFF5D4037), radius = r * 0.08f, center = Offset(cx + r * 0.22f, cy - r * 0.25f))
        drawCircle(color = Color.White, radius = r * 0.03f, center = Offset(cx + r * 0.18f, cy - r * 0.28f))
        
        // Left eye (wink curve)
        val strokeW = if (isExpanded) 8f else 5f
        val winkPath = Path().apply {
            moveTo(cx - r * 0.58f, cy - r * 0.22f)
            quadraticTo(cx - r * 0.42f, cy - r * 0.32f, cx - r * 0.26f, cy - r * 0.22f)
        }
        drawPath(winkPath, Color(0xFF5D4037), style = Stroke(strokeW, cap = StrokeCap.Round))

        // Open mouth O-shape surprise
        drawCircle(color = Color(0xFFEF476F), radius = r * 0.12f, center = Offset(cx - r * 0.1f, cy + r * 0.18f))

        // Little toddler hands holding a stacking square toy
        drawRoundRect(
            color = Color(0xFFFF9800),
            topLeft = Offset(cx + r * 0.2f, cy + r * 0.2f),
            size = Size(r * 0.6f, r * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(15f, 15f)
        )
        drawRoundRect(
            color = Color(0xFF4CAF50),
            topLeft = Offset(cx + r * 0.32f, cy + r * 0.4f),
            size = Size(r * 0.36f, r * 0.3f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f)
        )
    }
}

@Composable
fun CustomBabyIllustration(uriString: String, isExpanded: Boolean) {
    // Generate a unique charming pastel drawing strictly from the properties of the string seed!
    // This is incredibly robust, prevents any blank cards, and provides delightful visuals.
    val seed = uriString.hashCode()
    val rColor = remember(seed) {
        val h = (seed % 360).let { if (it < 0) it + 360 else it }.toFloat()
        Color.hsv(h, 0.12f, 0.98f) // soft pastel bg
    }
    val faceColor = remember(seed) {
        Color(0xFFFDE1D3)
    }
    val featureColor = remember(seed) {
        val h = (seed % 360).let { if (it < 0) it + 360 else it }.toFloat()
        Color.hsv(h, 0.70f, 0.50f) // matching accent tone
    }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(rColor)
    ) {
        val w = size.width
        val h = size.height
        val cx = w / 2
        val cy = h / 2
        val r = if (isExpanded) w * 0.28f else w * 0.24f

        // Circular dynamic background glow
        drawCircle(
            color = featureColor.copy(alpha = 0.18f),
            radius = r * 1.3f,
            center = Offset(cx, cy)
        )

        // Baby head
        drawCircle(
            color = faceColor,
            radius = r,
            center = Offset(cx, cy)
        )

        // Draw baby cheeks based on hash
        val cheekOffset = r * 0.52f
        drawCircle(
            color = Color(0xFFFFA69E).copy(alpha = 0.8f),
            radius = r * 0.18f,
            center = Offset(cx - cheekOffset, cy + r * 0.18f)
        )
        drawCircle(
            color = Color(0xFFFFA69E).copy(alpha = 0.8f),
            radius = r * 0.18f,
            center = Offset(cx + cheekOffset, cy + r * 0.18f)
        )

        val strokeW = if (isExpanded) 8f else 5f

        // Draw smiling closed eyes
        val eyeWidth = r * 0.24f
        val leftEyePath = Path().apply {
            moveTo(cx - r * 0.42f - eyeWidth/2, cy - r * 0.12f)
            quadraticTo(cx - r * 0.42f, cy - r * 0.24f, cx - r * 0.42f + eyeWidth/2, cy - r * 0.12f)
        }
        drawPath(leftEyePath, Color(0xFF5D4037), style = Stroke(strokeW, cap = StrokeCap.Round))

        val rightEyePath = Path().apply {
            moveTo(cx + r * 0.42f - eyeWidth/2, cy - r * 0.12f)
            quadraticTo(cx + r * 0.42f, cy - r * 0.24f, cx + r * 0.42f + eyeWidth/2, cy - r * 0.12f)
        }
        drawPath(rightEyePath, Color(0xFF5D4037), style = Stroke(strokeW, cap = StrokeCap.Round))

        // Cute custom smile based on seed
        val smileStyle = seed % 3
        if (smileStyle == 0) {
            // big round open mouth
            drawCircle(
                color = Color(0xFFEF476F),
                radius = r * 0.18f,
                center = Offset(cx, cy + r * 0.32f)
            )
        } else {
            // happy smiley line
            val mPath = Path().apply {
                moveTo(cx - r * 0.25f, cy + r * 0.25f)
                quadraticTo(cx, cy + r * 0.44f, cx + r * 0.25f, cy + r * 0.25f)
            }
            drawPath(mPath, Color(0xFF5D4037), style = Stroke(strokeW, cap = StrokeCap.Round))
        }

        // Draw custom camera outline overlay at corner to symbolize photography upload
        val iconSize = r * 0.3f
        val ox = cx + r * 0.5f
        val oy = cy - r * 0.8f
        drawCircle(color = featureColor, radius = iconSize, center = Offset(ox, oy))
        drawCircle(color = Color.White, radius = iconSize * 0.5f, center = Offset(ox, oy))
    }
}

@Composable
fun BabyPhotoCardItem(
    photo: BabyPhoto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onDelete: (() -> Unit)? = null,
    onToggleBest: (() -> Unit)? = null
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(4.dp)
            .testTag("baby_photo_card_${photo.id}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            ) {
                BabyPhotoIllustration(
                    uriString = photo.uriString,
                    modifier = Modifier.fillMaxSize(),
                    isExpanded = false
                )

                // Render Badge markers
                if (photo.isPreset) {
                    Surface(
                        color = Color(0xFFFFD97D),
                        shape = RoundedCornerShape(topStart = 0.dp, bottomEnd = 12.dp, topEnd = 0.dp, bottomStart = 12.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = "프리셋",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5D4037),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                if (photo.isSelectedAsBest) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "베스트 샷 추천",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (isSelected) Color(0xFFFC8B9C).copy(alpha = 0.25f) else Color.Transparent)
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(20.dp)
                            )
                    ) {
                        Surface(
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                            shape = CircleShape,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                                .size(28.dp)
                                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                        ) {
                            if (isSelected) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Star,
                                        contentDescription = "선택완료",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Photo Info Details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = photo.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = photo.takenDate,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )

                    // Stats indicators
                    if (photo.winsCount > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = "우승 횟수",
                                tint = Color(0xFFFFD97D),
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "${photo.winsCount}회 우승",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
