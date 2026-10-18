package com.justnpcx.starveil.core.model

/**
 * Where the local, Clash-compatible API listens.
 *
 * Loopback only, and the address is a constant rather than a setting: a
 * dashboard has to be pointed at something, and "find the port in the app"
 * is a worse experience than "9090", which is what every Mihomo/Clash tool
 * assumes. The port is safe to fix because the listener cannot be reached from
 * off the device and every request needs the token.
 */
object LocalApi {
    const val HOST = "127.0.0.1"
    const val PORT = 9090

    val address: String get() = "$HOST:$PORT"
}

/**
 * What the kernel needs to open the API, or null when the feature is off.
 *
 * [token] is generated on the device and wrapped by the Android Keystore; it
 * is never logged and never leaves the app except as the `secret` field of a
 * config the kernel reads.
 */
data class LocalApiConfig(val token: String) {
    val controller: String get() = LocalApi.address
}
