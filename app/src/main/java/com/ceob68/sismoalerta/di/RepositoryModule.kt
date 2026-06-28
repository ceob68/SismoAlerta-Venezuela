package com.ceob68.sismoalerta.di

import com.ceob68.sismoalerta.data.api.SismoApiService
import com.ceob68.sismoalerta.data.database.SismoDao
import com.ceob68.sismoalerta.domain.repository.SismoRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    
    @Singleton
    @Provides
    fun provideSismoRepository(
        sismoApiService: SismoApiService,
        sismoDao: SismoDao
    ): SismoRepository {
        return SismoRepository(sismoApiService, sismoDao)
    }
}