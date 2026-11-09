package com.example.ui.details

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.model.AiringStatus
import com.example.domain.model.Anime
import com.example.domain.model.AppError
import com.example.domain.model.Episode
import com.example.domain.model.MediaId
import com.example.domain.model.SourceResult
import com.example.domain.model.WatchProgress
import com.example.domain.repository.CatalogRepository
import com.example.domain.repository.LibraryRepository
import com.example.domain.usecase.RecordWatchProgressUseCase
import com.example.domain.usecase.ToggleFavoriteUseCase
import com.example.ui.components.BrandedPosterArt
import com.example.ui.components.EpisodeRow
import com.example.ui.components.ErrorMessages
import com.example.ui.components.ErrorState
import com.example.ui.components.FavoriteButton
import com.example.ui.components.GenreChip
import com.example.ui.components.HikariTopBar
import com.example.ui.components.LoadingState
import com.example.ui.components.ScoreBadge
import com.example.ui.theme.CardCornerShape
import com.example.ui.theme.Dimens
import com.example.ui.theme.PillCornerShape
import com.example.ui.theme.PosterCornerShape
import com.example.ui.theme.ScrimDark85
import com.example.ui.theme.ScrimTransparent
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class DetailsUiState(
    val mediaId: MediaId,
    val isLoading: Boolean = true,
    val anime: Anime? = null,
    val error: AppError? = null,
    val episodes: List<Episode> = emptyList(),
    val isEpisodesLoading: Boolean = false,
    val episodesError: AppError? = null,
    val isFavorite: Boolean = false,
    val progressByEpisode: Map<Int, WatchProgress> = emptyMap(),
    val targetCtaEpisodeNumber: Int = 1,
    val hasSavedProgress: Boolean = false
) {
    val isMetadataOnly: Boolean
        get() = mediaId.sourceId == "jikan"
}

class DetailsViewModel(
    private val mediaId: MediaId,
    private val catalogRepository: CatalogRepository,
    private val libraryRepository: LibraryRepository,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
) : ViewModel() {

    private val isLoadingState = MutableStateFlow(true)
    private val animeState = MutableStateFlow<Anime?>(null)
    private val detailsErrorState = MutableStateFlow<AppError?>(null)

    private val isEpisodesLoadingState = MutableStateFlow(false)
    private val episodesState = MutableStateFlow<List<Episode>>(emptyList())
    private val episodesErrorState = MutableStateFlow<AppError?>(null)

    val uiState: StateFlow<DetailsUiState> = combine(
        combine(isLoadingState, animeState, detailsErrorState) { loading, anime, err ->
            Triple(loading, anime, err)
        },
        combine(isEpisodesLoadingState, episodesState, episodesErrorState) { epLoading, eps, epErr ->
            Triple(epLoading, eps, epErr)
        },
        libraryRepository.observeIsFavorite(mediaId),
        libraryRepository.observeWatchProgressForAnime(mediaId)
    ) { (loading, anime, err), (epLoading, episodes, epErr), isFav, progressList ->
        val progressMap = progressList.associateBy { it.episodeNumber }
        val (targetEpisode, _) = RecordWatchProgressUseCase.resolveActiveEpisode(episodes, progressList)
        val hasAnyProgress = progressList.any { it.positionMs > 0L || it.completed }

        DetailsUiState(
            mediaId = mediaId,
            isLoading = loading,
            anime = anime,
            error = err,
            episodes = episodes,
            isEpisodesLoading = epLoading,
            episodesError = epErr,
            isFavorite = isFav,
            progressByEpisode = progressMap,
            targetCtaEpisodeNumber = targetEpisode,
            hasSavedProgress = hasAnyProgress
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DetailsUiState(mediaId = mediaId)
    )

    init {
        loadAll(forceRefresh = false)
    }

    fun loadAll(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            isLoadingState.value = true
            detailsErrorState.value = null
            isEpisodesLoadingState.value = true
            episodesErrorState.value = null

            val detailsDeferred = async { catalogRepository.getDetails(mediaId, forceRefresh) }
            val episodesDeferred = async { catalogRepository.getEpisodes(mediaId, forceRefresh) }

            when (val detailsRes = detailsDeferred.await()) {
                is SourceResult.Success -> {
                    animeState.value = detailsRes.data
                    detailsErrorState.value = null
                }
                is SourceResult.Failure -> {
                    detailsErrorState.value = detailsRes.error
                }
            }
            isLoadingState.value = false

            when (val epRes = episodesDeferred.await()) {
                is SourceResult.Success -> {
                    episodesState.value = epRes.data
                    episodesErrorState.value = null
                }
                is SourceResult.Failure -> {
                    episodesErrorState.value = epRes.error
                }
            }
            isEpisodesLoadingState.value = false
        }
    }

    fun retryEpisodes() {
        viewModelScope.launch {
            isEpisodesLoadingState.value = true
            episodesErrorState.value = null
            when (val epRes = catalogRepository.getEpisodes(mediaId, forceRefresh = true)) {
                is SourceResult.Success -> {
                    episodesState.value = epRes.data
                }
                is SourceResult.Failure -> {
                    episodesErrorState.value = epRes.error
                }
            }
            isEpisodesLoadingState.value = false
        }
    }

    fun toggleFavorite() {
        val currentAnime = animeState.value ?: return
        viewModelScope.launch {
            toggleFavoriteUseCase(currentAnime)
        }
    }

    companion object {
        fun provideFactory(
            mediaId: MediaId,
            catalogRepository: CatalogRepository,
            libraryRepository: LibraryRepository,
            toggleFavoriteUseCase: ToggleFavoriteUseCase
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DetailsViewModel(
                    mediaId = mediaId,
                    catalogRepository = catalogRepository,
                    libraryRepository = libraryRepository,
                    toggleFavoriteUseCase = toggleFavoriteUseCase
                ) as T
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsScreen(
    state: DetailsUiState,
    onBackClick: () -> Unit,
    onPlayEpisodeClick: (MediaId, Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onRetryDetails: () -> Unit,
    onRetryEpisodes: () -> Unit,
    modifier: Modifier = Modifier
) {
    var synopsisExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            HikariTopBar(
                title = state.anime?.title ?: "",
                onBackClick = onBackClick,
                actions = {
                    if (state.anime != null) {
                        FavoriteButton(
                            isFavorite = state.isFavorite,
                            onToggle = onToggleFavorite,
                            testTag = "details_favorite_button"
                        )
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                state.isLoading && state.anime == null -> {
                    LoadingState()
                }

                state.error != null && state.anime == null -> {
                    ErrorState(
                        error = state.error,
                        onRetry = onRetryDetails
                    )
                }

                state.anime != null -> {
                    val anime = state.anime
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = Dimens.SpaceXL),
                        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("details_content_list")
                    ) {
                        // Header with blurred/scrimmed backdrop + Poster left + Metadata right
                        item(key = "details_header") {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                // Backdrop
                                BrandedPosterArt(
                                    title = anime.title,
                                    posterUrl = anime.bannerUrl ?: anime.posterUrl,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(230.dp)
                                        .blur(20.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(230.dp)
                                        .background(
                                            brush = Brush.verticalGradient(
                                                colors = listOf(
                                                    ScrimTransparent,
                                                    ScrimDark85,
                                                    MaterialTheme.colorScheme.background
                                                )
                                            )
                                        )
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMD),
                                    verticalAlignment = Alignment.Bottom,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            start = Dimens.ScreenHorizontalPadding,
                                            end = Dimens.ScreenHorizontalPadding,
                                            top = Dimens.SpaceLG,
                                            bottom = Dimens.SpaceXS
                                        )
                                ) {
                                    BrandedPosterArt(
                                        title = anime.title,
                                        posterUrl = anime.posterUrl,
                                        contentDescription = anime.title,
                                        modifier = Modifier
                                            .width(124.dp)
                                            .aspectRatio(Dimens.PosterAspectRatio)
                                            .clip(PosterCornerShape)
                                    )

                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = anime.title,
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )

                                        if (anime.altTitles.isNotEmpty()) {
                                            Text(
                                                text = anime.altTitles.first(),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXS),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (anime.score != null && anime.score > 0.0) {
                                                ScoreBadge(score = anime.score)
                                            }
                                            if (anime.year != null) {
                                                GenreChip(genre = anime.year.toString())
                                            }
                                            val statusText = when (anime.status) {
                                                AiringStatus.AIRING -> stringResource(R.string.details_status_airing)
                                                AiringStatus.FINISHED -> stringResource(R.string.details_status_finished)
                                                AiringStatus.UPCOMING -> stringResource(R.string.details_status_upcoming)
                                                AiringStatus.UNKNOWN -> stringResource(R.string.details_status_unknown)
                                            }
                                            GenreChip(genre = statusText)
                                        }

                                        if (!state.isMetadataOnly && state.episodes.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(Dimens.SpaceXXS))
                                            Button(
                                                onClick = {
                                                    onPlayEpisodeClick(
                                                        state.mediaId,
                                                        state.targetCtaEpisodeNumber
                                                    )
                                                },
                                                shape = PillCornerShape,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .minimumInteractiveComponentSize()
                                                    .testTag("details_primary_play_cta")
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.PlayArrow,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(Dimens.SpaceXS))
                                                val ctaText = if (state.hasSavedProgress) {
                                                    stringResource(
                                                        R.string.details_resume_episode,
                                                        state.targetCtaEpisodeNumber
                                                    )
                                                } else {
                                                    stringResource(R.string.details_play_first)
                                                }
                                                Text(
                                                    text = ctaText,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Genre Chips
                        if (anime.genres.isNotEmpty()) {
                            item(key = "details_genres") {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXS),
                                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Dimens.ScreenHorizontalPadding)
                                ) {
                                    anime.genres.forEach { genre ->
                                        GenreChip(genre = genre)
                                    }
                                }
                            }
                        }

                        // Synopsis (3 lines expandable per §6.5-3)
                        item(key = "details_synopsis") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = Dimens.ScreenHorizontalPadding)
                                    .animateContentSize()
                            ) {
                                val synopsisText = anime.synopsis?.takeIf { it.isNotBlank() }
                                    ?: stringResource(R.string.details_no_synopsis)
                                Text(
                                    text = synopsisText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = if (synopsisExpanded) Int.MAX_VALUE else 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (synopsisText.length > 140) {
                                    TextButton(
                                        onClick = { synopsisExpanded = !synopsisExpanded },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier.testTag("details_synopsis_toggle")
                                    ) {
                                        Text(
                                            text = if (synopsisExpanded) {
                                                stringResource(R.string.details_synopsis_less)
                                            } else {
                                                stringResource(R.string.details_synopsis_more)
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        // Attribution line (required for Open Films CC BY 3.0 per §5.2 & §6.5-3)
                        if (!anime.attribution.isNullOrBlank()) {
                            item(key = "details_attribution") {
                                Card(
                                    shape = CardCornerShape,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Dimens.ScreenHorizontalPadding)
                                        .testTag("details_attribution_card")
                                ) {
                                    Text(
                                        text = anime.attribution,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(Dimens.SpaceSM)
                                    )
                                }
                            }
                        }

                        // Episodes or Metadata-Only Banner
                        if (state.isMetadataOnly) {
                            item(key = "details_metadata_only_notice") {
                                Card(
                                    shape = CardCornerShape,
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Dimens.ScreenHorizontalPadding)
                                        .testTag("details_metadata_only_card")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM),
                                        modifier = Modifier.padding(Dimens.SpaceMD)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Info,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Column(verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXXS)) {
                                            Text(
                                                text = stringResource(R.string.details_metadata_only_title),
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = stringResource(R.string.details_metadata_only_body),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            item(key = "details_episodes_header") {
                                Text(
                                    text = stringResource(
                                        R.string.details_episodes_header,
                                        state.episodes.size
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    modifier = Modifier.padding(
                                        horizontal = Dimens.ScreenHorizontalPadding,
                                        vertical = Dimens.SpaceXS
                                    )
                                )
                            }

                            when {
                                state.isEpisodesLoading && state.episodes.isEmpty() -> {
                                    item(key = "episodes_loading") {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(Dimens.SpaceLG)
                                        ) {
                                            CircularProgressIndicator()
                                        }
                                    }
                                }

                                state.episodesError != null && state.episodes.isEmpty() -> {
                                    item(key = "episodes_error") {
                                        Card(
                                            shape = CardCornerShape,
                                            colors = CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = Dimens.ScreenHorizontalPadding)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(Dimens.SpaceMD)
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.ErrorOutline,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                    Text(
                                                        text = ErrorMessages.resolve(state.episodesError),
                                                        style = MaterialTheme.typography.bodyMedium
                                                    )
                                                }
                                                FilledTonalButton(
                                                    onClick = onRetryEpisodes,
                                                    modifier = Modifier
                                                        .minimumInteractiveComponentSize()
                                                        .testTag("details_episodes_retry_button")
                                                ) {
                                                    Text(text = stringResource(R.string.action_retry))
                                                }
                                            }
                                        }
                                    }
                                }

                                else -> {
                                    items(
                                        items = state.episodes,
                                        key = { it.number }
                                    ) { episode ->
                                        EpisodeRow(
                                            episode = episode,
                                            progress = state.progressByEpisode[episode.number],
                                            isCurrent = episode.number == state.targetCtaEpisodeNumber,
                                            onClick = {
                                                onPlayEpisodeClick(state.mediaId, episode.number)
                                            },
                                            modifier = Modifier.padding(horizontal = Dimens.ScreenHorizontalPadding)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
