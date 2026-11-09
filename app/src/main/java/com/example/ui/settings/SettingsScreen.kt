package com.example.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.R
import com.example.domain.model.SourceInfo
import com.example.domain.model.ThemeMode
import com.example.domain.model.UserSettings
import com.example.domain.repository.CatalogRepository
import com.example.domain.repository.LibraryRepository
import com.example.domain.repository.SettingsRepository
import com.example.ui.components.HikariTopBar
import com.example.ui.theme.CardCornerShape
import com.example.ui.theme.Dimens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

@Immutable
data class SettingsUiState(
    val settings: UserSettings = UserSettings(),
    val registeredSources: List<SourceInfo> = emptyList(),
    val formattedCacheSize: String = "0 KB"
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val catalogRepository: CatalogRepository,
    private val libraryRepository: LibraryRepository
) : ViewModel() {

    private val cacheBytesState = MutableStateFlow(0L)

    val uiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.settingsFlow,
        cacheBytesState
    ) { settings, cacheBytes ->
        SettingsUiState(
            settings = settings,
            registeredSources = catalogRepository.getRegisteredSources(),
            formattedCacheSize = formatBytes(cacheBytes)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(
            registeredSources = catalogRepository.getRegisteredSources()
        )
    )

    init {
        refreshCacheSize()
    }

    fun refreshCacheSize() {
        viewModelScope.launch {
            cacheBytesState.value = catalogRepository.getEstimatedCacheSizeBytes()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            settingsRepository.setThemeMode(mode)
        }
    }

    fun setAutoplayNext(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoplayNext(enabled)
        }
    }

    fun setResumeAutomatically(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setResumeAutomatically(enabled)
        }
    }

    suspend fun toggleSource(sourceId: String): Boolean {
        val allIds = catalogRepository.getRegisteredSources().map { it.id }.toSet()
        return settingsRepository.toggleSourceEnabled(sourceId, allIds)
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            libraryRepository.clearWatchHistory()
        }
    }

    fun clearCatalogAndMetadataCaches() {
        viewModelScope.launch {
            catalogRepository.clearCatalogCache()
            refreshCacheSize()
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "0 KB"
        val kb = bytes / 1024.0
        return if (kb < 1024.0) {
            String.format(Locale.US, "%.1f KB", kb)
        } else {
            String.format(Locale.US, "%.2f MB", kb / 1024.0)
        }
    }

    companion object {
        fun provideFactory(
            settingsRepository: SettingsRepository,
            catalogRepository: CatalogRepository,
            libraryRepository: LibraryRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SettingsViewModel(
                    settingsRepository = settingsRepository,
                    catalogRepository = catalogRepository,
                    libraryRepository = libraryRepository
                ) as T
            }
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val lastSourceWarning = stringResource(R.string.settings_source_last_warning)

    var showClearHistoryDialog by rememberSaveable { mutableStateOf(false) }
    var showClearCacheDialog by rememberSaveable { mutableStateOf(false) }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.library_history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.library_history_clear_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearHistoryDialog = false
                        viewModel.clearWatchHistory()
                    },
                    modifier = Modifier.testTag("settings_confirm_clear_history")
                ) {
                    Text(
                        text = stringResource(R.string.action_confirm),
                        color = MaterialTheme.colorScheme.error
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text(stringResource(R.string.settings_clear_cache_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_cache_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearCacheDialog = false
                        viewModel.clearCatalogAndMetadataCaches()
                    },
                    modifier = Modifier.testTag("settings_confirm_clear_cache")
                ) {
                    Text(
                        text = stringResource(R.string.action_confirm),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            HikariTopBar(
                title = stringResource(R.string.nav_settings),
                onBackClick = onBackClick
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = Dimens.ScreenHorizontalPadding,
                end = Dimens.ScreenHorizontalPadding,
                top = Dimens.SpaceSM,
                bottom = Dimens.SpaceXL
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.SpaceMD),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .testTag("settings_list")
        ) {
            // 1. Appearance (Theme Radio Buttons)
            item(key = "section_appearance") {
                SettingsGroupCard(title = stringResource(R.string.settings_section_appearance)) {
                    ThemeRadioRow(
                        label = stringResource(R.string.settings_theme_dark),
                        selected = state.settings.themeMode == ThemeMode.DARK,
                        onClick = { viewModel.setThemeMode(ThemeMode.DARK) },
                        testTag = "theme_radio_dark"
                    )
                    ThemeRadioRow(
                        label = stringResource(R.string.settings_theme_light),
                        selected = state.settings.themeMode == ThemeMode.LIGHT,
                        onClick = { viewModel.setThemeMode(ThemeMode.LIGHT) },
                        testTag = "theme_radio_light"
                    )
                    ThemeRadioRow(
                        label = stringResource(R.string.settings_theme_system),
                        selected = state.settings.themeMode == ThemeMode.SYSTEM,
                        onClick = { viewModel.setThemeMode(ThemeMode.SYSTEM) },
                        testTag = "theme_radio_system"
                    )
                }
            }

            // 2. Content Sources (Registry list with >= 1 active source enforced)
            item(key = "section_sources") {
                SettingsGroupCard(title = stringResource(R.string.settings_section_sources)) {
                    Text(
                        text = stringResource(R.string.settings_sources_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Dimens.SpaceXS)
                    )
                    state.registeredSources.forEach { source ->
                        val isEnabled = source.id in state.settings.enabledSources
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = Dimens.SpaceXS)
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = source.displayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${source.language.uppercase()} · v${source.version} · ${source.capabilities.joinToString()}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = {
                                    coroutineScope.launch {
                                        val ok = viewModel.toggleSource(source.id)
                                        if (!ok) {
                                            snackbarHostState.showSnackbar(lastSourceWarning)
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .minimumInteractiveComponentSize()
                                    .testTag("source_switch_${source.id}")
                            )
                        }
                    }
                }
            }

            // 3. Playback Preferences
            item(key = "section_playback") {
                SettingsGroupCard(title = stringResource(R.string.settings_section_playback)) {
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_autoplay_title),
                        subtitle = stringResource(R.string.settings_autoplay_subtitle),
                        checked = state.settings.autoplayNext,
                        onCheckedChange = viewModel::setAutoplayNext,
                        testTag = "switch_autoplay_next"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = Dimens.SpaceXS))
                    SettingsSwitchRow(
                        title = stringResource(R.string.settings_resume_title),
                        subtitle = stringResource(R.string.settings_resume_subtitle),
                        checked = state.settings.resumeAutomatically,
                        onCheckedChange = viewModel::setResumeAutomatically,
                        testTag = "switch_resume_auto"
                    )
                }
            }

            // 4. Data & Storage
            item(key = "section_data") {
                SettingsGroupCard(title = stringResource(R.string.settings_section_data)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearHistoryDialog = true }
                            .padding(vertical = Dimens.SpaceSM)
                            .testTag("settings_clear_history_row")
                    ) {
                        Text(
                            text = stringResource(R.string.settings_clear_history_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.settings_clear_history_subtitle),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showClearCacheDialog = true }
                            .padding(vertical = Dimens.SpaceSM)
                            .testTag("settings_clear_cache_row")
                    ) {
                        Text(
                            text = stringResource(R.string.settings_clear_cache_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(
                                R.string.settings_clear_cache_subtitle,
                                state.formattedCacheSize
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // 5. About & Legal Notice
            item(key = "section_about") {
                SettingsGroupCard(title = stringResource(R.string.settings_section_about)) {
                    Text(
                        text = stringResource(R.string.settings_version_label, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(Dimens.SpaceXS))
                    Text(
                        text = stringResource(R.string.settings_legal_notice),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("settings_legal_notice_text")
                    )
                    Spacer(modifier = Modifier.height(Dimens.SpaceSM))
                    Text(
                        text = stringResource(R.string.settings_licenses_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        shape = CardCornerShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.SpaceMD)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = Dimens.SpaceSM)
            )
            content()
        }
    }
}

@Composable
private fun ThemeRadioRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
            .testTag(testTag)
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            modifier = Modifier.minimumInteractiveComponentSize()
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.SpaceXXS)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag(testTag)
        )
    }
}
