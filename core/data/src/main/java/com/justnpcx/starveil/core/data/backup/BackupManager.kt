package com.justnpcx.starveil.core.data.backup

import com.justnpcx.starveil.core.data.entity.SettingsJson
import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.data.repository.RuleRepository
import com.justnpcx.starveil.core.model.GroupMode
import com.justnpcx.starveil.core.model.NodeRegion
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.ProxyGroup
import com.justnpcx.starveil.core.model.Rule
import com.justnpcx.starveil.core.model.RuleAction
import com.justnpcx.starveil.core.model.RuleMatcher
import com.justnpcx.starveil.core.model.json.Json
import com.justnpcx.starveil.core.model.json.JsonException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/** Everything a backup carries: nodes (with credentials), groups and rules. */
data class BackupSnapshot(
    val profiles: List<Profile>,
    val groups: List<ProxyGroup>,
    val rules: List<Rule>,
)

/**
 * Encrypted backup export/import (PRD §2.3, V10).
 *
 * Export reads the current nodes/groups/rules, serialises them to JSON (with
 * per-protocol credentials preserved) and seals the JSON with
 * [BackupCrypto] (scrypt + AES-256-GCM). Import reverses it and restores by
 * upsert, so ids are preserved and the round-trip is lossless.
 *
 * Nothing here logs the snapshot or the passphrase.
 */
@Singleton
class BackupManager @Inject constructor(
    private val profiles: ProfileRepository,
    private val groups: GroupRepository,
    private val rules: RuleRepository,
    private val transaction: RestoreTransaction,
) {

    suspend fun export(passphrase: CharArray): ByteArray {
        val snapshot = BackupSnapshot(
            profiles = profiles.observeAll().first(),
            groups = groups.observeAll().first(),
            rules = rules.observeAllOrdered().first(),
        )
        val json = BackupSerializer.encode(snapshot)
        return BackupCrypto.encrypt(json.toByteArray(Charsets.UTF_8), passphrase)
    }

    /**
     * Restore a backup.
     *
     * Two ways this can fail, and both leave the database untouched: a wrong
     * passphrase or a corrupt file throws [BackupCryptoException] before the
     * first write, and a write that fails part-way rolls back with the
     * transaction. Without the transaction the second case left groups and
     * rules restored and profiles missing — half a backup, silently.
     */
    suspend fun restore(blob: ByteArray, passphrase: CharArray): RestoreResult {
        val json = BackupCrypto.decrypt(blob, passphrase)
        val snapshot = BackupSerializer.decode(String(json, Charsets.UTF_8))
        transaction.run {
            snapshot.groups.forEach { groups.upsert(it) }
            snapshot.rules.forEach { rules.upsert(it) }
            snapshot.profiles.forEach { profiles.upsert(it) }
        }
        return RestoreResult(snapshot.profiles.size, snapshot.groups.size, snapshot.rules.size)
    }
}

data class RestoreResult(val profiles: Int, val groups: Int, val rules: Int)

/**
 * Backup JSON (versioned). Credentials live in the per-protocol `settings`
 * blob, encoded by [SettingsJson] — the same codec the database uses, so a
 * restored node is byte-identical to the original.
 */
internal object BackupSerializer {

    const val VERSION = 1

    fun encode(snapshot: BackupSnapshot): String = Json.write(
        linkedMapOf(
            "version" to VERSION,
            "profiles" to snapshot.profiles.map { p ->
                linkedMapOf<String, Any?>(
                    "id" to p.id,
                    "name" to p.name,
                    "type" to p.type.name,
                    "server" to p.server,
                    "port" to p.port,
                    "latencyMs" to p.latencyMs,
                    "groupId" to p.groupId,
                    "settings" to SettingsJson.encode(p.settings),
                )
            },
            "groups" to snapshot.groups.map { g ->
                linkedMapOf<String, Any?>(
                    "id" to g.id,
                    "name" to g.name,
                    "mode" to g.mode.name,
                    "subscriptionUrl" to g.subscriptionUrl,
                    // A region group owns no profiles; without this marker the
                    // restore would produce an ordinary empty group and the
                    // membership would be gone for good.
                    "region" to g.region?.name,
                )
            },
            "rules" to snapshot.rules.map { r ->
                linkedMapOf<String, Any?>(
                    "id" to r.id,
                    "matcher" to r.matcher.encode(),
                    "action" to r.action.name,
                    "order" to r.order,
                    "enabled" to r.enabled,
                )
            },
        ),
    )

    fun decode(text: String): BackupSnapshot {
        val root = try {
            Json.parseObject(text)
        } catch (e: JsonException) {
            throw BackupCryptoException("backup payload is not valid JSON")
        }
        val version = (root["version"] as? Long)?.toInt() ?: 0
        if (version != VERSION) throw BackupCryptoException("unsupported backup version $version")

        val profiles = root.list("profiles").map { row ->
            val type = ProfileType.valueOf(row.str("type") ?: ProfileType.OTHER.name)
            Profile(
                id = row.long("id") ?: 0L,
                name = row.str("name").orEmpty(),
                type = type,
                server = row.str("server").orEmpty(),
                port = (row.long("port") ?: 0L).toInt(),
                latencyMs = row.long("latencyMs") ?: -1L,
                groupId = row.long("groupId") ?: 0L,
                settings = SettingsJson.decode(row.str("settings") ?: "{}", type),
            )
        }
        val groups = root.list("groups").map { row ->
            ProxyGroup(
                id = row.long("id") ?: 0L,
                name = row.str("name").orEmpty(),
                mode = runCatching { GroupMode.valueOf(row.str("mode") ?: "MANUAL") }.getOrDefault(GroupMode.MANUAL),
                subscriptionUrl = row.str("subscriptionUrl"),
                region = row.str("region")?.let {
                    runCatching { NodeRegion.valueOf(it) }.getOrNull()
                },
            )
        }
        val rules = root.list("rules").map { row ->
            Rule(
                id = row.long("id") ?: 0L,
                matcher = RuleMatcher.decode(row.str("matcher").orEmpty()),
                action = runCatching { RuleAction.valueOf(row.str("action") ?: "PROXY") }.getOrDefault(RuleAction.PROXY),
                order = (row.long("order") ?: 0L).toInt(),
                enabled = row.bool("enabled") ?: true,
            )
        }
        return BackupSnapshot(profiles, groups, rules)
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.list(key: String): List<Map<String, Any?>> =
        (this[key] as? List<*>)?.mapNotNull { it as? Map<String, Any?> } ?: emptyList()

    private fun Map<String, Any?>.str(key: String): String? = this[key] as? String

    private fun Map<String, Any?>.long(key: String): Long? = (this[key] as? Number)?.toLong()

    private fun Map<String, Any?>.bool(key: String): Boolean? = this[key] as? Boolean
}
