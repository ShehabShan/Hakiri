package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AnimeCacheDao {
    @Query("SELECT * FROM anime_cache WHERE sourceId = :sourceId AND sourceMediaId = :sourceMediaId LIMIT 1")
    suspend fun getAnimeCache(sourceId: String, sourceMediaId: String): AnimeCacheEntity?

    @Query("SELECT * FROM anime_cache WHERE sourceId = :sourceId ORDER BY fetchedAtEpochMs DESC")
    suspend fun getAllBySource(sourceId: String): List<AnimeCacheEntity>

    @Query("SELECT * FROM anime_cache ORDER BY fetchedAtEpochMs DESC")
    suspend fun getAllCachedAnime(): List<AnimeCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAnimeCache(entity: AnimeCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAllAnimeCache(entities: List<AnimeCacheEntity>)

    @Query("DELETE FROM anime_cache")
    suspend fun clearAll()
}

@Dao
interface EpisodeCacheDao {
    @Query("SELECT * FROM episode_cache WHERE sourceId = :sourceId AND sourceMediaId = :sourceMediaId LIMIT 1")
    suspend fun getEpisodeCache(sourceId: String, sourceMediaId: String): EpisodeCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEpisodeCache(entity: EpisodeCacheEntity)

    @Query("DELETE FROM episode_cache")
    suspend fun clearAll()
}

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorites ORDER BY addedAtEpochMs DESC")
    fun observeAllFavorites(): Flow<List<FavoriteEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE sourceId = :sourceId AND sourceMediaId = :sourceMediaId)")
    fun observeIsFavorite(sourceId: String, sourceMediaId: String): Flow<Boolean>

    @Query("SELECT * FROM favorites WHERE sourceId = :sourceId AND sourceMediaId = :sourceMediaId LIMIT 1")
    suspend fun getFavorite(sourceId: String, sourceMediaId: String): FavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(entity: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE sourceId = :sourceId AND sourceMediaId = :sourceMediaId")
    suspend fun deleteFavorite(sourceId: String, sourceMediaId: String)
}

@Dao
interface WatchProgressDao {
    @Query("SELECT * FROM watch_progress WHERE sourceId = :sourceId AND sourceMediaId = :sourceMediaId ORDER BY episodeNumber ASC")
    fun observeProgressForAnime(sourceId: String, sourceMediaId: String): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress ORDER BY updatedAtEpochMs DESC")
    fun observeAllProgress(): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE completed = 0 ORDER BY updatedAtEpochMs DESC LIMIT :limit")
    fun observeIncompleteProgress(limit: Int): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE sourceId = :sourceId AND sourceMediaId = :sourceMediaId AND episodeNumber = :episodeNumber LIMIT 1")
    suspend fun getProgress(sourceId: String, sourceMediaId: String, episodeNumber: Int): WatchProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(entity: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE sourceId = :sourceId AND sourceMediaId = :sourceMediaId AND episodeNumber = :episodeNumber")
    suspend fun deleteProgressItem(sourceId: String, sourceMediaId: String, episodeNumber: Int)

    @Query("DELETE FROM watch_progress")
    suspend fun clearAllProgress()
}

@Dao
interface JikanCacheDao {
    @Query("SELECT * FROM jikan_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getCache(cacheKey: String): JikanCacheEntity?

    @Query("SELECT * FROM jikan_cache")
    suspend fun getAllCacheEntries(): List<JikanCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCache(entity: JikanCacheEntity)

    @Query("DELETE FROM jikan_cache")
    suspend fun clearAll()
}
