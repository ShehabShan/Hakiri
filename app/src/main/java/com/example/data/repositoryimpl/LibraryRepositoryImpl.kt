package com.example.data.repositoryimpl

import com.example.data.local.AnimeCacheDao
import com.example.data.local.FavoriteDao
import com.example.data.local.FavoriteEntity
import com.example.data.local.WatchProgressDao
import com.example.data.local.WatchProgressEntity
import com.example.data.mapper.HikariMappers.toCacheEntity
import com.example.data.mapper.HikariMappers.toDomain
import com.example.data.mapper.HikariMappers.toDomainOrNull
import com.example.domain.model.Anime
import com.example.domain.model.ContinueWatchingItem
import com.example.domain.model.FavoriteItem
import com.example.domain.model.MediaId
import com.example.domain.model.WatchProgress
import com.example.domain.repository.LibraryRepository
import com.example.domain.usecase.RecordWatchProgressUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class LibraryRepositoryImpl(
    private val favoriteDao: FavoriteDao,
    private val watchProgressDao: WatchProgressDao,
    private val animeCacheDao: AnimeCacheDao,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : LibraryRepository {

    override fun observeFavorites(): Flow<List<FavoriteItem>> {
        return favoriteDao.observeAllFavorites()
            .map { entities -> entities.map { it.toDomain() } }
            .distinctUntilChanged()
    }

    override fun observeFavoriteIds(): Flow<Set<String>> {
        return favoriteDao.observeAllFavorites()
            .map { entities ->
                entities.map { "${it.sourceId}:${it.sourceMediaId}" }.toSet()
            }
            .distinctUntilChanged()
    }

    override fun observeIsFavorite(mediaId: MediaId): Flow<Boolean> {
        return favoriteDao.observeIsFavorite(mediaId.sourceId, mediaId.value)
            .distinctUntilChanged()
    }

    override suspend fun toggleFavorite(anime: Anime): Boolean {
        val now = clockMs()
        // Also ensure anime is cached so offline details/history have rich metadata
        animeCacheDao.upsertAnimeCache(anime.toCacheEntity(now))

        val existing = favoriteDao.getFavorite(anime.id.sourceId, anime.id.value)
        return if (existing != null) {
            favoriteDao.deleteFavorite(anime.id.sourceId, anime.id.value)
            false
        } else {
            favoriteDao.insertFavorite(
                FavoriteEntity(
                    sourceId = anime.id.sourceId,
                    sourceMediaId = anime.id.value,
                    addedAtEpochMs = now,
                    titleSnapshot = anime.title,
                    posterSnapshot = anime.posterUrl
                )
            )
            true
        }
    }

    override suspend fun removeFavorite(mediaId: MediaId) {
        favoriteDao.deleteFavorite(mediaId.sourceId, mediaId.value)
    }

    override fun observeWatchProgressForAnime(mediaId: MediaId): Flow<List<WatchProgress>> {
        return watchProgressDao.observeProgressForAnime(mediaId.sourceId, mediaId.value)
            .map { list -> list.map { it.toDomain() } }
            .distinctUntilChanged()
    }

    override fun observeAllWatchHistory(): Flow<List<ContinueWatchingItem>> {
        return watchProgressDao.observeAllProgress()
            .map { entities ->
                entities.map { entity ->
                    val progress = entity.toDomain()
                    val anime = resolveAnimeSnapshot(entity.sourceId, entity.sourceMediaId)
                    ContinueWatchingItem(anime = anime, progress = progress)
                }
            }
            .distinctUntilChanged()
    }

    override fun observeContinueWatching(limit: Int): Flow<List<ContinueWatchingItem>> {
        return watchProgressDao.observeAllProgress()
            .map { entities ->
                // Group by title so Continue Watching shows 1 active entry per title:
                // either an incomplete episode, or if E(n) completed and E(n+1) exists, advance to E(n+1)
                val grouped = entities.groupBy { "${it.sourceId}:${it.sourceMediaId}" }
                val items = mutableListOf<ContinueWatchingItem>()

                for ((_, titleEntities) in grouped) {
                    val latest = titleEntities.maxByOrNull { it.updatedAtEpochMs } ?: continue
                    val anime = resolveAnimeSnapshot(latest.sourceId, latest.sourceMediaId)

                    if (!latest.completed) {
                        items.add(
                            ContinueWatchingItem(
                                anime = anime,
                                progress = latest.toDomain()
                            )
                        )
                    } else {
                        val totalEpisodes = anime.episodeCount ?: 0
                        val nextEpNumber = latest.episodeNumber + 1
                        val nextEpCompleted = titleEntities.any {
                            it.episodeNumber == nextEpNumber && it.completed
                        }
                        if (totalEpisodes >= nextEpNumber && !nextEpCompleted) {
                            val existingNext = titleEntities.find { it.episodeNumber == nextEpNumber }
                            val syntheticProgress = existingNext?.toDomain() ?: WatchProgress(
                                animeId = anime.id,
                                episodeNumber = nextEpNumber,
                                positionMs = 0L,
                                durationMs = latest.durationMs,
                                updatedAtEpochMs = latest.updatedAtEpochMs,
                                completed = false
                            )
                            items.add(
                                ContinueWatchingItem(
                                    anime = anime,
                                    progress = syntheticProgress
                                )
                            )
                        }
                    }
                }

                items.sortedByDescending { it.progress.updatedAtEpochMs }.take(limit)
            }
            .distinctUntilChanged()
    }

    private suspend fun resolveAnimeSnapshot(sourceId: String, sourceMediaId: String): Anime {
        val cached = animeCacheDao.getAnimeCache(sourceId, sourceMediaId)?.toDomainOrNull()
        if (cached != null) return cached

        val fav = favoriteDao.getFavorite(sourceId, sourceMediaId)
        if (fav != null) {
            return Anime(
                id = MediaId(sourceId = sourceId, value = sourceMediaId),
                title = fav.titleSnapshot,
                posterUrl = fav.posterSnapshot,
                synopsis = null
            )
        }

        val fallbackTitle = sourceMediaId.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase() else it.toString()
        }
        return Anime(
            id = MediaId(sourceId = sourceId, value = sourceMediaId),
            title = fallbackTitle,
            posterUrl = null,
            synopsis = null
        )
    }

    override suspend fun getWatchProgress(mediaId: MediaId, episodeNumber: Int): WatchProgress? {
        return watchProgressDao.getProgress(mediaId.sourceId, mediaId.value, episodeNumber)?.toDomain()
    }

    override suspend fun recordWatchProgress(
        anime: Anime,
        episodeNumber: Int,
        positionMs: Long,
        durationMs: Long
    ) {
        val now = clockMs()
        animeCacheDao.upsertAnimeCache(anime.toCacheEntity(now))

        val existing = watchProgressDao.getProgress(anime.id.sourceId, anime.id.value, episodeNumber)
        val isNowCompleted = RecordWatchProgressUseCase.isCompleted(positionMs, durationMs) ||
            (existing?.completed == true)

        watchProgressDao.upsertProgress(
            WatchProgressEntity(
                sourceId = anime.id.sourceId,
                sourceMediaId = anime.id.value,
                episodeNumber = episodeNumber,
                positionMs = positionMs,
                durationMs = if (durationMs > 0L) durationMs else (existing?.durationMs ?: 0L),
                updatedAtEpochMs = now,
                completed = isNowCompleted
            )
        )
    }

    override suspend fun removeWatchHistoryItem(mediaId: MediaId, episodeNumber: Int) {
        watchProgressDao.deleteProgressItem(mediaId.sourceId, mediaId.value, episodeNumber)
    }

    override suspend fun clearWatchHistory() {
        watchProgressDao.clearAllProgress()
    }
}
