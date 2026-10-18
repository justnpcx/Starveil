package com.justnpcx.starveil.rules

import com.justnpcx.starveil.core.data.ruleset.MainlandRuleSetStore
import com.justnpcx.starveil.core.data.ruleset.RulePackStore
import com.justnpcx.starveil.core.kernel.KernelDiagnostics
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Puts a rule-set rejection in the Logs screen.
 *
 * The store lives in :core:data and must not know about the kernel, so the
 * bridge between "a download was rejected" and "there is a line in the log"
 * lives here, in :app, where both are allowed to meet.
 *
 * Written as an error, not a warning: the preset silently degrading to
 * "everything through the proxy" is safe, but it is not what the user asked
 * for, and the only way to find out why used to be adb.
 */
@Singleton
class RuleSetFailureReporter @Inject constructor(
    private val store: MainlandRuleSetStore,
    private val packs: RulePackStore,
) {

    fun start(scope: CoroutineScope) {
        scope.launch {
            store.failure.collect { failure ->
                if (failure == null) return@collect
                KernelDiagnostics.logError(
                    "rule set ${failure.name}: ${failure.reason.name.lowercase()}; " +
                        "mainland preset disabled",
                )
            }
        }
        scope.launch {
            packs.failure.collect { failure ->
                if (failure == null) return@collect
                KernelDiagnostics.logError(
                    "rule pack ${failure.id}: ${failure.reason.name.lowercase()}; pack disabled",
                )
            }
        }
    }
}
