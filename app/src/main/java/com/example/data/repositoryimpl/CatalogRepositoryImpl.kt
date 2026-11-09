package com.example.data.repositoryimpl

import com.example.data.local.AnimeCacheDao
import com.example.data.local.EpisodeCacheDao
import com.example.data.local.JikanCacheDao
import com.example.data.mapper.HikariMappers.toCacheEntity
import com.example.data.mapper.HikariMappers.toDomainOrNull
import com.example.data.source.SourceRegistry
import com.example.domain.model.Anime
import com.example.domain.model.AppError
import com.example.domain.model.Episode
import com.example.domain.model.MediaId
import com.example.domain.model.SourceInfo
import com.example.domain.model.SourceResult
import com.example.domain.model.StreamLink
import com.example.domain.repository.CatalogRepository
import com.example.domain.repository.MetadataRepository
import com.example.domain.repository.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull

class CatalogRepositoryImpl(
    private val sourceRegistry: SourceRegistry,
    private val animeCacheDao: AnimeCacheDao,
    private val episodeCacheDao: EpisodeCacheDao,
    private val jikanCacheDao: JikanCacheDao,
    private val settingsRepository: SettingsRepository,
    private val metadataRepository: MetadataRepository,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : CatalogRepository {

    companion object {
        const val CATALOG_TTL_MS = 24L * 60L * 60L * 1000L // 24 hours
        const val EPISODE_TTL_MS = 6L * 60L * 60L * 1000L  // 6 hours
        const val SOURCE_TIMEOUT_MS = 10_000L              // 10 seconds per source
    }

    override fun getRegisteredSources(): List<SourceInfo> {
        return sourceRegistry.getAllRegisteredSources()
    }

    override suspend fun browseSource(
        sourceId: String,
        page: Int,
        forceRefresh: Boolean
    ): SourceResult<List<Anime>> {
        val now = clockMs()
        if (!forceRefresh && page == 1) {
            val cachedEntities = animeCacheDao.getAllBySource(sourceId)
            val freshCached = cachedEntities
                .filter { now - it.fetchedAtEpochMs <= CATALOG_TTL_MS }
                .mapNotNull { it.toDomainOrNull() }
            if (freshCached.isNotEmpty()) {
                return SourceResult.Success(freshCached)
            }
        }

        val source = sourceRegistry.getSourceById(sourceId)
            ?: return SourceResult.Failure(AppError.SourceUnavailable(sourceId))

        val result = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
            source.browseCatalog(page)
        } ?: SourceResult.Failure(AppError.SourceUnavailable(sourceId))

        return when (result) {
            is SourceResult.Success -> {
                sourceRegistry.health.recordSuccess(sourceId)
                val entities = result.data.map { it.toCacheEntity(now) }
                animeCacheDao.upsertAllAnimeCache(entities)
                result
            }
            is SourceResult.Failure -> {
                sourceRegistry.health.recordFailure(sourceId)
                // Serve stale cache if available
                val stale = animeCacheDao.getAllBySource(sourceId).mapNotNull { it.toDomainOrNull() }
                if (stale.isNotEmpty()) {
                    SourceResult.Success(stale)
                } else {
                    result
                }
            }
        }
    }

    override suspend fun searchEnabledSources(
        query: String,
        page: Int
    ): SourceResult<List<Anime>> = supervisorScope {
        val settings = settingsRepository.settingsFlow.first()
        val enabledSources = sourceRegistry.getEnabledSources(settings.enabledSources)
        if (enabledSources.isEmpty()) {
            return@supervisorScope SourceResult.Success(emptyList())
        }

        val deferredResults = enabledSources.map { source ->
            async {
                val res = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
                    source.search(query, page)
                } ?: SourceResult.Failure(AppError.SourceUnavailable(source.info.id))
                source.info.id to res
            }
        }

        val allMatches = mutableListOf<Anime>()
        var firstError: AppError? = null
        var anySucceeded = false
        val now = clockMs()

        for (deferred in deferredResults) {
            val (sourceId, res) = deferred.await()
            when (res) {
                is SourceResult.Success -> {
                    anySucceeded = true
                    sourceRegistry.health.recordSuccess(sourceId)
                    animeCacheDao.upsertAllAnimeCache(res.data.map { it.toCacheEntity(now) })
                    allMatches.addAll(res.data)
                }
                is SourceResult.Failure -> {
                    sourceRegistry.health.recordFailure(sourceId)
                    if (firstError == null) firstError = res.error
                }
            }
        }

        if (anySucceeded) {
            SourceResult.Success(allMatches.distinctBy { it.id.compositeKey })
        } else {
            SourceResult.Failure(firstError ?: AppError.Network)
        }
    }

    override suspend fun searchCachedCatalog(query: String): List<Anime> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        return animeCacheDao.getAllCachedAnime()
            .mapNotNull { it.toDomainOrNull() }
            .filter { anime ->
                anime.title.contains(trimmed, ignoreCase = true) ||
                    anime.altTitles.any { it.contains(trimmed, ignoreCase = true) } ||
                    anime.genres.any { it.contains(trimmed, ignoreCase = true) }
            }
            .distinctBy { it.id.compositeKey }
    }

    override suspend fun getDetails(
        mediaId: MediaId,
        forceRefresh: Boolean
    ): SourceResult<Anime> {
        if (mediaId.sourceId == "jikan") {
            val malId = mediaId.value.toLongOrNull()
                ?: return SourceResult.Failure(AppError.NotFound)
            return metadataRepository.getMetadataDetails(malId, forceRefresh)
        }

        val now = clockMs()
        val cached = animeCacheDao.getAnimeCache(mediaId.sourceId, mediaId.value)
        if (!forceRefresh && cached != null && now - cached.fetchedAtEpochMs <= CATALOG_TTL_MS) {
            cached.toDomainOrNull()?.let { return SourceResult.Success(it) }
        }

        val source = sourceRegistry.getSourceById(mediaId.sourceId)
            ?: return cached?.toDomainOrNull()?.let { SourceResult.Success(it) }
                ?: SourceResult.Failure(AppError.SourceUnavailable(mediaId.sourceId))

        val result = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
            source.getDetails(mediaId.value)
        } ?: SourceResult.Failure(AppError.SourceUnavailable(mediaId.sourceId))

        return when (result) {
            is SourceResult.Success -> {
                sourceRegistry.health.recordSuccess(mediaId.sourceId)
                animeCacheDao.upsertAnimeCache(result.data.toCacheEntity(now))
                result
            }
            is SourceResult.Failure -> {
                sourceRegistry.health.recordFailure(mediaId.sourceId)
                cached?.toDomainOrNull()?.let { SourceResult.Success(it) } ?: result
            }
        }
    }

    override suspend fun getEpisodes(
        mediaId: MediaId,
        forceRefresh: Boolean
    ): SourceResult<List<Episode>> {
        if (mediaId.sourceId == "jikan") {
            return SourceResult.Success(emptyList())
        }

        val now = clockMs()
        val cached = episodeCacheDao.getEpisodeCache(mediaId.sourceId, mediaId.value)
        if (!forceRefresh && cached != null && now - cached.fetchedAtEpochMs <= EPISODE_TTL_MS) {
            cached.toDomainOrNull()?.let { return SourceResult.Success(it) }
        }

        val source = sourceRegistry.getSourceById(mediaId.sourceId)
            ?: return cached?.toDomainOrNull()?.let { SourceResult.Success(it) }
                ?: SourceResult.Failure(AppError.SourceUnavailable(mediaId.sourceId))

        val result = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
            source.getEpisodes(mediaId.value)
        } ?: SourceResult.Failure(AppError.SourceUnavailable(mediaId.sourceId))

        return when (result) {
            is SourceResult.Success -> {
                sourceRegistry.health.recordSuccess(mediaId.sourceId)
                episodeCacheDao.upsertEpisodeCache(result.data.toCacheEntity(mediaId, now))
                result
            }
            is SourceResult.Failure -> {
                sourceRegistry.health.recordFailure(mediaId.sourceId)
                cached?.toDomainOrNull()?.let { SourceResult.Success(it) } ?: result
            }
        }
    }

    override suspend fun getStreams(
        mediaId: MediaId,
        episodeNumber: Int
    ): SourceResult<List<StreamLink>> {
        val source = sourceRegistry.getSourceById(mediaId.sourceId)
            ?: return SourceResult.Failure(AppError.SourceUnavailable(mediaId.sourceId))

        val result = withTimeoutOrNull(SOURCE_TIMEOUT_MS) {
            source.getStreams(mediaId.value, episodeNumber)
        } ?: SourceResult.Failure(AppError.SourceUnavailable(mediaId.sourceId))

        when (result) {
            is SourceResult.Success -> sourceRegistry.health.recordSuccess(mediaId.sourceId)
            is SourceResult.Failure -> sourceRegistry.health.recordFailure(mediaId.sourceId)
        }
        return result
    }

    override suspend fun clearCatalogCache() {
        animeCacheDao.clearAll()
        episodeCacheDao.clearAll()
        jikanCacheDao.clearAll()
    }

    override suspend fun getEstimatedCacheSizeBytes(): Long {
        val animeBytes = animeCacheDao.getAllCachedAnime().sumOf { it.json.toByteArray().size.toLong() }
        val jikanBytes = jikanCacheDao.getAllCacheEntries().sumOf { it.json.toByteArray().size.toLong() }
        return animeBytes + jikanBytes
    }
}
