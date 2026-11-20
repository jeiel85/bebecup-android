package com.bebecup.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bebecup.app.ui.ai.AiCurationResultScreen
import com.bebecup.app.ui.ai.AiScanProgressScreen
import com.bebecup.app.ui.ai.AiScanSetupScreen
import com.bebecup.app.ui.dashboard.DashboardView
import com.bebecup.app.ui.history.TournamentHistoryListView
import com.bebecup.app.ui.onboarding.OnboardingScreen
import com.bebecup.app.ui.photo.BestBoxScreen
import com.bebecup.app.ui.print.PrintCartView
import com.bebecup.app.ui.settings.SettingsScreen
import com.bebecup.app.ui.theme.MyApplicationTheme
import com.bebecup.app.ui.tournament.WorldCupPlayView
import com.bebecup.app.ui.tournament.WorldCupSetupView
import com.bebecup.app.ui.tournament.WorldCupWinnerCelebrationView
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen
import com.bebecup.app.update.StoreUpdates

class MainActivity : ComponentActivity() {
    private val viewModel: BabyCupViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) viewModel.handleLaunchIntent(intent)
        if (BuildConfig.DEBUG) {
            // Debug builds only: `adb shell am start -n com.bebecup.app/.MainActivity --ei debug_fake_update 99`
            // pretends Play offers versionCode 99, to see the home update banner.
            intent.getIntExtra(EXTRA_DEBUG_FAKE_UPDATE, 0).takeIf { it > 0 }?.let(StoreUpdates::useFakeUpdate)
        }
        setContent {
            MyApplicationTheme {
                MainContentScreen(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.handleLaunchIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Photos may have been taken (or permissions changed) while away.
        viewModel.refreshHomeStatus()
    }

    private companion object {
        const val EXTRA_DEBUG_FAKE_UPDATE = "debug_fake_update"
    }
}

@Composable
fun MainContentScreen(
    viewModel: BabyCupViewModel = viewModel()
) {
    val bestPhotos by viewModel.bestPhotos.collectAsState()
    val printCartPhotos by viewModel.printCartPhotos.collectAsState()
    val tournaments by viewModel.tournaments.collectAsState()

    // Honor each navigation depth: pop in-app while there's a back target, and
    // only fall through to the OS (exiting the app) at the root screen.
    BackHandler(enabled = viewModel.canNavigateBack) {
        viewModel.navigateBack()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag("main_scaffold"),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            AnimatedContent(
                targetState = viewModel.currentScreen,
                transitionSpec = {
                    fadeIn(animationSpec = tween(250)) togetherWith fadeOut(animationSpec = tween(250))
                },
                label = "screen_transition"
            ) { screen ->
                when (screen) {
                    is UiScreen.Onboarding -> OnboardingScreen(viewModel = viewModel)
                    is UiScreen.Dashboard -> DashboardView(
                        viewModel = viewModel,
                        bestPhotos = bestPhotos,
                        printCartPhotos = printCartPhotos,
                        tournaments = tournaments
                    )
                    is UiScreen.BestBox -> BestBoxScreen(
                        viewModel = viewModel,
                        bestPhotos = bestPhotos,
                        printCartPhotos = printCartPhotos
                    )
                    is UiScreen.WorldCupSetup -> WorldCupSetupView(viewModel = viewModel, bestPhotos = bestPhotos)
                    is UiScreen.WorldCupPlay -> WorldCupPlayView(viewModel = viewModel)
                    is UiScreen.WorldCupWinner -> WorldCupWinnerCelebrationView(
                        viewModel = viewModel,
                        winner = screen.winner,
                        bracketSize = screen.bracketSize,
                        inPrintCart = printCartPhotos.any { it.id == screen.winner.id }
                    )
                    is UiScreen.PrintCart -> PrintCartView(viewModel = viewModel, cartPhotos = printCartPhotos)
                    is UiScreen.TournamentHistoryList -> TournamentHistoryListView(
                        viewModel = viewModel,
                        tournaments = tournaments
                    )
                    is UiScreen.AiScanSetup -> AiScanSetupScreen(viewModel = viewModel)
                    is UiScreen.AiScanProgress -> AiScanProgressScreen(viewModel = viewModel)
                    is UiScreen.AiCurationResult -> AiCurationResultScreen(viewModel = viewModel)
                    is UiScreen.Settings -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}
