package com.justnpcx.starveil.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The search box's filter.
 *
 * Pure, so it is worth testing without a Context: it is the one part of the
 * per-app screen that is not just plumbing, and the two fields it matches are
 * both things people actually type.
 */
class PerAppUiStateTest {

    private val apps = listOf(
        InstalledApp(label = "Bank of Somewhere", packageName = "com.example.bank"),
        InstalledApp(label = "Chat", packageName = "com.example.chat"),
        InstalledApp(label = "地图", packageName = "com.example.maps"),
    )

    private fun visible(query: String) = PerAppUiState(apps = apps, loading = false, query = query).visible

    @Test
    fun `a blank query shows every app`() {
        assertEquals(apps, visible(""))
        assertEquals(apps, visible("   "))
    }

    @Test
    fun `the query matches the label`() {
        assertEquals(listOf("com.example.chat"), visible("chat").map { it.packageName })
    }

    @Test
    fun `the query matches the package name`() {
        assertEquals(listOf("com.example.bank"), visible("bank").map { it.packageName })
    }

    @Test
    fun `matching ignores case`() {
        assertEquals(listOf("com.example.chat"), visible("CHAT").map { it.packageName })
    }

    @Test
    fun `no match is an empty list, not everything`() {
        assertEquals(emptyList<InstalledApp>(), visible("nothing-here"))
    }
}
