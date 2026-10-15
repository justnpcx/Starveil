package com.justnpcx.starveil.core.data.repository

import com.justnpcx.starveil.core.data.dao.GroupDao
import com.justnpcx.starveil.core.data.dao.ProfileDao
import com.justnpcx.starveil.core.data.entity.GroupEntity
import com.justnpcx.starveil.core.model.GroupMode
import com.justnpcx.starveil.core.model.ProxyGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Groups, in domain-model terms. Feature modules depend on this interface,
 * never on [GroupDao].
 *
 * `ProxyGroup.profileIds` is DERIVED (profiles carry `groupId`), not
 * stored — this repository composes it on read.
 */
interface GroupRepository {
    fun observeAll(): Flow<List<ProxyGroup>>
    suspend fun getById(id: Long): ProxyGroup?
    /** The group a subscription URL already has, if any. */
    suspend fun findBySubscriptionUrl(url: String): ProxyGroup?
    suspend fun upsert(group: ProxyGroup): Long
    suspend fun deleteById(id: Long)
}

class RoomGroupRepository @Inject constructor(
    private val groupDao: GroupDao,
    private val profileDao: ProfileDao,
) : GroupRepository {
    override fun observeAll(): Flow<List<ProxyGroup>> =
        groupDao.observeAll().map { rows ->
            rows.map { entity ->
                entity.toDomain(profileDao.getProfileIdsByGroup(entity.id))
            }
        }

    override suspend fun getById(id: Long): ProxyGroup? {
        val entity = groupDao.getById(id) ?: return null
        return entity.toDomain(profileDao.getProfileIdsByGroup(entity.id))
    }

    override suspend fun findBySubscriptionUrl(url: String): ProxyGroup? {
        val entity = groupDao.findBySubscriptionUrl(url) ?: return null
        return entity.toDomain(profileDao.getProfileIdsByGroup(entity.id))
    }

    override suspend fun upsert(group: ProxyGroup): Long = groupDao.upsert(group.toEntity())

    override suspend fun deleteById(id: Long) = groupDao.deleteById(id)
}

internal fun GroupEntity.toDomain(profileIds: List<Long>): ProxyGroup = ProxyGroup(
    id = id,
    name = name,
    mode = runCatching { GroupMode.valueOf(mode) }.getOrDefault(GroupMode.MANUAL),
    profileIds = profileIds,
    subscriptionUrl = subscriptionUrl,
)

internal fun ProxyGroup.toEntity(): GroupEntity = GroupEntity(
    id = id,
    name = name,
    mode = mode.name,
    subscriptionUrl = subscriptionUrl,
)
