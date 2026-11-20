package com.bebecup.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.bebecup.app.ui.components.UpdateBannerContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateBannerTest {

  @get:Rule val compose = createComposeRule()

  @Test
  fun `banner says a new version is out and tapping it opens the store`() {
    var opened = 0
    compose.setContent { UpdateBannerContent(onClick = { opened++ }) }

    compose.onNodeWithText("새 버전이 나왔어요").assertIsDisplayed()
    compose.onNodeWithTag("update_banner").performClick()

    assertEquals(1, opened)
  }
}
