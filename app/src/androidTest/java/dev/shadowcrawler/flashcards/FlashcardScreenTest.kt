package dev.shadowcrawler.flashcards

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.shadowcrawler.flashcards.model.CardType
import dev.shadowcrawler.flashcards.model.Flashcard
import dev.shadowcrawler.flashcards.ui.SettingsViewModel
import dev.shadowcrawler.flashcards.ui.SettingsViewModelFactory
import org.junit.Rule
import org.junit.Test

/**
 * Covers the swipe-driven flashcard flow: tap the card to reveal the answer, then swipe to
 * advance. Written to exercise this without relying on manual on-device taps.
 *
 * Scoring is currently disabled (SCORING_ENABLED = false in MainActivity.kt), so this test
 * doesn't assert on it — re-add those checks alongside re-enabling the flag.
 */
class FlashcardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val qaCard = Flashcard(
        id = 1,
        deckId = 1,
        type = CardType.QA,
        question = "What is 2+2?",
        answer = "Four",
        difficulty = 7
    )

    @Test
    fun tapRevealsAnswer_thenSwipeAdvances() {
        composeTestRule.setContent {
            val settingsViewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(LocalContext.current)
            )
            FlashcardScreen(
                flashcards = listOf(qaCard),
                settingsViewModel = settingsViewModel,
                onBack = {}
            )
        }

        composeTestRule.onNodeWithText("1 of 1").assertExists()
        composeTestRule.onNodeWithText("What is 2+2?").assertExists()
        composeTestRule.onNodeWithText("Tap the card to reveal the answer").assertExists()

        // Swiping before answering should not advance.
        composeTestRule.onNodeWithTag("flashcard").performTouchInput { swipeLeft() }
        composeTestRule.onNodeWithText("What is 2+2?").assertExists()

        // Tap flips the card (no "Show Answer" button involved).
        composeTestRule.onNodeWithTag("flashcard").performTouchInput { click() }
        composeTestRule.onNodeWithText("Four").assertExists()
        composeTestRule.onNodeWithText("Swipe to continue →").assertExists()

        // Swiping now advances. A single-card deck reshuffles back to itself, so the
        // question reappears unrevealed.
        composeTestRule.onNodeWithTag("flashcard").performTouchInput { swipeLeft() }
        composeTestRule.onNodeWithText("What is 2+2?").assertExists()
        composeTestRule.onNodeWithText("Tap the card to reveal the answer").assertExists()
    }
}
