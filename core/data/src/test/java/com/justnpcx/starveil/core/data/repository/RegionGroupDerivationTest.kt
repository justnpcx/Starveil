package com.justnpcx.starveil.core.data.repository

import com.justnpcx.starveil.core.data.dao.GroupDao
import com.justnpcx.starveil.core.data.dao.ProfileDao
import com.justnpcx.starveil.core.data.entity.GroupEntity
import com.justnpcx.starveil.core.data.entity.ProfileEntity
import com.justnpcx.starveil.core.model.NodeRegion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Region groups derive their members from node names instead of owning them.
 *
 * That is the whole reason they can coexist with a subscription: a node's
 * `groupId` stays with the subscription, and the region group recomputes. If
 * this ever regressed to storing membership, the next subscription refresh —
 * which deletes and re-inserts every profile in its group — would silently
 * empty every region group, so it is worth a test of its own.
 */
class RegionGroupDerivationTest {

    private val profiles = listOf(
        profile(id = 1, name = "🇭🇰 香港 01", groupId = 5),
        profile(id = 2, name = "日本 东京 IEPL", groupId = 5),
        profile(id = 3, name = "Node 01", groupId = 5),
        profile(id = 4, name = "US-LA-02", groupId = 0),
    )

    private val groups = listOf(
        GroupEntity(id = 5, name = "sub", mode = "URL_TEST"),
        GroupEntity(id = 9, name = "香港", mode = "URL_TEST", region = "HONG_KONG"),
    )

    private val repository = RoomGroupRepository(
        groupDao = StubGroupDao(groups),
        profileDao = StubProfileDao(profiles),
    )

    @Test
    fun `a region group holds the nodes whose names name that region`() = runBlocking {
        val region = repository.observeAll().first().single { it.region == NodeRegion.HONG_KONG }

        assertEquals(listOf(1L), region.profileIds)
    }

    @Test
    fun `a node stays in its subscription group and in the region group`() = runBlocking {
        val all = repository.observeAll().first()

        // The same node, in both. This is what storing membership could not do:
        // a profile has one groupId.
        assertEquals(listOf(1L, 2L, 3L), all.single { it.id == 5L }.profileIds)
        assertEquals(listOf(1L), all.single { it.id == 9L }.profileIds)
    }

    @Test
    fun `a name with no region is in no region group`() = runBlocking {
        val all = repository.observeAll().first()
        val regionMembers = all.filter { it.region != null }.flatMap { it.profileIds }

        assertEquals(listOf(1L), regionMembers)
    }

    @Test
    fun `a node added later joins the region group without being touched`() = runBlocking {
        val grown = RoomGroupRepository(
            groupDao = StubGroupDao(groups),
            profileDao = StubProfileDao(profiles + profile(id = 6, name = "HK-03", groupId = 5)),
        )

        val region = grown.observeAll().first().single { it.region == NodeRegion.HONG_KONG }

        assertEquals(listOf(1L, 6L), region.profileIds)
    }

    @Test
    fun `getById derives the same members as the list`() = runBlocking {
        val region = repository.getById(9L)!!

        assertEquals(NodeRegion.HONG_KONG, region.region)
        assertEquals(listOf(1L), region.profileIds)
    }

    private fun profile(id: Long, name: String, groupId: Long) = ProfileEntity(
        id = id,
        name = name,
        type = "VLESS",
        server = "n$id.example.com",
        port = 443,
        groupId = groupId,
    )
}

private class StubGroupDao(private val rows: List<GroupEntity>) : GroupDao {
    override fun observeAll(): Flow<List<GroupEntity>> = flowOf(rows)
    override suspend fun getById(id: Long): GroupEntity? = rows.firstOrNull { it.id == id }
    override suspend fun findBySubscriptionUrl(url: String): GroupEntity? =
        rows.firstOrNull { it.subscriptionUrl == url }
    override suspend fun findByRegion(region: String): GroupEntity? =
        rows.firstOrNull { it.region == region }
    override suspend fun upsert(entity: GroupEntity): Long = error("read-only")
    override suspend fun update(entity: GroupEntity) = error("read-only")
    override suspend fun delete(entity: GroupEntity) = error("read-only")
    override suspend fun deleteById(id: Long) = error("read-only")
}

private class StubProfileDao(private val rows: List<ProfileEntity>) : ProfileDao {
    override fun observeAll(): Flow<List<ProfileEntity>> = flowOf(rows)
    override suspend fun getAll(): List<ProfileEntity> = rows
    override fun observeByGroup(groupId: Long): Flow<List<ProfileEntity>> =
        flowOf(rows.filter { it.groupId == groupId })
    override suspend fun getProfileIdsByGroup(groupId: Long): List<Long> =
        rows.filter { it.groupId == groupId }.map { it.id }
    override suspend fun getById(id: Long): ProfileEntity? = rows.firstOrNull { it.id == id }
    override suspend fun upsert(entity: ProfileEntity): Long = error("read-only")
    override suspend fun update(entity: ProfileEntity) = error("read-only")
    override suspend fun delete(entity: ProfileEntity) = error("read-only")
    override suspend fun deleteById(id: Long) = error("read-only")
    override suspend fun deleteByGroup(groupId: Long) = error("read-only")
}
