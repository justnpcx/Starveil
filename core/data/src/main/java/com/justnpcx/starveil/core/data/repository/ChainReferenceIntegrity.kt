package com.justnpcx.starveil.core.data.repository

import com.justnpcx.starveil.core.data.dao.ProfileDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/** Where a user-visible chain WARN goes; :app adapts it to KernelDiagnostics. */
fun interface ChainWarningSink {
    fun warn(text: String)
}

/** Buffered so a refresh while the node page is closed still produces its one notice. */
@Singleton
class ChainInvalidationBus @Inject constructor() {
    private val channel = Channel<String>(Channel.BUFFERED)
    val events: Flow<String> = channel.receiveAsFlow()
    fun emit(exitName: String) {
        channel.trySend(exitName)
    }
}

/** Clears dangling/self chain references immediately after a destructive mutation. */
class ChainReferenceIntegrity @Inject constructor(
    private val dao: ProfileDao,
    private val warnings: ChainWarningSink,
    private val invalidations: ChainInvalidationBus,
) {
    suspend fun repair() {
        val rows = dao.getAll()
        val keys = rows.map { it.toDomain().nodeKey }.toSet()
        rows.forEach { row ->
            val profile = row.toDomain()
            val key = row.frontNodeKey ?: return@forEach
            if (key in keys && key != profile.nodeKey) return@forEach
            dao.upsert(row.copy(frontNodeKey = null))
            warnings.warn("WARN chain front invalidated for ${profile.name}; using direct")
            invalidations.emit(profile.name)
        }
    }
}

/** Refresh keeps a user's chain when the provider still publishes the same business key. */
internal fun preserveFronts(previous: List<com.justnpcx.starveil.core.model.Profile>, refreshed: List<com.justnpcx.starveil.core.model.Profile>): List<com.justnpcx.starveil.core.model.Profile> {
    val old = previous.associate { it.nodeKey to it.frontNodeKey }
    return refreshed.map { profile ->
        if (profile.frontNodeKey != null) profile
        else profile.copy(frontNodeKey = old[profile.nodeKey])
    }
}
