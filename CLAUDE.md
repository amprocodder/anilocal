# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**AniLocal** — an Android anime player (Kotlin, Compose, single APK) built around a deliberate
"scraper-shaped hole": all content goes through one abstract `AnimeSource` plugin seam, and the
only bundled implementation is a lawful Creative-Commons sample clip. There is **no scraper, no
private backend**. Adding a real source is a single Hilt binding swap (see below). Read `README.md`
for the product-level feature list and the rationale.

## Build & run

The Gradle **wrapper is not committed** (no `gradlew`, and not even `gradle/wrapper/gradle-wrapper.jar` —
only `gradle-wrapper.properties`). Either open the project in Android Studio (Koala+) to generate the
wrapper, or use a system `gradle`. The wrapper/CI are pinned to **Gradle 8.9** (required by AGP 8.7).
Toolchain: **JDK 17**, **compileSdk/targetSdk 35**, **minSdk 24**.

```bash
gradle :app:assembleDebug          # build the installable debug APK (what CI does)
gradle build                       # build + verify all modules
gradle :domain:test                # JVM unit tests for the pure-Kotlin domain module
gradle :data:testDebugUnitTest     # Android-library unit tests
```

There is **no committed test suite yet**. The pure-JVM `:domain` module is the fast, dependency-free
place to add unit tests. CI (`.github/workflows/android.yml`) runs `gradle :app:assembleDebug` on every
push and uploads the APK as an artifact for sideloading.

This codebase was **authored but not yet compiled** (see `README.md`) — don't assume a clean first build;
the version catalog may have nits to settle. `gradle.properties` turns on `org.gradle.configuration-cache`
and `nonTransitiveRClass`; if a first build trips a config-cache violation, disabling it is a fair triage step.

## Module architecture — the boundary is compile-enforced

Three Gradle modules with a strict, one-way dependency direction:

```
:app  ──►  :data  ──►  :domain
  └────────────────────►─┘
```

- **`:domain`** — pure Kotlin (`kotlin("jvm")`, **no Android dependency**). Models, repository
  *interfaces*, and the `AnimeSource` seam. `import android.*` here will not compile — that is the
  enforced boundary, not a convention. Keep it that way. The repository interfaces (each bound in
  `:data`'s `AppModule`): `CatalogRepository`, `StreamRepository`, `SkipRepository`, `LibraryRepository`,
  `ProgressRepository` (all in `repo/Repositories.kt`), `DownloadRepository`, `MalRepository`,
  `SettingsRepository`, and `AuthRepository` (`auth/Auth.kt`).
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
implementation (e.g. a Jellyfin/Plex/local-files source). Nothing else in the app knows or cares:
`StreamRepository` is bound to the *source-agnostic* `SourceStreamRepository`, which injects the
**abstract** `AnimeSource`, so it works with any bound source — leave that binding alone. Note the
`AnimeSource` contract is **five suspend methods**: `popular`/`search`/`detail` (catalog-shaped) *and*
`servers`/`resolve` (stream-shaped), so a custom source must implement all five even though browsing
goes through AniList's `CatalogRepository`.

The single `@Binds` module is **`AppModule` (in `:data`, package `com.anilocal.app.di`)**, which
`@Binds` every domain interface → `:data` impl. `:data` additionally has three `@Provides` `object`
modules under `com.anilocal.app.data.*`: `NetworkModule` (Retrofit/Moshi/OkHttp + the four APIs),
`DatabaseModule` (Room), and `DownloadModule` (the Media3 cache/manager stack). Adding a new repo/source
impl → add a `@Binds` in `AppModule`. **`:app` has no DI module:** build-time keys like
`GOOGLE_WEB_CLIENT_ID` are read straight from `BuildConfig` in the UI, and nothing bridges `:app`'s
`BuildConfig` into `:data`. If a future data-layer impl needs an `:app` build-time key, add a small
`@Provides @Named(...)` Hilt module in `:app` to surface it (the now-removed `AppConfigModule` did this
for the old MAL client id).

## Key cross-cutting patterns

- **Room is the offline source of truth for the UI.** Everything the UI shows (downloads list,
  badges, progress) reads from Room — never directly from Media3 or the network. `DownloadRepositoryImpl`
  registers a `DownloadManager.Listener` that **bridges** Media3 download state → Room
  (`dao.updateState(...)`), so the UI stays correct and fully offline. When adding download/offline
  features, follow this bridge pattern rather than reading Media3 state in the UI. The DB (`anilocal.db`,
  version 4, `exportSchema = false`) uses `fallbackToDestructiveMigration()` with **no `Migration`
  objects** (`DatabaseModule.kt`) — any schema change must bump the version and **wipes all local data**
  (library, progress, downloads, MAL cache) on next launch.

- **One shared Media3 `Cache`** (`data/.../download/DownloadModule.kt`) is written by the
  `DownloadManager` and read back by the playback `CacheDataSource.Factory`. The player is built on that
  factory, so it transparently serves downloaded bytes offline and falls through to network when online.

- **The download service crosses the module/manifest boundary.** `AniLocalDownloadService` (code in
  `:data`) plus its permissions (`FOREGROUND_SERVICE*`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`)
  and the `PlatformScheduler` JobService are declared in **`:app`'s `AndroidManifest.xml`** — `:data`'s
  manifest is empty by design, so edit `:app`'s manifest for any download-service change. The framework
  instantiates the service, so it can't constructor-inject; it pulls its deps through a Hilt `@EntryPoint`
  (`DownloadEntryPoint`) — add new service deps there + `DownloadModule`, not to a constructor.

- **Player has a dual online/offline path** (`app/.../player/PlayerViewModel.kt`): on load it first
  checks `downloads.getOffline(...)`. Offline → skip markers and subtitles come from the cached Room
  record. Online → AniList resolves the title, the bound `AnimeSource` resolves the stream, and AniSkip
  markers are fetched on `STATE_READY` (once duration is known). Auto-skip fires at most once per marker.

- **Optional features no-op when unconfigured.** TMDB artwork and Google Sign-In are gated on build-time
  config that defaults to blank; **MAL sync needs no config at all** (username only). Build-time keys flow
  `gradle.properties` (or `~/.gradle/gradle.properties`) → `app/build.gradle.kts` `buildConfigField` →
  `BuildConfig` — which **only `:app` generates** (`buildConfig = true` is set there, not in `:data`).
  Each feature consumes its config differently:
  - **MAL sync** uses **no API key and no OAuth** — it mirrors the user's PUBLIC list from MAL's own
    `load.json` page endpoint (`myanimelist.net/animelist/{username}/load.json?status=7`), by **username
    only** (entered in Settings → DataStore). Per-entry status is MAL's numeric code, mapped in
    `MalRepositoryImpl`. Sync is one-way/read-only: it pages the list (~300/page, ≤50 pages), then
    `dao.clear()` + `upsertAll()` (full replace, not merge), throttled to once per 30 min. Requires the
    user's MAL list privacy to be Public.
  - **`GOOGLE_WEB_CLIENT_ID`** is read directly in `:app` UI (`ui/more/MoreScreen.kt`); blank → the
    `GoogleSignInClient` is null, so tapping "Sign in with Google" shows a Toast prompting you to configure
    it (the button is **not** hidden). `app/google-services.json` is git-ignored and the Google Services
    plugin self-applies only when that file exists. `FirebaseAuthRepository` is *always* bound but reaches
    Firebase lazily via `runCatching { FirebaseAuth.getInstance() }.getOrNull()`, so a missing
    `google-services.json` degrades gracefully instead of crashing at startup — keep that lazy/guarded pattern.
  - **TMDB** is a documented *scaffold* (`data/.../metadata/tmdb/TmdbApi.kt`) — it doesn't consume its key
    yet (AniList already supplies artwork). Code paths must stay functional with all of these absent.

- **Catalog vs. source are separate concerns.** AniList (`CatalogRepository`) provides the *metadata*
  catalog (trending, search, detail pages) with no API key; the bound `AnimeSource` provides the *playable
  streams*. They are deliberately decoupled — the sample source matches any query so playback always
  resolves against AniList-browsed titles. The join happens in `data/.../source/SourceStreamRepository.kt`,
  which bridges catalog metadata → the active `AnimeSource`; that is the seam consumer, while
  `AppModule.bindAnimeSource` chooses *which* source it talks to.

## Conventions

- Navigation is a single Compose `NavHost` in `MainActivity.kt`; routes live in
  `app/.../ui/navigation/Destinations.kt` (`TopTab` enum = bottom nav, `Routes` = detail/player). The
  bottom `NavigationBar` shows only when the current route is a `TopTab` route, so detail/player have none;
  adding a tab = a new `TopTab` entry **plus** a `composable(tab.route)` in `MainActivity`'s `NavHost`.
- ViewModels are `@HiltViewModel`, obtained via `hiltViewModel()`, and inject only domain interfaces/models
  (plus framework types like `SavedStateHandle`) — never `:data` types.
- Networking: Retrofit + Moshi + OkHttp, wired in `data/.../remote/NetworkModule.kt`.
- Settings persist via DataStore (`DataStoreSettingsRepository`) exposed as Kotlin `Flow`s.
- Source-dir quirk: `:domain` keeps sources under `src/main/kotlin/`, while `:data` and `:app` use
  `src/main/java/` (still Kotlin). Put new files in the directory the module already uses.
