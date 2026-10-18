package com.justnpcx.starveil.core.data.ruleset

import com.justnpcx.starveil.core.model.RulePack
import com.justnpcx.starveil.core.model.RulePackAction
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The market's download path.
 *
 * Same rule as the mainland preset — a file is trusted only when it hashes to
 * the pin — plus one thing that is specific to packs: one bad pack must not
 * take the other subscriptions with it, and the failure has to name which one.
 */
class RulePackStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun pack(id: String, body: String, action: RulePackAction = RulePackAction.REJECT) = RulePack(
        id = id,
        name = id,
        action = action,
        fileName = "$id.srs",
        sha256 = FileMainlandRuleSetStore.sha256(body.toByteArray()),
        urls = listOf("https://cdn.test/$id.srs"),
    )

    private val adsBody = "ads-pack-fixture"
    private val devBody = "dev-pack-fixture"

    private fun store(
        packs: List<RulePack>,
        download: suspend (String) -> ByteArray,
    ) = FileRulePackStore(
        directory = temp.root,
        download = download,
        warn = {},
        packs = packs,
    )

    @Test
    fun `a subscribed pack is downloaded once and reused`() = runBlocking {
        val fetched = mutableListOf<String>()
        val store = store(listOf(pack("ads", adsBody))) { url ->
            fetched += url
            adsBody.toByteArray()
        }

        val first = store.ensure(setOf("ads"))
        assertEquals(1, first.size)
        assertEquals("ads", first.single().pack.id)
        assertEquals(1, fetched.size)

        fetched.clear()
        val second = store.ensure(setOf("ads"))
        assertEquals(first, second)
        assertTrue("no refetch when the file is present", fetched.isEmpty())
        assertNull(store.failure.value)
    }

    @Test
    fun `only subscribed packs are fetched`() = runBlocking {
        val fetched = mutableListOf<String>()
        val store = store(listOf(pack("ads", adsBody), pack("dev", devBody))) { url ->
            fetched += url
            if (url.endsWith("ads.srs")) adsBody.toByteArray() else devBody.toByteArray()
        }

        val installed = store.ensure(setOf("dev"))

        assertEquals(listOf("dev"), installed.map { it.pack.id })
        assertEquals(1, fetched.size)
    }

    @Test
    fun `a tampered pack is rejected and never written`() = runBlocking {
        val store = store(listOf(pack("ads", adsBody))) { "tampered".toByteArray() }

        assertTrue(store.ensure(setOf("ads")).isEmpty())
        assertFalse(File(temp.root, "ads.srs").exists())
        assertEquals(RuleSetFailure.Reason.HASH_MISMATCH, store.failure.value?.reason)
        assertEquals("ads", store.failure.value?.id)
    }

    @Test
    fun `one bad pack does not take the others down`() = runBlocking {
        val store = store(listOf(pack("ads", adsBody), pack("dev", devBody))) { url ->
            if (url.endsWith("ads.srs")) "tampered".toByteArray() else devBody.toByteArray()
        }

        val installed = store.ensure(setOf("ads", "dev"))

        assertEquals(listOf("dev"), installed.map { it.pack.id })
        assertEquals("ads", store.failure.value?.id)
    }

    @Test
    fun `an id this build does not know is ignored`() = runBlocking {
        val store = store(listOf(pack("ads", adsBody))) { adsBody.toByteArray() }

        // A subscription written by a newer build must not fail the connect.
        assertTrue(store.ensure(setOf("from-the-future")).isEmpty())
        assertNull(store.failure.value)
    }

    @Test
    fun `every mirror failing is a null pack, not a throw`() = runBlocking {
        val store = store(listOf(pack("ads", adsBody))) { throw IOException("offline") }

        assertTrue(store.ensure(setOf("ads")).isEmpty())
        assertEquals(RuleSetFailure.Reason.DOWNLOAD_FAILED, store.failure.value?.reason)
    }

    @Test
    fun `a file on disk with the wrong hash is replaced`() = runBlocking {
        temp.root.mkdirs()
        File(temp.root, "ads.srs").writeBytes("something else".toByteArray())
        val store = store(listOf(pack("ads", adsBody))) { adsBody.toByteArray() }

        val installed = store.ensure(setOf("ads"))

        assertEquals(adsBody, File(installed.single().path).readText())
    }
}
