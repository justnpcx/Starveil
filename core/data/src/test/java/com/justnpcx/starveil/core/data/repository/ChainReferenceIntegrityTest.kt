package com.justnpcx.starveil.core.data.repository

import com.justnpcx.starveil.core.data.dao.ProfileDao
import com.justnpcx.starveil.core.data.entity.ProfileEntity
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChainReferenceIntegrityTest {
    @Test
    fun `refresh preserves a chain by business key rather than row id`() {
        val front = node(1, "front")
        val oldExit = node(2, "exit").copy(frontNodeKey = front.nodeKey)
        val refreshedExit = node(0, "exit")

        val result = preserveFronts(listOf(front, oldExit), listOf(node(0, "front"), refreshedExit))

        assertEquals(front.nodeKey, result.single { it.name == "exit" }.frontNodeKey)
    }

    @Test
    fun `a deleted front clears the exit and emits both visible signals`() = runBlocking {
        val dao = IntegrityDao()
        val front = node(1, "front")
        val exit = node(2, "exit").copy(frontNodeKey = front.nodeKey)
        dao.rows += listOf(front, exit).map { it.toEntity() }
        dao.rows.removeAt(0)
        val warnings = mutableListOf<String>()
        val bus = ChainInvalidationBus()

        ChainReferenceIntegrity(dao, ChainWarningSink(warnings::add), bus).repair()

        assertNull(dao.rows.single().frontNodeKey)
        assertTrue(warnings.single().contains("exit"))
        assertEquals("exit", bus.events.first())
    }

    @Test
    fun `a self reference is cleared but a valid chain is untouched`() = runBlocking {
        val dao = IntegrityDao()
        val front = node(1, "front")
        val valid = node(2, "valid").copy(frontNodeKey = front.nodeKey)
        val self = node(3, "self").copy(frontNodeKey = node(3, "self").nodeKey)
        dao.rows += listOf(front, valid, self).map { it.toEntity() }

        ChainReferenceIntegrity(dao, ChainWarningSink {}, ChainInvalidationBus()).repair()

        assertEquals(front.nodeKey, dao.rows.single { it.name == "valid" }.frontNodeKey)
        assertNull(dao.rows.single { it.name == "self" }.frontNodeKey)
    }

    private fun node(id: Long, name: String) = Profile(
        id = id,
        name = name,
        type = ProfileType.VLESS,
        server = "$name.example",
        port = 443,
    )
}

private class IntegrityDao : ProfileDao {
    val rows = mutableListOf<ProfileEntity>()
    override fun observeAll(): Flow<List<ProfileEntity>> = flowOf(rows)
    override suspend fun getAll(): List<ProfileEntity> = rows
    override fun observeByGroup(groupId: Long): Flow<List<ProfileEntity>> =
        flowOf(rows.filter { it.groupId == groupId })
    override suspend fun getProfileIdsByGroup(groupId: Long): List<Long> =
        rows.filter { it.groupId == groupId }.map { it.id }
    override suspend fun getById(id: Long): ProfileEntity? = rows.firstOrNull { it.id == id }
    override suspend fun upsert(entity: ProfileEntity): Long {
        rows.removeAll { it.id == entity.id }
        rows += entity
        return entity.id
    }
    override suspend fun upsertAll(entities: List<ProfileEntity>): List<Long> = entities.map { upsert(it) }
    override suspend fun update(entity: ProfileEntity) { upsert(entity) }
    override suspend fun delete(entity: ProfileEntity) { rows.removeAll { it.id == entity.id } }
    override suspend fun setFavorite(id: Long, favorite: Boolean) = Unit
    override suspend fun setCountry(id: Long, country: String) = Unit
    override suspend fun deleteById(id: Long) { rows.removeAll { it.id == id } }
    override suspend fun deleteByGroup(groupId: Long) { rows.removeAll { it.groupId == groupId } }
}
