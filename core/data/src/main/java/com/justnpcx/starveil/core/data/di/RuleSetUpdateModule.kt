package com.justnpcx.starveil.core.data.di

import android.content.Context
import com.justnpcx.starveil.core.data.preferences.PreferencesRepository
import com.justnpcx.starveil.core.data.ruleset.FileMainlandRuleSetStore
import com.justnpcx.starveil.core.data.ruleset.GitHubRuleSetVersionSource
import com.justnpcx.starveil.core.data.ruleset.RuleSetAutoCheck
import com.justnpcx.starveil.core.data.ruleset.RuleSetInstaller
import com.justnpcx.starveil.core.data.ruleset.RuleSetUpdater
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * The weekly rule-set check.
 *
 * Wired here rather than inside the store: the store answers "give me the files
 * to route by", and updating them is a different job with a different failure
 * mode — one is needed for every connect, the other happens once a week and is
 * allowed to fail silently.
 */
@Module
@InstallIn(SingletonComponent::class)
object RuleSetUpdateModule {

    private fun warn(line: String) {
        android.util.Log.w("StarveilRuleSets", line)
    }

    @Provides
    @Singleton
    fun provideRuleSetAutoCheck(
        @ApplicationContext context: Context,
        preferences: PreferencesRepository,
    ): RuleSetAutoCheck {
        val directory = File(context.filesDir, "rulesets")
        val installer = RuleSetInstaller(directory, ::warn)
        val updater = RuleSetUpdater(
            directory = directory,
            installer = installer,
            versionOf = { feed ->
                GitHubRuleSetVersionSource(
                    url = "https://api.github.com/repos/" + feed.repo + "/branches/" + feed.branch,
                    warn = ::warn,
                ).latest()
            },
            download = { url -> FileMainlandRuleSetStore.httpGet(url) },
            warn = ::warn,
        )
        return RuleSetAutoCheck(
            updater = updater,
            lastCheckedAt = { preferences.ruleSetsCheckedAt.first() },
            recordCheckedAt = { at -> preferences.setRuleSetsCheckedAt(at) },
            warn = ::warn,
        )
    }
}
