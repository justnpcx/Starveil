package com.justnpcx.starveil.core.model

/** Why a node cannot front this exit. Deleted nodes are absent from the input list. */
enum class ChainFrontExclusion {
    SELF,
    ALREADY_CHAINED,
}

/** One-hop chains only: self-reference and a front with its own front are impossible to pick. */
fun chainFrontExclusion(exit: Profile, candidate: Profile): ChainFrontExclusion? = when {
    candidate.nodeKey == exit.nodeKey -> ChainFrontExclusion.SELF
    candidate.frontNodeKey != null -> ChainFrontExclusion.ALREADY_CHAINED
    else -> null
}

fun chainFrontCandidates(exit: Profile, all: List<Profile>): List<Profile> =
    all.filter { chainFrontExclusion(exit, it) == null }
