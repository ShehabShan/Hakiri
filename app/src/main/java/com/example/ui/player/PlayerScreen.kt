package com.example.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.R
import com.example.domain.model.Anime
import com.example.domain.model.AppError
import com.example.domain.model.Episode
import com.example.domain.model.MediaId
import com.example.domain.model.SourceResult
import com.example.domain.model.StreamKind
import com.example.domain.model.StreamLink
import com.example.domain.repository.CatalogRepository
import com.example.domain.repository.SettingsRepository
import com.example.domain.usecase.RecordWatchProgressUseCase
import com.example.domain.usecase.ResolvePlayableStreamUseCase
import com.example.ui.components.ErrorMessages
import com.example.ui.components.ErrorState
import com.example.ui.components.LoadingState
import com.example.ui.theme.CardCornerShape
import com.example.ui.theme.Dimens
import com.example.ui.theme.HikariMotion
import com.example.ui.theme.PillCornerShape
import com.example.ui.theme.ScrimDark60
import com.example.ui.theme.ScrimTransparent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import java.util.Locale

@Immutable
data class PlayerUiState(
    val mediaId: MediaId,
    val episodeNumber: Int,
    val isLoading: Boolean = true,
    val anime: Anime? = null,
    val episode: Episode? = null,
    val nextEpisodeNumber: Int? = null,
    val selectedStream: StreamLink? = null,
    val availableStreams: List<StreamLink> = emptyList(),
    val sanitizedHeaders: Map<String, String> = emptyMap(),
    val initialPositionMs: Long = 0L,
    val autoplayNextEnabled: Boolean = true,
    val resolveError: AppError? = null,
    val playbackError: AppError? = null
)

class PlayerViewModel(
    initialMediaId: MediaId,
    initialEpisodeNumber: Int,
    private val catalogRepository: CatalogRepository,
    private val settingsRepository: SettingsRepository,
    private val resolvePlayableStreamUseCase: ResolvePlayableStreamUseCase,
    private val recordWatchProgressUseCase: RecordWatchProgressUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            mediaId = initialMediaId,
            episodeNumber = initialEpisodeNumber
        )
    )
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    init {
        loadEpisodeSession(
            episodeNumber = initialEpisodeNumber,
            requestedQuality = null,
            overrideStartPositionMs = null
        )
    }

    fun loadEpisodeSession(
        episodeNumber: Int = _uiState.value.episodeNumber,
        requestedQuality: String? = null,
        overrideStartPositionMs: Long? = null
    ) {
        val mediaId = _uiState.value.mediaId
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    episodeNumber = episodeNumber,
                    isLoading = true,
                    resolveError = null,
                    playbackError = null
                )
            }

            val settings = settingsRepository.settingsFlow.first()
            val detailsRes = catalogRepository.getDetails(mediaId)
            val episodesRes = catalogRepository.getEpisodes(mediaId)
            val streamRes = resolvePlayableStreamUseCase(
                mediaId = mediaId,
                episodeNumber = episodeNumber,
                requestedQualityLabel = requestedQuality
            )

            val anime = (detailsRes as? SourceResult.Success)?.data
            val episodes = (episodesRes as? SourceResult.Success)?.data.orEmpty()
            val currentEp = episodes.firstOrNull { it.number == episodeNumber }
            val nextEp = episodes.sortedBy { it.number }
                .firstOrNull { it.number > episodeNumber }
                ?.number

            when (streamRes) {
                is SourceResult.Success -> {
                    val session = streamRes.data
                    val startPos = overrideStartPositionMs ?: session.resumePositionMs
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            anime = anime,
                            episode = currentEp,
                            nextEpisodeNumber = nextEp,
                            selectedStream = session.selectedStream,
                            availableStreams = session.availableStreams,
                            sanitizedHeaders = session.sanitizedHeaders,
                            initialPositionMs = startPos,
                            autoplayNextEnabled = settings.autoplayNext,
                            resolveError = null,
                            playbackError = null
                        )
                    }
                }
                is SourceResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            anime = anime,
                            episode = currentEp,
                            nextEpisodeNumber = nextEp,
                            resolveError = streamRes.error
                        )
                    }
                }
            }
        }
    }

    fun selectQuality(qualityLabel: String, currentPositionMs: Long) {
        viewModelScope.launch {
            settingsRepository.setPreferredQuality(qualityLabel)
            loadEpisodeSession(
                episodeNumber = _uiState.value.episodeNumber,
                requestedQuality = qualityLabel,
                overrideStartPositionMs = currentPositionMs.coerceAtLeast(0L)
            )
        }
    }

    fun saveProgress(positionMs: Long, durationMs: Long) {
        val currentAnime = _uiState.value.anime ?: return
        val epNum = _uiState.value.episodeNumber
        if (durationMs <= 0L && positionMs <= 0L) return
        viewModelScope.launch {
            recordWatchProgressUseCase(
                anime = currentAnime,
                episodeNumber = epNum,
                positionMs = positionMs,
                durationMs = durationMs
            )
        }
    }

    fun onPlaybackError(message: String?, currentPositionMs: Long) {
        _uiState.update {
            it.copy(
                initialPositionMs = currentPositionMs.coerceAtLeast(0L),
                playbackError = AppError.Network
            )
        }
    }

    fun retryMidPlayback(currentPositionMs: Long) {
        loadEpisodeSession(
            episodeNumber = _uiState.value.episodeNumber,
            requestedQuality = _uiState.value.selectedStream?.qualityLabel,
            overrideStartPositionMs = currentPositionMs.coerceAtLeast(_uiState.value.initialPositionMs)
        )
    }

    companion object {
        fun provideFactory(
            mediaId: MediaId,
            episodeNumber: Int,
            catalogRepository: CatalogRepository,
            settingsRepository: SettingsRepository,
            resolvePlayableStreamUseCase: ResolvePlayableStreamUseCase,
            recordWatchProgressUseCase: RecordWatchProgressUseCase
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlayerViewModel(
                    initialMediaId = mediaId,
                    initialEpisodeNumber = episodeNumber,
                    catalogRepository = catalogRepository,
                    settingsRepository = settingsRepository,
                    resolvePlayableStreamUseCase = resolvePlayableStreamUseCase,
                    recordWatchProgressUseCase = recordWatchProgressUseCase
                ) as T
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun formatDurationMs(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1000L).toInt()
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    okHttpClient: OkHttpClient,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isPlaying by remember { mutableStateOf(true) }
    var isBuffering by remember { mutableStateOf(true) }
    var currentPosMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var bufferedPosMs by remember { mutableLongStateOf(0L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var qualityMenuExpanded by remember { mutableStateOf(false) }
    var isLandscapeFullscreen by rememberSaveable { mutableStateOf(false) }

    var showAutoplayCountdown by remember { mutableStateOf(false) }
    var autoplayCountdownSec by remember { mutableIntStateOf(10) }

    // Keep screen on during playback per §6.5-4
    DisposableEffect(view) {
        val previousKeepScreenOn = view.keepScreenOn
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = previousKeepScreenOn
            context.findActivity()?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    val exoPlayer = remember(context) {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()
        ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
    }

    // Bind player listener & release onDispose
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
                if (playbackState == Player.STATE_ENDED) {
                    val dur = exoPlayer.duration.coerceAtLeast(0L)
                    viewModel.saveProgress(dur, dur)
                    if (state.autoplayNextEnabled && state.nextEpisodeNumber != null) {
                        autoplayCountdownSec = 10
                        showAutoplayCountdown = true
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                viewModel.onPlaybackError(error.message, exoPlayer.currentPosition)
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            val pos = exoPlayer.currentPosition.coerceAtLeast(0L)
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            viewModel.saveProgress(pos, dur)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Pause on backgrounding (do not stop) and save progress per §6.5-4
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                exoPlayer.pause()
                viewModel.saveProgress(
                    positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Load MediaItem when selectedStream changes
    LaunchedEffect(state.selectedStream, state.episodeNumber) {
        val stream = state.selectedStream ?: return@LaunchedEffect
        showAutoplayCountdown = false

        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setDefaultRequestProperties(state.sanitizedHeaders)
        val mediaSourceFactory = DefaultMediaSourceFactory(context)
            .setDataSourceFactory(dataSourceFactory)

        val mimeType = when (stream.kind) {
            StreamKind.HLS -> MimeTypes.APPLICATION_M3U8
            StreamKind.MP4 -> MimeTypes.VIDEO_MP4
        }
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(stream.url))
            .setMimeType(mimeType)
            .build()

        val mediaSource = mediaSourceFactory.createMediaSource(mediaItem)
        exoPlayer.setMediaSource(mediaSource)
        exoPlayer.prepare()
        if (state.initialPositionMs > 0L) {
            exoPlayer.seekTo(state.initialPositionMs)
            currentPosMs = state.initialPositionMs
        }
        exoPlayer.playWhenReady = true
    }

    // Poll UI progress every 500ms & save progress to Room every 5 seconds per §6.5-4 & §7
    LaunchedEffect(exoPlayer, state.selectedStream) {
        var tickCount = 0
        while (true) {
            delay(500L)
            currentPosMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.coerceAtLeast(0L)
            bufferedPosMs = exoPlayer.bufferedPosition.coerceAtLeast(0L)

            tickCount++
            if (tickCount >= 10 && exoPlayer.isPlaying && durationMs > 0L) {
                tickCount = 0
                viewModel.saveProgress(currentPosMs, durationMs)
            }
        }
    }

    // Auto-hide controls after 3 seconds when playing per §6.5-4
    LaunchedEffect(controlsVisible, isPlaying, qualityMenuExpanded) {
        if (controlsVisible && isPlaying && !qualityMenuExpanded) {
            delay(3_000L)
            controlsVisible = false
        }
    }

    // 10-second autoplay next countdown per §6.5-4
    LaunchedEffect(showAutoplayCountdown, state.nextEpisodeNumber) {
        val nextEp = state.nextEpisodeNumber
        if (showAutoplayCountdown && nextEp != null) {
            while (autoplayCountdownSec > 0 && showAutoplayCountdown) {
                delay(1_000L)
                autoplayCountdownSec -= 1
            }
            if (showAutoplayCountdown && autoplayCountdownSec <= 0) {
                showAutoplayCountdown = false
                viewModel.loadEpisodeSession(
                    episodeNumber = nextEp,
                    overrideStartPositionMs = 0L
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                controlsVisible = !controlsVisible
            }
            .testTag("player_screen_root")
    ) {
        when {
            state.isLoading -> {
                LoadingState()
            }

            state.resolveError != null -> {
                ErrorState(
                    error = state.resolveError!!,
                    onRetry = {
                        viewModel.loadEpisodeSession(episodeNumber = state.episodeNumber)
                    }
                )
            }

            else -> {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            player = exoPlayer
                        }
                    },
                    update = { playerView ->
                        playerView.player = exoPlayer
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isBuffering && state.playbackError == null) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Mid-playback error overlay preserving position per §6.5-4
                if (state.playbackError != null) {
                    Card(
                        shape = CardCornerShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                        ),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(Dimens.SpaceXL)
                            .testTag("player_mid_playback_error_card")
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM),
                            modifier = Modifier.padding(Dimens.SpaceLG)
                        ) {
                            Text(
                                text = ErrorMessages.resolve(state.playbackError!!),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                                Button(
                                    onClick = { viewModel.retryMidPlayback(currentPosMs) },
                                    shape = PillCornerShape,
                                    modifier = Modifier.testTag("player_inline_retry_button")
                                ) {
                                    Text(text = stringResource(R.string.action_retry))
                                }
                                if (state.availableStreams.size > 1) {
                                    FilledTonalButton(
                                        onClick = {
                                            val alt = state.availableStreams.firstOrNull {
                                                it.qualityLabel != state.selectedStream?.qualityLabel
                                            }
                                            if (alt != null) {
                                                viewModel.selectQuality(alt.qualityLabel, currentPosMs)
                                            }
                                        },
                                        shape = PillCornerShape
                                    ) {
                                        Text(text = stringResource(R.string.player_try_another_quality))
                                    }
                                }
                            }
                        }
                    }
                }

                // Autoplay Next Episode 10s End-Card Overlay
                if (showAutoplayCountdown && state.nextEpisodeNumber != null) {
                    Card(
                        shape = CardCornerShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(Dimens.SpaceLG)
                            .testTag("player_autoplay_end_card")
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceSM),
                            modifier = Modifier.padding(Dimens.SpaceMD)
                        ) {
                            Text(
                                text = stringResource(
                                    R.string.player_autoplay_countdown,
                                    state.nextEpisodeNumber!!,
                                    autoplayCountdownSec
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceSM)) {
                                OutlinedButton(
                                    onClick = { showAutoplayCountdown = false },
                                    shape = PillCornerShape,
                                    modifier = Modifier.testTag("player_autoplay_cancel_button")
                                ) {
                                    Text(text = stringResource(R.string.player_autoplay_cancel))
                                }
                                Button(
                                    onClick = {
                                        showAutoplayCountdown = false
                                        viewModel.loadEpisodeSession(
                                            episodeNumber = state.nextEpisodeNumber!!,
                                            overrideStartPositionMs = 0L
                                        )
                                    },
                                    shape = PillCornerShape,
                                    modifier = Modifier.testTag("player_autoplay_play_now_button")
                                ) {
                                    Text(text = stringResource(R.string.player_autoplay_play_now))
                                }
                            }
                        }
                    }
                }

                // Transport Controls Overlay (150ms fade per §6.6)
                AnimatedVisibility(
                    visible = controlsVisible,
                    enter = fadeIn(tween(HikariMotion.ControlsFadeDurationMs)),
                    exit = fadeOut(tween(HikariMotion.ControlsFadeDurationMs)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        ScrimDark60,
                                        ScrimTransparent,
                                        ScrimDark60
                                    )
                                )
                            )
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                    ) {
                        // Top bar: Back, Title + Episode, Quality selector, Fullscreen toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter)
                                .padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceXS)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                IconButton(
                                    onClick = {
                                        viewModel.saveProgress(currentPosMs, durationMs)
                                        onBackClick()
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("player_back_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(R.string.cd_back),
                                        tint = Color.White
                                    )
                                }
                                Column(modifier = Modifier.padding(start = Dimens.SpaceXS)) {
                                    Text(
                                        text = state.anime?.title ?: "",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val epTitle = state.episode?.title
                                        ?: stringResource(R.string.details_episode_number, state.episodeNumber)
                                    Text(
                                        text = epTitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = Color.White.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (state.availableStreams.isNotEmpty()) {
                                    Box {
                                        Surface(
                                            color = Color.White.copy(alpha = 0.18f),
                                            shape = PillCornerShape,
                                            modifier = Modifier
                                                .minimumInteractiveComponentSize()
                                                .clickable { qualityMenuExpanded = true }
                                                .testTag("player_quality_button")
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(
                                                    horizontal = Dimens.SpaceSM,
                                                    vertical = 6.dp
                                                )
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.HighQuality,
                                                    contentDescription = null,
                                                    tint = Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = state.selectedStream?.qualityLabel ?: "auto",
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }

                                        DropdownMenu(
                                            expanded = qualityMenuExpanded,
                                            onDismissRequest = { qualityMenuExpanded = false }
                                        ) {
                                            state.availableStreams.forEach { stream ->
                                                DropdownMenuItem(
                                                    text = {
                                                        Text(
                                                            text = "${stream.qualityLabel} (${stream.kind.name})"
                                                        )
                                                    },
                                                    onClick = {
                                                        qualityMenuExpanded = false
                                                        viewModel.selectQuality(
                                                            stream.qualityLabel,
                                                            currentPosMs
                                                        )
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        isLandscapeFullscreen = !isLandscapeFullscreen
                                        context.findActivity()?.requestedOrientation =
                                            if (isLandscapeFullscreen) {
                                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                            } else {
                                                ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                            }
                                    },
                                    modifier = Modifier
                                        .minimumInteractiveComponentSize()
                                        .testTag("player_fullscreen_button")
                                ) {
                                    Icon(
                                        imageVector = if (isLandscapeFullscreen) {
                                            Icons.Default.FullscreenExit
                                        } else {
                                            Icons.Default.Fullscreen
                                        },
                                        contentDescription = stringResource(R.string.player_fullscreen_toggle),
                                        tint = Color.White
                                    )
                                }
                            }
                        }

                        // Center controls: -10s, Play/Pause, +10s
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(Dimens.SpaceXL),
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            IconButton(
                                onClick = {
                                    val target = (exoPlayer.currentPosition - 10_000L).coerceAtLeast(0L)
                                    exoPlayer.seekTo(target)
                                    currentPosMs = target
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .testTag("player_rewind_10_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = stringResource(R.string.player_rewind_10),
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                shape = PillCornerShape,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clickable {
                                        if (exoPlayer.isPlaying) {
                                            exoPlayer.pause()
                                        } else {
                                            exoPlayer.play()
                                        }
                                    }
                                    .testTag("player_play_pause_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isPlaying) {
                                            stringResource(R.string.player_pause)
                                        } else {
                                            stringResource(R.string.player_play)
                                        },
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(38.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    val maxDur = exoPlayer.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
                                    val target = (exoPlayer.currentPosition + 10_000L).coerceAtMost(maxDur)
                                    exoPlayer.seekTo(target)
                                    currentPosMs = target
                                },
                                modifier = Modifier
                                    .size(56.dp)
                                    .testTag("player_forward_10_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = stringResource(R.string.player_forward_10),
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        // Bottom bar: Seek bar + timestamps + Next Episode button
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = Dimens.SpaceMD, vertical = Dimens.SpaceSM)
                        ) {
                            val sliderFraction = if (durationMs > 0L) {
                                (currentPosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                            } else {
                                0f
                            }

                            Slider(
                                value = sliderFraction,
                                onValueChange = { fraction ->
                                    if (durationMs > 0L) {
                                        val seekTarget = (fraction * durationMs).toLong()
                                        currentPosMs = seekTarget
                                        exoPlayer.seekTo(seekTarget)
                                    }
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("player_seek_slider")
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val bufferedPercent = if (durationMs > 0L) {
                                    ((bufferedPosMs * 100L) / durationMs).coerceIn(0L, 100L)
                                } else {
                                    0L
                                }
                                Text(
                                    text = "${formatDurationMs(currentPosMs)} / ${formatDurationMs(durationMs)} · Buffered $bufferedPercent%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )

                                if (state.nextEpisodeNumber != null) {
                                    FilledTonalButton(
                                        onClick = {
                                            viewModel.saveProgress(currentPosMs, durationMs)
                                            viewModel.loadEpisodeSession(
                                                episodeNumber = state.nextEpisodeNumber!!,
                                                overrideStartPositionMs = 0L
                                            )
                                        },
                                        shape = PillCornerShape,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .testTag("player_next_episode_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.SkipNext,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(
                                                R.string.player_next_episode,
                                                state.nextEpisodeNumber!!
                                            )
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
