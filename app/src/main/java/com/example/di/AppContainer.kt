package com.example.di

import android.content.Context
import com.example.data.jikan.JikanApi
import com.example.data.jikan.JikanRateLimiter
import com.example.data.local.HikariDatabase
import com.example.data.mapper.HikariMappers
import com.example.data.repositoryimpl.CatalogRepositoryImpl
import com.example.data.repositoryimpl.LibraryRepositoryImpl
import com.example.data.repositoryimpl.MetadataRepositoryImpl
import com.example.data.repositoryimpl.SettingsRepositoryImpl
import com.example.data.repositoryimpl.hikariDataStore
import com.example.data.source.SourceHealth
import com.example.data.source.SourceRegistry
import com.example.data.source.openfilms.OpenFilmsSource
import com.example.domain.repository.CatalogRepository
import com.example.domain.repository.LibraryRepository
import com.example.domain.repository.MetadataRepository
import com.example.domain.repository.SettingsRepository
import com.example.domain.usecase.BuildHomeFeedUseCase
import com.example.domain.usecase.RecordWatchProgressUseCase
import com.example.domain.usecase.ResolvePlayableStreamUseCase
import com.example.domain.usecase.ToggleFavoriteUseCase
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

interface AppContainer {
    val okHttpClient: OkHttpClient
    val sourceRegistry: SourceRegistry
    val settingsRepository: SettingsRepository
    val libraryRepository: LibraryRepository
    val metadataRepository: MetadataRepository
    val catalogRepository: CatalogRepository

    val buildHomeFeedUseCase: BuildHomeFeedUseCase
    val resolvePlayableStreamUseCase: ResolvePlayableStreamUseCase
    val toggleFavoriteUseCase: ToggleFavoriteUseCase
    val recordWatchProgressUseCase: RecordWatchProgressUseCase
}

class DefaultAppContainer(
    context: Context
) : AppContainer {

    private val appContext = context.applicationContext

    override val okHttpClient: OkHttpClient by lazy {
        val cacheDir = File(appContext.cacheDir, "http_cache")
        OkHttpClient.Builder()
            .cache(Cache(cacheDir, 20L * 1024L * 1024L)) // 20 MB HTTP disk cache per §5.5
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private val database: HikariDatabase by lazy {
        HikariDatabase.create(appContext)
    }

    override val sourceRegistry: SourceRegistry by lazy {
        val openFilmsSource = OpenFilmsSource(appContext)
        SourceRegistry(
            sources = listOf(openFilmsSource),
            health = SourceHealth(maxConsecutiveFailures = 3)
        )
    }

    private val jikanApi: JikanApi by lazy {
        val contentType = "application/json".toMediaType()
        Retrofit.Builder()
            .baseUrl(JikanApi.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(HikariMappers.json.asConverterFactory(contentType))
            .build()
            .create(JikanApi::class.java)
    }

    private val jikanRateLimiter: JikanRateLimiter by lazy {
        JikanRateLimiter()
    }

    override val settingsRepository: SettingsRepository by lazy {
        SettingsRepositoryImpl(appContext.hikariDataStore)
    }

    override val libraryRepository: LibraryRepository by lazy {
        LibraryRepositoryImpl(
            favoriteDao = database.favoriteDao(),
            watchProgressDao = database.watchProgressDao(),
            animeCacheDao = database.animeCacheDao()
        )
    }

    override val metadataRepository: MetadataRepository by lazy {
        MetadataRepositoryImpl(
            jikanApi = jikanApi,
            rateLimiter = jikanRateLimiter,
            jikanCacheDao = database.jikanCacheDao(),
            animeCacheDao = database.animeCacheDao()
        )
    }

    override val catalogRepository: CatalogRepository by lazy {
        CatalogRepositoryImpl(
            sourceRegistry = sourceRegistry,
            animeCacheDao = database.animeCacheDao(),
            episodeCacheDao = database.episodeCacheDao(),
            jikanCacheDao = database.jikanCacheDao(),
            settingsRepository = settingsRepository,
            metadataRepository = metadataRepository
        )
    }

    override val buildHomeFeedUseCase: BuildHomeFeedUseCase by lazy {
        BuildHomeFeedUseCase(
            catalogRepository = catalogRepository,
            metadataRepository = metadataRepository,
            settingsRepository = settingsRepository
        )
    }

    override val resolvePlayableStreamUseCase: ResolvePlayableStreamUseCase by lazy {
        ResolvePlayableStreamUseCase(
            catalogRepository = catalogRepository,
            libraryRepository = libraryRepository,
            settingsRepository = settingsRepository
        )
    }

    override val toggleFavoriteUseCase: ToggleFavoriteUseCase by lazy {
        ToggleFavoriteUseCase(libraryRepository)
    }

    override val recordWatchProgressUseCase: RecordWatchProgressUseCase by lazy {
        RecordWatchProgressUseCase(libraryRepository)
    }
}
