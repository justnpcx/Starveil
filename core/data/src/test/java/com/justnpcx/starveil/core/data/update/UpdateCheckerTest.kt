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
        val result = checker.parse(release("v0.8.0"), currentVersion = "0.7.0")

        val available = result as UpdateCheck.Available
        assertEquals("0.8.0", available.info.version)
        assertEquals("https://example.com/starveil-arm64-v8a.apk", available.info.apkUrl)
        // The "sha256:" prefix is part of GitHub's format, not part of the hash.
        assertEquals("abc123", available.info.sha256)
        assertEquals(1234L, available.info.sizeBytes)
    }

    @Test
    fun `the same version is not an update`() {
        assertEquals(UpdateCheck.UpToDate, checker.parse(release("v0.7.0"), "0.7.0"))
    }

    @Test
    fun `an older release is not an update`() {
        assertEquals(UpdateCheck.UpToDate, checker.parse(release("v0.6.0"), "0.7.0"))
    }

    @Test
    fun `a release without a digest still parses`() {
        val result = checker.parse(release("v0.8.0", digest = null), "0.7.0")
        assertEquals(null, (result as UpdateCheck.Available).info.sha256)
    }

    @Test
    fun `the arm64 asset is preferred when there are several`() {
        val body = """
            {"tag_name":"v0.8.0","assets":[
              {"name":"starveil-armeabi-v7a.apk","browser_download_url":"https://example.com/arm"},
              {"name":"starveil-arm64-v8a.apk","browser_download_url":"https://example.com/arm64"}
            ]}
        """.trimIndent()

        val result = checker.parse(body, "0.7.0") as UpdateCheck.Available
        assertEquals("https://example.com/arm64", result.info.apkUrl)
    }

    @Test
    fun `a release with no apk is a failure, not an update`() {
        val result = checker.parse(release("v0.8.0", apk = null), "0.7.0")
        assertTrue(result is UpdateCheck.Failed)
    }

    @Test
    fun `a draft is not offered`() {
        assertEquals(UpdateCheck.UpToDate, checker.parse(release("v0.8.0", draft = true), "0.7.0"))
    }

    @Test
    fun `malformed json is a failure`() {
        assertTrue(checker.parse("not json", "0.7.0") is UpdateCheck.Failed)
    }

    @Test
    fun `a network error becomes a failure, not a crash`() {
        val offline = UpdateChecker { throw java.io.IOException("offline") }
        val result = kotlinx.coroutines.runBlocking { offline.check("0.7.0", "someone/repo") }
        assertTrue(result is UpdateCheck.Failed)
        assertEquals("offline", (result as UpdateCheck.Failed).reason)
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
