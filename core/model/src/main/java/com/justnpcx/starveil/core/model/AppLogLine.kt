package com.justnpcx.starveil.core.model

/**
 * One log line, in a shape the UI can render without knowing anything about the
 * kernel. Lives in :core:model so :feature:* can display logs through a narrow
 * port instead of importing :core:kernel.
 *
 * [message] is expected to be already redacted: the bridge strips credentials
 * before a line ever leaves the kernel (README 安全红线).
 */
data class AppLogLine(
    val level: AppLogLevel,
    val tag: String,
    val message: String,
    val timestampMs: Long,
)

enum class AppLogLevel { DEBUG, INFO, WARN, ERROR }
