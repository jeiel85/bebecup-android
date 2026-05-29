package com.bebecup.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * On-device integration smoke test: launches the real [MainActivity] (real
 * Compose runtime + Room database) and drives the AI-first entry flow. Catches
 * launch/navigation crashes that Robolectric unit tests can miss.
 */
@RunWith(AndroidJUnit4::class)
class DashboardNavigationTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun dashboard_launches_and_navigates_to_ai_scan_setup() {
        // Dashboard renders with the primary AI curation CTA.
        composeRule.onNodeWithTag("main_scaffold").assertIsDisplayed()
        composeRule.onNodeWithTag("goto_ai_curation_button").assertIsDisplayed()

        // Tapping it routes to the AI scan setup screen.
        composeRule.onNodeWithTag("goto_ai_curation_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("ai_scan_setup_view").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("ai_scan_setup_view").assertIsDisplayed()
        composeRule.onNodeWithTag("start_ai_curation_btn").assertIsDisplayed()
    }

    @Test
    fun dashboard_navigates_to_settings() {
        composeRule.onNodeWithTag("goto_settings_button").performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("settings_view").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("settings_view").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_delete_data_btn").assertIsDisplayed()
    }
}
