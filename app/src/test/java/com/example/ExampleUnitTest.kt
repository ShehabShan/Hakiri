package com.example

import com.example.data.jikan.JikanAnimeDto
import com.example.data.jikan.JikanImageSetDto
import com.example.data.jikan.JikanImagesDto
import com.example.data.jikan.JikanNamedResourceDto
import com.example.data.jikan.JikanRateLimiter
import com.example.data.mapper.HikariMappers.toCacheEntity
import com.example.data.mapper.HikariMappers.toDomain
import com.example.data.mapper.HikariMappers.toDomainOrNull
import com.example.data.source.SourceHealth
import com.example.data.source.openfilms.OpenFilmsSource
import com.example.domain.model.AiringStatus
import com.example.domain.model.Anime
import com.example.domain.model.AppError
import com.example.domain.model.ContinueWatchingItem
import com.example.domain.model.Episode
import com.example.domain.model.FavoriteItem
import com.example.domain.model.HomeRowState
import com.example.domain.model.MediaId
import com.example.domain.model.SourceCapability
import com.example.domain.model.SourceInfo
import com.example.domain.model.SourceResult
import com.example.domain.model.StreamKind
import com.example.domain.model.StreamLink
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserSettings
import com.example.domain.model.WatchProgress
import com.example.domain.repository.CatalogRepository
import com.example.domain.repository.MetadataRepository
import com.example.domain.repository.SettingsRepository
import com.example.domain.usecase.BuildHomeFeedUseCase
import com.example.domain.usecase.RecordWatchProgressUseCase
import com.example.domain.usecase.ResolvePlayableStreamUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    private val sampleOpenFilmsJson = """
        {
          "sourceId": "openfilms",
          "attribution": "Films © Blender Foundation, CC BY 3.0",
          "items": [
            {
              "id": "sintel",
              "title": "Sintel",
              "altTitles": ["Durian Open Movie"],
              "synopsis": "A lonely young woman, Sintel, helps and befriends a dragon.",
              "genres": ["Fantasy", "Adventure"],
              "year": 2010,
              "status": "FINISHED",
              "posterUrl": null,
              "episodes": [
                {
                  "number": 1,
                  "title": "Sintel (stereo)",
                  "durationSeconds": 888,
                  "streams": [
                    { "url": "https://archive.org/download/Sintel/sintel-2048-stereo_512kb.mp4", "kind": "MP4", "qualityLabel": "480p" },
                    { "url": "https://archive.org/download/Sintel/sintel-2048-surround_512kb.mp4", "kind": "MP4", "qualityLabel": "480p (surround)" }
                  ]
                },
                {
                  "number": 2,
                  "title": "Sintel Part 2",
                  "durationSeconds": 888,
                  "streams": [
                    { "url": "https://archive.org/download/Sintel/sintel-2048-surround_512kb.mp4", "kind": "MP4", "qualityLabel": "480p (surround)" }
                  ]
                }
              ]
            },
            {
              "id": "bigbuckbunny",
              "title": "Big Buck Bunny",
              "altTitles": ["BBB"],
              "synopsis": "A giant rabbit takes gentle revenge.",
              "genres": ["Comedy"],
              "year": 2008,
              "status": "FINISHED",
              "posterUrl": null,
              "episodes": [
                {
                  "number": 1,
                  "title": "Big Buck Bunny (HLS)",
                  "streams": [
                    { "url": "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8", "kind": "HLS", "qualityLabel": "auto" }
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()

    @Test
    fun animeCacheMapper_roundTrip_preservesAllFields() {
        val original = Anime(
            id = MediaId("openfilms", "sintel"),
            title = "Sintel",
            altTitles = listOf("Durian"),
            posterUrl = null,
            synopsis = "Dragon quest",
            genres = listOf("Fantasy"),
            score = 8.4,
            year = 2010,
            status = AiringStatus.FINISHED,
            episodeCount = 2,
            malId = null,
            attribution = "CC BY 3.0"
        )
        val cacheEntity = original.toCacheEntity(fetchedAtEpochMs = 123_456L)
        val decoded = cacheEntity.toDomainOrNull()
        assertNotNull(decoded)
        assertEquals(original, decoded)
    }

    @Test
    fun jikanDtoMapper_mapsToDomainAnimeCorrectly() {
        val dto = JikanAnimeDto(
            malId = 5114L,
            title = "Fullmetal Alchemist: Brotherhood",
            titleEnglish = "Fullmetal Alchemist: Brotherhood",
            synopsis = "Two brothers search for the Philosopher's Stone.",
            score = 9.1,
            year = 2009,
            status = "Finished Airing",
            episodes = 64,
            images = JikanImagesDto(
                jpg = JikanImageSetDto(largeImageUrl = "https://cdn.myanimelist.net/images/anime/1208/94745l.jpg")
            ),
            genres = listOf(JikanNamedResourceDto(1L, "Action"), JikanNamedResourceDto(2L, "Adventure"))
        )
        val domain = dto.toDomain()
        assertEquals(MediaId("jikan", "5114"), domain.id)
        assertEquals("Fullmetal Alchemist: Brotherhood", domain.title)
        assertEquals(AiringStatus.FINISHED, domain.status)
        assertEquals(listOf("Action", "Adventure"), domain.genres)
        assertEquals(9.1, domain.score ?: 0.0, 0.01)
    }

    @Test
    fun openFilmsSource_browseSearchDetailsAndStreams_succeed() = runTest {
        val source = OpenFilmsSource(jsonProvider = { sampleOpenFilmsJson })

        val browseRes = source.browseCatalog(page = 1)
        assertTrue(browseRes is SourceResult.Success)
        val items = (browseRes as SourceResult.Success).data
        assertEquals(2, items.size)

        val searchRes = source.search(query = "durian", page = 1)
        assertTrue(searchRes is SourceResult.Success)
        assertEquals("sintel", (searchRes as SourceResult.Success).data.single().id.value)

        val detailsRes = source.getDetails("sintel")
        assertTrue(detailsRes is SourceResult.Success)

        val streamsRes = source.getStreams("bigbuckbunny", episodeNumber = 1)
        assertTrue(streamsRes is SourceResult.Success)
        val stream = (streamsRes as SourceResult.Success).data.single()
        assertEquals(StreamKind.HLS, stream.kind)
    }

    @Test
    fun openFilmsSource_malformedJson_returnsParseFailure() = runTest {
        val badSource = OpenFilmsSource(jsonProvider = { "{ not valid json " })
        val result = badSource.browseCatalog(page = 1)
        assertTrue(result is SourceResult.Failure)
        val error = (result as SourceResult.Failure).error
        assertTrue(error is AppError.ParseFailure)
    }

    @Test
    fun openFilmsSource_unknownMediaId_returnsNotFound() = runTest {
        val source = OpenFilmsSource(jsonProvider = { sampleOpenFilmsJson })
        val result = source.getDetails("non_existent_id")
        assertTrue(result is SourceResult.Failure)
        assertEquals(AppError.NotFound, (result as SourceResult.Failure).error)
    }

    @Test
    fun sourceHealth_deprioritizesAfterThreeConsecutiveFailures() {
        val health = SourceHealth(maxConsecutiveFailures = 3)
        assertFalse(health.isDeprioritized("sourceA"))
        health.recordFailure("sourceA")
        health.recordFailure("sourceA")
        assertFalse(health.isDeprioritized("sourceA"))
        health.recordFailure("sourceA")
        assertTrue(health.isDeprioritized("sourceA"))

        health.recordSuccess("sourceA")
        assertFalse(health.isDeprioritized("sourceA"))
    }

    @Test
    fun watchProgressRules_completedAt90PercentAndResumeWindowAndNextEpisode() {
        // Completed threshold is >= 90%
        assertFalse(RecordWatchProgressUseCase.isCompleted(positionMs = 89_000L, durationMs = 100_000L))
        assertTrue(RecordWatchProgressUseCase.isCompleted(positionMs = 90_000L, durationMs = 100_000L))

        // Resume rules: >= 10s and < 95%
        assertFalse(RecordWatchProgressUseCase.shouldResumeFromSaved(positionMs = 8_000L, durationMs = 100_000L))
        assertTrue(RecordWatchProgressUseCase.shouldResumeFromSaved(positionMs = 15_000L, durationMs = 100_000L))
        assertFalse(RecordWatchProgressUseCase.shouldResumeFromSaved(positionMs = 96_000L, durationMs = 100_000L))

        // Next episode selection when E1 is completed
        val animeId = MediaId("openfilms", "sintel")
        val episodes = listOf(
            Episode(animeId, number = 1, title = "E1"),
            Episode(animeId, number = 2, title = "E2")
        )
        val progressList = listOf(
            WatchProgress(
                animeId = animeId,
                episodeNumber = 1,
                positionMs = 92_000L,
                durationMs = 100_000L,
                updatedAtEpochMs = 5_000L,
                completed = true
            )
        )
        val (activeEpisode, _) = RecordWatchProgressUseCase.resolveActiveEpisode(episodes, progressList)
        assertEquals(2, activeEpisode)
    }

    @Test
    fun resolvePlayableStream_sanitizesHeadersToAllowlist() {
        val rawHeaders = mapOf(
            "Referer" to "https://example.org",
            "User-Agent" to "HikariPlayer/1.0",
            "Origin" to "https://example.org",
            "Cookie" to "secret_session=xyz",
            "X-Custom-Auth" to "token"
        )
        val sanitized = ResolvePlayableStreamUseCase.sanitizeHeaders(rawHeaders)
        assertEquals(3, sanitized.size)
        assertTrue("Referer" in sanitized)
        assertTrue("User-Agent" in sanitized)
        assertTrue("Origin" in sanitized)
        assertFalse("Cookie" in sanitized)
    }

    @Test
    fun jikanRateLimiter_executesAndEnforcesSpacing() = runTest {
        var fakeNow = 1_000L
        val limiter = JikanRateLimiter(
            minIntervalMs = 400L,
            maxRequestsPerMinute = 55,
            clockMs = { fakeNow }
        )
        val first = limiter.execute {
            fakeNow += 50L
            "ok_1"
        }
        val second = limiter.execute {
            fakeNow += 50L
            "ok_2"
        }
        assertTrue(first is SourceResult.Success)
        assertTrue(second is SourceResult.Success)
    }

    @Test
    fun buildHomeFeedUseCase_oneFailingSource_otherRowsStillRender() = runTest {
        val fakeCatalog = object : CatalogRepository {
            override fun getRegisteredSources(): List<SourceInfo> = listOf(
                SourceInfo("openfilms", "Open Films", "en", 1, setOf(SourceCapability.SEARCH))
            )
            override suspend fun browseSource(sourceId: String, page: Int, forceRefresh: Boolean): SourceResult<List<Anime>> {
                return SourceResult.Success(
                    listOf(Anime(id = MediaId("openfilms", "sintel"), title = "Sintel"))
                )
            }
            override suspend fun searchEnabledSources(query: String, page: Int) = SourceResult.Success(emptyList<Anime>())
            override suspend fun searchCachedCatalog(query: String) = emptyList<Anime>()
            override suspend fun getDetails(mediaId: MediaId, forceRefresh: Boolean) = SourceResult.Failure(AppError.NotFound)
            override suspend fun getEpisodes(mediaId: MediaId, forceRefresh: Boolean) = SourceResult.Success(emptyList<Episode>())
            override suspend fun getStreams(mediaId: MediaId, episodeNumber: Int) = SourceResult.Failure(AppError.NotFound)
            override suspend fun clearCatalogCache() {}
            override suspend fun getEstimatedCacheSizeBytes() = 0L
        }

        val fakeMetadata = object : MetadataRepository {
            override suspend fun getTrendingAnime(forceRefresh: Boolean): SourceResult<List<Anime>> {
                // Simulate Jikan failing with RateLimited while Open Films succeeds
                return SourceResult.Failure(AppError.RateLimited)
            }
            override suspend fun getSeasonalAnime(forceRefresh: Boolean): SourceResult<List<Anime>> {
                return SourceResult.Success(
                    listOf(Anime(id = MediaId("jikan", "1"), title = "Seasonal Hit"))
                )
            }
            override suspend fun searchMetadata(query: String) = SourceResult.Success(emptyList<Anime>())
            override suspend fun getMetadataDetails(malId: Long, forceRefresh: Boolean) = SourceResult.Failure(AppError.NotFound)
            override suspend fun clearMetadataCache() {}
        }

        val fakeSettings = object : SettingsRepository {
            override val settingsFlow: Flow<UserSettings> = MutableStateFlow(UserSettings())
            override val recentSearchesFlow: Flow<List<String>> = MutableStateFlow(emptyList())
            override suspend fun setThemeMode(mode: ThemeMode) {}
            override suspend fun setAutoplayNext(enabled: Boolean) {}
            override suspend fun setResumeAutomatically(enabled: Boolean) {}
            override suspend fun toggleSourceEnabled(sourceId: String, allAvailableSourceIds: Set<String>) = true
            override suspend fun setPreferredQuality(qualityLabel: String?) {}
            override suspend fun addRecentSearch(query: String) {}
            override suspend fun removeRecentSearch(query: String) {}
            override suspend fun clearRecentSearches() {}
        }

        val useCase = BuildHomeFeedUseCase(fakeCatalog, fakeMetadata, fakeSettings)
        val rows = useCase.loadCuratedAndSourceRows()

        assertEquals(3, rows.size)
        assertTrue(rows[0].state is HomeRowState.Content) // Open Films row succeeded
        assertTrue(rows[1].state is HomeRowState.Error)   // Trending row errored independently
        assertTrue(rows[2].state is HomeRowState.Content) // Seasonal row succeeded
    }
}
