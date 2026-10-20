package com.justnpcx.starveil.core.data.backup

import com.justnpcx.starveil.core.data.repository.GroupRepository
import com.justnpcx.starveil.core.data.repository.ProfileRepository
import com.justnpcx.starveil.core.data.repository.RuleRepository
import com.justnpcx.starveil.core.model.GroupMode
import com.justnpcx.starveil.core.model.MatcherKind
import com.justnpcx.starveil.core.model.NodeRegion
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.ProxyGroup
import com.justnpcx.starveil.core.model.RealityOptions
import com.justnpcx.starveil.core.model.Rule
import com.justnpcx.starveil.core.model.RuleAction
import com.justnpcx.starveil.core.model.RuleMatcher
import com.justnpcx.starveil.core.model.SecurityMode
import com.justnpcx.starveil.core.model.TlsOptions
import com.justnpcx.starveil.core.model.VlessSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pass-through transaction for the cases that are not about atomicity. */
private object NoTransaction : RestoreTransaction {
    override suspend fun <T> run(block: suspend () -> T): T = block()
}

/** Snapshots the in-memory stores so a throw rolls them back, like Room does. */
private class MemTransaction(
    private val profiles: MemProfiles,
    private val groups: MemGroups,
    private val rules: MemRules,
) : RestoreTransaction {
    override suspend fun <T> run(block: suspend () -> T): T {
        val beforeProfiles = LinkedHashMap(profiles.rows)
        val beforeGroups = LinkedHashMap(groups.rows)
        val beforeRules = LinkedHashMap(rules.rows)
        return try {
            block()
        } catch (e: Throwable) {
            profiles.rows.clear()
            profiles.rows.putAll(beforeProfiles)
            groups.rows.clear()
            groups.rows.putAll(beforeGroups)
            rules.rows.clear()
            rules.rows.putAll(beforeRules)
            throw e
        }
    }
}

/** Fails the nth upsert, the way a disk error would arrive mid-restore. */
private class FailingProfiles(
    private val delegate: ProfileRepository,
    private val failOnCall: Int,
) : ProfileRepository by delegate {
    private var calls = 0

    override suspend fun upsert(profile: Profile): Long {
        calls++
        if (calls >= failOnCall) error("disk full")
        return delegate.upsert(profile)
    }
}

private class MemProfiles : ProfileRepository {
    val rows = LinkedHashMap<Long, Profile>()
    override fun observeAll(): Flow<List<Profile>> = flow { emit(rows.values.toList()) }
    override fun observeByGroup(groupId: Long): Flow<List<Profile>> = flow { emit(rows.values.filter { it.groupId == groupId }) }
    override suspend fun getById(id: Long): Profile? = rows[id]
    override suspend fun upsert(profile: Profile): Long {
        val id = if (profile.id == 0L) (rows.keys.maxOrNull() ?: 0L) + 1 else profile.id
        rows[id] = profile.copy(id = id)
        return id
    }

    override suspend fun deleteById(id: Long) {
        rows.remove(id)
    }

    override suspend fun deleteByGroup(groupId: Long) {
        rows.values.removeAll { it.groupId == groupId }
    }

    override suspend fun replaceGroupProfiles(groupId: Long, profiles: List<Profile>) {
        rows.values.removeAll { it.groupId == groupId }
        profiles.forEach { upsert(it.copy(groupId = groupId)) }
    }
}

private class MemGroups : GroupRepository {
    val rows = LinkedHashMap<Long, ProxyGroup>()
    override fun observeAll(): Flow<List<ProxyGroup>> = flow { emit(rows.values.toList()) }
    override suspend fun getById(id: Long): ProxyGroup? = rows[id]
    override suspend fun findBySubscriptionUrl(url: String): ProxyGroup? =
        rows.values.firstOrNull { it.subscriptionUrl == url }

    override suspend fun findByRegion(region: NodeRegion): ProxyGroup? =
        rows.values.firstOrNull { it.region == region }
    override suspend fun upsert(group: ProxyGroup): Long {
        val id = if (group.id == 0L) (rows.keys.maxOrNull() ?: 0L) + 1 else group.id
        rows[id] = group.copy(id = id)
        return id
    }

    override suspend fun deleteById(id: Long) {
        rows.remove(id)
    }

    override suspend fun deleteWithProfiles(id: Long, deleteProfiles: Boolean) {
        rows.remove(id)
    }
}

private class MemRules : RuleRepository {
    val rows = LinkedHashMap<Long, Rule>()
    override fun observeAllOrdered(): Flow<List<Rule>> = flow { emit(rows.values.sortedBy { it.order }) }
    override suspend fun getById(id: Long): Rule? = rows[id]
    override suspend fun upsert(rule: Rule): Long {
        val id = if (rule.id == 0L) (rows.keys.maxOrNull() ?: 0L) + 1 else rule.id
        rows[id] = rule.copy(id = id)
        return id
    }

    override suspend fun deleteById(id: Long) {
        rows.remove(id)
    }
}

class BackupManagerTest {

    private val passphrase = "backup-pass".toCharArray()

    private val node = Profile(
        id = 1L,
        name = "reality-node",
        type = ProfileType.VLESS,
        server = "r.example.com",
        port = 443,
        groupId = 5L,
        settings = VlessSettings(
            uuid = "11111111-2222-3333-4444-555555555555",
            tls = TlsOptions(
                security = SecurityMode.REALITY,
                sni = "r.example.com",
                reality = RealityOptions(publicKey = "PBK", shortId = "ab12", mlKem = true),
            ),
        ),
    )

    @Test
    fun `export then restore brings back nodes, rules and settings with credentials intact`() = runBlocking {
        val srcProfiles = MemProfiles().apply { upsert(node) }
        val srcGroups = MemGroups().apply {
            upsert(ProxyGroup(id = 5L, name = "sub", mode = GroupMode.URL_TEST, subscriptionUrl = "https://sub/x?token=t"))
        }
        val srcRules = MemRules().apply { upsert(Rule(id = 9L, matcher = RuleMatcher(MatcherKind.DOMAIN_SUFFIX, "cn"), action = RuleAction.DIRECT, order = 1)) }

        val blob = BackupManager(srcProfiles, srcGroups, srcRules, NoTransaction).export(passphrase)

        // Restore into a fresh, empty store.
        val dstProfiles = MemProfiles()
        val dstGroups = MemGroups()
        val dstRules = MemRules()
        val result = BackupManager(dstProfiles, dstGroups, dstRules, NoTransaction).restore(blob, passphrase)

        assertEquals(RestoreResult(profiles = 1, groups = 1, rules = 1), result)
        assertEquals(node, dstProfiles.rows[1L])
        assertEquals("https://sub/x?token=t", dstGroups.rows[5L]?.subscriptionUrl)
        assertEquals(RuleAction.DIRECT, dstRules.rows[9L]?.action)
    }

    @Test
    fun `a region group keeps its marker through the round trip`() = runBlocking {
        val srcGroups = MemGroups().apply {
            upsert(
                ProxyGroup(
                    id = 9L,
                    name = "香港",
                    mode = GroupMode.URL_TEST,
                    region = NodeRegion.HONG_KONG,
                ),
            )
        }

        val blob = BackupManager(MemProfiles(), srcGroups, MemRules(), NoTransaction).export(passphrase)
        val dstGroups = MemGroups()
        BackupManager(MemProfiles(), dstGroups, MemRules(), NoTransaction).restore(blob, passphrase)

        // A region group owns no profiles, so the marker IS its membership.
        // Dropping it would restore an ordinary empty group and the members
        // would be gone for good.
        assertEquals(NodeRegion.HONG_KONG, dstGroups.rows[9L]?.region)
    }

    @Test
    fun `restore with the wrong passphrase writes nothing`() = runBlocking {
        val srcProfiles = MemProfiles().apply { upsert(node) }
        val blob = BackupManager(srcProfiles, MemGroups(), MemRules(), NoTransaction).export(passphrase)

        val dstProfiles = MemProfiles()
        try {
            BackupManager(dstProfiles, MemGroups(), MemRules(), NoTransaction).restore(blob, "wrong".toCharArray())
            throw AssertionError("expected BackupCryptoException")
        } catch (e: BackupCryptoException) {
            assertTrue(dstProfiles.rows.isEmpty())
        }
    }

    @Test
    fun `a restore that fails part-way leaves the store as it was`() = runBlocking {
        val srcProfiles = MemProfiles().apply {
            upsert(node)
            upsert(node.copy(id = 0L, name = "second", server = "r2.example.com"))
        }
        val srcGroups = MemGroups().apply {
            upsert(ProxyGroup(id = 5L, name = "sub", mode = GroupMode.URL_TEST))
        }
        val srcRules = MemRules().apply {
            upsert(Rule(id = 9L, matcher = RuleMatcher(MatcherKind.DOMAIN_SUFFIX, "cn"), action = RuleAction.DIRECT, order = 1))
        }
        val blob = BackupManager(srcProfiles, srcGroups, srcRules, NoTransaction).export(passphrase)

        // The destination already holds a group; groups and rules are written
        // before profiles, so without a transaction the failure would leave
        // them replaced and the nodes missing.
        val dstProfiles = MemProfiles()
        val dstGroups = MemGroups().apply {
            upsert(ProxyGroup(id = 1L, name = "keep", mode = GroupMode.SELECTOR))
        }
        val dstRules = MemRules()
        val before = dstGroups.rows.toMap()

        val manager = BackupManager(
            FailingProfiles(dstProfiles, failOnCall = 2),
            dstGroups,
            dstRules,
            MemTransaction(dstProfiles, dstGroups, dstRules),
        )

        try {
            manager.restore(blob, passphrase)
            throw AssertionError("expected the restore to fail")
        } catch (e: IllegalStateException) {
            // The write error, as it would reach the caller.
        }

        assertTrue("profiles were left behind", dstProfiles.rows.isEmpty())
        assertEquals("groups were left half-restored", before, dstGroups.rows)
        assertTrue("rules were left behind", dstRules.rows.isEmpty())
    }

    @Test
    fun `the exported blob does not contain the credentials in the clear`() = runBlocking {
        val srcProfiles = MemProfiles().apply { upsert(node) }
        val blob = BackupManager(srcProfiles, MemGroups(), MemRules(), NoTransaction).export(passphrase)
        val asText = String(blob, Charsets.ISO_8859_1)
        assertTrue(!asText.contains("11111111-2222-3333-4444-555555555555"))
        assertTrue(!asText.contains("r.example.com"))
    }
}
