package com.justnpcx.starveil.onboarding

import com.justnpcx.starveil.core.subscription.ImportOutcome
import com.justnpcx.starveil.feature.dashboard.ConnectionOutcome

/**
 * The two things onboarding does that live outside it.
 *
 * Narrow function types rather than the concrete classes: the screen needs
 * "import this text" and "connect now", and neither should drag the whole
 * subscription or connection machinery into a view-model test. Bound in
 * [OnboardingModule].
 */
fun interface NodeImporter {
    suspend fun import(text: String): ImportOutcome
}

fun interface ConnectNow {
    suspend fun connect(): ConnectionOutcome
}
