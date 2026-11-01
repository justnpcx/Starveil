package com.justnpcx.starveil.feature.settings

import com.justnpcx.starveil.core.data.preferences.ConnectionTarget
import com.justnpcx.starveil.core.model.LatencySample
import com.justnpcx.starveil.core.model.NodeStatus
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChainDiagnosticTest {
    private val front = node(1, "front")
    private val exit = node(2, "exit").copy(frontNodeKey = front.nodeKey)

    @Test
    fun `the current chain reports both segments from their own latest samples`() {
        val result = chainDiagnostic(
            ConnectionTarget.Node(2),
            listOf(front, exit),
            mapOf(
                1L to listOf(LatencySample(1, 30)),
                2L to listOf(LatencySample(2, -1)),
            ),
        )

        assertEquals(NodeStatus.UP, result?.frontStatus)
        assertEquals(NodeStatus.DOWN, result?.exitStatus)
    }

    @Test
    fun `no front means no chain row`() {
        assertNull(chainDiagnostic(ConnectionTarget.Node(1), listOf(front), emptyMap()))
    }

    private fun node(id: Long, name: String) = Profile(
        id = id,
        name = name,
        type = ProfileType.VLESS,
        server = "$name.example",
        port = 443,
    )
}
