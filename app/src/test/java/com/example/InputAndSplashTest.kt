package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.data.localization.Localization
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.NearKartTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InputAndSplashTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  @Test
  fun authScreen_emailAndPasswordInput_andVisibilityToggleWork() {
    composeTestRule.setContent {
      NearKartTheme {
        AuthScreen(
          role = "buyer",
          lang = Localization.EN,
          isLoading = false,
          onLogin = { _, _, _ -> },
          onSignUp = { _, _, _ -> },
          onClose = {}
        )
      }
    }

    composeTestRule.onNodeWithTag("auth_email_input")
      .assertIsDisplayed()
      .performTextInput("buyer@example.com")
    composeTestRule.onNodeWithTag("auth_email_input")
      .assertTextContains("buyer@example.com")

    composeTestRule.onNodeWithTag("auth_password_input")
      .assertIsDisplayed()
      .performTextInput("secret123")

    // Toggle password visibility on and off
    composeTestRule.onNodeWithContentDescription("Show password").performClick()
    composeTestRule.onNodeWithTag("auth_password_input")
      .assertTextContains("secret123")
    composeTestRule.onNodeWithContentDescription("Hide password").performClick()
  }

  @Test
  fun splashScreen_rendersCustomSplashScreen() {
    composeTestRule.setContent {
      NearKartTheme {
        SplashScreen(onSplashFinished = {})
      }
    }

    composeTestRule.onNodeWithTag("splash_screen").assertIsDisplayed()
  }
}
