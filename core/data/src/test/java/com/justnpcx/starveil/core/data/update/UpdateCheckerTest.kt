package com.justnpcx.starveil.core.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The release JSON and the version comparison.
 *
 * No network: the checker's `fetch` is a parameter, so these run against a
 * recorded response. The rules being tested are "is this newer" and "which
 * asset", and both are worth getting right without a release to publish.
 */
class UpdateCheckerTest {

    private val checker = UpdateChecker { error("no network in tests") }

    /** Most cases are an arm64 phone; the ABI-specific ones pass their own. */
    private fun parse(body: String, currentVersion: String, abi: String? = "arm64-v8a") =
        checker.parse(body, currentVersion, abi)

    private fun release(
        tag: String,
        apk: String? = "starveil-arm64-v8a.apk",
        digest: String? = "sha256:abc123",
        draft: Boolean = false,
    ): String {
        val assets = if (apk == null) {
            "[]"
        } else {
            """[{"name":"$apk","browser_download_url":"https://example.com/$apk","size":1234${if (digest != null) ""","digest":"$digest"""" else ""}}]"""
        }
        return """
            {"tag_name":"$tag","body":"notes","draft":$draft,"assets":$assets}
        """.trimIndent()
    }

    @Test
    fun `a later release is offered with its checksum`() {
        val result = parse(release("v0.8.0"), currentVersion = "0.7.0")

        val available = result as UpdateCheck.Available
        assertEquals("0.8.0", available.info.version)
        assertEquals("https://example.com/starveil-arm64-v8a.apk", available.info.apkUrl)
        // The "sha256:" prefix is part of GitHub's format, not part of the hash.
        assertEquals("abc123", available.info.sha256)
        assertEquals(1234L, available.info.sizeBytes)
    }

    @Test
    fun `the same version is not an update`() {
        assertEquals(UpdateCheck.UpToDate, parse(release("v0.7.0"), "0.7.0"))
    }

    @Test
    fun `an older release is not an update`() {
        assertEquals(UpdateCheck.UpToDate, parse(release("v0.6.0"), "0.7.0"))
    }

    @Test
    fun `a release without a digest still parses`() {
        val result = parse(release("v0.8.0", digest = null), "0.7.0")
        assertEquals(null, (result as UpdateCheck.Available).info.sha256)
    }

    @Test
    fun `the asset for the device's ABI is chosen, not the first one`() {
        val body = """
            {"tag_name":"v0.8.0","assets":[
              {"name":"starveil-0.8.0-armeabi-v7a.apk","browser_download_url":"https://example.com/v7a"},
              {"name":"starveil-0.8.0-arm64-v8a.apk","browser_download_url":"https://example.com/arm64"},
              {"name":"starveil-0.8.0-x86_64.apk","browser_download_url":"https://example.com/x86"}
            ]}
        """.trimIndent()

        assertEquals(
            "https://example.com/arm64",
            (parse(body, "0.7.0", "arm64-v8a") as UpdateCheck.Available).info.apkUrl,
        )
        assertEquals(
            "https://example.com/v7a",
            (parse(body, "0.7.0", "armeabi-v7a") as UpdateCheck.Available).info.apkUrl,
        )
        assertEquals(
            "https://example.com/x86",
            (parse(body, "0.7.0", "x86_64") as UpdateCheck.Available).info.apkUrl,
        )
    }

    @Test
    fun `a device with no matching asset is a failure, not a wrong package`() {
        val body = """
            {"tag_name":"v0.8.0","assets":[
              {"name":"starveil-0.8.0-armeabi-v7a.apk","browser_download_url":"https://example.com/v7a"},
              {"name":"starveil-0.8.0-arm64-v8a.apk","browser_download_url":"https://example.com/arm64"}
            ]}
        """.trimIndent()

        val result = parse(body, "0.7.0", "x86_64")
        assertTrue(result is UpdateCheck.Failed)
        assertEquals(UpdateFailure.NoApkForDevice("x86_64"), (result as UpdateCheck.Failed).reason)
    }

    @Test
    fun `a single universal APK still updates an unknown ABI`() {
        // Releases before the split carried one APK and no ABI in the name.
        val result = parse(release("v0.8.0"), "0.7.0", abi = null)
        assertTrue(result is UpdateCheck.Available)
    }

    @Test
    fun `a release with no apk is a failure, not an update`() {
        val result = parse(release("v0.8.0", apk = null), "0.7.0")
        assertTrue(result is UpdateCheck.Failed)
    }

    @Test
    fun `a draft is not offered`() {
        assertEquals(UpdateCheck.UpToDate, parse(release("v0.8.0", draft = true), "0.7.0"))
    }

    @Test
    fun `malformed json is a failure`() {
        val result = parse("not json", "0.7.0")
        assertEquals(UpdateFailure.Unreadable, (result as UpdateCheck.Failed).reason)
    }

    @Test
    fun `a network error becomes a failure, not a crash`() {
        val offline = UpdateChecker { throw java.io.IOException("offline") }
        val result = kotlinx.coroutines.runBlocking { offline.check("0.7.0", "someone/repo") }
        // The exception's message is a diagnostic; the reason is a value the
        // screen can translate. Losing the message here is the point.
        assertEquals(UpdateFailure.Network, (result as UpdateCheck.Failed).reason)
    }

    @Test
    fun `version comparison is numeric, not lexical`() {
        assertTrue(isNewer("0.10.0", "0.9.0"))
        assertFalse(isNewer("0.9.0", "0.10.0"))
        assertTrue(isNewer("1.0", "0.9.9"))
        assertFalse(isNewer("0.7", "0.7.0"))
    }

    @Test
    fun `a pre-release is older than the release it precedes`() {
        assertTrue(isNewer("0.8.0", "0.8.0-beta.1"))
        assertFalse(isNewer("0.8.0-beta.1", "0.8.0"))
        // Between two pre-releases this says "no update". GitHub's `latest`
        // endpoint does not offer pre-releases at all, so that case cannot
        // reach the app; ordering them would be a semver implementation for a
        // code path that does not exist.
        assertFalse(isNewer("0.8.0-beta.2", "0.8.0-beta.1"))
    }
}
