package com.justnpcx.starveil.di

import com.justnpcx.starveil.core.ui.snackbar.UndoBus
import com.justnpcx.starveil.core.ui.snackbar.UndoRequests
import com.justnpcx.starveil.ui.AppUndoBus
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The undo channel, offered to features as [UndoBus] and to the shell as
 * [UndoRequests].
 *
 * Both are provided from one [AppUndoBus] instance — two providers would make
 * two buses, and a feature would push into one while the shell listened to the
 * other. That failure is silent: the delete happens and no Snackbar appears.
 */
@Module
@InstallIn(SingletonComponent::class)
object UndoModule {

    @Provides
    @Singleton
    fun provideAppUndoBus(): AppUndoBus = AppUndoBus()

    @Provides
    fun provideUndoBus(bus: AppUndoBus): UndoBus = bus

    @Provides
    fun provideUndoRequests(bus: AppUndoBus): UndoRequests = bus
}
