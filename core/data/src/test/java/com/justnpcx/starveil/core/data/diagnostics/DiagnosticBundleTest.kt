package com.justnpcx.starveil.core.data.diagnostics

import com.justnpcx.starveil.core.data.geo.DnsSnapshot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The redaction list, tested as a list.
 *
 * A bundle is the one artefact this app produces that is meant to be published.
 * Every value the task book names is put into the input here, and every one is
 * asserted absent from the output — plus the shapes (link, URL, credential,
 * UUID, address) that a log line can carry without anyone choosing to put them
 * there.
 */
class DiagnosticBundleTest {

    private val server = "node-01.secret-host.example"
    private val subscription = "https://sub.secret-host.example/api/v1/client/subscribe?token=abcdef123456"
    private val apiToken = "Zx9Kq2Lm4Np7Rs1Tv3Wx5Yz8Ab2Cd4Ef6Gh8Ij0K"
    private val uuid = "11111111-2222-3333-4444-555555555555"
    private val password = "hunter2-but-longer"

    private val input = DiagnosticInput(
        appVersion = "0.8.6",
        kernelVersion = "1.14.3-647c447",
        androidSdk = 36,
        dns = DnsSnapshot(samples = 23, p50Ms = 81, p95Ms = 587, cacheHitPct = 0, fallbackPct = 0),
        leakCheckedAtMs = 1_700_000_000_000,
        leakLeaking = false,
        nodeCount = 3,
        groupCount = 2,
        ruleCount = 11,
        routePreset = "bypass_mainland",
        killSwitchMode = "disconnect",
        localApiEnabled = false,
        perAppDirectCount = 1,
        dnsMetricsEnabled = true,
        logLines = listOf(
            "20:10:44 I kernel starting",
            "20:10:45 I dial tcp 192.168.50.218:8443: connect: connection refused",
            "20:10:46 W outbound $server rejected: password=$password",
            "20:10:47 I subscribe $subscription",
            "20:10:48 I uuid $uuid token=$apiToken",
        ),
        secrets = listOf(server, subscription, apiToken),
    )

    @Test
    fun `the bundle carries the four things a report needs`() {
        val text = DiagnosticBundle.build(input, nowMs = 1_700_000_100_000)

        assertTrue(text.contains("app=0.8.6"))
        assertTrue(text.contains("kernel=1.14.3-647c447"))
        assertTrue(text.contains("samples=23"))
        assertTrue(text.contains("p50=81ms"))
        assertTrue(text.contains("result=no leak found"))
    }

    @Test
    fun `nothing on the redaction list survives`() {
        val text = DiagnosticBundle.build(input, nowMs = 1_700_000_100_000)

        assertFalse(text.contains(server))
        assertFalse(text.contains(subscription))
        assertFalse(text.contains(apiToken))
        assertFalse(text.contains(uuid))
        assertFalse(text.contains(password))
        assertFalse(text.contains("192.168.50.218"))
        assertFalse(text.contains("secret-host"))
        assertFalse(text.contains("token=abcdef"))
    }

    @Test
    fun `links and credentials are removed by shape, not only by value`() {
        val text = DiagnosticBundle.scrub(
            "vless://abc@1.2.3.4:443?security=tls Bearer abc.def password=hunter2 " +
                "token: xyz https://example.com/sub",
            secrets = emptyList(),
        )

        assertFalse(text.contains("vless://"))
        assertFalse(text.contains("Bearer abc"))
        assertFalse(text.contains("hunter2"))
        assertFalse(text.contains("xyz"))
        assertFalse(text.contains("https://example.com"))
    }

    @Test
    fun `clock times in a log line are not mistaken for addresses`() {
        val text = DiagnosticBundle.scrub("20:10:44 I kernel starting", secrets = emptyList())

        assertEquals("20:10:44 I kernel starting", text)
    }

    @Test
    fun `the leak check states its own limit`() {
        val text = DiagnosticBundle.build(input, nowMs = 1_700_000_100_000)

        assertTrue(text.contains("cannot prove"))
    }

    @Test
    fun `the built bundle is written out for the grep check`() {
        val text = DiagnosticBundle.build(input, nowMs = 1_700_000_100_000)
        val out = File("build/tmp/diagnostic-bundle.txt")
        out.parentFile?.mkdirs()
        out.writeText(text)
        assertTrue(out.exists())
    }
}
