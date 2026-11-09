package com.example.data.local

import androidx.room.Entity

@Entity(
    tableName = "anime_cache",
    primaryKeys = ["sourceId", "sourceMediaId"]
)
data class AnimeCacheEntity(
    val sourceId: String,
    val sourceMediaId: String,
    val json: String,
    val fetchedAtEpochMs: Long
)

@Entity(
    tableName = "episode_cache",
    primaryKeys = ["sourceId", "sourceMediaId"]
)
data class EpisodeCacheEntity(
    val sourceId: String,
    val sourceMediaId: String,
    val json: String,
    val fetchedAtEpochMs: Long
)

@Entity(
    tableName = "favorites",
    primaryKeys = ["sourceId", "sourceMediaId"]
)
data class FavoriteEntity(
    val sourceId: String,
    val sourceMediaId: String,
    val addedAtEpochMs: Long,
    val titleSnapshot: String,
    val posterSnapshot: String?
)

@Entity(
    tableName = "watch_progress",
    primaryKeys = ["sourceId", "sourceMediaId", "episodeNumber"]
)
data class WatchProgressEntity(
    val sourceId: String,
    val sourceMediaId: String,
    val episodeNumber: Int,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtEpochMs: Long,
    val completed: Boolean
)

@Entity(
    tableName = "jikan_cache",
    primaryKeys = ["cacheKey"]
)
data class JikanCacheEntity(
    val cacheKey: String,
    val json: String,
    val fetchedAtEpochMs: Long
)
