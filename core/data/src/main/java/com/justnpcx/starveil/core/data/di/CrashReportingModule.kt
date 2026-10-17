package com.justnpcx.starveil.core.data.di

import com.justnpcx.starveil.core.data.crash.CrashReportingPreference
import com.justnpcx.starveil.core.data.crash.CrashReportingSwitch
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CrashReportingModule {

    @Binds
    @Singleton
    abstract fun bindCrashReportingSwitch(impl: CrashReportingPreference): CrashReportingSwitch
}
