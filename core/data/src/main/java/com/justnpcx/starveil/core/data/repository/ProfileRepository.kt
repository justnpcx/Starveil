package com.justnpcx.starveil.core.data.repository

import com.justnpcx.starveil.core.data.dao.ProfileDao
import com.justnpcx.starveil.core.data.entity.ProfileEntity
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
        // Preserve per-protocol settings on update: a plain Profile upsert
        // must never silently wipe settingsJson.
        val existingSettings = if (profile.id != 0L) {
            dao.getById(profile.id)?.settingsJson
        } else {
            null
        }
        return dao.upsert(profile.toEntity(settingsJson = existingSettings ?: "{}"))
    }

    override suspend fun deleteById(id: Long) = dao.deleteById(id)
}

/** Unknown stored type names fall back to OTHER — never crash on old data. */
internal fun ProfileEntity.toDomain(): Profile = Profile(
    id = id,
    name = name,
    type = runCatching { ProfileType.valueOf(type) }.getOrDefault(ProfileType.OTHER),
    server = server,
    port = port,
    latencyMs = latencyMs,
    groupId = groupId,
)

internal fun Profile.toEntity(settingsJson: String = "{}"): ProfileEntity = ProfileEntity(
    id = id,
    name = name,
    type = type.name,
    server = server,
    port = port,
    latencyMs = latencyMs,
    groupId = groupId,
    settingsJson = settingsJson,
)
