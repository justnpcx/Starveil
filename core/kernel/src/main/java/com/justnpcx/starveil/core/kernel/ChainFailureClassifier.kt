package com.justnpcx.starveil.core.kernel

/** Which dial failed, based only on evidence in sing-box's own error text. */
enum class ChainFailureStage { FRONT, EXIT, UNSEGMENTED }

object ChainFailureClassifier {
    private val front = Regex("(?:outbound/)?[a-z0-9_-]*\\[(front(?:-\\d+)?)]|\\bfront(?:-\\d+)?\\b", RegexOption.IGNORE_CASE)
    private val exit = Regex("(?:outbound/)?[a-z0-9_-]*\\[(proxy(?:-\\d+)?)]|\\bproxy(?:-\\d+)?\\b", RegexOption.IGNORE_CASE)

    fun classify(error: String): ChainFailureStage {
        val hasFront = front.containsMatchIn(error)
        val hasExit = exit.containsMatchIn(error)
        return when {
            hasFront && !hasExit -> ChainFailureStage.FRONT
            hasExit && !hasFront -> ChainFailureStage.EXIT
            else -> ChainFailureStage.UNSEGMENTED
        }
    }
}
