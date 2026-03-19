package com.example.mempass

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NoteFlowTest {

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
    fun testCreateAndLockNote() {
        composeTestRule.onNodeWithText("Notes").performClick()
        composeTestRule.onNodeWithContentDescription("Add", ignoreCase = true).performClick()

        composeTestRule.onNodeWithText("Untitled").performTextInput("My Secret Note")
        composeTestRule.onNodeWithText("Start typing your secrets here...").performTextInput("This is a very private message.")
        
        composeTestRule.onNodeWithText("Lock").performClick()
        composeTestRule.onNodeWithText("Save").performClick()

        composeTestRule.onNodeWithText("My Secret Note").assertExists()
    }
    
    @Test
    fun testNoteCategoryAndTags() {
        composeTestRule.onNodeWithText("Notes").performClick()
        composeTestRule.onNodeWithContentDescription("Add", ignoreCase = true).performClick()
        
        composeTestRule.onNodeWithText("Untitled").performTextInput("Work Idea")
        
        // Change Category
        composeTestRule.onNodeWithText("General").performClick()
        composeTestRule.onNodeWithText("Work").performClick()
        
        // Add Tag
        composeTestRule.onNodeWithText("Tags (comma separated)").performTextInput("urgent,project-x")
        
        composeTestRule.onNodeWithText("Save").performClick()
        composeTestRule.onNodeWithText("#urgent").assertExists()
    }
}
