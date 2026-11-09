package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AnimeCacheEntity::class,
        EpisodeCacheEntity::class,
        FavoriteEntity::class,
        WatchProgressEntity::class,
        JikanCacheEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class HikariDatabase : RoomDatabase() {
    abstract fun animeCacheDao(): AnimeCacheEntityDaoBridge
    abstract fun episodeCacheDao(): EpisodeCacheDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun jikanCacheDao(): JikanCacheDao

    companion object {
        private const val DB_NAME = "hikari_app.db"

        fun create(context: Context): HikariDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                HikariDatabase::class.java,
                DB_NAME
            )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }

        fun createInMemory(context: Context): HikariDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                HikariDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}

typealias AnimeCacheEntityDaoBridge = AnimeCacheDao
