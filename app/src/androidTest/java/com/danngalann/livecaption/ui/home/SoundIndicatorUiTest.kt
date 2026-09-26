package com.danngalann.livecaption.ui.home

import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.danngalann.livecaption.audio.SoundEvent
import com.danngalann.livecaption.ui.theme.LiveCaptionTheme
import org.junit.Rule
import org.junit.Test

class SoundIndicatorUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun detectedSoundHasProminentDedicatedAreaAboveCaptions() {
        composeRule.setContent {
            LiveCaptionTheme {
                HomeScreenContent(
                    state = HomeUiState(
                        isRecording = true,
                        soundEvent = SoundEvent("Ladridos"),
                        partialText = "Una frase en curso"
                    ),
                    onStart = {},
                    onStop = {},
                    onClear = {}
                )
            }
        }

        composeRule.onNodeWithTag("soundIndicator")
            .assertIsDisplayed()
            .assertHeightIsEqualTo(88.dp)
            .assertWidthIsAtLeast(300.dp)
        composeRule.onNodeWithText("Ladridos").assertIsDisplayed()
        composeRule.onNodeWithText("Una frase en curso").assertIsDisplayed()
    }
}
