package com.bebecup.app.ui.print

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.bebecup.app.data.BabyPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Hands the print box to an app the parent picks (a print-shop app, a
 * messenger, Drive…) through the system share sheet. Nothing is uploaded by
 * BebeCup itself: the parent chooses where the photos go.
 *
 * Photos are first copied into the app's private cache and shared through our
 * own FileProvider, because gallery/picker URIs can't always be re-granted to
 * another app.
 */
object PrintShare {

    /** [photos] are the cart entries actually attached, in the same order as [uris]. */
    data class Prepared(val uris: List<Uri>, val photos: List<BabyPhoto>, val skipped: Int)

    suspend fun prepare(context: Context, photos: List<BabyPhoto>): Prepared =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, SHARE_DIR).apply {
                deleteRecursively()
                mkdirs()
            }
            var skipped = 0
            val attached = mutableListOf<BabyPhoto>()
            val uris = photos.mapIndexedNotNull { index, photo ->
                try {
                    val source = Uri.parse(photo.uriString)
                    val ext = extensionFor(context.contentResolver.getType(source))
                    val name = "bebecup_${index + 1}_${photo.printSize}_x${photo.printQuantity}.$ext"
                    val target = File(dir, name)
                    val copied = context.contentResolver.openInputStream(source)?.use { input ->
                        target.outputStream().use { input.copyTo(it) }
                        true
                    } ?: false
                    if (!copied) {
                        skipped++
                        null
                    } else {
                        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", target)
                            .also { attached += photo }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "could not prepare photo ${photo.id} for sharing", e)
                    skipped++
                    null
                }
            }
            Prepared(uris, attached, skipped)
        }

    fun shareIntent(uris: List<Uri>, summary: String): Intent {
        val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "image/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            putExtra(Intent.EXTRA_TEXT, summary)
            clipData = ClipData.newRawUri(null, uris.first()).apply {
                uris.drop(1).forEach { addItem(ClipData.Item(it)) }
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, "인화할 곳으로 보내기")
    }

    /**
     * "4x6 2장, 5x7 1장" — sent as text so a print shop sees the wanted sizes.
     * Pass only the photos that were actually attached.
     */
    fun summary(photos: List<BabyPhoto>): String {
        val bySize = photos.groupBy { it.printSize }
            .map { (size, list) -> "$size ${list.sumOf { it.printQuantity }}장" }
        return "베베컵 인화 목록: " + bySize.joinToString(", ")
    }

    private fun extensionFor(mime: String?): String = when (mime) {
        "image/png" -> "png"
        "image/heic", "image/heif" -> "heic"
        "image/webp" -> "webp"
        else -> "jpg"
    }

    private const val SHARE_DIR = "print_share"
    private const val TAG = "PrintShare"
}
