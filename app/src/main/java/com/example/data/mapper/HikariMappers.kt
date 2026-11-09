package com.example.data.mapper

import com.example.data.jikan.JikanAnimeDto
import com.example.data.local.AnimeCacheEntity
import com.example.data.local.EpisodeCacheEntity
import com.example.data.local.FavoriteEntity
import com.example.data.local.WatchProgressEntity
import com.example.domain.model.AiringStatus
import com.example.domain.model.Anime
import com.example.domain.model.Episode
import com.example.domain.model.FavoriteItem
import com.example.domain.model.MediaId
import com.example.domain.model.WatchProgress
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object HikariMappers {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun Anime.toCacheEntity(fetchedAtEpochMs: Long): AnimeCacheEntity {
        return AnimeCacheEntity(
            sourceId = id.sourceId,
            sourceMediaId = id.value,
            json = json.encodeToString(this),
            fetchedAtEpochMs = fetchedAtEpochMs
        )
    }

    fun AnimeCacheEntity.toDomainOrNull(): Anime? {
        return runCatching {
            HikariMappers.json.decodeFromString<Anime>(this.json)
        }.getOrNull()
    }

    fun List<Episode>.toCacheEntity(
        mediaId: MediaId,
        fetchedAtEpochMs: Long
    ): EpisodeCacheEntity {
        return EpisodeCacheEntity(
            sourceId = mediaId.sourceId,
            sourceMediaId = mediaId.value,
            json = HikariMappers.json.encodeToString(this),
            fetchedAtEpochMs = fetchedAtEpochMs
        )
    }

    fun EpisodeCacheEntity.toDomainOrNull(): List<Episode>? {
        return runCatching {
            HikariMappers.json.decodeFromString<List<Episode>>(this.json)
        }.getOrNull()
    }

    fun FavoriteEntity.toDomain(): FavoriteItem {
        return FavoriteItem(
            animeId = MediaId(sourceId = sourceId, value = sourceMediaId),
            title = titleSnapshot,
            posterUrl = posterSnapshot,
            addedAtEpochMs = addedAtEpochMs
        )
    }

    fun FavoriteItem.toFallbackAnime(): Anime {
        return Anime(
            id = animeId,
            title = title,
            posterUrl = posterUrl,
            synopsis = null
        )
    }

    fun WatchProgressEntity.toDomain(): WatchProgress {
        return WatchProgress(
            animeId = MediaId(sourceId = sourceId, value = sourceMediaId),
            episodeNumber = episodeNumber,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAtEpochMs = updatedAtEpochMs,
            completed = completed
        )
    }

    fun WatchProgress.toEntity(): WatchProgressEntity {
        return WatchProgressEntity(
            sourceId = animeId.sourceId,
            sourceMediaId = animeId.value,
            episodeNumber = episodeNumber,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAtEpochMs = updatedAtEpochMs,
            completed = completed
        )
    }

    fun JikanAnimeDto.toDomain(): Anime {
        val chosenTitle = titleEnglish?.takeIf { it.isNotBlank() }
            ?: title?.takeIf { it.isNotBlank() }
            ?: "Untitled (#$malId)"
        val altList = buildList {
            title?.takeIf { it.isNotBlank() && it != chosenTitle }?.let { add(it) }
            titleJapanese?.takeIf { it.isNotBlank() }?.let { add(it) }
            addAll(titleSynonyms)
        }.distinct()

        val mappedStatus = when {
            status?.contains("Currently Airing", ignoreCase = true) == true -> AiringStatus.AIRING
            status?.contains("Finished Airing", ignoreCase = true) == true -> AiringStatus.FINISHED
            status?.contains("Not yet aired", ignoreCase = true) == true -> AiringStatus.UPCOMING
            airing == true -> AiringStatus.AIRING
            else -> AiringStatus.UNKNOWN
        }

        val largeImage = images?.webp?.largeImageUrl
            ?: images?.jpg?.largeImageUrl
            ?: images?.webp?.imageUrl
            ?: images?.jpg?.imageUrl

        val bannerImage = trailer?.images?.maximumImageUrl
            ?: trailer?.images?.largeImageUrl
            ?: largeImage

        return Anime(
            id = MediaId(sourceId = "jikan", value = malId.toString()),
            title = chosenTitle,
            altTitles = altList,
            posterUrl = largeImage,
            bannerUrl = bannerImage,
            synopsis = synopsis?.trim(),
            genres = genres.mapNotNull { it.name?.takeIf(String::isNotBlank) },
            score = score,
            year = year ?: aired?.prop?.from?.year,
            status = mappedStatus,
            episodeCount = episodes,
            malId = malId,
            attribution = "Metadata provided by Jikan v4 (MyAnimeList)."
        )
    }
}
