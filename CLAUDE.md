# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**AniLocal** — an Android anime player (Kotlin, Compose, single APK) built around a deliberate
"scraper-shaped hole": all content goes through one abstract `AnimeSource` plugin seam, and the
only bundled implementation is a lawful Creative-Commons sample clip. There is **no scraper, no
private backend**. Adding a real source is a single Hilt binding swap (see below). Read `README.md`
for the product-level feature list and the rationale.

## Build & run

The Gradle **wrapper scripts are not committed** (no `gradlew`). Either open the project in Android
Studio (Koala+) to generate them, or use a system `gradle`. The wrapper/CI are pinned to **Gradle 8.9**
(required by AGP 8.7). Toolchain: **JDK 17**, **compileSdk/targetSdk 35**, **minSdk 24**.

```bash
gradle :app:assembleDebug          # build the installable debug APK (what CI does)
gradle build                       # build + verify all modules
gradle :domain:test                # JVM unit tests for the pure-Kotlin domain module
gradle :data:testDebugUnitTest     # Android-library unit tests
```

There is **no committed test suite yet**. The pure-JVM `:domain` module is the fast, dependency-free
place to add unit tests. CI (`.github/workflows/android.yml`) runs `gradle :app:assembleDebug` on every
push and uploads the APK as an artifact for sideloading.

## Module architecture — the boundary is compile-enforced

Three Gradle modules with a strict, one-way dependency direction:

```
:app  ──►  :data  ──►  :domain
  └────────────────────►─┘
```

- **`:domain`** — pure Kotlin (`kotlin("jvm")`, **no Android dependency**). Models, repository
  *interfaces*, and the `AnimeSource` seam. `import android.*` here will not compile — that is the
  enforced boundary, not a convention. Keep it that way.
- **`:data`** — Android library. *All* implementations live here: AniList/TMDB/MAL APIs, AniSkip,
  Room, DataStore, Firebase auth, the Media3 download stack, the sample source, and the Hilt wiring
  (`di/AppModule`). Depends only on `:domain`.
- **`:app`** — Compose UI, navigation, ViewModels, the Media3 player UI, Google Sign-In UI.
  **References only domain interfaces** — it must not import `com.anilocal.app.data.*` (an injected
  impl reaching into the UI would break the seam). Inject domain repository interfaces instead.

Dependencies are managed centrally in the version catalog `gradle/libs.versions.toml`; module
`build.gradle.kts` files stay thin and reference `libs.*`.

## Where everything is wired: `data/.../di/AppModule.kt`

A single Hilt `@Module` `@Binds` every domain interface to its `:data` impl. This is the swap point
for the whole app. The most important binding:

```kotlin
@Binds @Singleton
abstract fun bindAnimeSource(impl: SampleLocalSource): AnimeSource
```

**To change where streams come from, change this one line** to bind a different `AnimeSource`
implementation (e.g. a Jellyfin/Plex/local-files source). Nothing else in the app knows or cares.

## Key cross-cutting patterns

- **Room is the offline source of truth for the UI.** Everything the UI shows (downloads list,
  badges, progress) reads from Room — never directly from Media3 or the network. `DownloadRepositoryImpl`
  registers a `DownloadManager.Listener` that **bridges** Media3 download state → Room
  (`dao.updateState(...)`), so the UI stays correct and fully offline. When adding download/offline
  features, follow this bridge pattern rather than reading Media3 state in the UI.

- **One shared Media3 `Cache`** (`data/.../download/DownloadModule.kt`) is written by the
  `DownloadManager` and read back by the playback `CacheDataSource.Factory`. The player is built on that
  factory, so it transparently serves downloaded bytes offline and falls through to network when online.

- **Player has a dual online/offline path** (`app/.../player/PlayerViewModel.kt`): on load it first
  checks `downloads.getOffline(...)`. Offline → skip markers and subtitles come from the cached Room
  record. Online → AniList resolves the title, the bound `AnimeSource` resolves the stream, and AniSkip
  markers are fetched on `STATE_READY` (once duration is known). Auto-skip fires at most once per marker.

- **Optional features no-op when unconfigured.** TMDB artwork, Google Sign-In, and MAL sync are gated on
  build-time config that defaults to blank. Keys come from `gradle.properties` (or `~/.gradle/gradle.properties`)
  → `app/build.gradle.kts` `buildConfigField` → exposed to `:data` via `app/.../di/AppConfigModule.kt`
  (`@Named("mal_client_id")`, etc.). `app/google-services.json` is git-ignored; the Google Services plugin
  auto-applies only if that file is present. Code paths must remain functional with these absent.

- **Catalog vs. source are separate concerns.** AniList (`CatalogRepository`) provides the *metadata*
  catalog (trending, search, detail pages) with no API key; the bound `AnimeSource` provides the *playable
  streams*. They are deliberately decoupled — the sample source matches any query so playback always
  resolves against AniList-browsed titles.

## Conventions

- Navigation is a single Compose `NavHost` in `MainActivity.kt`; routes live in
  `app/.../ui/navigation/Destinations.kt` (`TopTab` enum = bottom nav, `Routes` = detail/player).
- ViewModels are `@HiltViewModel`, obtained via `hiltViewModel()`, and depend only on domain interfaces.
- Networking: Retrofit + Moshi + OkHttp, wired in `data/.../remote/NetworkModule.kt`.
- Settings persist via DataStore (`DataStoreSettingsRepository`) exposed as Kotlin `Flow`s.
