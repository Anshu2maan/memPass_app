package com.example.mempass

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PasswordFlowTest {

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
    fun testAddAndSearchPassword() {
        composeTestRule.onNodeWithText("Passwords").performClick()
        composeTestRule.onNodeWithContentDescription("Add", ignoreCase = true).performClick()

        composeTestRule.onNodeWithText("Service Name").performTextInput("TestService")
        composeTestRule.onNodeWithText("Username / Email").performTextInput("testuser@example.com")
        composeTestRule.onNodeWithText("Password").performTextInput("SecurePass123!")
        
        composeTestRule.onNodeWithText("Save Securely").performClick()
        composeTestRule.onNodeWithText("TestService").assertExists()

        composeTestRule.onNodeWithText("Search accounts...").performTextInput("NonExistent")
        composeTestRule.onNodeWithText("TestService").assertDoesNotExist()

        composeTestRule.onNodeWithText("NonExistent").performTextReplacement("Test")
        composeTestRule.onNodeWithText("TestService").assertExists()
    }
}
