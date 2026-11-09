package com.example.domain.repository

import com.example.domain.model.Anime
import com.example.domain.model.ContinueWatchingItem
import com.example.domain.model.Episode
import com.example.domain.model.FavoriteItem
import com.example.domain.model.MediaId
import com.example.domain.model.SourceInfo
import com.example.domain.model.SourceResult
import com.example.domain.model.StreamLink
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserSettings
import com.example.domain.model.WatchProgress
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    fun getRegisteredSources(): List<SourceInfo>
    suspend fun browseSource(sourceId: String, page: Int = 1, forceRefresh: Boolean = false): SourceResult<List<Anime>>
    suspend fun searchEnabledSources(query: String, page: Int = 1): SourceResult<List<Anime>>
    suspend fun searchCachedCatalog(query: String): List<Anime>
    suspend fun getDetails(mediaId: MediaId, forceRefresh: Boolean = false): SourceResult<Anime>
    suspend fun getEpisodes(mediaId: MediaId, forceRefresh: Boolean = false): SourceResult<List<Episode>>
    suspend fun getStreams(mediaId: MediaId, episodeNumber: Int): SourceResult<List<StreamLink>>
    suspend fun clearCatalogCache()
    suspend fun getEstimatedCacheSizeBytes(): Long
}

interface MetadataRepository {
    suspend fun getTrendingAnime(forceRefresh: Boolean = false): SourceResult<List<Anime>>
    suspend fun getSeasonalAnime(forceRefresh: Boolean = false): SourceResult<List<Anime>>
    suspend fun searchMetadata(query: String): SourceResult<List<Anime>>
    suspend fun getMetadataDetails(malId: Long, forceRefresh: Boolean = false): SourceResult<Anime>
    suspend fun clearMetadataCache()
}

interface LibraryRepository {
    fun observeFavorites(): Flow<List<FavoriteItem>>
    fun observeFavoriteIds(): Flow<Set<String>>
    fun observeIsFavorite(mediaId: MediaId): Flow<Boolean>
    suspend fun toggleFavorite(anime: Anime): Boolean
    suspend fun removeFavorite(mediaId: MediaId)

    fun observeWatchProgressForAnime(mediaId: MediaId): Flow<List<WatchProgress>>
    fun observeAllWatchHistory(): Flow<List<ContinueWatchingItem>>
    fun observeContinueWatching(limit: Int = 10): Flow<List<ContinueWatchingItem>>
    suspend fun getWatchProgress(mediaId: MediaId, episodeNumber: Int): WatchProgress?
    suspend fun recordWatchProgress(
        anime: Anime,
        episodeNumber: Int,
        positionMs: Long,
        durationMs: Long
    )
    suspend fun removeWatchHistoryItem(mediaId: MediaId, episodeNumber: Int)
    suspend fun clearWatchHistory()
}

interface SettingsRepository {
    val settingsFlow: Flow<UserSettings>
    val recentSearchesFlow: Flow<List<String>>

    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setAutoplayNext(enabled: Boolean)
    suspend fun setResumeAutomatically(enabled: Boolean)
    suspend fun toggleSourceEnabled(sourceId: String, allAvailableSourceIds: Set<String>): Boolean
    suspend fun setPreferredQuality(qualityLabel: String?)
    suspend fun addRecentSearch(query: String)
    suspend fun removeRecentSearch(query: String)
    suspend fun clearRecentSearches()
}
