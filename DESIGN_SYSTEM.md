# Hikari — Design System

## 1. Color Palette (Static Brand Palette, No Dynamic Color)

| Token | Dark (Default) | Light |
|---|---|---|
| `primary` | `#8B9BFF` (indigo glow) | `#4353C7` |
| `onPrimary` | `#101433` | `#FFFFFF` |
| `secondary` | `#F2A5C0` (sakura) | `#B04A72` |
| `background` | `#0E0F13` | `#FAF9FC` |
| `surface` | `#16171D` | `#FFFFFF` |
| `surfaceVariant` | `#1F2129` | `#ECEAF2` |
| `onBackground` / `onSurface` | `#E6E6EB` | `#1A1B20` |
| `onSurfaceVariant` | `#A6A8B3` | `#49454F` |
| `error` | `#FFB4AB` | `#BA1A1A` |
| `Success accent (watched)` | `#7DD8A0` | `#2E7D4F` |

Scrims over posters: vertical gradient `#000000` 0% -> 60% alpha for text-on-image.

## 2. Typography, Shape & Spacing

- **Typography**: System font.
  - `displaySmall`: 36/40 sp, w700 (hero titles)
  - `headlineSmall`: 24/32 sp, w600 (screen titles)
  - `titleMedium`: 16/24 sp, w600 (cards, rows)
  - `bodyMedium`: 14/20 sp, w400 (synopsis)
  - `labelMedium`: 12/16 sp, w500 (chips, metadata)
  - `labelSmall`: 11/16 sp, w500 (badges)
- **Shape**:
  - Cards: 12 dp
  - Posters: 10 dp
  - Buttons / Chips: Full (pill)
  - Bottom Sheets: 20 dp top
- **Spacing Scale (`Dimens.kt`)**:
  - `4 / 8 / 12 / 16 / 24 / 32 dp`
  - Screen edge padding: `16 dp`
  - Poster aspect ratio: `2:3`
  - Poster width: `120 dp` in horizontal rows; `GridCells.Adaptive(minSize = 110.dp)` in grids.

## 3. Core Components (`ui/components/`)

- `PosterCard` (poster + title + optional progress bar + favorite badge)
- `MediaRow` (`SectionHeader` + `LazyRow` of `PosterCard`s + independent row state)
- `EpisodeRow` (number, title, duration, watched check + color / progress bar)
- `HikariTopBar`
- `GenreChip`
- `ScoreBadge`
- `LoadingState`
- `ErrorState` (icon + message from `ErrorMessages.kt` + Retry button)
- `EmptyState` (icon + title + hint)
- `FavoriteButton` (heart toggle, animated spring scale)
- `OfflineBanner`
