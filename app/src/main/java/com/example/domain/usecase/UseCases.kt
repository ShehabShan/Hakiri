package com.example.domain.usecase

import com.example.domain.model.Anime
import com.example.domain.model.AppError
import com.example.domain.model.Episode
import com.example.domain.model.HomeFeedRow
import com.example.domain.model.HomeRowContent
import com.example.domain.model.HomeRowState
import com.example.domain.model.MediaId
import com.example.domain.model.SourceResult
import com.example.domain.model.StreamLink
import com.example.domain.model.WatchProgress
import com.example.domain.repository.CatalogRepository
import com.example.domain.repository.LibraryRepository
import com.example.domain.repository.MetadataRepository
import com.example.domain.repository.SettingsRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

class ToggleFavoriteUseCase(
    private val libraryRepository: LibraryRepository
) {
    suspend operator fun invoke(anime: Anime): Boolean {
        return libraryRepository.toggleFavorite(anime)
    }
}

class RecordWatchProgressUseCase(
    private val libraryRepository: LibraryRepository
) {
    companion object {
        const val COMPLETED_THRESHOLD_RATIO = 0.90f
        const val MIN_RESUME_POSITION_MS = 10_000L
        const val MAX_RESUME_RATIO = 0.95f

        fun isCompleted(positionMs: Long, durationMs: Long): Boolean {
            if (durationMs <= 0L) return false
            return positionMs.toFloat() / durationMs.toFloat() >= COMPLETED_THRESHOLD_RATIO
        }

        fun shouldResumeFromSaved(positionMs: Long, durationMs: Long): Boolean {
            if (positionMs < MIN_RESUME_POSITION_MS) return false
            if (durationMs <= 0L) return true
            val ratio = positionMs.toFloat() / durationMs.toFloat()
            return ratio < MAX_RESUME_RATIO
        }

        fun resolveActiveEpisode(
            episodes: List<Episode>,
            progressList: List<WatchProgress>
        ): Pair<Int, WatchProgress?> {
            if (episodes.isEmpty()) return 1 to null
            val sortedEpisodes = episodes.sortedBy { it.number }
            val latestProgress = progressList.maxByOrNull { it.updatedAtEpochMs }
                ?: return sortedEpisodes.first().number to null

            return if (latestProgress.completed) {
                val nextEp = sortedEpisodes.firstOrNull { it.number > latestProgress.episodeNumber }
                if (nextEp != null) {
                    val nextProgress = progressList.find { it.episodeNumber == nextEp.number }
                    nextEp.number to nextProgress
                } else {
                    latestProgress.episodeNumber to latestProgress
                }
            } else {
                latestProgress.episodeNumber to latestProgress
            }
        }
    }

    suspend operator fun invoke(
        anime: Anime,
        episodeNumber: Int,
        positionMs: Long,
        durationMs: Long
    ) {
        if (durationMs <= 0L && positionMs <= 0L) return
        libraryRepository.recordWatchProgress(
            anime = anime,
            episodeNumber = episodeNumber,
            positionMs = positionMs.coerceAtLeast(0L),
            durationMs = durationMs.coerceAtLeast(0L)
        )
    }
}

data class ResolvedPlaybackSession(
    val selectedStream: StreamLink,
    val availableStreams: List<StreamLink>,
    val sanitizedHeaders: Map<String, String>,
    val resumePositionMs: Long
)

class ResolvePlayableStreamUseCase(
    private val catalogRepository: CatalogRepository,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        val ALLOWED_HEADERS = setOf("referer", "user-agent", "origin")

        fun sanitizeHeaders(headers: Map<String, String>): Map<String, String> {
            return headers.filterKeys { key ->
                key.lowercase() in ALLOWED_HEADERS
            }
        }

        fun selectBestStream(
            streams: List<StreamLink>,
            preferredQuality: String?
        ): StreamLink? {
            if (streams.isEmpty()) return null
            if (!preferredQuality.isNullOrBlank()) {
                streams.firstOrNull {
                    it.qualityLabel.equals(preferredQuality, ignoreCase = true)
                }?.let { return it }
            }
            // Prefer highest numeric quality (1080p > 720p > 480p > auto)
            return streams.maxByOrNull { stream ->
                val digits = stream.qualityLabel.filter { it.isDigit() }.toIntOrNull() ?: 0
                val surroundBonus = if (stream.qualityLabel.contains("surround", ignoreCase = true)) 1 else 0
                digits * 10 + surroundBonus
            } ?: streams.first()
        }
    }

    suspend operator fun invoke(
        mediaId: MediaId,
        episodeNumber: Int,
        requestedQualityLabel: String? = null
    ): SourceResult<ResolvedPlaybackSession> {
        return when (val streamsResult = catalogRepository.getStreams(mediaId, episodeNumber)) {
            is SourceResult.Failure -> streamsResult
            is SourceResult.Success -> {
                val streams = streamsResult.data
                val settings = settingsRepository.settingsFlow.first()
                val targetQuality = requestedQualityLabel ?: settings.preferredQuality
                val chosen = selectBestStream(streams, targetQuality)
                    ?: return SourceResult.Failure(AppError.NotFound)

                val savedProgress = libraryRepository.getWatchProgress(mediaId, episodeNumber)
                val resumePositionMs = if (
                    settings.resumeAutomatically &&
                    savedProgress != null &&
                    RecordWatchProgressUseCase.shouldResumeFromSaved(
                        savedProgress.positionMs,
                        savedProgress.durationMs
                    )
                ) {
                    savedProgress.positionMs
                } else {
                    0L
                }

                SourceResult.Success(
                    ResolvedPlaybackSession(
                        selectedStream = chosen,
                        availableStreams = streams,
                        sanitizedHeaders = sanitizeHeaders(chosen.headers),
                        resumePositionMs = resumePositionMs
                    )
                )
            }
        }
    }
}

class BuildHomeFeedUseCase(
    private val catalogRepository: CatalogRepository,
    private val metadataRepository: MetadataRepository,
    private val settingsRepository: SettingsRepository
) {
    companion object {
        const val ROW_CONTINUE = "continue_watching"
        const val ROW_TRENDING = "trending_jikan"
        const val ROW_SEASONAL = "seasonal_jikan"
        fun sourceRowId(sourceId: String) = "source_$sourceId"
    }

    suspend fun loadCuratedAndSourceRows(forceRefresh: Boolean = false): List<HomeFeedRow> = coroutineScope {
        val settings = settingsRepository.settingsFlow.first()
        val enabledSources = catalogRepository.getRegisteredSources()
            .filter { it.id in settings.enabledSources }

        val sourceJobs = enabledSources.map { sourceInfo ->
            async {
                val result = catalogRepository.browseSource(sourceInfo.id, page = 1, forceRefresh = forceRefresh)
                val rowState = when (result) {
                    is SourceResult.Success -> HomeRowState.Content(HomeRowContent.AnimeList(result.data))
                    is SourceResult.Failure -> HomeRowState.Error(result.error)
                }
                HomeFeedRow(
                    id = sourceRowId(sourceInfo.id),
                    title = "${sourceInfo.displayName} (Playable)",
                    subtitle = "Creative Commons Open Movies · Instant Stream",
                    state = rowState
                )
            }
        }

        val trendingDeferred = async {
            val result = metadataRepository.getTrendingAnime(forceRefresh = forceRefresh)
            val state = when (result) {
                is SourceResult.Success -> HomeRowState.Content(HomeRowContent.AnimeList(result.data))
                is SourceResult.Failure -> HomeRowState.Error(result.error)
            }
            HomeFeedRow(
                id = ROW_TRENDING,
                title = "Trending Now",
                subtitle = "Top rated on MyAnimeList via Jikan",
                state = state
            )
        }

        val seasonalDeferred = async {
            val result = metadataRepository.getSeasonalAnime(forceRefresh = forceRefresh)
            val state = when (result) {
                is SourceResult.Success -> HomeRowState.Content(HomeRowContent.AnimeList(result.data))
                is SourceResult.Failure -> HomeRowState.Error(result.error)
            }
            HomeFeedRow(
                id = ROW_SEASONAL,
                title = "This Season",
                subtitle = "Currently airing seasonal lineup",
                state = state
            )
        }

        val sourceRows = sourceJobs.map { it.await() }
        val trendingRow = trendingDeferred.await()
        val seasonalRow = seasonalDeferred.await()

        // Put playable Open Films row first among catalog rows so users can test video streaming immediately,
        // followed by Trending Now and This Season from Jikan.
        sourceRows + listOf(trendingRow, seasonalRow)
    }
}
