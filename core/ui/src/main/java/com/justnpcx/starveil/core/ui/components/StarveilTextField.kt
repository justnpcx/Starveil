package com.justnpcx.starveil.core.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import com.justnpcx.starveil.core.ui.theme.StarveilColors

/**
 * The one text field in this app.
 *
 * Material's defaults paint the resting border with `outline`, which this theme
 * maps to the glass border — white at 75%. That is right for a card edge, which
 * has a fill behind it, and wrong for a field, whose fill is transparent: the
 * border is the only thing saying "this is a field", and white on a light page
 * or a near-white dialog says nothing. Measured on the phone: a resting field
 * showed no border pixels at all, focused it showed a full blue outline.
 *
 * So the resting border is ink, everywhere. The theme itself is left alone
 * because every card edge depends on that white.
 *
 * Error colours are deliberately left to Material: the theme already maps them
 * to the palette's error, and a second definition here would drift.
 */
@Composable
fun StarveilTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    enabled: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    supportingText: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        label = { Text(label) },
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        supportingText = supportingText?.let { text -> { Text(text) } },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = StarveilColors.accent,
            unfocusedBorderColor = StarveilColors.ink3,
            focusedLabelColor = StarveilColors.accentText,
            unfocusedLabelColor = StarveilColors.ink2,
            cursorColor = StarveilColors.accent,
        ),
    )
}
