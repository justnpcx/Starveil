package com.justnpcx.starveil.core.data.repository

import com.justnpcx.starveil.core.data.dao.GroupDao
import com.justnpcx.starveil.core.data.dao.ProfileDao
import com.justnpcx.starveil.core.data.entity.GroupEntity
import com.justnpcx.starveil.core.data.entity.ProfileEntity
import com.justnpcx.starveil.core.model.GroupMode
import com.justnpcx.starveil.core.model.NodeRegion
import com.justnpcx.starveil.core.model.NodeRegions
import com.justnpcx.starveil.core.model.ProxyGroup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Groups, in domain-model terms. Feature modules depend on this interface,
 * never on [GroupDao].
 *
 * `ProxyGroup.profileIds` is DERIVED, not stored. For an ordinary group the
 * members are the profiles whose `groupId` points at it; for a region group
 * they are whichever nodes classify to that region, recomputed on read. The
 * second kind is why a node can be in its subscription group and in "香港" at
 * the same time — a profile's `groupId` stays with the subscription, and the
 * region group owns nothing.
 */
interface GroupRepository {
    fun observeAll(): Flow<List<ProxyGroup>>
    suspend fun getById(id: Long): ProxyGroup?
    /** The group a subscription URL already has, if any. */
    suspend fun findBySubscriptionUrl(url: String): ProxyGroup?
    /** The group that already represents [region], if any. */
    suspend fun findByRegion(region: NodeRegion): ProxyGroup?
    suspend fun upsert(group: ProxyGroup): Long
    suspend fun deleteById(id: Long)

    /**
     * Delete a group and, when [deleteProfiles], the nodes it owns.
     *
     * One transaction on purpose: the row and its members must disappear
     * together, or an interrupted delete leaves an empty group that looks
     * like a subscription that refreshed to nothing.
     */
    suspend fun deleteWithProfiles(id: Long, deleteProfiles: Boolean)
}

class RoomGroupRepository @Inject constructor(
    private val groupDao: GroupDao,
    private val profileDao: ProfileDao,
) : GroupRepository {
    override fun observeAll(): Flow<List<ProxyGroup>> =
        combine(groupDao.observeAll(), profileDao.observeAll()) { groups, profiles ->
            val membersByRegion = regionMembers(profiles)
            // Grouped from the list this emission already carries. Asking the
            // DAO per group meant one query per group per emission, against a
            // SQLCipher database, for data that was already in hand.
            val membersByGroup = profiles.groupBy({ it.groupId }, { it.id })
            groups.map { entity ->
                val region = entity.region.toRegion()
                val profileIds = if (region != null) {
                    membersByRegion[region].orEmpty()
                } else {
                    membersByGroup[entity.id].orEmpty()
                }
                entity.toDomain(profileIds, region)
            }
        }

    override suspend fun getById(id: Long): ProxyGroup? {
        val entity = groupDao.getById(id) ?: return null
        return entity.toDomain(profileIdsOf(entity), entity.region.toRegion())
    }

    override suspend fun findBySubscriptionUrl(url: String): ProxyGroup? {
        val entity = groupDao.findBySubscriptionUrl(url) ?: return null
        return entity.toDomain(profileIdsOf(entity), entity.region.toRegion())
    }

    override suspend fun findByRegion(region: NodeRegion): ProxyGroup? {
        val entity = groupDao.findByRegion(region.name) ?: return null
        return entity.toDomain(profileIdsOf(entity), region)
    }

    override suspend fun upsert(group: ProxyGroup): Long = groupDao.upsert(group.toEntity())

    override suspend fun deleteById(id: Long) = groupDao.deleteById(id)

    override suspend fun deleteWithProfiles(id: Long, deleteProfiles: Boolean) =
        groupDao.deleteWithProfiles(id, deleteProfiles)

    private suspend fun profileIdsOf(entity: GroupEntity): List<Long> {
        val region = entity.region.toRegion() ?: return profileDao.getProfileIdsByGroup(entity.id)
        return regionMembers(profileDao.getAll())[region].orEmpty()
    }
}

/**
 * Which profiles belong to which region, from their names.
 *
 * Recomputed on every read rather than stored, so it can never disagree with
 * the node list and a subscription refresh cannot invalidate it.
 */
private fun regionMembers(profiles: List<ProfileEntity>): Map<NodeRegion, List<Long>> =
    profiles
        .mapNotNull { profile ->
            NodeRegions.resolve(profile.name, profile.country)?.let { it to profile.id }
        }
        .groupBy({ it.first }, { it.second })

private fun String?.toRegion(): NodeRegion? =
    this?.let { runCatching { NodeRegion.valueOf(it) }.getOrNull() }

internal fun GroupEntity.toDomain(profileIds: List<Long>, region: NodeRegion? = null): ProxyGroup =
    ProxyGroup(
        id = id,
        name = name,
        mode = runCatching { GroupMode.valueOf(mode) }.getOrDefault(GroupMode.MANUAL),
        profileIds = profileIds,
        subscriptionUrl = subscriptionUrl,
        region = region,
    )

internal fun ProxyGroup.toEntity(): GroupEntity = GroupEntity(
    id = id,
    name = name,
    mode = mode.name,
    subscriptionUrl = subscriptionUrl,
    region = region?.name,
)
