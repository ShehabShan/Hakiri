package com.example.data.source.openfilms

import android.content.Context
import com.example.data.mapper.HikariMappers
import com.example.data.source.AnimeSource
import com.example.domain.model.AiringStatus
import com.example.domain.model.Anime
import com.example.domain.model.AppError
import com.example.domain.model.Episode
import com.example.domain.model.MediaId
import com.example.domain.model.SourceCapability
import com.example.domain.model.SourceInfo
import com.example.domain.model.SourceResult
import com.example.domain.model.StreamKind
import com.example.domain.model.StreamLink
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString

@Serializable
data class OpenFilmsCatalogDto(
    val sourceId: String = "openfilms",
    val attribution: String = "",
    val items: List<OpenFilmItemDto> = emptyList()
)

@Serializable
data class OpenFilmItemDto(
    val id: String,
    val title: String,
    val altTitles: List<String> = emptyList(),
    val synopsis: String? = null,
    val genres: List<String> = emptyList(),
    val year: Int? = null,
    val status: String = "FINISHED",
    val posterUrl: String? = null,
    val episodes: List<OpenFilmEpisodeDto> = emptyList()
)

@Serializable
data class OpenFilmEpisodeDto(
    val number: Int,
    val title: String? = null,
    val durationSeconds: Long? = null,
    val streams: List<OpenFilmStreamDto> = emptyList()
)

@Serializable
data class OpenFilmStreamDto(
    val url: String,
    val kind: String = "MP4",
    val qualityLabel: String = "auto",
    val headers: Map<String, String> = emptyMap()
)

class OpenFilmsSource(
    private val jsonProvider: suspend () -> String
) : AnimeSource {

    constructor(context: Context, assetFileName: String = "open_films_catalog.json") : this(
        jsonProvider = {
            context.assets.open(assetFileName).bufferedReader().use { it.readText() }
        }
    )

    companion object {
        const val SOURCE_ID = "openfilms"
    }

    override val info: SourceInfo = SourceInfo(
        id = SOURCE_ID,
        displayName = "Open Films",
        language = "en",
        version = 1,
        capabilities = setOf(
            SourceCapability.SEARCH,
            SourceCapability.MULTI_QUALITY
        )
    )

    private suspend fun loadCatalogDto(): SourceResult<OpenFilmsCatalogDto> {
        return try {
            val rawJson = jsonProvider()
            if (rawJson.isBlank()) {
                return SourceResult.Failure(AppError.ParseFailure(SOURCE_ID, "Empty catalog JSON"))
            }
            val parsed = HikariMappers.json.decodeFromString<OpenFilmsCatalogDto>(rawJson)
            if (parsed.items.isEmpty()) {
                return SourceResult.Failure(AppError.ParseFailure(SOURCE_ID, "No items in catalog"))
            }
            SourceResult.Success(parsed)
        } catch (e: Exception) {
            SourceResult.Failure(
                AppError.ParseFailure(
                    sourceId = SOURCE_ID,
                    what = e.message ?: "Invalid catalog JSON"
                )
            )
        }
    }

    private fun OpenFilmItemDto.toDomainAnime(catalogAttribution: String): Anime {
        val mappedStatus = when (status.uppercase()) {
            "AIRING" -> AiringStatus.AIRING
            "FINISHED" -> AiringStatus.FINISHED
            "UPCOMING" -> AiringStatus.UPCOMING
            else -> AiringStatus.UNKNOWN
        }
        return Anime(
            id = MediaId(sourceId = SOURCE_ID, value = id),
            title = title,
            altTitles = altTitles,
            posterUrl = posterUrl,
            bannerUrl = posterUrl,
            synopsis = synopsis,
            genres = genres,
            score = null,
            year = year,
            status = mappedStatus,
            episodeCount = episodes.size,
            malId = null,
            attribution = catalogAttribution.ifBlank {
                "Films © Blender Foundation, CC BY 3.0 — durian.blender.org / bigbuckbunny.org."
            }
        )
    }

    override suspend fun browseCatalog(page: Int): SourceResult<List<Anime>> {
        if (page > 1) return SourceResult.Success(emptyList())
        return when (val catalogResult = loadCatalogDto()) {
            is SourceResult.Failure -> catalogResult
            is SourceResult.Success -> {
                val dto = catalogResult.data
                SourceResult.Success(
                    dto.items.map { it.toDomainAnime(dto.attribution) }
                )
            }
        }
    }

    override suspend fun search(query: String, page: Int): SourceResult<List<Anime>> {
        if (page > 1) return SourceResult.Success(emptyList())
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return SourceResult.Success(emptyList())

        return when (val catalogResult = loadCatalogDto()) {
            is SourceResult.Failure -> catalogResult
            is SourceResult.Success -> {
                val dto = catalogResult.data
                val matched = dto.items.filter { item ->
                    item.title.contains(trimmed, ignoreCase = true) ||
                        item.altTitles.any { alt -> alt.contains(trimmed, ignoreCase = true) } ||
                        item.genres.any { genre -> genre.contains(trimmed, ignoreCase = true) }
                }.map { it.toDomainAnime(dto.attribution) }
                SourceResult.Success(matched)
            }
        }
    }

    override suspend fun getDetails(mediaId: String): SourceResult<Anime> {
        return when (val catalogResult = loadCatalogDto()) {
            is SourceResult.Failure -> catalogResult
            is SourceResult.Success -> {
                val dto = catalogResult.data
                val item = dto.items.firstOrNull { it.id.equals(mediaId, ignoreCase = true) }
                    ?: return SourceResult.Failure(AppError.NotFound)
                SourceResult.Success(item.toDomainAnime(dto.attribution))
            }
        }
    }

    override suspend fun getEpisodes(mediaId: String): SourceResult<List<Episode>> {
        return when (val catalogResult = loadCatalogDto()) {
            is SourceResult.Failure -> catalogResult
            is SourceResult.Success -> {
                val dto = catalogResult.data
                val item = dto.items.firstOrNull { it.id.equals(mediaId, ignoreCase = true) }
                    ?: return SourceResult.Failure(AppError.NotFound)
                val domainId = MediaId(sourceId = SOURCE_ID, value = item.id)
                val episodes = item.episodes.map { ep ->
                    Episode(
                        animeId = domainId,
                        number = ep.number,
                        title = ep.title,
                        thumbnailUrl = item.posterUrl,
                        durationSeconds = ep.durationSeconds
                    )
                }.sortedBy { it.number }
                SourceResult.Success(episodes)
            }
        }
    }

    override suspend fun getStreams(
        mediaId: String,
        episodeNumber: Int
    ): SourceResult<List<StreamLink>> {
        return when (val catalogResult = loadCatalogDto()) {
            is SourceResult.Failure -> catalogResult
            is SourceResult.Success -> {
                val dto = catalogResult.data
                val item = dto.items.firstOrNull { it.id.equals(mediaId, ignoreCase = true) }
                    ?: return SourceResult.Failure(AppError.NotFound)
                val episode = item.episodes.firstOrNull { it.number == episodeNumber }
                    ?: return SourceResult.Failure(AppError.NotFound)
                if (episode.streams.isEmpty()) {
                    return SourceResult.Failure(AppError.NotFound)
                }
                val links = episode.streams.map { streamDto ->
                    val kind = if (
                        streamDto.kind.equals("HLS", ignoreCase = true) ||
                        streamDto.url.endsWith(".m3u8", ignoreCase = true)
                    ) {
                        StreamKind.HLS
                    } else {
                        StreamKind.MP4
                    }
                    StreamLink(
                        url = streamDto.url,
                        kind = kind,
                        qualityLabel = streamDto.qualityLabel,
                        headers = streamDto.headers
                    )
                }
                SourceResult.Success(links)
            }
        }
    }
}
