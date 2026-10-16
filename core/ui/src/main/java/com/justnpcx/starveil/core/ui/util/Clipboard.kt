package com.justnpcx.starveil.core.ui.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/**
 * The app's clipboard access, in one place.
 *
 * Two screens had each written their own write helper and one its own read
 * helper — three copies of the same four lines, which is how one of them ends
 * up with a different clip label or a different null check (K3).
 */

/** Puts [text] on the clipboard under the app's own label. */
fun writeClipboard(context: Context, text: String, label: String) {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return
    manager.setPrimaryClip(ClipData.newPlainText(label, text))
}

/**
 * The clipboard's text, trimmed, or null when there is nothing usable.
 *
 * Android 10+ only hands the clipboard to the focused app, so this is called
 * from an interaction (a dialog opening, a paste button) rather than at
 * startup — anywhere else it silently returns null.
 */
fun readClipboardText(context: Context): String? {
    val manager = context.getSystemService(ClipboardManager::class.java) ?: return null
    val clip = manager.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
}
