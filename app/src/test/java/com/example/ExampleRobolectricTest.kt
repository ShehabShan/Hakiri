package com.example

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.HikariDatabase
import com.example.data.repositoryimpl.LibraryRepositoryImpl
import com.example.domain.model.AiringStatus
import com.example.domain.model.Anime
import com.example.domain.model.AppError
import com.example.domain.model.Episode
import com.example.domain.model.MediaId
import com.example.domain.model.WatchProgress
import com.example.ui.components.EpisodeRow
import com.example.ui.components.ErrorState
import com.example.ui.details.DetailsScreen
import com.example.ui.details.DetailsUiState
import com.example.ui.theme.HikariTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: HikariDatabase
    private lateinit var libraryRepository: LibraryRepositoryImpl

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = HikariDatabase.createInMemory(context)
        libraryRepository = LibraryRepositoryImpl(
            favoriteDao = database.favoriteDao(),
            watchProgressDao = database.watchProgressDao(),
            animeCacheDao = database.animeCacheDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun appNameResource_matchesHikari() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("Hikari", context.getString(R.string.app_name))
    }

    @Test
    fun roomLibraryRepository_favoritesToggleAndWatchProgress90PercentRoundTrip() = runBlocking {
        val sintel = Anime(
            id = MediaId("openfilms", "sintel"),
            title = "Sintel",
            synopsis = "Open movie",
            episodeCount = 2
        )

        // Initially not favorite
        assertFalse(libraryRepository.observeIsFavorite(sintel.id).first())

        // Toggle on
        val added = libraryRepository.toggleFavorite(sintel)
        assertTrue(added)
        assertTrue(libraryRepository.observeIsFavorite(sintel.id).first())
        assertEquals(1, libraryRepository.observeFavorites().first().size)

        // Record progress at 50% -> incomplete, shows in Continue Watching for E1
        libraryRepository.recordWatchProgress(
            anime = sintel,
            episodeNumber = 1,
            positionMs = 50_000L,
            durationMs = 100_000L
        )
        val cw1 = libraryRepository.observeContinueWatching().first()
        assertEquals(1, cw1.size)
        assertEquals(1, cw1.first().progress.episodeNumber)
        assertFalse(cw1.first().progress.completed)

        // Record progress at 92% -> completed = true, Continue Watching advances to E2
        libraryRepository.recordWatchProgress(
            anime = sintel,
            episodeNumber = 1,
            positionMs = 92_000L,
            durationMs = 100_000L
        )
        val savedEp1 = libraryRepository.getWatchProgress(sintel.id, 1)
        assertTrue(savedEp1?.completed == true)

        val cwAdvanced = libraryRepository.observeContinueWatching().first()
        assertEquals(1, cwAdvanced.size)
        assertEquals(2, cwAdvanced.first().progress.episodeNumber)

        // Toggle favorite off
        val stillFav = libraryRepository.toggleFavorite(sintel)
        assertFalse(stillFav)
        assertTrue(libraryRepository.observeFavorites().first().isEmpty())
    }

    @Test
    fun errorState_retryButton_invokesCallback() {
        var retryClicked = false
        composeTestRule.setContent {
            HikariTheme {
                ErrorState(
                    error = AppError.Network,
                    onRetry = { retryClicked = true }
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("error_retry_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("error_retry_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(retryClicked)
    }

    @Test
    fun detailsScreen_displaysEpisodesAttributionAndTogglesFavorite() {
        val mediaId = MediaId("openfilms", "sintel")
        val anime = Anime(
            id = mediaId,
            title = "Sintel",
            synopsis = "A lonely young woman helps a dragon.",
            genres = listOf("Fantasy", "Adventure"),
            year = 2010,
            status = AiringStatus.FINISHED,
            episodeCount = 1,
            attribution = "Films © Blender Foundation, CC BY 3.0"
        )
        val episodes = listOf(
            Episode(
                animeId = mediaId,
                number = 1,
                title = "Sintel (stereo, 512kb)",
                durationSeconds = 888
            )
        )
        var isFavoriteState by mutableStateOf(false)

        composeTestRule.setContent {
            HikariTheme {
                DetailsScreen(
                    state = DetailsUiState(
                        mediaId = mediaId,
                        isLoading = false,
                        anime = anime,
                        episodes = episodes,
                        isFavorite = isFavoriteState,
                        progressByEpisode = mapOf(
                            1 to WatchProgress(
                                animeId = mediaId,
                                episodeNumber = 1,
                                positionMs = 850_000L,
                                durationMs = 888_000L,
                                updatedAtEpochMs = 1_000L,
                                completed = true
                            )
                        )
                    ),
                    onBackClick = {},
                    onPlayEpisodeClick = { _, _ -> },
                    onToggleFavorite = { isFavoriteState = !isFavoriteState },
                    onRetryDetails = {},
                    onRetryEpisodes = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Films © Blender Foundation, CC BY 3.0", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("details_content_list")
            .performScrollToNode(androidx.compose.ui.test.hasTestTag("episode_row_1"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("episode_row_1", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("episode_watched_icon_1", useUnmergedTree = true).assertIsDisplayed()

        // Toggle favorite button
        composeTestRule.onNodeWithTag("details_favorite_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(isFavoriteState)
    }

    @Test
    fun episodeRow_watchedState_displaysCheckIcon() {
        val mediaId = MediaId("openfilms", "sintel")
        composeTestRule.setContent {
            HikariTheme {
                EpisodeRow(
                    episode = Episode(mediaId, 1, "Episode One", durationSeconds = 600),
                    progress = WatchProgress(
                        animeId = mediaId,
                        episodeNumber = 1,
                        positionMs = 580_000L,
                        durationMs = 600_000L,
                        updatedAtEpochMs = 100L,
                        completed = true
                    ),
                    isCurrent = true,
                    onClick = {}
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("episode_watched_icon_1", useUnmergedTree = true).assertIsDisplayed()
    }
}
