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
    /**
     * ISO-3166 alpha-2 of the node's address, resolved once from the GeoIP
     * database. Null until resolved, or when the address could not be looked
     * up — the node still works, it just has no flag.
     */
    val country: String? = null,
    /**
     * Per-protocol settings (UUID, password, TLS/REALITY, transport, mux).
     * Null when the profile came from a source that carried no settings, or
     * for a protocol whose settings are not modelled yet (see the stub
     * descriptors in ProtocolRegistry).
     */
    val settings: ProfileSettings? = null,
    /**
     * Pinned by the user: shown first in the list, and the only thing the
     * "Favorites" filter shows. Storage, not a score — the picker ignores it.
     */
    val favorite: Boolean = false,
    /**
     * The node this one dials through, as [nodeKey] of that node.
     *
     * A key rather than a row id because a subscription refresh deletes and
     * re-inserts its group's rows, so ids do not survive an update. Null means
     * a single hop, which is what every node was before this field existed.
     */
    val frontNodeKey: String? = null,
) {
    /**
     * What makes two entries the same node.
     *
     * The same tuple the importer already dedupes on: protocol, address, port
     * and name. It survives a refresh as long as the provider keeps publishing
     * the same entry — and when it does not, the reference is treated as
     * dangling rather than silently pointing at a different node.
     */
    val nodeKey: String get() = type.name + "|" + server + "|" + port + "|" + name
}

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
