package com.example.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.domain.model.Anime
import com.example.domain.model.AppError
import com.example.domain.model.MediaId
import com.example.domain.model.SourceResult
import com.example.domain.repository.CatalogRepository
import com.example.domain.repository.LibraryRepository
import com.example.domain.repository.MetadataRepository
import com.example.domain.repository.SettingsRepository
import com.example.domain.usecase.ToggleFavoriteUseCase
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorState
import com.example.ui.components.HikariTopBar
import com.example.ui.components.LoadingState
import com.example.ui.components.OfflineBanner
import com.example.ui.components.PosterCard
import com.example.ui.components.SectionHeader
import com.example.ui.theme.Dimens
import com.example.ui.theme.PillCornerShape
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class SearchUiState(
    val query: String = "",
    val isLoading: Boolean = false,
    val playableResults: List<Anime> = emptyList(),
    val metadataResults: List<Anime> = emptyList(),
    val offlineCachedResults: List<Anime> = emptyList(),
    val isOfflineFallback: Boolean = false,
    val error: AppError? = null,
    val recentSearches: List<String> = emptyList(),
    val favoriteIds: Set<String> = emptySet()
) {
    val isIdle: Boolean get() = query.trim().length < 2
    val isEmptyResult: Boolean
        get() = !isIdle && !isLoading && error == null &&
            playableResults.isEmpty() && metadataResults.isEmpty() && offlineCachedResults.isEmpty()
}

private data class InternalSearchExecution(
    val isLoading: Boolean = false,
    val playable: List<Anime> = emptyList(),
    val metadata: List<Anime> = emptyList(),
    val offlineCached: List<Anime> = emptyList(),
    val isOffline: Boolean = false,
    val error: AppError? = null
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(
    private val catalogRepository: CatalogRepository,
    private val metadataRepository: MetadataRepository,
    private val libraryRepository: LibraryRepository,
    private val settingsRepository: SettingsRepository,
    private val toggleFavoriteUseCase: ToggleFavoriteUseCase
) : ViewModel() {

    private val queryState = MutableStateFlow("")
    private val retryTrigger = MutableStateFlow(0)

    private val searchExecutionFlow = combine(
        queryState.debounce(300L).distinctUntilChanged(),
        retryTrigger
    ) { q, _ -> q.trim() }
        .flatMapLatest { trimmedQuery ->
            flow {
                if (trimmedQuery.length < 2) {
                    emit(InternalSearchExecution())
                    return@flow
                }
                emit(InternalSearchExecution(isLoading = true))

                val execution = coroutineScope {
                    val playableDeferred = async {
                        catalogRepository.searchEnabledSources(trimmedQuery, page = 1)
                    }
                    val metadataDeferred = async {
                        metadataRepository.searchMetadata(trimmedQuery)
                    }
                    val playableRes = playableDeferred.await()
                    val metadataRes = metadataDeferred.await()

                    val playableList = (playableRes as? SourceResult.Success)?.data.orEmpty()
                    val metadataList = (metadataRes as? SourceResult.Success)?.data.orEmpty()
                    val metadataFailure = (metadataRes as? SourceResult.Failure)?.error

                    if (playableList.isEmpty() && metadataList.isEmpty() && metadataFailure != null) {
                        val cachedFallback = catalogRepository.searchCachedCatalog(trimmedQuery)
                        if (cachedFallback.isNotEmpty()) {
                            InternalSearchExecution(
                                isLoading = false,
                                offlineCached = cachedFallback,
                                isOffline = true
                            )
                        } else {
                            InternalSearchExecution(
                                isLoading = false,
                                error = metadataFailure
                            )
                        }
                    } else {
                        InternalSearchExecution(
                            isLoading = false,
                            playable = playableList.distinctBy { it.id.compositeKey },
                            metadata = metadataList.distinctBy { it.malId ?: it.id.compositeKey },
                            isOffline = metadataFailure is AppError.Network
                        )
                    }
                }
                emit(execution)
            }
        }

    val uiState: StateFlow<SearchUiState> = combine(
        queryState,
        searchExecutionFlow,
        settingsRepository.recentSearchesFlow,
        libraryRepository.observeFavoriteIds()
    ) { rawQuery, exec, recents, favIds ->
        SearchUiState(
            query = rawQuery,
            isLoading = exec.isLoading,
            playableResults = exec.playable,
            metadataResults = exec.metadata,
            offlineCachedResults = exec.offlineCached,
            isOfflineFallback = exec.isOffline,
            error = exec.error,
            recentSearches = recents,
            favoriteIds = favIds
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState()
    )

    fun onQueryChange(newQuery: String) {
        queryState.value = newQuery
    }

    fun submitSearch(query: String = queryState.value) {
        val cleaned = query.trim()
        queryState.value = query
        if (cleaned.length >= 2) {
            viewModelScope.launch {
                settingsRepository.addRecentSearch(cleaned)
            }
        }
    }

    fun retrySearch() {
        retryTrigger.value += 1
    }

    fun removeRecentSearch(query: String) {
        viewModelScope.launch {
            settingsRepository.removeRecentSearch(query)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            settingsRepository.clearRecentSearches()
        }
    }

    fun toggleFavorite(anime: Anime) {
        viewModelScope.launch {
            toggleFavoriteUseCase(anime)
        }
    }

    companion object {
        fun provideFactory(
            catalogRepository: CatalogRepository,
            metadataRepository: MetadataRepository,
            libraryRepository: LibraryRepository,
            settingsRepository: SettingsRepository,
            toggleFavoriteUseCase: ToggleFavoriteUseCase
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SearchViewModel(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onAnimeClick: (MediaId) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    Scaffold(
        topBar = {
            HikariTopBar(
                title = stringResource(R.string.nav_search),
                onSettingsClick = onSettingsClick
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = {
                    Text(text = stringResource(R.string.search_placeholder))
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.onQueryChange("") },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("search_clear_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.search_clear)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = PillCornerShape,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        viewModel.submitSearch()
                        focusManager.clearFocus()
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Dimens.ScreenHorizontalPadding, vertical = Dimens.SpaceXS)
                    .testTag("search_input_field")
            )

            if (state.isOfflineFallback) {
                OfflineBanner()
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    state.isIdle -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = Dimens.SpaceSM)
                        ) {
                            if (state.recentSearches.isNotEmpty()) {
                                SectionHeader(
                                    title = stringResource(R.string.search_recent_header),
                                    trailing = {
                                        TextButton(
                                            onClick = viewModel::clearRecentSearches,
                                            modifier = Modifier.testTag("clear_recent_searches_button")
                                        ) {
                                            Text(text = stringResource(R.string.search_clear_recent))
                                        }
                                    }
                                )
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXS),
                                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXS),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = Dimens.ScreenHorizontalPadding)
                                ) {
                                    state.recentSearches.forEach { recent ->
                                        AssistChip(
                                            onClick = { viewModel.submitSearch(recent) },
                                            label = { Text(recent) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.History,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            },
                                            trailingIcon = {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = stringResource(R.string.search_clear),
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clickable { viewModel.removeRecentSearch(recent) }
                                                )
                                            },
                                            shape = PillCornerShape
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(Dimens.SpaceLG))
                            }

                            EmptyState(
                                icon = Icons.Default.Search,
                                title = stringResource(R.string.search_idle_title),
                                hint = stringResource(R.string.search_idle_hint)
                            )
                        }
                    }

                    state.isLoading -> {
                        LoadingState()
                    }

                    state.error != null -> {
                        ErrorState(
                            error = state.error!!,
                            onRetry = viewModel::retrySearch
                        )
                    }

                    state.isEmptyResult -> {
                        EmptyState(
                            icon = Icons.Default.SearchOff,
                            title = stringResource(R.string.search_empty_title, state.query.trim()),
                            hint = stringResource(R.string.search_empty_hint)
                        )
                    }

                    else -> {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = Dimens.PosterGridMinWidth),
                            contentPadding = PaddingValues(
                                start = Dimens.ScreenHorizontalPadding,
                                end = Dimens.ScreenHorizontalPadding,
                                top = Dimens.SpaceSM,
                                bottom = Dimens.SpaceXL
                            ),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM),
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("search_results_grid")
                        ) {
                            if (state.playableResults.isNotEmpty()) {
                                item(
                                    key = "header_playable",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Text(
                                        text = stringResource(R.string.search_section_playable),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(vertical = Dimens.SpaceXS)
                                    )
                                }

                                items(
                                    items = state.playableResults,
                                    key = { "playable_${it.id.compositeKey}" }
                                ) { anime ->
                                    PosterCard(
                                        title = anime.title,
                                        posterUrl = anime.posterUrl,
                                        score = anime.score,
                                        badgeText = "Playable",
                                        isFavorite = anime.id.compositeKey in state.favoriteIds,
                                        onToggleFavorite = { viewModel.toggleFavorite(anime) },
                                        onClick = {
                                            viewModel.submitSearch()
                                            onAnimeClick(anime.id)
                                        },
                                        testTag = "search_playable_${anime.id.compositeKey}"
                                    )
                                }
                            }

                            if (state.metadataResults.isNotEmpty()) {
                                item(
                                    key = "header_discover",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Text(
                                        text = stringResource(R.string.search_section_discover),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier.padding(
                                            top = Dimens.SpaceMD,
                                            bottom = Dimens.SpaceXS
                                        )
                                    )
                                }

                                items(
                                    items = state.metadataResults,
                                    key = { "discover_${it.id.compositeKey}" }
                                ) { anime ->
                                    PosterCard(
                                        title = anime.title,
                                        posterUrl = anime.posterUrl,
                                        score = anime.score,
                                        isFavorite = anime.id.compositeKey in state.favoriteIds,
                                        onToggleFavorite = { viewModel.toggleFavorite(anime) },
                                        onClick = {
                                            viewModel.submitSearch()
                                            onAnimeClick(anime.id)
                                        },
                                        testTag = "search_discover_${anime.id.compositeKey}"
                                    )
                                }
                            }

                            if (state.offlineCachedResults.isNotEmpty()) {
                                item(
                                    key = "header_offline_cached",
                                    span = { GridItemSpan(maxLineSpan) }
                                ) {
                                    Text(
                                        text = stringResource(R.string.search_section_offline_cached),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(vertical = Dimens.SpaceXS)
                                    )
                                }

                                items(
                                    items = state.offlineCachedResults,
                                    key = { "cached_${it.id.compositeKey}" }
                                ) { anime ->
                                    PosterCard(
                                        title = anime.title,
                                        posterUrl = anime.posterUrl,
                                        score = anime.score,
                                        isFavorite = anime.id.compositeKey in state.favoriteIds,
                                        onToggleFavorite = { viewModel.toggleFavorite(anime) },
                                        onClick = { onAnimeClick(anime.id) },
                                        testTag = "search_cached_${anime.id.compositeKey}"
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
