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
 * Where the field is drawn, which decides what its resting border can be.
 *
 * The palette's [StarveilColors.glassBorder] is white at 75%: right for a card
 * edge sitting on the page, invisible for a field whose only outline is that
 * border and whose fill is transparent. Dialogs sit on `surfaceSolid`, a near
 * white, so a white border there is not a border at all — the field looks like
 * a gap until it is focused and the accent appears.
 */
enum class FieldSurface {
    /** On the page or inside a glass card. */
    Page,

    /** On a dialog's solid surface. */
    Dialog,
}

/**
 * The one text field in this app.
 *
 * Material's defaults paint the resting border with `outline`, which this theme
 * maps to the glass border; that is correct for cards and wrong for every field
 * drawn on a solid surface. Rather than override the theme (which would change
 * every card edge), the field takes its resting colour from where it sits —
 * the same split [StarveilChip] already makes for dialog chips.
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
    surface: FieldSurface = FieldSurface.Page,
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
            unfocusedBorderColor = when (surface) {
                FieldSurface.Dialog -> StarveilColors.ink3
                FieldSurface.Page -> StarveilColors.glassBorder
            },
            focusedLabelColor = StarveilColors.accentText,
            unfocusedLabelColor = StarveilColors.ink2,
            cursorColor = StarveilColors.accent,
        ),
    )
}
