package com.justnpcx.starveil.benchmark

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.repository.NodeHealthRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.WEIGHT_FULL
import androidx.room.withTransaction
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import kotlin.random.Random

/**
 * Seeds the list a scroll benchmark needs — benchmark variant only.
 *
 * The task was blocked for weeks on "we have no big subscription to test with".
 * Waiting for one is the wrong dependency: what the list costs to draw is a
 * function of row count and row shape, and both can be synthesised. The data is
 * shaped like a real subscription — flag, region name, protocol, host, port,
 * a latency history with the odd timeout — because a list of "node 1..1000"
 * would measure a list nobody has.
 *
 * Invoked from the benchmark with:
 * `am broadcast -n com.justnpcx.starveil/.benchmark.SeedReceiver --ei count 1000`
 * and it blocks until the rows are written, so the benchmark can start measuring
 * the moment the command returns.
 */
class SeedReceiver : BroadcastReceiver() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SeedEntryPoint {
        fun profiles(): ProfileRepository
        fun health(): NodeHealthRepository
        fun preferences(): PreferencesRepository

        /**
         * The database itself, so the whole fixture can be written in one
         * transaction.
         *
         * Without it the seed was 7000 separate transactions: on a phone that
         * outlived the broadcast's budget, the process was killed mid-write,
         * and the benchmark sat on the welcome screen waiting for rows that
         * never arrived.
         */
        fun database(): com.justnpcx.starveil.core.data.StarveilDatabase
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEED) return
        val count = intent.getIntExtra(EXTRA_COUNT, DEFAULT_COUNT).coerceIn(1, MAX_COUNT)
        val app = context.applicationContext
        val entry = EntryPointAccessors.fromApplication(app, SeedEntryPoint::class.java)
        val profiles = entry.profiles()
        val health = entry.health()
        val now = System.currentTimeMillis()

        runBlocking {
            // Onboarding is part of the fixture: a freshly installed benchmark
            // build opens on the welcome screen, where a tap on the tab bar
            // does nothing and the measured scroll moves no frames at all.
            entry.preferences().setOnboardingDone(true)
            // One transaction for the lot. A thousand nodes and six thousand
            // samples is a few seconds of work when it is one commit, and more
            // than a broadcast's budget when it is seven thousand.
            entry.database().withTransaction {
            repeat(count) { index ->
                val region = REGIONS[index % REGIONS.size]
                // The flag comes from country, not from the name: prefixing it
                // here as well drew two flags on every row.
                val profile = Profile(
                    name = region.code + "-" +
                        (index + 1).toString().padStart(4, '0') + " · " + region.label,
                    type = ProfileType.VLESS,
                    server = region.host + (index % 40 + 1) + ".example.com",
                    port = if (index % 3 == 0) 443 else 2053,
                    country = region.iso,
                )
                val id = profiles.upsert(profile)
                // Six probes: enough for the row to show a reading rather than
                // "untested", which is what a real list looks like. One in
                // twenty is a timeout, so the loss path is drawn too.
                repeat(SAMPLES_PER_NODE) { sample ->
                    val at = now - (SAMPLES_PER_NODE - sample) * 30_000L
                    // One node in twenty-five ends on a timeout, so the loss
                    // path is drawn — but only on the last probe, which is the
                    // one the row reads. A fixture where everything times out
                    // measures a row nobody has.
                    val timeout = index % 25 == 0 && sample == SAMPLES_PER_NODE - 1
                    val latency = if (timeout) {
                        -1L
                    } else {
                        (40 + Random(index * 31 + sample).nextInt(280)).toLong()
                    }
                    health.record(
                        profileId = id,
                        latencyMs = latency,
                        atMs = at,
                        weightTenths = WEIGHT_FULL,
                    )
                }
            }
            }
            // A marker the benchmark can read from the shell: "the broadcast
            // returned" is not the same as "the rows are on disk", and the two
            // look identical from a failing test.
            val marker = java.io.File(app.getExternalFilesDir(null), "seed.done")
            marker.parentFile?.mkdirs()
            marker.writeText(count.toString())
        }
    }

    private data class Region(val iso: String, val code: String, val label: String, val host: String)

    private companion object {
        const val ACTION_SEED = "com.justnpcx.starveil.benchmark.SEED"
        const val EXTRA_COUNT = "count"
        const val DEFAULT_COUNT = 1000
        const val MAX_COUNT = 5_000
        const val SAMPLES_PER_NODE = 6

        /**
         * Twelve regions, which is what a commercial subscription looks like —
         * enough distinct flags and labels to exercise text layout, not so many
         * that the rows stop looking alike.
         */
        val REGIONS = listOf(
            Region("hk", "HK", "香港中转", "hk"),
            Region("jp", "JP", "东京 BGP", "jp"),
            Region("sg", "SG", "新加坡", "sg"),
            Region("us", "US", "洛杉矶", "us"),
            Region("tw", "TW", "台湾 HiNet", "tw"),
            Region("kr", "KR", "首尔", "kr"),
            Region("de", "DE", "法兰克福", "de"),
            Region("gb", "GB", "伦敦", "uk"),
            Region("ca", "CA", "多伦多", "ca"),
            Region("au", "AU", "悉尼", "au"),
            Region("in", "IN", "孟买", "in"),
            Region("br", "BR", "圣保罗", "br"),
        )
    }
}
