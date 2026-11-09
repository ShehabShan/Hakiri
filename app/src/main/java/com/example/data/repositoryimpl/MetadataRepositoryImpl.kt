package com.example.data.repositoryimpl

import com.example.data.jikan.JikanApi
import com.example.data.jikan.JikanRateLimiter
import com.example.data.local.AnimeCacheDao
import com.example.data.local.JikanCacheDao
import com.example.data.local.JikanCacheEntity
import com.example.data.mapper.HikariMappers
import com.example.data.mapper.HikariMappers.toCacheEntity
import com.example.data.mapper.HikariMappers.toDomain
import com.example.domain.model.Anime
import com.example.domain.model.AppError
import com.example.domain.model.SourceResult
import com.example.domain.repository.MetadataRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

class MetadataRepositoryImpl(
    private val jikanApi: JikanApi,
    private val rateLimiter: JikanRateLimiter,
    private val jikanCacheDao: JikanCacheDao,
    private val animeCacheDao: AnimeCacheDao,
    private val clockMs: () -> Long = { System.currentTimeMillis() }
) : MetadataRepository {

    companion object {
        const val JIKAN_CACHE_TTL_MS = 24L * 60L * 60L * 1000L // 24 hours
        private const val KEY_TOP_ANIME = "top_anime_v1"
        private const val KEY_SEASON_NOW = "season_now_v1"
        private fun searchCacheKey(query: String) = "search_${query.trim().lowercase()}"
        private fun detailsCacheKey(malId: Long) = "anime_$malId"
    }

    override suspend fun getTrendingAnime(forceRefresh: Boolean): SourceResult<List<Anime>> {
        return fetchListWithCache(
            cacheKey = KEY_TOP_ANIME,
            forceRefresh = forceRefresh
        ) {
            jikanApi.getTopAnime(limit = 20).data.map { it.toDomain() }
        }
    }

    override suspend fun getSeasonalAnime(forceRefresh: Boolean): SourceResult<List<Anime>> {
        return fetchListWithCache(
            cacheKey = KEY_SEASON_NOW,
            forceRefresh = forceRefresh
        ) {
            jikanApi.getSeasonNow(limit = 20).data.map { it.toDomain() }
        }
    }

    override suspend fun searchMetadata(query: String): SourceResult<List<Anime>> {
        val cleaned = query.trim()
        if (cleaned.length < 2) return SourceResult.Success(emptyList())

        return fetchListWithCache(
            cacheKey = searchCacheKey(cleaned),
            forceRefresh = false
        ) {
            jikanApi.searchAnime(query = cleaned, limit = 20).data.map { it.toDomain() }
        }
    }

    override suspend fun getMetadataDetails(
        malId: Long,
        forceRefresh: Boolean
    ): SourceResult<Anime> {
        val now = clockMs()
        val key = detailsCacheKey(malId)
        val cached = jikanCacheDao.getCache(key)
        if (!forceRefresh && cached != null && now - cached.fetchedAtEpochMs <= JIKAN_CACHE_TTL_MS) {
            runCatching {
                HikariMappers.json.decodeFromString<Anime>(cached.json)
            }.getOrNull()?.let { return SourceResult.Success(it) }
        }

        val networkResult = rateLimiter.execute {
            val dto = jikanApi.getAnimeById(malId).data
                ?: throw IllegalStateException("Empty Jikan anime payload")
            dto.toDomain()
        }

        return when (networkResult) {
            is SourceResult.Success -> {
                val anime = networkResult.data
                jikanCacheDao.upsertCache(
                    JikanCacheEntity(
                        cacheKey = key,
                        json = HikariMappers.json.encodeToString(anime),
                        fetchedAtEpochMs = now
                    )
                )
                animeCacheDao.upsertAnimeCache(anime.toCacheEntity(now))
                SourceResult.Success(anime)
            }
            is SourceResult.Failure -> {
                if (cached != null) {
                    runCatching {
                        HikariMappers.json.decodeFromString<Anime>(cached.json)
                    }.getOrNull()?.let { return SourceResult.Success(it) }
                }
                networkResult
            }
        }
    }

    override suspend fun clearMetadataCache() {
        jikanCacheDao.clearAll()
    }

    private suspend fun fetchListWithCache(
        cacheKey: String,
        forceRefresh: Boolean,
        networkCall: suspend () -> List<Anime>
    ): SourceResult<List<Anime>> {
        val now = clockMs()
        val cached = jikanCacheDao.getCache(cacheKey)
        if (!forceRefresh && cached != null && now - cached.fetchedAtEpochMs <= JIKAN_CACHE_TTL_MS) {
            val decoded = runCatching {
                HikariMappers.json.decodeFromString<List<Anime>>(cached.json)
            }.getOrNull()
            if (!decoded.isNullOrEmpty()) {
                return SourceResult.Success(decoded)
            }
        }

        val result = rateLimiter.execute {
            networkCall()
        }

        return when (result) {
            is SourceResult.Success -> {
                val list = result.data.distinctBy { it.id.compositeKey }
                if (list.isNotEmpty()) {
                    jikanCacheDao.upsertCache(
                        JikanCacheEntity(
                            cacheKey = cacheKey,
                            json = HikariMappers.json.encodeToString(list),
                            fetchedAtEpochMs = now
                        )
                    )
                    animeCacheDao.upsertAllAnimeCache(list.map { it.toCacheEntity(now) })
                }
                SourceResult.Success(list)
            }
            is SourceResult.Failure -> {
                if (cached != null) {
                    val staleList = runCatching {
                        HikariMappers.json.decodeFromString<List<Anime>>(cached.json)
                    }.getOrNull()
                    if (!staleList.isNullOrEmpty()) {
                        return SourceResult.Success(staleList)
                    }
                }
                result
            }
        }
    }
}
