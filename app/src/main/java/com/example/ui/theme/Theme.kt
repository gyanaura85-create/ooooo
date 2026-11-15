package com.example.ui.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle

private val LightColorScheme = lightColorScheme(
  primary = NearKartBlue,
  onPrimary = Color.White,
  primaryContainer = NearKartBlueBg,
  onPrimaryContainer = NearKartInk,
  secondary = NearKartGreen,
  onSecondary = Color.White,
  secondaryContainer = NearKartGreenBg,
  onSecondaryContainer = NearKartDarkGreen,
  tertiary = NearKartLime,
  onTertiary = NearKartInk,
  background = NearKartBg,
  onBackground = NearKartInk,
  surface = Color.White,
  onSurface = NearKartInputText,
  surfaceVariant = NearKartBg,
  onSurfaceVariant = NearKartMute,
  outline = NearKartLine,
  error = NearKartRed,
  onError = Color.White,
  errorContainer = NearKartRedBg,
  onErrorContainer = NearKartRed
)

val NearKartTextSelectionColors = TextSelectionColors(
  handleColor = NearKartBlue,
  backgroundColor = NearKartBlue.copy(alpha = 0.25f)
)

@Composable
fun nearKartInputTextStyle(): TextStyle = LocalTextStyle.current.copy(color = NearKartInputText)

@Composable
fun nearKartTextFieldColors(
  focusedBorderColor: Color = NearKartBlue,
  unfocusedBorderColor: Color = NearKartLine,
  containerColor: Color = NearKartInputBg
): TextFieldColors = OutlinedTextFieldDefaults.colors(
  focusedTextColor = NearKartInputText,
  unfocusedTextColor = NearKartInputText,
  disabledTextColor = NearKartInputText,
  errorTextColor = NearKartInputText,
  focusedContainerColor = containerColor,
  unfocusedContainerColor = containerColor,
  disabledContainerColor = containerColor,
  errorContainerColor = containerColor,
  cursorColor = NearKartBlue,
  errorCursorColor = NearKartRed,
  selectionColors = NearKartTextSelectionColors,
  focusedBorderColor = focusedBorderColor,
  unfocusedBorderColor = unfocusedBorderColor,
  errorBorderColor = NearKartRed,
  focusedLabelColor = NearKartBlue,
  unfocusedLabelColor = NearKartLabelMuted,
  errorLabelColor = NearKartRed,
  focusedPlaceholderColor = NearKartPlaceholder,
  unfocusedPlaceholderColor = NearKartPlaceholder,
  disabledPlaceholderColor = NearKartPlaceholder,
  focusedLeadingIconColor = NearKartMute,
  unfocusedLeadingIconColor = NearKartMute,
  focusedTrailingIconColor = NearKartInputText,
  unfocusedTrailingIconColor = NearKartLabelMuted
)

@Composable
fun NearKartTheme(
  content: @Composable () -> Unit
) {
  CompositionLocalProvider(
    LocalTextSelectionColors provides NearKartTextSelectionColors
  ) {
    MaterialTheme(
      colorScheme = LightColorScheme,
      typography = Typography,
      content = content
    )
  }
}
