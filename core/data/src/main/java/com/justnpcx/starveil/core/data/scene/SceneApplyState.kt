package com.justnpcx.starveil.core.data.scene

import javax.inject.Inject
import javax.inject.Singleton

/**
 * When a scene last rebuilt the tunnel.
 *
 * The scene and the dashboard's policy watcher both react to a preset change,
 * and for one network switch that is two rebuilds of the same config. This is
 * the handshake that makes it one: the scene stamps the time, the dashboard
 * skips its own rebuild if the stamp is fresh.
 *
 * In-memory on purpose. A process restart has no pending rebuild to dedupe, and
 * a persisted timestamp would make the first launch after a scene look like a
 * duplicate.
 */
@Singleton
class SceneApplyState @Inject constructor() {
    @Volatile
    var lastAppliedAtMs: Long = 0L
}
