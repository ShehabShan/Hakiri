package com.example.ui.navigation

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.R
import com.example.di.AppContainer
import com.example.domain.model.MediaId
import com.example.ui.details.DetailsScreen
import com.example.ui.details.DetailsViewModel
import com.example.ui.home.HomeScreen
import com.example.ui.home.HomeViewModel
import com.example.ui.library.LibraryScreen
import com.example.ui.library.LibraryViewModel
import com.example.ui.player.PlayerScreen
import com.example.ui.player.PlayerViewModel
import com.example.ui.search.SearchScreen
import com.example.ui.search.SearchViewModel
import com.example.ui.settings.SettingsScreen
import com.example.ui.settings.SettingsViewModel
import com.example.ui.theme.HikariMotion

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Search : Screen("search")
    data object Library : Screen("library")
    data object Settings : Screen("settings")

    data object Details : Screen("details/{sourceId}/{mediaId}") {
        const val ARG_SOURCE_ID = "sourceId"
        const val ARG_MEDIA_ID = "mediaId"

        fun createRoute(mediaId: MediaId): String {
            return "details/${Uri.encode(mediaId.sourceId)}/${Uri.encode(mediaId.value)}"
        }
    }

    data object Player : Screen("player/{sourceId}/{mediaId}/{episode}") {
        const val ARG_SOURCE_ID = "sourceId"
        const val ARG_MEDIA_ID = "mediaId"
        const val ARG_EPISODE = "episode"

        fun createRoute(mediaId: MediaId, episodeNumber: Int): String {
            return "player/${Uri.encode(mediaId.sourceId)}/${Uri.encode(mediaId.value)}/$episodeNumber"
        }
    }
}

enum class BottomNavDestination(
    val screen: Screen,
    @StringRes val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    HOME(
        screen = Screen.Home,
        labelRes = R.string.nav_home,
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
        testTag = "bottom_nav_home"
    ),
    SEARCH(
        screen = Screen.Search,
        labelRes = R.string.nav_search,
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search,
        testTag = "bottom_nav_search"
    ),
    LIBRARY(
        screen = Screen.Library,
        labelRes = R.string.nav_library,
        selectedIcon = Icons.Filled.CollectionsBookmark,
        unselectedIcon = Icons.Outlined.CollectionsBookmark,
        testTag = "bottom_nav_library"
    )
}

@Composable
fun HikariNavHost(
    appContainer: AppContainer,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentRoute = currentDestination?.route

    val showBottomBar = currentRoute in setOf(
        Screen.Home.route,
        Screen.Search.route,
        Screen.Library.route
    )

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.testTag("hikari_bottom_bar")
                ) {
                    BottomNavDestination.entries.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == dest.screen.route
                        } == true
                        val label = stringResource(dest.labelRes)

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                if (!selected) {
                                    navController.navigate(dest.screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                    contentDescription = label
                                )
                            },
                            label = { Text(text = label) },
                            modifier = Modifier.testTag(dest.testTag)
                        )
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            enterTransition = { HikariMotion.navEnterTransition() },
            exitTransition = { HikariMotion.navExitTransition() },
            popEnterTransition = { HikariMotion.navEnterTransition() },
            popExitTransition = { HikariMotion.navExitTransition() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            composable(Screen.Home.route) {
                val vm: HomeViewModel = viewModel(
                    factory = HomeViewModel.provideFactory(
                        buildHomeFeedUseCase = appContainer.buildHomeFeedUseCase,
                        catalogRepository = appContainer.catalogRepository,
                        metadataRepository = appContainer.metadataRepository,
                        libraryRepository = appContainer.libraryRepository,
                        settingsRepository = appContainer.settingsRepository,
                        toggleFavoriteUseCase = appContainer.toggleFavoriteUseCase
                    )
                )
                HomeScreen(
                    viewModel = vm,
                    onAnimeClick = { mediaId ->
                        navController.navigate(Screen.Details.createRoute(mediaId))
                    },
                    onResumeEpisodeClick = { mediaId, episodeNumber ->
                        navController.navigate(Screen.Player.createRoute(mediaId, episodeNumber))
                    },
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(Screen.Search.route) {
                val vm: SearchViewModel = viewModel(
                    factory = SearchViewModel.provideFactory(
                        catalogRepository = appContainer.catalogRepository,
                        metadataRepository = appContainer.metadataRepository,
                        libraryRepository = appContainer.libraryRepository,
                        settingsRepository = appContainer.settingsRepository,
                        toggleFavoriteUseCase = appContainer.toggleFavoriteUseCase
                    )
                )
                SearchScreen(
                    viewModel = vm,
                    onAnimeClick = { mediaId ->
                        navController.navigate(Screen.Details.createRoute(mediaId))
                    },
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(Screen.Library.route) {
                val vm: LibraryViewModel = viewModel(
                    factory = LibraryViewModel.provideFactory(
                        libraryRepository = appContainer.libraryRepository
                    )
                )
                LibraryScreen(
                    viewModel = vm,
                    onAnimeClick = { mediaId ->
                        navController.navigate(Screen.Details.createRoute(mediaId))
                    },
                    onResumeEpisodeClick = { mediaId, episodeNumber ->
                        navController.navigate(Screen.Player.createRoute(mediaId, episodeNumber))
                    },
                    onSettingsClick = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(Screen.Settings.route) {
                val vm: SettingsViewModel = viewModel(
                    factory = SettingsViewModel.provideFactory(
                        settingsRepository = appContainer.settingsRepository,
                        catalogRepository = appContainer.catalogRepository,
                        libraryRepository = appContainer.libraryRepository
                    )
                )
                SettingsScreen(
                    viewModel = vm,
                    onBackClick = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Details.route,
                arguments = listOf(
                    navArgument(Screen.Details.ARG_SOURCE_ID) { type = NavType.StringType },
                    navArgument(Screen.Details.ARG_MEDIA_ID) { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val sourceId = Uri.decode(
                    backStackEntry.arguments?.getString(Screen.Details.ARG_SOURCE_ID).orEmpty()
                )
                val rawMediaId = Uri.decode(
                    backStackEntry.arguments?.getString(Screen.Details.ARG_MEDIA_ID).orEmpty()
                )
                val mediaId = MediaId(sourceId = sourceId, value = rawMediaId)

                val vm: DetailsViewModel = viewModel(
                    key = "details_${mediaId.compositeKey}",
                    factory = DetailsViewModel.provideFactory(
                        mediaId = mediaId,
                        catalogRepository = appContainer.catalogRepository,
                        libraryRepository = appContainer.libraryRepository,
                        toggleFavoriteUseCase = appContainer.toggleFavoriteUseCase
                    )
                )
                val state by vm.uiState.collectAsStateWithLifecycle()

                DetailsScreen(
                    state = state,
                    onBackClick = { navController.popBackStack() },
                    onPlayEpisodeClick = { id, episodeNumber ->
                        navController.navigate(Screen.Player.createRoute(id, episodeNumber))
                    },
                    onToggleFavorite = vm::toggleFavorite,
                    onRetryDetails = { vm.loadAll(forceRefresh = true) },
                    onRetryEpisodes = vm::retryEpisodes
                )
            }

            composable(
                route = Screen.Player.route,
                arguments = listOf(
                    navArgument(Screen.Player.ARG_SOURCE_ID) { type = NavType.StringType },
                    navArgument(Screen.Player.ARG_MEDIA_ID) { type = NavType.StringType },
                    navArgument(Screen.Player.ARG_EPISODE) { type = NavType.IntType }
                )
            ) { backStackEntry ->
                val sourceId = Uri.decode(
                    backStackEntry.arguments?.getString(Screen.Player.ARG_SOURCE_ID).orEmpty()
                )
                val rawMediaId = Uri.decode(
                    backStackEntry.arguments?.getString(Screen.Player.ARG_MEDIA_ID).orEmpty()
                )
                val episodeNum = backStackEntry.arguments?.getInt(Screen.Player.ARG_EPISODE) ?: 1
                val mediaId = MediaId(sourceId = sourceId, value = rawMediaId)

                val vm: PlayerViewModel = viewModel(
                    key = "player_${mediaId.compositeKey}_$episodeNum",
                    factory = PlayerViewModel.provideFactory(
                        mediaId = mediaId,
                        episodeNumber = episodeNum,
                        catalogRepository = appContainer.catalogRepository,
                        settingsRepository = appContainer.settingsRepository,
                        resolvePlayableStreamUseCase = appContainer.resolvePlayableStreamUseCase,
                        recordWatchProgressUseCase = appContainer.recordWatchProgressUseCase
                    )
                )

                PlayerScreen(
                    viewModel = vm,
                    okHttpClient = appContainer.okHttpClient,
                    onBackClick = { navController.popBackStack() }
                )
            }
        }
    }
}
