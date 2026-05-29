package com.bebecup.app

import android.os.Bundle
import androidx.activity.ComponentActivity
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
import com.bebecup.app.ui.photo.BestShotSelectorView
import com.bebecup.app.ui.photo.PhotoManagerView
import com.bebecup.app.ui.print.PrintCartView
import com.bebecup.app.ui.print.ZzixxSuccessDialog
import com.bebecup.app.ui.print.ZzixxTransferingOverlay
import com.bebecup.app.ui.settings.SettingsScreen
import com.bebecup.app.ui.theme.MyApplicationTheme
import com.bebecup.app.ui.tournament.WorldCupPlayView
import com.bebecup.app.ui.tournament.WorldCupSetupView
import com.bebecup.app.ui.tournament.WorldCupWinnerCelebrationView
import com.bebecup.app.ui.viewmodel.BabyCupViewModel
import com.bebecup.app.ui.viewmodel.UiScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainContentScreen()
            }
        }
    }
}

@Composable
fun MainContentScreen(
    viewModel: BabyCupViewModel = viewModel()
) {
    val allPhotos by viewModel.allPhotos.collectAsState()
    val bestPhotos by viewModel.bestPhotos.collectAsState()
    val printCartPhotos by viewModel.printCartPhotos.collectAsState()
    val tournaments by viewModel.tournaments.collectAsState()

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
                    fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                },
                label = "screen_transition"
            ) { screen ->
                when (screen) {
                    is UiScreen.Dashboard -> DashboardView(
                        viewModel = viewModel,
                        allPhotos = allPhotos,
                        bestPhotos = bestPhotos,
                        printCartPhotos = printCartPhotos
                    )
                    is UiScreen.PhotoManager -> PhotoManagerView(
                        viewModel = viewModel,
                        allPhotos = allPhotos
                    )
                    is UiScreen.BestShotSelector -> BestShotSelectorView(
                        viewModel = viewModel,
                        allPhotos = allPhotos
                    )
                    is UiScreen.WorldCupSetup -> WorldCupSetupView(
                        viewModel = viewModel,
                        bestPhotos = bestPhotos,
                        allPhotos = allPhotos
                    )
                    is UiScreen.WorldCupPlay -> WorldCupPlayView(
                        viewModel = viewModel
                    )
                    is UiScreen.WorldCupWinner -> WorldCupWinnerCelebrationView(
                        viewModel = viewModel,
                        winner = screen.winner,
                        bracketSize = screen.bracketSize
                    )
                    is UiScreen.PrintCart -> PrintCartView(
                        viewModel = viewModel,
                        cartPhotos = printCartPhotos
                    )
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

            // ZZIXX order handoff overlay
            if (viewModel.isSendingToZzixx) {
                ZzixxTransferingOverlay()
            }

            // ZZIXX order handoff dialog
            if (viewModel.showZzixxSuccessDialog) {
                ZzixxSuccessDialog(
                    onDismiss = { viewModel.showZzixxSuccessDialog = false },
                    cartPhotos = printCartPhotos
                )
            }
        }
    }
}
