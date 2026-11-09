package com.example.ui.library

import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.example.domain.model.ContinueWatchingItem
import com.example.domain.model.FavoriteItem
import com.example.domain.model.MediaId
import com.example.domain.repository.LibraryRepository
import com.example.ui.components.BrandedPosterArt
import com.example.ui.components.EmptyState
import com.example.ui.components.HikariTopBar
import com.example.ui.components.PosterCard
import com.example.ui.theme.CardCornerShape
import com.example.ui.theme.Dimens
import com.example.ui.theme.LocalHikariColors
import com.example.ui.theme.PosterCornerShape
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
data class LibraryUiState(
    val favorites: List<FavoriteItem> = emptyList(),
    val history: List<ContinueWatchingItem> = emptyList()
)

class LibraryViewModel(
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    val uiState: StateFlow<LibraryUiState> = combine(
        libraryRepository.observeFavorites(),
        libraryRepository.observeAllWatchHistory()
    ) { favorites, history ->
        LibraryUiState(
            favorites = favorites,
            history = history
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState()
    )

    fun removeFavorite(mediaId: MediaId) {
        viewModelScope.launch {
            libraryRepository.removeFavorite(mediaId)
        }
    }

    fun removeHistoryItem(mediaId: MediaId, episodeNumber: Int) {
        viewModelScope.launch {
            libraryRepository.removeWatchHistoryItem(mediaId, episodeNumber)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            libraryRepository.clearWatchHistory()
        }
    }

    companion object {
        fun provideFactory(
            libraryRepository: LibraryRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LibraryViewModel(libraryRepository) as T
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onAnimeClick: (MediaId) -> Unit,
    onResumeEpisodeClick: (MediaId, Int) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showClearHistoryDialog by rememberSaveable { mutableStateOf(false) }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(text = stringResource(R.string.library_history_clear_confirm_title)) },
            text = { Text(text = stringResource(R.string.library_history_clear_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearHistoryDialog = false
                        viewModel.clearAllHistory()
                    },
                    modifier = Modifier.testTag("library_confirm_clear_history")
                ) {
                    Text(
                        text = stringResource(R.string.action_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearHistoryDialog = false }
                ) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            HikariTopBar(
                title = stringResource(R.string.nav_library),
                onSettingsClick = onSettingsClick,
                actions = {
                    if (selectedTab == 1 && state.history.isNotEmpty()) {
                        IconButton(
                            onClick = { showClearHistoryDialog = true },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("library_clear_history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.library_history_clear_all)
                            )
                        }
                    }
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "${stringResource(R.string.library_tab_favorites)} (${state.favorites.size})"
                        )
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("library_tab_favorites")
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "${stringResource(R.string.library_tab_history)} (${state.history.size})"
                        )
                    },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("library_tab_history")
                )
            }

            if (selectedTab == 0) {
                if (state.favorites.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.FavoriteBorder,
                        title = stringResource(R.string.library_favorites_empty_title),
                        hint = stringResource(R.string.library_favorites_empty_hint)
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = Dimens.PosterGridMinWidth),
                        contentPadding = PaddingValues(Dimens.ScreenHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM),
                        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("library_favorites_grid")
                    ) {
                        items(
                            items = state.favorites,
                            key = { it.animeId.compositeKey }
                        ) { fav ->
                            PosterCard(
                                title = fav.title,
                                posterUrl = fav.posterUrl,
                                isFavorite = true,
                                onToggleFavorite = { viewModel.removeFavorite(fav.animeId) },
                                onClick = { onAnimeClick(fav.animeId) },
                                testTag = "favorite_card_${fav.animeId.compositeKey}"
                            )
                        }
                    }
                }
            } else {
                if (state.history.isEmpty()) {
                    EmptyState(
                        icon = Icons.Default.History,
                        title = stringResource(R.string.library_history_empty_title),
                        hint = stringResource(R.string.library_history_empty_hint)
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(Dimens.ScreenHorizontalPadding),
                        verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("library_history_list")
                    ) {
                        items(
                            items = state.history,
                            key = { "${it.anime.id.compositeKey}_ep_${it.progress.episodeNumber}" }
                        ) { item ->
                            val dismissState = rememberSwipeToDismissBoxState(
                                confirmValueChange = { value ->
                                    if (value == SwipeToDismissBoxValue.EndToStart ||
                                        value == SwipeToDismissBoxValue.StartToEnd
                                    ) {
                                        viewModel.removeHistoryItem(
                                            item.anime.id,
                                            item.progress.episodeNumber
                                        )
                                        true
                                    } else {
                                        false
                                    }
                                }
                            )

                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = {
                                    Box(
                                        contentAlignment = Alignment.CenterEnd,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(CardCornerShape)
                                            .background(MaterialTheme.colorScheme.errorContainer)
                                            .padding(horizontal = Dimens.SpaceLG)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = stringResource(R.string.library_remove_item),
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            ) {
                                HistoryItemRow(
                                    item = item,
                                    onResumeClick = {
                                        onResumeEpisodeClick(
                                            item.anime.id,
                                            item.progress.episodeNumber
                                        )
                                    },
                                    onRemoveClick = {
                                        viewModel.removeHistoryItem(
                                            item.anime.id,
                                            item.progress.episodeNumber
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryItemRow(
    item: ContinueWatchingItem,
    onResumeClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    val watchedColor = LocalHikariColors.current.watchedSuccess
    val relativeTime = rememberRelativeTime(item.progress.updatedAtEpochMs)

    Card(
        shape = CardCornerShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardCornerShape)
            .clickable(onClick = onResumeClick)
            .testTag("history_row_${item.anime.id.compositeKey}_${item.progress.episodeNumber}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMD),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SpaceSM)
            ) {
                BrandedPosterArt(
                    title = item.anime.title,
                    posterUrl = item.anime.posterUrl,
                    contentDescription = item.anime.title,
                    modifier = Modifier
                        .width(Dimens.HistoryThumbWidth)
                        .aspectRatio(Dimens.PosterAspectRatio)
                        .clip(PosterCornerShape)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(Dimens.SpaceXXS),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = item.anime.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    val statusLabel = if (item.progress.completed) {
                        stringResource(
                            R.string.library_history_episode_completed,
                            item.progress.episodeNumber
                        )
                    } else {
                        stringResource(
                            R.string.library_history_episode_left,
                            item.progress.episodeNumber,
                            item.progress.remainingMinutes
                        )
                    }

                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (item.progress.completed) watchedColor else MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = relativeTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onResumeClick,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.player_play),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = onRemoveClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("history_delete_${item.anime.id.compositeKey}_${item.progress.episodeNumber}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.library_remove_item),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            LinearProgressIndicator(
                progress = { item.progress.progressFraction.coerceAtLeast(0.05f) },
                color = if (item.progress.completed) watchedColor else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
            )
        }
    }
}

@Composable
private fun rememberRelativeTime(timestampMs: Long): String {
    return DateUtils.getRelativeTimeSpanString(
        timestampMs,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
        DateUtils.FORMAT_ABBREV_RELATIVE
    ).toString()
}
