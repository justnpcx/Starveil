package com.justnpcx.starveil.core.data.repository

import com.justnpcx.starveil.core.data.dao.RuleDao
import com.justnpcx.starveil.core.data.entity.RuleEntity
import com.justnpcx.starveil.core.model.Rule
import com.justnpcx.starveil.core.model.RuleAction
import com.justnpcx.starveil.core.model.RuleMatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Routing rules, in domain-model terms. Feature modules depend on this
 * interface, never on [RuleDao].
 */
interface RuleRepository {
    /** Rules in user-defined order. */
    fun observeAllOrdered(): Flow<List<Rule>>
    suspend fun getById(id: Long): Rule?
    suspend fun upsert(rule: Rule): Long
    suspend fun deleteById(id: Long)
}

class RoomRuleRepository @Inject constructor(
    private val dao: RuleDao,
) : RuleRepository {
    override fun observeAllOrdered(): Flow<List<Rule>> =
        dao.observeAllOrdered().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getById(id: Long): Rule? = dao.getById(id)?.toDomain()

    override suspend fun upsert(rule: Rule): Long = dao.upsert(rule.toEntity())

    override suspend fun deleteById(id: Long) = dao.deleteById(id)
}

internal fun RuleEntity.toDomain(): Rule = Rule(
    id = id,
    matcher = RuleMatcher.decode(matcher),
    action = runCatching { RuleAction.valueOf(action) }.getOrDefault(RuleAction.PROXY),
    order = sortOrder,
    enabled = enabled,
)

internal fun Rule.toEntity(): RuleEntity = RuleEntity(
    id = id,
    matcher = matcher.encode(),
    action = action.name,
    sortOrder = order,
    enabled = enabled,
)
