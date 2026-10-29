package com.justnpcx.starveil.core.model

/**
 * How the app chooses its light or dark palette.
 *
 * [SYSTEM] follows the device in real time; the other two pin one table. Stored
 * as a string, so a value written by a newer build degrades to [SYSTEM] rather
 * than crashing an older one.
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK;

    companion object {
        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}

/**
 * How the node in force was chosen.
 *
 * [AUTO] means the picker may move the selection (on a drop, or on an explicit
 * "pick the best node"); [MANUAL] means it must not, until the user asks for
 * auto again. Persisted, so a restart keeps the promise the segmented control
 * made.
 */
enum class NodeSelectionMode {
    AUTO,
    MANUAL;

    companion object {
        fun fromStorage(value: String?): NodeSelectionMode =
            entries.firstOrNull { it.name == value } ?: AUTO
    }
}
