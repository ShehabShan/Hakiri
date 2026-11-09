# Hikari — Anime Discovery, Library & Swappable Streaming Companion

Hikari is a fast, private, client-only Android application built with Kotlin, Jetpack Compose, and Material 3 for browsing anime metadata, streaming episodes through a swappable provider layer, and tracking personal favorites and watch history locally with zero accounts or cloud lock-in.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> Based on your instruction to choose the approach that brings the best result, we have locked in the highest-reliability architectural decisions that adhere strictly to your specification while ensuring rock-solid compilation and emulator stability in AI Studio Build:

- **End-to-End Cohesive Build Execution**: Rather than pausing after partial skeletons, we will implement all 8 specification milestones (M0–M7) into a complete, compile-verified, and unit-tested application once you approve this plan.
- **Swappable Content Source Architecture (`AnimeSource` SPI)**: The app ships with the legally safe, Creative Commons BY 3.0 **Open Films** source (Sintel & Big Buck Bunny with multi-quality MP4 and HLS streams, plus episodic chapter modeling so episode progression and auto-advance are testable end-to-end) alongside live **Jikan v4** anime discovery (Trending & Seasonal).
- **Rich Playback UX (`Media3 ExoPlayer`)**: Uses `androidx.media3:media3-exoplayer` + `media3-exoplayer-hls` with a custom Material 3 Compose overlay (auto-hiding transport controls, ±10s seek, buffered progress bar, stream quality switcher, landscape fullscreen toggle, 5-second progress persistence, and a 10-second autoplay-next countdown card).
- **Strictly Local & Private Persistence**: All favorites, watch history, episode progress, 24-hour Jikan metadata caches, and user preferences are persisted on-device via **Room** and **DataStore Preferences**—no accounts, analytics, or cloud dependencies required.

---

### 1. Overview & Core Concept

- **What It Does**: Hikari aggregates anime discovery metadata from the public Jikan v4 (MyAnimeList) API and pairs it with a modular, fault-tolerant `AnimeSource` Service Provider Interface (SPI). Users can browse trending and seasonal anime, search across playable sources and metadata catalogs simultaneously, inspect rich title details, stream video episodes with automatic resume and 90%-completion tracking, and manage their personal library offline.
- **Target Audience / Persona**: Anime and animation enthusiasts on Android who want a refined, distraction-free native interface with reliable continue-watching, local privacy, and resilient offline caching.
- **Key Value**:
  - **Fault-Tolerant Multi-Source Design**: Individual source timeouts, parse failures, or rate limits degrade gracefully at the row or section level without crashing or blanking the screen.
  - **Stale-While-Revalidate Offline Resilience**: Favorites (stored with title and poster snapshots), watch history, and 24-hour cached catalog feeds load instantaneously even when offline.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Home & Discovery Flow**: Launch into a dark-first feed featuring a dynamic *Continue Watching* row (with episode progress bars and remaining minutes), *Trending Now* (Jikan top anime), *This Season* (Jikan current season), and *Open Films* (playable CC-licensed catalog). Each row manages its own loading, content, and inline retry state independently.
  2. **Dual-Section Search Flow**: Enter a query (debounced at 300 ms with in-flight cancellation) or tap a saved recent search chip. Results appear in two clearly labeled grids: **Playable (Sources)** for immediately streamable titles and **Discover (Metadata)** for broader Jikan anime discovery.
  3. **Details & Episode Inspection Flow**: Tap any poster to view a blurred/scrimmed backdrop header, poster art (or branded gradient placeholder when `posterUrl` is null), score badge, airing status, genre chips, expandable synopsis, mandatory CC BY 3.0 attribution (when applicable), an optimistic heart favorite toggle, a primary *Play E1* / *Resume E{n}* CTA, and a live episode list with watched checkmarks and progress bars. Metadata-only titles clearly display a *"Not available from your enabled sources"* badge while still allowing library favoriting.
  4. **Video Playback & Auto-Advance Flow**: Tap an episode or the primary Resume CTA to enter the full-bleed Media3 video player. If prior progress exists (between 10% and 95%), playback resumes automatically. Every 5 seconds (and on pause/exit), position is saved to Room. Reaching 90% marks the episode completed, advances *Continue Watching* to `E(n+1)`, and triggers a 10-second *Next Episode* countdown card if autoplay is enabled.
  5. **Library & Settings Management**: Switch to the **Library** tab to browse saved *Favorites* (adaptive poster grid) or *History* (chronological episode list with swipe-to-remove and Clear All confirmation). Open **Settings** from the top bar gear to switch themes live (System / Light / Dark), toggle registered content sources on/off (enforcing at least one active source), configure playback behavior, inspect/clear local caches, and read the full legal & attribution notice.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Atmospheric, cinema-grade dark UI (`#0E0F13` obsidian canvas) accented with luminous indigo (`#8B9BFF`) and soft sakura (`#F2A5C0`), paired with a crisp, high-contrast light mode (`#FAF9FC` canvas with `#4353C7` indigo). Dynamic color is intentionally disabled to preserve brand consistency across devices.
  - *Color Palette & Tokens*:
    - **Dark (Default)**: `primary` `#8B9BFF`, `onPrimary` `#101433`, `secondary` `#F2A5C0`, `background` `#0E0F13`, `surface` `#16171D`, `surfaceVariant` `#1F2129`, `onBackground`/`onSurface` `#E6E6EB`, `onSurfaceVariant` `#A6A8B3`, `error` `#FFB4AB`, `watchedSuccess` `#7DD8A0`.
    - **Light**: `primary` `#4353C7`, `onPrimary` `#FFFFFF`, `secondary` `#B04A72`, `background` `#FAF9FC`, `surface` `#FFFFFF`, `surfaceVariant` `#ECEAF2`, `onBackground`/`onSurface` `#1A1B20`, `onSurfaceVariant` `#49454F`, `error` `#BA1A1A`, `watchedSuccess` `#2E7D4F`.
  - *Typography & Hierarchy*: Clean Material 3 typographic scale (`displaySmall` 36/40sp bold for hero headers, `headlineSmall` 24/32sp semibold for screen titles, `titleMedium` 16/24sp semibold for cards and section headers, `bodyMedium` 14/20sp for synopses, `labelMedium` 12/16sp for chips and metadata, `labelSmall` 11/16sp for badges).
  - *Component Styling & Layout*: 8dp spatial grid (`Dimens`: 4, 8, 12, 16, 24, 32 dp), 16dp screen edge padding, 2:3 poster aspect ratio (`120.dp` width in horizontal rows; `GridCells.Adaptive(minSize = 110.dp)` in grids for seamless phone-to-tablet scaling), 12dp card corners, 10dp poster corners, and pill-shaped chips/buttons.

- **Interactive Feedback & Motion**:
  - Fade-through screen transitions (220 ms), spring scale bounce (`1f → 1.25f → 1f`) on the favorite heart button, smooth 150 ms fade on player transport controls, and explicit `OfflineBanner`, `LoadingState`, `EmptyState`, and `ErrorState` components on every screen.
  - All interactive controls enforce a minimum `48.dp` touch target, descriptive `contentDescription` / `stateDescription` semantics for TalkBack, and dual icon+color indicators for watched episodes.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Manual Dependency Injection via `AppContainer`**
  - *Chosen Approach*: Construct repositories, database DAOs, rate-limited Retrofit services, and `SourceRegistry` inside a clean `AppContainer` attached to `HikariApp`, using constructor injection and `ViewModelProvider.Factory` helpers.
  - *Why*: Eliminates KSP/Hilt annotation-processing fragility, guarantees fast incremental builds, and makes unit testing with fake repositories effortless.
  - *Alternatives Considered*: Dagger/Hilt (adds build overhead and annotation complexity unnecessary for a single-module architecture).

- **Decision 2: Sealed `AppError` & `SourceResult` Boundary**
  - *Chosen Approach*: Every network, database, and source call catches exceptions at the data boundary and maps them to `AppError` (`Network`, `SourceUnavailable`, `ParseFailure`, `RateLimited`, `NotFound`, `Unknown`). User-facing error strings are resolved exclusively through a centralized `ErrorMessages.kt` mapper.
  - *Why*: Guarantees that an unreachable stream host, malformed JSON payload, or Jikan HTTP 429 rate-limit never crashes the app and always presents a clear, actionable Retry button.
  - *Alternatives Considered*: Unchecked exceptions or generic error strings (leads to unhandled coroutine crashes and opaque error states).

- **Decision 3: Client-Side Jikan Rate Limiter + 24-Hour SQLite Cache**
  - *Chosen Approach*: Wrap `JikanApi` calls in a `Mutex`-backed `JikanRateLimiter` enforcing `>= 400 ms` spacing between requests, `<= 55 requests/minute`, and exponential backoff (`1s, 2s, 4s`) on HTTP 429 responses, backed by Room's `jikan_cache` table with a 24-hour TTL.
  - *Why*: Respects Jikan v4's strict public rate limits (`3 req/sec`, `60 req/min`), speeds up home/search navigation to near-zero latency on repeat visits, and keeps browse rows working offline.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

#### Architecture & Component Diagram

```text
┌────────────────────────────────────────────────────────────────────────────┐
│                        UI LAYER (Jetpack Compose + M3)                     │
│                                                                            │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌────────────────┐  │
│  │  HomeScreen  │  │ SearchScreen │  │DetailsScreen │  │  PlayerScreen  │  │
│  │HomeViewModel │  │SearchViewMod.│  │DetailsViewM. │  │PlayerViewModel │  │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └───────┬────────┘  │
│         │                 │                 │                  │           │
│  ┌──────┴─────────────────┴─────────────────┴──────────────────┴────────┐  │
│  │          LibraryScreen (Favorites + History) & SettingsScreen        │  │
│  └──────────────────────────────────┬───────────────────────────────────┘  │
└─────────────────────────────────────┼──────────────────────────────────────┘
                                      │ Immutable UiState (StateFlow)
                                      ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                         DOMAIN LAYER (Pure Kotlin)                         │
│                                                                            │
│  Use Cases:                                                                │
│  • BuildHomeFeedUseCase            • ResolvePlayableStreamUseCase          │
│  • ToggleFavoriteUseCase           • RecordWatchProgressUseCase            │
│                                                                            │
│  Repository Interfaces & Domain Models:                                    │
│  • CatalogRepository  • MetadataRepository  • LibraryRepository            │
│  • SettingsRepository • Anime, Episode, StreamLink, WatchProgress, AppError│
└─────────────────┬───────────────────────────────────────┬──────────────────┘
                  │                                       │
                  ▼                                       ▼
┌─────────────────────────────────────┐ ┌────────────────────────────────────┐
│     DATA LAYER: SOURCES & API       │ │   DATA LAYER: LOCAL PERSISTENCE    │
│                                     │ │                                    │
│ ┌─────────────────────────────────┐ │ │ ┌────────────────────────────────┐ │
│ │ SourceRegistry & SourceHealth   │ │ │ │ HikariDatabase (Room SQLite)   │ │
│ │ • AnimeSource SPI               │ │ │ │ • anime_cache (TTL 24h)        │ │
│ │ • OpenFilmsSource (Assets JSON) │ │ │ │ • episode_cache (TTL 6h)       │ │
│ └─────────────────────────────────┘ │ │ │ • favorites (with snapshots)   │ │
│ ┌─────────────────────────────────┐ │ │ │ • watch_progress (pos & 90%)   │ │
│ │ JikanApi (Retrofit + OkHttp)    │ │ │ │ • jikan_cache (TTL 24h)        │ │
│ │ • JikanRateLimiter (400ms/429)  │ │ │ └────────────────────────────────┘ │
│ └─────────────────────────────────┘ │ │ ┌────────────────────────────────┐ │
│                                     │ │ │ DataStore Preferences          │ │
│                                     │ │ │ • Theme, Autoplay, Sources     │ │
│                                     │ │ │ • Recent Searches (max 10)     │ │
│                                     │ │ └────────────────────────────────┘ │
└─────────────────────────────────────┘ └────────────────────────────────────┘
```

#### Data Model & State
- **Core Entities**:
  - `MediaId(sourceId, value)`: Composite identity linking any title to its originating `AnimeSource` or `"jikan"` metadata source.
  - `Anime`: Canonical domain representation holding `id`, `title`, `altTitles`, `posterUrl`, `bannerUrl`, `synopsis`, `genres`, `score`, `year`, `status`, `episodeCount`, `malId`, and optional `attribution`.
  - `Episode`: Holds `animeId`, `number` (1-based), `title`, `thumbnailUrl`, and `durationSeconds`.
  - `StreamLink`: Holds `url`, `kind` (`MP4` or `HLS`), `qualityLabel` (`"480p"`, `"480p (surround)"`, `"auto"`), and optional HTTP `headers` (restricted to an allowlist of `Referer`, `User-Agent`, `Origin`).
  - `WatchProgress`: Tracks `animeId`, `episodeNumber`, `positionMs`, `durationMs`, `updatedAtEpochMs`, and `completed` (`positionMs >= 0.90 * durationMs`).
- **Room Database (`HikariDatabase`)**:
  - 5 tables (`anime_cache`, `episode_cache`, `favorites`, `watch_progress`, `jikan_cache`) with reactive `Flow` queries and suspend upserts.
  - `favorites` stores `titleSnapshot` and `posterSnapshot` so favorited titles remain viewable in the Library even if their source is disabled in Settings or offline.

#### Interactive Component & State Mapping
- **Home Screen (`HomeViewModel`)**: Combines `LibraryRepository.observeContinueWatching()`, Jikan Trending/Seasonal flows, and `CatalogRepository.browseEnabledSources()` via `BuildHomeFeedUseCase`. Each row is modeled with an independent `RowState` (`Loading`, `Content`, `Error(AppError)`) and a per-row `retryRow(rowId)` handler.
- **Search Screen (`SearchViewModel`)**: Observes query input with `debounce(300)` + `flatMapLatest`, querying enabled `AnimeSource`s in parallel (`supervisorScope` with 10s timeout) alongside `MetadataRepository.searchJikan()`. Manages recent search chips (add on submit, remove single, clear all).
- **Details Screen (`DetailsViewModel`)**: Loads title metadata and episodes cache-first, merges live `Flow<List<WatchProgress>>` and `Flow<Boolean>` favorite status from Room, computes the primary CTA (`Resume E{n}` vs `Play E1`), and exposes `toggleFavorite()`, `retryDetails()`, and `retryEpisodes()`.
- **Player Screen (`PlayerViewModel`)**: Resolves streams via `ResolvePlayableStreamUseCase`, initializes `ExoPlayer` with an `OkHttpDataSource.Factory` (injecting allowlisted per-stream headers), seeks to saved progress if `>= 10s` and `< 95%`, persists playback progress every 5 seconds and on lifecycle pause/dispose via `RecordWatchProgressUseCase`, switches qualities seamlessly while preserving timestamp, and triggers the 10-second autoplay countdown overlay when an episode completes.
- **Library & Settings (`LibraryViewModel`, `SettingsViewModel`)**: Provide instant reactive updates for Favorites grid, History swipe-to-delete / clear-all confirmation, live Theme switching, source enabling/disabling (preventing the user from disabling the last remaining active source), and cache size inspection/clearing.
