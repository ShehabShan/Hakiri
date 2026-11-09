# Hikari — Architecture Specification

## 1. Layering (Strict Dependency Rule: `ui -> domain <- data`)

- **ui/**: Compose screens, ViewModels, navigation. ViewModels expose a single immutable `UiState` data class via `StateFlow`; events flow up as function calls.
- **domain/**: Pure Kotlin models, repository interfaces, and use cases (`BuildHomeFeedUseCase`, `ResolvePlayableStreamUseCase`, `ToggleFavoriteUseCase`, `RecordWatchProgressUseCase`).
- **data/**: Repository implementations, Room database, DataStore preferences, Retrofit/Jikan service + rate limiter, `AnimeSource` SPI + `OpenFilmsSource`, and DTO/entity mappers.

## 2. Core Domain Models

- `MediaId(sourceId: String, value: String)`
- `Anime(...)`
- `Episode(...)`
- `StreamLink(...)`
- `WatchProgress(...)`
- `AppError` (`Network`, `SourceUnavailable`, `ParseFailure`, `RateLimited`, `NotFound`, `Unknown`)
- `SourceResult<out T>` (`Success<T>`, `Failure(AppError)`)

## 3. Room Schema (`HikariDatabase`)

- `anime_cache(sourceId, sourceMediaId, json, fetchedAtEpochMs)` — TTL 24h
- `episode_cache(sourceId, sourceMediaId, json, fetchedAtEpochMs)` — TTL 6h
- `favorites(sourceId, sourceMediaId, addedAtEpochMs, titleSnapshot, posterSnapshot)`
- `watch_progress(sourceId, sourceMediaId, episodeNumber, positionMs, durationMs, updatedAtEpochMs, completed)`
- `jikan_cache(cacheKey, json, fetchedAtEpochMs)` — TTL 24h
