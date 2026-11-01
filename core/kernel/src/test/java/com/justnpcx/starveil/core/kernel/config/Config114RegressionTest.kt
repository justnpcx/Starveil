package com.justnpcx.starveil.core.kernel.config

import com.justnpcx.starveil.core.kernel.registry.ProtocolRegistry
import com.justnpcx.starveil.core.model.GroupMode
import com.justnpcx.starveil.core.model.MainlandRuleSets
import com.justnpcx.starveil.core.model.Profile
import com.justnpcx.starveil.core.model.ProfileType
import com.justnpcx.starveil.core.model.RoutePreset
import com.justnpcx.starveil.core.model.VlessSettings
import com.justnpcx.starveil.core.model.json.Json
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One identical fixture run against both the 1.14.3 and 1.14.4 trees. */
class Config114RegressionTest {
    @Test
    fun groupMainlandConfigHasUniqueTagsAndCurrentDnsShape() {
        val profiles = listOf("one", "two").mapIndexed { index, name ->
            Profile(
                name = name,
                type = ProfileType.VLESS,
                server = "$name.example.com",
                port = 443,
                settings = VlessSettings(uuid = "11111111-2222-3333-4444-55555555555$index"),
            )
        }
        val config = ConfigBuilder(ProtocolRegistry.default()).build(
            profiles = profiles,
            mode = GroupMode.URL_TEST,
            mainland = MainlandRuleSets(
                geositeCnPath = "/root/Starveil/core/data/src/main/assets/rulesets/geosite-cn.srs",
                geoipCnPath = "/root/Starveil/core/data/src/main/assets/rulesets/geoip-cn.srs",
                googlePath = "/root/Starveil/core/data/src/main/assets/rulesets/geosite-google.srs",
            ),
            preset = RoutePreset.BYPASS_MAINLAND,
        )
        val root = Json.parseObject(config)
        val outbounds = root["outbounds"] as List<*>
        val endpoints = root["endpoints"] as? List<*> ?: emptyList<Any?>()
        val tags = (outbounds + endpoints).map { (it as Map<*, *>) ["tag"] as String }
        assertEquals("1.14.4 rejects duplicate outbound group tags", tags.size, tags.distinct().size)
        val dnsServers = (root["dns"] as Map<*, *>) ["servers"] as List<*>
        dnsServers.forEach { server ->
            val map = server as Map<*, *>
            assertFalse("legacy DNS address form", map.containsKey("address"))
            assertTrue("new DNS server form", map["type"] in listOf("local", "https"))
        }
        assertFalse("this configuration does not enable fakeip", config.contains("fakeip"))
        System.getenv("STARVEIL_CONFIG_OUT")?.let { File(it).writeText(config) }
    }
}
