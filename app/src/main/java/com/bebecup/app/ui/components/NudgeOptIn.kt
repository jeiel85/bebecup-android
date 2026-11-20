package com.bebecup.app.ui.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.bebecup.app.data.media.PhotoAccess
import com.bebecup.app.nudge.NudgeNotifier
import com.bebecup.app.ui.viewmodel.BabyCupViewModel

/**
 * Turning on "사진이 쌓이면 알려드려요" needs two grants: reading the gallery
 * (to count new photos) and posting notifications (Android 13+). [request]
 * asks for both, then enables the reminder only if both were granted.
 */
class NudgeOptIn(
    val request: () -> Unit,
    /** Set when the parent declined; screens show a gentle explanation. */
    val declined: MutableState<Boolean>
)

@Composable
fun rememberNudgeOptIn(viewModel: BabyCupViewModel, onResult: (Boolean) -> Unit = {}): NudgeOptIn {
    val context = LocalContext.current
    val declined = remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val ok = PhotoAccess.canReadPhotos(context) && NudgeNotifier.canPost(context)
        viewModel.updateNudgeEnabled(ok)
        declined.value = !ok
        viewModel.refreshHomeStatus()
        onResult(ok)
    }
    return remember(launcher) {
        NudgeOptIn(
            request = {
                val permissions = buildList {
                    addAll(PhotoAccess.requestPermissions)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
                launcher.launch(permissions.toTypedArray())
            },
            declined = declined
        )
    }
}
