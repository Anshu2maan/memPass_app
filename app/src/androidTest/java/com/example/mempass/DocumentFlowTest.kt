package com.example.mempass

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        try {
            composeTestRule.waitForIdle()
            if (composeTestRule.onAllNodesWithText("Create Vault").fetchSemanticsNodes().isNotEmpty()) {
                TestUtils.setupAppFirstTime(composeTestRule)
            } else if (composeTestRule.onAllNodesWithText("Welcome Back").fetchSemanticsNodes().isNotEmpty()) {
                TestUtils.unlockApp(composeTestRule)
            }
        } catch (e: Exception) {}
    }

    @Test
    fun testAddDocument() {
        composeTestRule.onNodeWithText("Documents").performClick()
        composeTestRule.onNodeWithContentDescription("Add", ignoreCase = true).performClick()

        composeTestRule.onNodeWithText("Document Title").performTextInput("My Passport")
        
        // Change category
        composeTestRule.onNodeWithText("Official ID Proofs").performClick()
        composeTestRule.onNodeWithText("Travel Documents").performClick()
        
        composeTestRule.onNodeWithText("ID Number").performTextInput("L1234567")
        composeTestRule.onNodeWithText("Save Securely").performClick()

        composeTestRule.onNodeWithText("My Passport").assertExists()
    }
}
