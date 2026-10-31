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
/**
 * The five accent colours the app offers.
 *
 * A short list of presets, not a colour picker: an app that lets you pick any
 * hue ends up with unreadable combinations, and a palette measured against
 * both backgrounds is worth more than the freedom to pick a bad one. A preset
 * only remaps the accent family — the brand gradient, the app icon and the
 * semantic colours never follow it.
 */
enum class AccentPreset {
    /** The default: the blue the app shipped with. */
    STAR_BLUE,
    VIOLET,
    TEAL,
    PINE,
    AMBER,
    ;

    companion object {
        fun fromStorage(value: String?): AccentPreset =
            entries.firstOrNull { it.name == value } ?: STAR_BLUE
    }
}

/**
 * Three text sizes.
 *
 * A multiplier on the type scale rather than a second scale: every token keeps
 * its shape and only the size moves, so a screen that fits at standard still
 * fits at large — and the one layout that cannot give (the home page's
 * one-screen promise) is allowed to scroll rather than to clip a word.
 */
enum class FontScale(val factor: Float) {
    STANDARD(1f),
    LARGE(1.08f),
    LARGEST(1.16f),
    ;

    companion object {
        fun fromStorage(value: String?): FontScale =
            entries.firstOrNull { it.name == value } ?: STANDARD
    }
}
