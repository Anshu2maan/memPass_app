package com.example.mempass

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        // Handle initial setup/unlock
        try {
            // Wait for any screen to load
            composeTestRule.waitForIdle()
            
            // Check if we are on Setup Screen
            val isSetup = composeTestRule.onAllNodesWithText("Create Vault").fetchSemanticsNodes().isNotEmpty()
            if (isSetup) {
                TestUtils.setupAppFirstTime(composeTestRule)
            } else {
                val isUnlock = composeTestRule.onAllNodesWithText("Welcome Back").fetchSemanticsNodes().isNotEmpty()
                if (isUnlock) {
                    TestUtils.unlockApp(composeTestRule)
                }
            }
        } catch (e: Exception) {
            // Might already be on dashboard or screen hasn't loaded
        }
    }

    @Test
    fun testDashboardNavigation() {
        composeTestRule.onNodeWithText("Passwords").assertIsDisplayed().performClick()
        // The title in PasswordListScreen is "Passwords" but we can check for a unique element
        composeTestRule.onNodeWithText("Search accounts...").assertIsDisplayed()
    }

    @Test
    fun testSettingsNavigation() {
        composeTestRule.onNodeWithText("Settings").assertIsDisplayed().performClick()
        // Check for specific text in settings
        composeTestRule.onNodeWithText("Security & Privacy", substring = true).assertIsDisplayed()
    }
}
