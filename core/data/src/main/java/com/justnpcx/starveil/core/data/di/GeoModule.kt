package com.justnpcx.starveil.core.data.di

import com.justnpcx.starveil.core.data.geo.CountryResolver
import com.justnpcx.starveil.core.data.geo.NodeCountryResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class GeoModule {

    @Binds
    @Singleton
    abstract fun bindCountryResolver(impl: NodeCountryResolver): CountryResolver
}
