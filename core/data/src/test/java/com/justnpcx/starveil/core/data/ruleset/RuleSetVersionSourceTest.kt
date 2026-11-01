package com.justnpcx.starveil.core.data.ruleset

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reading the upstream branch head.
 *
 * The shape tested here is the one the GitHub API actually returns for
 * /branches/{branch} — the commit object is nested twice, and the date lives
 * under commit.commit.committer. Getting that path wrong produces a null that
 * looks exactly like "the network is down", which is why it is pinned by a test.
 */
class RuleSetVersionSourceTest {

    private val body = """
        {
          "name": "rule-set",
          "commit": {
            "sha": "4f2dea911f1dbc3fd7516dbc2c82e03a0add71e6",
            "commit": {
              "author": { "name": "a", "date": "2026-10-16T08:00:00Z" },
              "committer": { "name": "b", "date": "2026-10-16T09:12:33Z" }
            }
          }
        }
    """.trimIndent()

    @Test
    fun `the branch head gives a commit and its date`() = runBlocking {
        val source = GitHubRuleSetVersionSource(get = { body.toByteArray() })

        val version = source.latest()

        assertEquals("4f2dea911f1dbc3fd7516dbc2c82e03a0add71e6", version?.commit)
        assertEquals("2026-10-16T09:12:33Z", version?.dateIso)
        // The rules page shows a date, not a hash.
        assertEquals("2026-10-16", version?.date)
    }

    @Test
    fun `a failed check is a null, not a throw`() = runBlocking {
        val warnings = mutableListOf<String>()
        val source = GitHubRuleSetVersionSource(
            get = { throw java.io.IOException("offline") },
            warn = warnings::add,
        )

        assertNull(source.latest())
        assertEquals(1, warnings.size)
    }

    @Test
    fun `a body without a commit is a null`() = runBlocking {
        val source = GitHubRuleSetVersionSource(get = { "{\"message\":\"rate limited\"}".toByteArray() })

        assertNull(source.latest())
    }
}
