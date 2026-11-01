package com.justnpcx.starveil.core.data.ruleset

import com.justnpcx.starveil.core.model.ruleset.Srs
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Installing a generation of rule sets: what passes, what is refused, and what
 * survives a refusal.
 *
 * The fixtures are real .srs bytes compiled by the sing-box binary — 100
 * domains and 20. A hand-built byte string would test the test.
 */
class RuleSetInstallerTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val warnings = mutableListOf<String>()

    private fun many(): ByteArray = javaClass.getResourceAsStream("/domain-many.srs")!!.readBytes()

    private fun few(): ByteArray = javaClass.getResourceAsStream("/domain-few.srs")!!.readBytes()

    private fun installer() = RuleSetInstaller(temp.root, warnings::add)

    private fun version() = RuleSetVersion("a".repeat(40), "2026-11-01T00:00:00Z")

    @Test
    fun `a whole batch is installed and the manifest records it`() {
        val outcome = installer().install(
            version(),
            mapOf("geosite-cn.srs" to many(), "geoip-cn.srs" to many()),
        )

        assertTrue(outcome is RuleSetInstaller.Outcome.Installed)
        assertEquals(1, Srs.read(File(temp.root, "geosite-cn.srs").readBytes()).rules.size)
        val manifest = installer().readManifest()
        assertNotNull(manifest)
        assertEquals("2026-11-01", manifest!!.version.date)
        assertEquals(2, manifest.hashes.size)
    }

    @Test
    fun `a set that parses to nothing refuses the whole batch`() {
        File(temp.root, "geosite-cn.srs").writeBytes(many())
        val outcome = installer().install(
            version(),
            mapOf("geosite-cn.srs" to many(), "geoip-cn.srs" to ByteArray(0)),
        )

        assertTrue(outcome is RuleSetInstaller.Outcome.Rejected)
        // Nothing was touched: the file in use is still the old one, and no
        // manifest claims a generation that was never installed.
        assertEquals(1, Srs.read(File(temp.root, "geosite-cn.srs").readBytes()).rules.size)
        assertNull(installer().readManifest())
    }

    @Test
    fun `a set that shrank below half refuses the batch`() {
        File(temp.root, "geosite-cn.srs").writeBytes(many())

        val outcome = installer().install(version(), mapOf("geosite-cn.srs" to few()))

        assertTrue(outcome is RuleSetInstaller.Outcome.Rejected)
        assertEquals(1, Srs.read(File(temp.root, "geosite-cn.srs").readBytes()).rules.size)
    }

    @Test
    fun `a set that grew is accepted and the previous generation is kept`() {
        File(temp.root, "geosite-cn.srs").writeBytes(few())

        val outcome = installer().install(version(), mapOf("geosite-cn.srs" to many()))

        assertTrue(outcome is RuleSetInstaller.Outcome.Installed)
        assertEquals(1, Srs.read(File(temp.root, "geosite-cn.srs").readBytes()).rules.size)
        assertEquals(1, Srs.read(File(temp.root, "geosite-cn.srs.prev").readBytes()).rules.size)
    }

    @Test
    fun `a rollback restores the previous generation`() {
        File(temp.root, "geosite-cn.srs").writeBytes(few())
        installer().install(version(), mapOf("geosite-cn.srs" to many()))
        // Corrupt what is in use, the way a bad write or a wiped file would.
        File(temp.root, "geosite-cn.srs").writeBytes(ByteArray(0))

        val restored = installer().rollback("geosite-cn.srs")

        assertNotNull(restored)
        assertEquals(1, Srs.read(File(temp.root, "geosite-cn.srs").readBytes()).rules.size)
    }
}
