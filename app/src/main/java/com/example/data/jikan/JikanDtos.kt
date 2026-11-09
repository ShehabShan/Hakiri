package com.example.data.jikan

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JikanListResponse(
    val data: List<JikanAnimeDto> = emptyList()
)

@Serializable
data class JikanSingleResponse(
    val data: JikanAnimeDto? = null
)

@Serializable
data class JikanAnimeDto(
    @SerialName("mal_id") val malId: Long,
    val title: String? = null,
    @SerialName("title_english") val titleEnglish: String? = null,
    @SerialName("title_japanese") val titleJapanese: String? = null,
    @SerialName("title_synonyms") val titleSynonyms: List<String> = emptyList(),
    val synopsis: String? = null,
    val score: Double? = null,
    val year: Int? = null,
    val status: String? = null,
    val airing: Boolean? = null,
    val episodes: Int? = null,
    val images: JikanImagesDto? = null,
    val trailer: JikanTrailerDto? = null,
    val genres: List<JikanNamedResourceDto> = emptyList(),
    val aired: JikanAiredDto? = null
)

@Serializable
data class JikanImagesDto(
    val jpg: JikanImageSetDto? = null,
    val webp: JikanImageSetDto? = null
)

@Serializable
data class JikanImageSetDto(
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("small_image_url") val smallImageUrl: String? = null,
    @SerialName("large_image_url") val largeImageUrl: String? = null
)

@Serializable
data class JikanTrailerDto(
    val images: JikanTrailerImagesDto? = null
)

@Serializable
data class JikanTrailerImagesDto(
    @SerialName("large_image_url") val largeImageUrl: String? = null,
    @SerialName("maximum_image_url") val maximumImageUrl: String? = null
)

@Serializable
data class JikanNamedResourceDto(
    @SerialName("mal_id") val malId: Long? = null,
    val name: String? = null
)

@Serializable
data class JikanAiredDto(
    val prop: JikanAiredPropDto? = null
)

@Serializable
data class JikanAiredPropDto(
    val from: JikanAiredDateDto? = null
)

@Serializable
data class JikanAiredDateDto(
    val year: Int? = null
)
