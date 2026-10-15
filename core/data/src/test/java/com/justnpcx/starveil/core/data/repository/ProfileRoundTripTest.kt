package com.justnpcx.starveil.core.data.repository

import com.justnpcx.starveil.core.data.dao.ProfileDao
import com.justnpcx.starveil.core.data.entity.ProfileEntity
import com.justnpcx.starveil.core.model.Hysteria2Settings
import com.justnpcx.starveil.core.model.MuxOptions
import com.justnpcx.starveil.core.model.ObfsOptions
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.RealityOptions
import com.justnpcx.starveil.core.model.SecurityMode
import com.justnpcx.starveil.core.model.ShadowsocksSettings
import com.justnpcx.starveil.core.model.TlsOptions
import com.justnpcx.starveil.core.model.TransportOptions
import com.justnpcx.starveil.core.model.TransportType
import com.justnpcx.starveil.core.model.TrojanSettings
import com.justnpcx.starveil.core.model.VlessSettings
import com.justnpcx.starveil.core.model.VmessSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * In-memory stand-in for the Room DAO. The repository only depends on this
 * interface, so the full import -> persist -> reload path (codec + mapping +
 * repository) is exercised here without an emulator.
 */
private class FakeProfileDao : ProfileDao {
    private val rows = LinkedHashMap<Long, ProfileEntity>()
    private var nextId = 1L

    override fun observeAll(): Flow<List<ProfileEntity>> = flow { emit(rows.values.toList()) }

    override fun observeByGroup(groupId: Long): Flow<List<ProfileEntity>> =
        flow { emit(rows.values.filter { it.groupId == groupId }) }

    override suspend fun getProfileIdsByGroup(groupId: Long): List<Long> =
        rows.values.filter { it.groupId == groupId }.map { it.id }

    override suspend fun getById(id: Long): ProfileEntity? = rows[id]

    override suspend fun upsert(entity: ProfileEntity): Long {
        val id = if (entity.id == 0L) nextId++ else entity.id
        rows[id] = entity.copy(id = id)
        return id
    }

    override suspend fun update(entity: ProfileEntity) {
        rows[entity.id] = entity
    }

    override suspend fun delete(entity: ProfileEntity) {
        rows.remove(entity.id)
    }

    override suspend fun deleteById(id: Long) {
        rows.remove(id)
    }

    override suspend fun deleteByGroup(groupId: Long) {
        rows.entries.removeAll { it.value.groupId == groupId }
    }
}

class ProfileRoundTripTest {

    private val repo = RoomProfileRepository(FakeProfileDao())

    private val vless = Profile(
        name = "reality-node",
        type = ProfileType.VLESS,
        server = "r.example.com",
        port = 443,
        settings = VlessSettings(
            uuid = "11111111-2222-3333-4444-555555555555",
            flow = "xtls-rprx-vision",
            tls = TlsOptions(
                security = SecurityMode.REALITY,
                sni = "r.example.com",
                alpn = listOf("h2", "http/1.1"),
                fingerprint = "chrome",
                reality = RealityOptions(publicKey = "PBK", shortId = "ab12", mlKemPublicKey = "MLKEM"),
            ),
            transport = TransportOptions(type = TransportType.XHTTP, path = "/x", host = "h.example"),
            mux = MuxOptions(enabled = true, protocol = "smux"),
        ),
    )

    private val trojan = Profile(
        name = "trojan-node",
        type = ProfileType.TROJAN,
        server = "t.example.com",
        port = 8443,
        settings = TrojanSettings(password = "p@ss:word"),
    )

    private val shadowsocks = Profile(
        name = "ss-node",
        type = ProfileType.SHADOWSOCKS,
        server = "ss.example.com",
        port = 8388,
        settings = ShadowsocksSettings(method = "2022-blake3-aes-128-gcm", password = "b64pass"),
    )

    private val vmess = Profile(
        name = "vm-node",
        type = ProfileType.VMESS,
        server = "vm.example.com",
        port = 443,
        settings = VmessSettings(
            uuid = "11111111-2222-3333-4444-555555555555",
            security = "chacha20-poly1305",
            alterId = 4,
            tls = TlsOptions.Plain,
            transport = TransportOptions(type = TransportType.WS, path = "/ws", host = "vm.example.com"),
        ),
    )

    private val hysteria2 = Profile(
        name = "hy2-node",
        type = ProfileType.HYSTERIA2,
        server = "hy.example.com",
        port = 8443,
        settings = Hysteria2Settings(
            password = "user:pass",
            obfs = ObfsOptions(type = "salamander", password = "obfspw"),
            upMbps = 100,
            downMbps = 200,
            tls = TlsOptions(
                security = SecurityMode.TLS,
                sni = "hy.example.com",
                alpn = listOf("h3"),
                allowInsecure = true,
            ),
        ),
    )

    @Test
    fun `credentials survive import persist reload`() = runBlocking {
        listOf(vless, trojan, shadowsocks, vmess, hysteria2).forEach { original ->
            val id = repo.upsert(original)
            val loaded = repo.getById(id)
            assertEquals("settings for ${original.type}", original.settings, loaded?.settings)
            assertEquals(original.server, loaded?.server)
            assertEquals(original.port, loaded?.port)
        }
    }

    @Test
    fun `a latency-only update preserves stored credentials`() = runBlocking {
        val id = repo.upsert(vless)
        // Simulate a url-test writing back only the latency (no settings).
        repo.upsert(vless.copy(id = id, latencyMs = 42L, settings = null))

        val loaded = repo.getById(id)!!
        assertEquals(42L, loaded.latencyMs)
        assertEquals(vless.settings, loaded.settings)
    }

    @Test
    fun `observeAll round-trips settings too`() = runBlocking {
        repo.upsert(shadowsocks)
        val all = mutableListOf<Profile>()
        // Collect a single emission from the flow.
        repo.observeAll().collect { list ->
            all += list
            return@collect
        }
        assertEquals(shadowsocks.settings, all.single().settings)
    }

    @Test
    fun `malformed settings json decodes to null instead of crashing`() = runBlocking {
        val id = repo.upsert(vless)
        // Corrupt the stored blob the way an old/hand-edited row might be.
        val entity = ProfileEntity(
            id = id,
            name = "x",
            type = ProfileType.VLESS.name,
            server = "h",
            port = 1,
            settingsJson = "{not valid json",
        )
        val loaded = entity.toDomain()
        assertNull(loaded.settings)
    }

    @Test
    fun `unmodelled protocol settings decode to null`() {
        val entity = ProfileEntity(
            name = "tuic",
            type = ProfileType.TUIC.name,
            server = "h",
            port = 443,
            settingsJson = """{"kind":"tuic","uuid":"x"}""",
        )
        assertNull(entity.toDomain().settings)
    }
}
