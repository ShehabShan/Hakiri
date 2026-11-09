package com.example.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.model.Anime
import com.example.domain.model.AppError
import com.example.domain.model.ContinueWatchingItem
import com.example.domain.model.HomeFeedRow
import com.example.domain.model.HomeRowContent
import com.example.domain.model.HomeRowState
import com.example.domain.model.MediaId
import com.example.domain.model.SourceResult
import com.example.domain.repository.CatalogRepository
import com.example.domain.repository.LibraryRepository
import com.example.domain.repository.MetadataRepository
import com.example.domain.repository.SettingsRepository
import com.example.domain.usecase.BuildHomeFeedUseCase
import com.example.domain.usecase.ToggleFavoriteUseCase
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorState
import com.example.ui.components.HikariTopBar
import com.example.ui.components.LoadingState
import com.example.ui.components.MediaRow
import com.example.ui.components.OfflineBanner
import com.example.ui.theme.Dimens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class HomeUiState(
    val isLoadingInitial: Boolean = true,
    val isOffline: Boolean = false,
    val rows: List<HomeFeedRow> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val fullScreenError: AppError? = null
)

class HomeViewModel(
    private val buildHomeFeedUseCase: BuildHomeFeedUseCase,
    private val catalogRepository: CatalogRepository,
    private val metadataRepository: MetadataRepository,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
) : ViewModel() {

    private val curatedRowsState = MutableStateFlow<List<HomeFeedRow>>(emptyList())
    private val isInitialLoadingState = MutableStateFlow(true)
    private val isOfflineState = MutableStateFlow(false)

    val uiState: StateFlow<HomeUiState> = combine(
        curatedRowsState,
        libraryRepository.observeContinueWatching(limit = 10),
        libraryRepository.observeFavoriteIds(),
        isInitialLoadingState,
        isOfflineState
    ) { curatedRows, continueWatching, favIds, isLoading, isOffline ->
        val continueRow = if (continueWatching.isNotEmpty()) {
            listOf(
                HomeFeedRow(
                    id = BuildHomeFeedUseCase.ROW_CONTINUE,
                    title = "Continue Watching",
                    subtitle = "Pick up right where you left off",
                    state = HomeRowState.Content(HomeRowContent.ContinueWatching(continueWatching))
                )
            )
        } else {
            emptyList()
        }

        val allRows = continueRow + curatedRows
        val allErrored = allRows.isNotEmpty() && allRows.all { it.state is HomeRowState.Error }
        val firstError = allRows.mapNotNull { (it.state as? HomeRowState.Error)?.error }.firstOrNull()

        HomeUiState(
            isLoadingInitial = isLoading && allRows.isEmpty(),
            isOffline = isOffline,
            rows = allRows,
            favoriteIds = favIds,
            fullScreenError = if (allErrored && continueWatching.isEmpty()) firstError else null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    init {
        // Reload home rows automatically whenever enabled sources change in Settings
        viewModelScope.launch {
            settingsRepository.settingsFlow
                .map { it.enabledSources }
                .distinctUntilChanged()
                .collect {
                    loadFeed(forceRefresh = false)
                }
        }
    }

    fun loadFeed(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            if (curatedRowsState.value.isEmpty()) {
                isInitialLoadingState.value = true
            }
            val loadedRows = buildHomeFeedUseCase.loadCuratedAndSourceRows(forceRefresh = forceRefresh)
            curatedRowsState.value = loadedRows
            isInitialLoadingState.value = false

            val hasNetworkFailure = loadedRows.any { row ->
                val err = (row.state as? HomeRowState.Error)?.error
                err is AppError.Network
            }
            isOfflineState.value = hasNetworkFailure
        }
    }

    fun retryRow(rowId: String) {
        viewModelScope.launch {
            curatedRowsState.update { rows ->
                rows.map { row ->
                    if (row.id == rowId) row.copy(state = HomeRowState.Loading) else row
                }
            }

            val updatedState: HomeRowState = when {
                rowId == BuildHomeFeedUseCase.ROW_TRENDING -> {
                    when (val res = metadataRepository.getTrendingAnime(forceRefresh = true)) {
                        is SourceResult.Success -> HomeRowState.Content(HomeRowContent.AnimeList(res.data))
                        is SourceResult.Failure -> HomeRowState.Error(res.error)
                    }
                }
                rowId == BuildHomeFeedUseCase.ROW_SEASONAL -> {
                    when (val res = metadataRepository.getSeasonalAnime(forceRefresh = true)) {
                        is SourceResult.Success -> HomeRowState.Content(HomeRowContent.AnimeList(res.data))
                        is SourceResult.Failure -> HomeRowState.Error(res.error)
                    }
                }
                rowId.startsWith("source_") -> {
                    val sourceId = rowId.removePrefix("source_")
                    when (val res = catalogRepository.browseSource(sourceId, page = 1, forceRefresh = true)) {
                        is SourceResult.Success -> HomeRowState.Content(HomeRowContent.AnimeList(res.data))
                        is SourceResult.Failure -> HomeRowState.Error(res.error)
                    }
                }
                else -> return@launch
            }

            curatedRowsState.update { rows ->
                rows.map { row ->
                    if (row.id == rowId) row.copy(state = updatedState) else row
                }
            }
            isOfflineState.value = curatedRowsState.value.any {
                (it.state as? HomeRowState.Error)?.error is AppError.Network
            }
        }
    }

    fun toggleFavorite(anime: Anime) {
        viewModelScope.launch {
            toggleFavoriteUseCase(anime)
        }
    }

    companion object {
        fun provideFactory(
            buildHomeFeedUseCase: BuildHomeFeedUseCase,
            catalogRepository: CatalogRepository,
            metadataRepository: MetadataRepository,
            libraryRepository: LibraryRepository,
            settingsRepository: SettingsRepository,
            toggleFavoriteUseCase: ToggleFavoriteUseCase
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return HomeViewModel(
                    buildHomeFeedUseCase = buildHomeFeedUseCase,
                    catalogRepository = catalogRepository,
                    metadataRepository = metadataRepository,
                    libraryRepository = libraryRepository,
                    settingsRepository = settingsRepository,
                    toggleFavoriteUseCase = toggleFavoriteUseCase
                ) as T
            }
        }
    }
}

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onAnimeClick: (MediaId) -> Unit,
    onResumeEpisodeClick: (MediaId, Int) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            HikariTopBar(
                title = stringResource(R.string.app_name),
                onSettingsClick = onSettingsClick,
                actions = {
                    IconButton(
                        onClick = { viewModel.loadFeed(forceRefresh = true) },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("home_refresh_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.action_refresh)
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
                state.isLoadingInitial -> {
                    LoadingState()
                }

                state.fullScreenError != null -> {
                    ErrorState(
                        error = state.fullScreenError!!,
                        onRetry = { viewModel.loadFeed(forceRefresh = true) }
                    )
                }

                state.rows.isEmpty() -> {
                    EmptyState(
                        icon = Icons.Default.MovieFilter,
                        title = stringResource(R.string.home_empty_title),
                        hint = stringResource(R.string.home_empty_subtitle)
                    )
                }

                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (state.isOffline) {
                            OfflineBanner()
                        }

                        LazyColumn(
                            contentPadding = PaddingValues(bottom = Dimens.SpaceXL),
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceLG),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("home_feed_list")
                        ) {
                            items(
                                items = state.rows,
                                key = { it.id }
                            ) { row ->
                                MediaRow(
                                    row = row,
                                    favoriteIds = state.favoriteIds,
                                    onAnimeClick = { anime -> onAnimeClick(anime.id) },
                                    onContinueWatchingClick = { item: ContinueWatchingItem ->
                                        onResumeEpisodeClick(
                                            item.anime.id,
                                            item.progress.episodeNumber
                                        )
                                    },
                                    onToggleFavorite = { anime -> viewModel.toggleFavorite(anime) },
                                    onRetryRow = { rowId -> viewModel.retryRow(rowId) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
