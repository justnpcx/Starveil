package com.justnpcx.starveil.core.data.geo

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import org.lionsoul.ip2region.xdb.Searcher
import org.lionsoul.ip2region.xdb.Version

/**
 * IP → country, from a database that ships inside the APK.
 *
 * Offline on purpose. The alternative — asking an IP-geolocation service — would
 * hand a third party the address of every node the user connects to, which is a
 * worse trade than an 11 MB asset. It also means the answer is available when
 * the phone is offline, which is when a node list is most likely to be looked at.
 *
 * The database is `ip2region`'s v4 xdb: a compact Chinese-made range table whose
 * records end with the ISO country code. Hong Kong, Macao and Taiwan come back
 * as `CN` with the province saying which, so those three are split out here.
 */
@Singleton
class GeoIpLookup @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Loaded on first use, and kept for the process lifetime. The vector index
     * is 256 KB; the rest is read from the file per lookup, so this does not
     * hold the 11 MB table in memory.
     */
    private val searcher: Searcher? by lazy { open() }

    fun countryCode(ip: String): String? {
        val active = searcher ?: return null
        val record = runCatching { active.search(ip) }.getOrNull() ?: return null
        return countryFromRecord(record)
    }

    private fun open(): Searcher? = runCatching {
        // Assets cannot be read with a RandomAccessFile, and the searcher wants
        // one, so the database is copied out once.
        val database = File(context.filesDir, "ip2region.xdb")
        if (!database.exists() || database.length() == 0L) {
            context.assets.open(ASSET).use { input ->
                database.outputStream().use { output -> input.copyTo(output) }
            }
        }
        val vectorIndex = Searcher.loadVectorIndexFromFile(database)
        Searcher.newWithVectorIndex(Version.IPv4, database, vectorIndex)
    }.getOrNull()

    private companion object {
        const val ASSET = "ip2region.xdb"
    }
}

/**
 * The ISO code at the end of an `ip2region` record, e.g.
 * `United States|California|0|Google LLC|US`.
 *
 * Separate from the lookup so it can be tested without the database: this is
 * where the rules live, and the lookup is just IO.
 */
internal fun countryFromRecord(record: String): String? {
    val fields = record.split('|')
    val iso = fields.lastOrNull()
        ?.trim()
        ?.takeIf { it.length == 2 && it.all(Char::isLetter) }
        ?: return null
    if (iso.equals("CN", ignoreCase = true)) {
        // ip2region treats the SARs and Taiwan as China; the province field is
        // what distinguishes them, and for a proxy client they are separate
        // destinations with separate routes.
        val province = fields.getOrNull(1).orEmpty()
        return when {
            province.contains("香港") -> "HK"
            province.contains("澳门") || province.contains("澳門") -> "MO"
            province.contains("台湾") || province.contains("台灣") -> "TW"
            else -> "CN"
        }
    }
    return iso.uppercase()
}
