package com.example.monday.di

import android.content.Context
import android.content.SharedPreferences
import com.example.monday.data.TodoRepository
import com.example.monday.data.local.AppDatabase
import com.example.monday.data.local.TodoDao
import com.example.monday.managers.CategoryManager
import com.example.monday.managers.MasterRecordManager
import com.example.monday.managers.PreferenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideTodoDao(database: AppDatabase): TodoDao {
        return database.todoDao()
    }

    @Provides
    @Singleton
    fun provideTodoRepository(todoDao: TodoDao): TodoRepository {
        return TodoRepository(todoDao)
    }

    @Provides
    @Singleton
    @Named("CategoryPrefs")
    fun provideCategoryPrefs(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("categories_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    @Named("MonitorPrefs")
    fun provideMonitorPrefs(@ApplicationContext context: Context): SharedPreferences {
        return context.getSharedPreferences("monitor_prefs", Context.MODE_PRIVATE)
    }

    @Provides
    @Singleton
    fun providePreferenceManager(
        @Named("CategoryPrefs") categoryPrefs: SharedPreferences,
        @Named("MonitorPrefs") monitorPrefs: SharedPreferences
    ): PreferenceManager {
        return PreferenceManager(categoryPrefs, monitorPrefs)
    }

    @Provides
    @Singleton
    fun provideMasterRecordManager(repository: TodoRepository): MasterRecordManager {
        return MasterRecordManager(repository)
    }

    @Provides
    @Singleton
    fun provideCategoryManager(
        repository: TodoRepository,
        preferenceManager: PreferenceManager
    ): CategoryManager {
        return CategoryManager(repository, preferenceManager)
    }
}

