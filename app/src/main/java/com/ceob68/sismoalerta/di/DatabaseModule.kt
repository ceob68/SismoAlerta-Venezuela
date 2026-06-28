package com.ceob68.sismoalerta.di

import android.content.Context
import androidx.room.Room
import com.ceob68.sismoalerta.data.database.AppDatabase
import com.ceob68.sismoalerta.data.database.SismoDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Singleton
    @Provides
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "sismo_alerta_db"
        ).build()
    }
    
    @Singleton
    @Provides
    fun provideSismoDao(database: AppDatabase): SismoDao {
        return database.sismoDao()
    }
}