package com.justnpcx.starveil.core.data.geo

/**
 * Where a node's server is, by ISO code, or null when it cannot be told.
 *
 * An interface so the node list can be tested without a database, a DNS
 * resolver or an 11 MB asset.
 */
interface CountryResolver {
    suspend fun countryOf(host: String): String?
}
