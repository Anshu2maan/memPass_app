package com.example.mempass

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput

object TestUtils {
    fun unlockApp(composeTestRule: ComposeContentTestRule, pin: String = "123456") {
        composeTestRule.waitForIdle()
        // Wait for a digit to appear to ensure we're on the PIN screen
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("1").fetchSemanticsNodes().isNotEmpty()
        }
        
        pin.forEach { digit ->
            composeTestRule.onNodeWithText(digit.toString()).performClick()
        }
    }

    fun setupAppFirstTime(composeTestRule: ComposeContentTestRule, pin: String = "123456") {
        composeTestRule.waitForIdle()
        // Wait for setup screen
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("1").fetchSemanticsNodes().isNotEmpty()
        }

        pin.forEach { digit ->
            composeTestRule.onNodeWithText(digit.toString()).performClick()
        }
        
        // Wait for Recovery Key Dialog
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("I've saved it").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("I've saved it").performClick()
        
        // Handle Biometric Offer if it appears
        try {
            composeTestRule.waitUntil(2000) {
                composeTestRule.onAllNodesWithText("Skip for now").fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithText("Skip for now").performClick()
        } catch (e: Exception) {}
    }
}
