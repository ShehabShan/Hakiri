package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.domain.model.Anime
import com.example.domain.model.AppError
import com.example.domain.model.ContinueWatchingItem
import com.example.domain.model.Episode
import com.example.domain.model.HomeFeedRow
import com.example.domain.model.HomeRowContent
import com.example.domain.model.HomeRowState
import com.example.domain.model.WatchProgress
import com.example.ui.theme.CardCornerShape
import com.example.ui.theme.Dimens
import com.example.ui.theme.HikariMotion
import com.example.ui.theme.LocalHikariColors
import com.example.ui.theme.PillCornerShape
import com.example.ui.theme.PosterCornerShape
import com.example.ui.theme.ScrimDark60
import com.example.ui.theme.ScrimTransparent
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HikariTopBar(
    title: String,
    onSettingsClick: (() -> Unit)? = null,
    onBackClick: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXS)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("top_bar_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
            }
        },
        actions = {
            actions()
            if (onSettingsClick != null) {
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("top_bar_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = stringResource(R.string.cd_open_settings)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground
        )
    )
}

@Composable
fun OfflineBanner(
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = CardCornerShape,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenHorizontalPadding, vertical = Dimens.SpaceXS)
            .testTag("offline_banner")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM),
            modifier = Modifier.padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM)
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(R.string.offline_banner_message),
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.ScreenHorizontalPadding, vertical = Dimens.SpaceXS)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
fun BrandedPosterArt(
    title: String,
    posterUrl: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = title
) {
    val palettePairs = listOf(
        Color(0xFF1F2756) to Color(0xFF53336A),
        Color(0xFF173747) to Color(0xFF392759),
        Color(0xFF2B224C) to Color(0xFF6B2D5C),
        Color(0xFF1C2D42) to Color(0xFF3A4E7A)
    )
    val pairIndex = abs(title.hashCode()) % palettePairs.size
    val (startColor, endColor) = palettePairs[pairIndex]

    Box(
        modifier = modifier
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(startColor, endColor)
                )
            )
    ) {
        if (!posterUrl.isNullOrBlank()) {
            AsyncImage(
                model = posterUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Branded gradient placeholder per Appendix A
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Dimens.SpaceSM)
            ) {
                Icon(
                    imageVector = Icons.Default.MovieFilter,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.78f),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.height(Dimens.SpaceXS))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Subtle bottom scrim for badges
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(ScrimTransparent, ScrimDark60),
                        startY = 120f
                    )
                )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    title: String,
    posterUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    score: Double? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    progressFraction: Float? = null,
    badgeText: String? = null,
    testTag: String = "poster_card_$title"
) {
    Column(
        modifier = modifier
            .clip(PosterCornerShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { onToggleFavorite?.invoke() }
            )
            .semantics { contentDescription = title }
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(Dimens.PosterAspectRatio)
                .clip(PosterCornerShape)
        ) {
            BrandedPosterArt(
                title = title,
                posterUrl = posterUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize()
            )

            if (score != null && score > 0.0) {
                ScoreBadge(
                    score = score,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(Dimens.SpaceXS)
                )
            }

            if (!badgeText.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = PillCornerShape,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(Dimens.SpaceXS)
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            if (onToggleFavorite != null) {
                FavoriteButton(
                    isFavorite = isFavorite,
                    onToggle = onToggleFavorite,
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }

            if (progressFraction != null && progressFraction > 0f) {
                LinearProgressIndicator(
                    progress = { progressFraction.coerceIn(0f, 1f) },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.ProgressBarHeight)
                        .align(Alignment.BottomCenter)
                )
            }
        }

        Spacer(modifier = Modifier.height(Dimens.SpaceXS))

        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

@Composable
fun FavoriteButton(
    isFavorite: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "favorite_button"
) {
    var bounceTrigger by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (bounceTrigger) 1.25f else 1f,
        animationSpec = HikariMotion.favoriteBounceSpring,
        label = "favoriteScale"
    )

    LaunchedEffect(bounceTrigger) {
        if (bounceTrigger) {
            delay(160)
            bounceTrigger = false
        }
    }

    val cd = if (isFavorite) {
        stringResource(R.string.cd_favorite_remove)
    } else {
        stringResource(R.string.cd_favorite_add)
    }

    IconButton(
        onClick = {
            bounceTrigger = true
            onToggle()
        },
        modifier = modifier
            .minimumInteractiveComponentSize()
            .scale(scale)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = cd,
            tint = if (isFavorite) MaterialTheme.colorScheme.secondary else Color.White
        )
    }
}

@Composable
fun ScoreBadge(
    score: Double,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xCC101433),
        contentColor = Color.White,
        shape = PillCornerShape,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = Color(0xFFFFD54F),
                modifier = Modifier.size(12.dp)
            )
            Text(
                text = String.format(Locale.US, "%.1f", score),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun GenreChip(
    genre: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = PillCornerShape,
        modifier = modifier
    ) {
        Text(
            text = genre,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = Dimens.SpaceSM, vertical = 6.dp)
        )
    }
}

@Composable
fun MediaRow(
    row: HomeFeedRow,
    favoriteIds: Set<String>,
    onAnimeClick: (Anime) -> Unit,
    onContinueWatchingClick: (ContinueWatchingItem) -> Unit,
    onToggleFavorite: (Anime) -> Unit,
    onRetryRow: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = row.title,
            subtitle = row.subtitle
        )

        when (val state = row.state) {
            is HomeRowState.Loading -> {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.ScreenHorizontalPadding, vertical = Dimens.SpaceSM)
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .width(Dimens.PosterRowWidth)
                                .aspectRatio(Dimens.PosterAspectRatio)
                                .clip(PosterCornerShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            if (it == 0) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }

            is HomeRowState.Error -> {
                Card(
                    shape = CardCornerShape,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.ScreenHorizontalPadding, vertical = Dimens.SpaceXS)
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
                                text = ErrorMessages.resolve(state.error),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { onRetryRow(row.id) },
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("retry_row_${row.id}")
                        ) {
                            Text(text = stringResource(R.string.action_retry))
                        }
                    }
                }
            }

            is HomeRowState.Content -> {
                when (val content = state.content) {
                    is HomeRowContent.ContinueWatching -> {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Dimens.ScreenHorizontalPadding),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM),
                            modifier = Modifier.testTag("row_${row.id}")
                        ) {
                            items(
                                items = content.items,
                                key = { "${it.anime.id.compositeKey}_ep_${it.progress.episodeNumber}" }
                            ) { item ->
                                val badge = if (item.progress.positionMs > 0L) {
                                    stringResource(
                                        R.string.library_history_episode_left,
                                        item.progress.episodeNumber,
                                        item.progress.remainingMinutes
                                    )
                                } else {
                                    stringResource(
                                        R.string.details_episode_number,
                                        item.progress.episodeNumber
                                    )
                                }
                                PosterCard(
                                    title = item.anime.title,
                                    posterUrl = item.anime.posterUrl,
                                    onClick = { onContinueWatchingClick(item) },
                                    progressFraction = item.progress.progressFraction.coerceAtLeast(0.06f),
                                    badgeText = badge,
                                    isFavorite = item.anime.id.compositeKey in favoriteIds,
                                    onToggleFavorite = { onToggleFavorite(item.anime) },
                                    modifier = Modifier.width(Dimens.PosterRowWidth),
                                    testTag = "continue_card_${item.anime.id.compositeKey}"
                                )
                            }
                        }
                    }

                    is HomeRowContent.AnimeList -> {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = Dimens.ScreenHorizontalPadding),
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM),
                            modifier = Modifier.testTag("row_${row.id}")
                        ) {
                            items(
                                items = content.items,
                                key = { it.id.compositeKey }
                            ) { anime ->
                                val badge = if (anime.id.sourceId != "jikan") "Playable" else null
                                PosterCard(
                                    title = anime.title,
                                    posterUrl = anime.posterUrl,
                                    score = anime.score,
                                    badgeText = badge,
                                    isFavorite = anime.id.compositeKey in favoriteIds,
                                    onToggleFavorite = { onToggleFavorite(anime) },
                                    onClick = { onAnimeClick(anime) },
                                    modifier = Modifier.width(Dimens.PosterRowWidth),
                                    testTag = "poster_card_${anime.id.compositeKey}"
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
fun EpisodeRow(
    episode: Episode,
    progress: WatchProgress?,
    isCurrent: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val watchedColor = LocalHikariColors.current.watchedSuccess
    val isWatched = progress?.completed == true
    val progressFraction = progress?.progressFraction ?: 0f

    val stateDesc = when {
        isWatched -> stringResource(R.string.details_episode_watched)
        progressFraction > 0f -> "${(progressFraction * 100).toInt()}% watched"
        else -> "Unwatched"
    }

    Card(
        shape = CardCornerShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(CardCornerShape)
            .clickable(onClick = onClick)
            .semantics { stateDescription = stateDesc }
            .testTag("episode_row_${episode.number}")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceMD),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Dimens.SpaceMD)
            ) {
                Surface(
                    color = if (isWatched) {
                        watchedColor.copy(alpha = 0.2f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    shape = PillCornerShape,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.details_episode_number, episode.number),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isWatched) watchedColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title ?: stringResource(R.string.details_episode_number, episode.number),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    val durationMinutes = episode.durationSeconds?.let { (it / 60L).coerceAtLeast(1L) }
                    val subtitleParts = buildList {
                        if (durationMinutes != null) add("${durationMinutes}m")
                        if (isWatched) {
                            add(stringResource(R.string.details_episode_watched))
                        } else if (progress != null && progress.positionMs > 0L) {
                            add("${progress.remainingMinutes}m left")
                        }
                    }
                    if (subtitleParts.isNotEmpty()) {
                        Text(
                            text = subtitleParts.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isWatched) watchedColor else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isWatched) {
                    // Watched = check icon + color per §6.7 accessibility rule
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = stringResource(R.string.details_episode_watched),
                        tint = watchedColor,
                        modifier = Modifier
                            .size(24.dp)
                            .testTag("episode_watched_icon_${episode.number}")
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = stringResource(R.string.player_play),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (progressFraction > 0f && !isWatched) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                )
            }
        }
    }
}

@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    message: String? = null
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.SpaceXL)
            .testTag("loading_state")
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        if (!message.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(Dimens.SpaceMD))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ErrorState(
    error: AppError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.SpaceXL)
            .testTag("error_state")
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(Dimens.SpaceMD))
        Text(
            text = ErrorMessages.resolve(error),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.SpaceLG))
        Button(
            onClick = onRetry,
            shape = PillCornerShape,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag("error_retry_button")
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(Dimens.SpaceXS))
            Text(text = stringResource(R.string.action_retry))
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    hint: String,
    modifier: Modifier = Modifier,
    actionButton: (@Composable () -> Unit)? = null
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxSize()
            .padding(Dimens.SpaceXL)
            .testTag("empty_state")
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = PillCornerShape,
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(Dimens.SpaceMD))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Dimens.SpaceXS))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (actionButton != null) {
            Spacer(modifier = Modifier.height(Dimens.SpaceLG))
            actionButton()
        }
    }
}
