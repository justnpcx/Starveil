package com.justnpcx.starveil.di

import com.justnpcx.starveil.core.kernel.FakeKernelBridge
import com.justnpcx.starveil.core.kernel.KernelBridge
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Kernel binding. Skeleton provides the Fake; Phase 2 swaps this single
 * function to RealKernelBridge. No UI code changes when the kernel becomes
 * real — that is the entire point of the KernelBridge interface.
 *
 * Note: @Provides (not @Binds) keeps :core:kernel free of DI annotations.
 */
@Module
@InstallIn(SingletonComponent::class)
object KernelModule {

    @Provides
    @Singleton
    fun provideKernelBridge(): KernelBridge = FakeKernelBridge()
}
