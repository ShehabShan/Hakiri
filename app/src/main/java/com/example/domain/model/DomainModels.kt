package com.example.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class MediaId(
    val sourceId: String,
    val value: String
) {
    val compositeKey: String get() = "$sourceId:$value"
}

@Serializable
enum class AiringStatus {
    AIRING,
    FINISHED,
    UPCOMING,
    UNKNOWN
}

@Serializable
data class Anime(
    val id: MediaId,
    val title: String,
    val altTitles: List<String> = emptyList(),
    val posterUrl: String? = null,
    val bannerUrl: String? = null,
    val synopsis: String? = null,
    val genres: List<String> = emptyList(),
    val score: Double? = null,
    val year: Int? = null,
    val status: AiringStatus = AiringStatus.UNKNOWN,
    val episodeCount: Int? = null,
    val malId: Long? = null,
    val attribution: String? = null
)

@Serializable
data class Episode(
    val animeId: MediaId,
    val number: Int,
    val title: String?,
    val thumbnailUrl: String? = null,
    val durationSeconds: Long? = null
)

@Serializable
enum class StreamKind {
    MP4,
    HLS
}

@Serializable
data class StreamLink(
    val url: String,
    val kind: StreamKind,
    val qualityLabel: String,
    val headers: Map<String, String> = emptyMap()
)

enum class SourceCapability {
    SEARCH,
    PAGINATED_BROWSE,
    MULTI_QUALITY
}

data class SourceInfo(
    val id: String,
    val displayName: String,
    val language: String,
    val version: Int,
    val capabilities: Set<SourceCapability>
)

data class WatchProgress(
    val animeId: MediaId,
    val episodeNumber: Int,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtEpochMs: Long,
    val completed: Boolean
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) {
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

    val remainingMinutes: Int
        get() = if (durationMs > positionMs) {
            (((durationMs - positionMs) / 1000L) / 60L).toInt().coerceAtLeast(1)
        } else {
            0
        }
}

data class ContinueWatchingItem(
    val anime: Anime,
    val progress: WatchProgress
)

data class FavoriteItem(
    val animeId: MediaId,
    val title: String,
    val posterUrl: String?,
    val addedAtEpochMs: Long
)

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

data class UserSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val autoplayNext: Boolean = true,
    val resumeAutomatically: Boolean = true,
    val enabledSources: Set<String> = setOf("openfilms"),
    val preferredQuality: String? = null
)

sealed interface AppError {
    data object Network : AppError
    data class SourceUnavailable(val sourceId: String) : AppError
    data class ParseFailure(val sourceId: String, val what: String) : AppError
    data object RateLimited : AppError
    data object NotFound : AppError
    data class Unknown(val cause: String?) : AppError
}

sealed interface SourceResult<out T> {
    data class Success<T>(val data: T) : SourceResult<T>
    data class Failure(val error: AppError) : SourceResult<Nothing>
}

sealed interface HomeRowContent {
    data class ContinueWatching(val items: List<ContinueWatchingItem>) : HomeRowContent
    data class AnimeList(val items: List<Anime>, val isFromCache: Boolean = false) : HomeRowContent
}

sealed interface HomeRowState {
    data object Loading : HomeRowState
    data class Content(val content: HomeRowContent) : HomeRowState
    data class Error(val error: AppError) : HomeRowState
}

data class HomeFeedRow(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val state: HomeRowState
)

data class HomeFeed(
    val rows: List<HomeFeedRow>,
    val isOffline: Boolean = false
)
