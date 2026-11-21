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
 * Turning on reminders asks for two grants: reading the gallery (to count new
 * photos) and posting notifications (Android 13+). Notifications alone are
 * enough to turn reminders on: the 이달의 월드컵 reminder reads only the app's
 * own records, and the worker skips the unsorted-photo count by itself while
 * the app lacks full gallery access.
 */
class NudgeOptIn(
    val request: () -> Unit,
    /** Set when notifications were declined; screens show a gentle explanation. */
    val declined: MutableState<Boolean>
)

@Composable
fun rememberNudgeOptIn(viewModel: BabyCupViewModel, onResult: (Boolean) -> Unit = {}): NudgeOptIn {
    val context = LocalContext.current
    val declined = remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        // "선택한 사진만 허용" (Android 14+) never exposes newly taken photos, so
        // without full access only the monthly reminder can fire; that is still
        // worth turning on. Only a refused notification permission stops it.
        val ok = NudgeNotifier.canPost(context)
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
