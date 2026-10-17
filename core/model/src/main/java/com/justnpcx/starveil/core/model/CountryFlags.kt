package com.justnpcx.starveil.core.model

/**
 * Flags from ISO codes.
 *
 * A regional-indicator flag is just the two letters shifted into the emoji
 * block, so there is nothing to maintain: every code that renders has a flag,
 * and no table can drift from the codes it claims to cover. A device without
 * flag glyphs shows the two letters, which is still readable.
 */
object CountryFlags {

    fun flag(iso: String?): String? {
        val code = iso?.trim()?.uppercase() ?: return null
        if (code.length != 2 || !code.all { it in 'A'..'Z' }) return null
        // A code point, not a Char: the flags live above the BMP, so `Char`
        // arithmetic would wrap into the private-use area and render as two
        // boxes.
        return buildString {
            code.forEach { letter -> appendCodePoint(REGIONAL_INDICATOR_A + (letter - 'A')) }
        }
    }

    private const val REGIONAL_INDICATOR_A = 0x1F1E6
}

/**
 * The name as the UI shows it: `🇺🇸 HY2+Reality`, or the plain name when the
 * country is unknown. The stored name never changes — the flag is a rendering
 * detail, so exports and backups stay exactly what the provider wrote.
 */
fun Profile.displayName(): String = CountryFlags.flag(country)?.let { "$it $name" } ?: name
