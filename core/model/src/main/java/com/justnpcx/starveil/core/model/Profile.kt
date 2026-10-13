package com.justnpcx.starveil.core.model

/**
 * A single proxy node. Deliberately minimal for the skeleton: the real
 * model grows by adding per-protocol settings objects, never by widening
 * this class into a god-table.
 */
data class Profile(
    val id: Long = 0L,
    val name: String,
    val type: ProfileType,
    val server: String,
    val port: Int,
    /** Last measured latency in ms; -1 = untested. */
    val latencyMs: Long = -1L,
    /** Belongs to this group (by group id). */
    val groupId: Long = 0L,
)

enum class ProfileType {
    VLESS,
    VMESS,
    TROJAN,
    SHADOWSOCKS,
    HYSTERIA2,
    TUIC,
    WIREGUARD,
    /** Placeholder for future protocols. Adding a type must not touch UI code. */
    OTHER,
}
