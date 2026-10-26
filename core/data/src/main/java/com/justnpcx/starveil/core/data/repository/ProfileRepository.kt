package com.justnpcx.starveil.core.data.repository

import com.justnpcx.starveil.core.data.dao.ProfileDao
import com.justnpcx.starveil.core.data.entity.ProfileEntity
import com.justnpcx.starveil.core.data.entity.SettingsJson
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Profiles, in domain-model terms. Feature modules depend on this
 * interface, never on [ProfileDao] — the data-layer boundary.
 */
interface ProfileRepository {
    fun observeAll(): Flow<List<Profile>>
    fun observeByGroup(groupId: Long): Flow<List<Profile>>
    suspend fun getById(id: Long): Profile?
    suspend fun upsert(profile: Profile): Long
    suspend fun deleteById(id: Long)

    /**
     * Star or unstar a node.
     *
     * The default is a read-modify-write through [upsert], which is correct for
     * any implementation; the real repository overrides it with a single
     * UPDATE so a pin does not rewrite the settings JSON.
     */
    suspend fun setFavorite(id: Long, favorite: Boolean) {
        val profile = getById(id) ?: return
        if (profile.favorite == favorite) return
        upsert(profile.copy(favorite = favorite))
    }

    /**
     * Remove every profile in [groupId] in one statement. Used when a group is
     * deleted: a subscription's nodes are the group, so they go with it.
     */
    suspend fun deleteByGroup(groupId: Long)

    /**
     * Replace every profile in [groupId] with [profiles] — the subscription
     * refresh path. Runs in one transaction so a failure cannot leave the
     * group half-updated (the caller keeps the last-good set instead).
     */
    suspend fun replaceGroupProfiles(groupId: Long, profiles: List<Profile>)
}

class RoomProfileRepository @Inject constructor(
    private val dao: ProfileDao,
) : ProfileRepository {
    override fun observeAll(): Flow<List<Profile>> =
        dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeByGroup(groupId: Long): Flow<List<Profile>> =
        dao.observeByGroup(groupId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: Long): Profile? = dao.getById(id)?.toDomain()

    override suspend fun upsert(profile: Profile): Long {
        // Per-protocol settings (UUID / password / REALITY keys) live in
        // settingsJson. Preserve the stored blob when the caller passes a
        // profile without settings — e.g. a latency-only update must never
        // silently wipe credentials.
        val settingsJson = when {
            profile.settings != null -> SettingsJson.encode(profile.settings)
            profile.id != 0L -> dao.getById(profile.id)?.settingsJson ?: EMPTY_SETTINGS
            else -> EMPTY_SETTINGS
        }
        return dao.upsert(profile.toEntity(settingsJson = settingsJson))
    }

    override suspend fun deleteById(id: Long) = dao.deleteById(id)

    override suspend fun setFavorite(id: Long, favorite: Boolean) = dao.setFavorite(id, favorite)

    override suspend fun deleteByGroup(groupId: Long) = dao.deleteByGroup(groupId)

    override suspend fun replaceGroupProfiles(groupId: Long, profiles: List<Profile>) {
        dao.replaceGroup(groupId, profiles.map { it.copy(groupId = groupId).toEntity() })
    }

    private companion object {
        const val EMPTY_SETTINGS = "{}"
    }
}

/** Unknown stored type names fall back to OTHER — never crash on old data. */
internal fun ProfileEntity.toDomain(): Profile {
    val profileType = runCatching { ProfileType.valueOf(type) }.getOrDefault(ProfileType.OTHER)
    return Profile(
        id = id,
        name = name,
        type = profileType,
        server = server,
        port = port,
        latencyMs = latencyMs,
        groupId = groupId,
        country = country,
        favorite = favorite,
        settings = SettingsJson.decode(settingsJson, profileType),
    )
}

internal fun Profile.toEntity(settingsJson: String = SettingsJson.encode(settings)): ProfileEntity =
    ProfileEntity(
        id = id,
        name = name,
        type = type.name,
        server = server,
        port = port,
        latencyMs = latencyMs,
        groupId = groupId,
        country = country,
        favorite = favorite,
        settingsJson = settingsJson,
    )
