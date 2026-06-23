# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**AniLocal** — an Android anime player (Kotlin, Compose, single APK). AniList provides the entire
browse/metadata catalog (no API key); playable streams come from a **user-selected** `AnimeSource`
behind one plugin seam. Two lawful Creative-Commons sample sources ship in-app, and the app can
**discover, install, and load Aniyomi/Anikku-style extension APKs** as additional stream sources, so
the user can "choose any source". Read `README.md` for the product-level feature list and rationale.

> **Posture note.** Earlier revisions framed this as a deliberate "scraper-shaped hole" with no
> scraper and a single bundled CC clip. That hole has been filled by the extension subsystem (the
> `:extensions` module, added across Phases 1–4). AniLocal is now a **sideload-only third-party
> extension host**: it needs `QUERY_ALL_PACKAGES` + `REQUEST_INSTALL_PACKAGES` and is therefore **not
> Google Play eligible** (it already ships via the CI APK). It still bundles **no** extensions and no
> default piracy content — the user adds repos and installs sources. AniList browsing is unchanged;
> extensions only resolve streams.

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
push and uploads the APK (artifact `anilocal-debug-apk`) for sideloading — it provisions Gradle 8.9 via
`gradle/actions/setup-gradle`, **not** a committed wrapper, so don't "fix" CI by adding one.

This codebase was **authored but not yet compiled** (see `README.md`) — don't assume a clean first build;
the version catalog may have nits to settle. `gradle.properties` turns on `org.gradle.configuration-cache`
and `nonTransitiveRClass`; if a first build trips a config-cache violation, disabling it is a fair triage step.

## Module architecture — the boundary is compile-enforced

Four Gradle modules with a strict, one-way dependency direction:

```
:app  ──►  :data  ──►  :extensions  ──►  :domain
  └──────────►─┴─────────────────────────►─┘
```

- **`:domain`** — pure Kotlin (`kotlin("jvm")`, **no Android dependency**). Models, repository
  *interfaces*, and the `AnimeSource` seam. `import android.*` here will not compile — that is the
  enforced boundary, not a convention. Keep it that way. The repository interfaces (each bound in
  `:data`'s `AppModule`): `CatalogRepository`, `StreamRepository`, `SkipRepository`, `LibraryRepository`,
  `ProgressRepository` (all in `repo/Repositories.kt`), then **one file each** —
  `DownloadRepository` (`repo/DownloadRepository.kt`), `MalRepository` (`repo/MalRepository.kt`),
  `SettingsRepository` (`repo/SettingsRepository.kt`), and `AuthRepository` (`auth/Auth.kt`).
- **`:data`** — Android library. *All* repository implementations live here: AniList/TMDB/MAL APIs,
  AniSkip, Room, DataStore, Firebase auth, the Media3 download stack, the built-in sample sources, the
  extension-repo browse/install impls, and the Hilt wiring (`di/AppModule`). Depends on `:domain` and
  `:extensions`.
- **`:extensions`** — Android library that makes AniLocal a host for Aniyomi extensions. Vendors the
  **real** Aniyomi anime source-api (`eu.kanade.tachiyomi.animesource.*` + a trimmed `network` package,
  copied from aniyomiorg/aniyomi — see `extensions/VENDORING.md`), plus host code under
  `com.anilocal.app.extensions`: `AniyomiSourceAdapter` (maps a loaded source onto the domain
  `AnimeSource` seam), `AniyomiInjektModule`/`AniyomiRuntime` (seed the Injekt singletons loaded
  sources resolve at construction), and `loader/AnimeExtensionLoader` + `ChildFirstPathClassLoader`
  (discover installed extension APKs by the `tachiyomi.animeextension` feature, lib-version 12–16, and
  reflect their source classes in). Depends on `:domain`; `:data` consumes it via `implementation` so
  the vendored `eu.kanade.*` types never reach `:app`. **OkHttp is pinned app-wide to `5.0.0-alpha.14`**
  to match the vendored stack, and the module compiles with `-Xcontext-receivers`.
- **`:app`** — Compose UI, navigation, ViewModels, the Media3 player UI, Google Sign-In UI.
  **References only domain interfaces** — it must not import `com.anilocal.app.data.*` (an injected
  impl reaching into the UI would break the seam). Inject domain repository interfaces instead.

Dependencies are managed centrally in the version catalog `gradle/libs.versions.toml`; module
`build.gradle.kts` files stay thin and reference `libs.*`.

## Where everything is wired: `data/.../di/AppModule.kt`

A single Hilt `@Module` `@Binds` every domain interface to its `:data` impl. This is the swap point
for the whole app. The most important binding:

```kotlin
// Built-in sources are contributed into a Set — NOT bound singly. The user picks one at runtime.
@Binds @IntoSet abstract fun bindSampleSource(impl: SampleLocalSource): AnimeSource
@Binds @IntoSet abstract fun bindSintelSource(impl: SampleSintelSource): AnimeSource
@Binds @Singleton abstract fun bindSourceRegistry(impl: SourceRegistryImpl): SourceRegistry
```

Streams are **no longer one compile-time binding** (the old `bindAnimeSource` is gone). Built-in
`AnimeSource`s are contributed via `@IntoSet`; `SourceRegistryImpl` (in `:data`) merges that set with
the extensions `AnimeExtensionLoader` discovers and exposes a reactive `sources: StateFlow`.
`SourceStreamRepository` injects the `SourceRegistry` + `SettingsRepository` and routes resolution to
the **user-selected** source (`selectedSourceId` in DataStore, chosen in the More-screen picker),
falling back to the sample. **To add a built-in source, add one `@IntoSet` line** — selection, routing,
and the picker are automatic. The `AnimeSource` contract is **five suspend methods** —
`popular`/`search`/`detail` (catalog-shaped) *and* `servers`/`resolve` (stream-shaped) — **plus a
`val info: SourceInfo` property** (`info.id` keys the registry; `info.isExternal` tags extensions in
the picker), so a source implements all six members even though browsing goes through AniList's
`CatalogRepository`. `SourceStreamRepository` is the join: per request it runs `search` → `detail` →
`servers().firstOrNull()` → `resolve`, sorts variants by height descending, and throws when `search`
finds no match (the sample sources dodge this by matching *any* query, so playback always resolves; a
real extension that finds nothing throws and the player degrades to a short hint). It exposes
`resolveStream` (single, highest quality → online playback) and `resolveStreams` (all variants → the
download quality picker).

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
  badges, progress) reads from Room — never directly from Media3 or the network. `DownloadRepositoryImpl`'s
  `init` block **bridges** Media3 → Room three ways: a `DownloadManager.Listener` writes state changes
  (`dao.updateState(...)`) and removals (`dao.deleteById`), and the `wifiOnlyDownloads` settings `Flow`
  drives `downloadManager.setRequirements(...)`. When adding download/offline features, follow this bridge
  pattern rather than reading Media3 state in the UI. **Gotchas:** download state persists as an Int code,
  and the DAO hard-codes `state = 1` to mean COMPLETED — a magic number duplicated from private companion
  consts, easy to break; subtitles and skip markers are stored as Moshi **JSON columns** on `DownloadEntity`
  (not separate tables); offline subtitle files are pulled into `<downloadDir>/subs/` with their URLs
  rewritten to `file://` and **deleted manually in `remove()`**. The DB (`anilocal.db`, version 4,
  `exportSchema = false`) uses `fallbackToDestructiveMigration()` with **no `Migration` objects**
  (`DatabaseModule.kt`) — any schema change must bump the version and **wipes all local data** (library,
  progress, downloads, MAL cache) on next launch.

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
  markers are fetched once on `STATE_READY` (gated on `markers.isEmpty() && !offline`, so the fetch never
  re-runs or overrides offline markers). When a title has **no MAL id** (the keyless sample, or anything
  AniSkip can't key), `AniSkipRepository` returns **hard-coded demo markers** instead of an empty list so
  the Skip control always demonstrates — that `idMal == null` branch is intentional, not a bug. Auto-skip
  fires at most once per marker.

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
    user's MAL list privacy to be Public. It fires from `AppViewModel.onAppOpen()` (a `MainActivity`
    `LaunchedEffect` on every app open) through the gated/throttled `syncIfDue()`; **"Sync now"** in More
    calls the un-throttled `sync()`. Library's **"My List"** then merges the Room library with the *entire*
    MAL mirror (deduped by MAL id, local wins); MAL-only rows get a synthetic `mal-<malId>` id, so opening
    one must first resolve `catalog.anilistIdForMal(malId)` before navigating — the detail route is keyed by
    **AniList id** (`DetailsScreen` does `animeId.toInt()`), and a miss shows a "not found" Toast.
  - **`GOOGLE_WEB_CLIENT_ID`** is read directly in `:app` UI (`ui/more/MoreScreen.kt`); blank → the
    `GoogleSignInClient` is null, so tapping "Sign in with Google" shows a Toast prompting you to configure
    it (the button is **not** hidden). `app/google-services.json` is git-ignored and the Google Services
    plugin self-applies only when that file exists. `FirebaseAuthRepository` is *always* bound but reaches
    Firebase lazily via `runCatching { FirebaseAuth.getInstance() }.getOrNull()`, so a missing
    `google-services.json` degrades gracefully instead of crashing at startup — keep that lazy/guarded pattern.
  - **TMDB** is a documented *scaffold* (`data/.../metadata/tmdb/TmdbApi.kt`) — it doesn't consume its key
    yet (AniList already supplies artwork). Code paths must stay functional with all of these absent.

- **Catalog vs. source are separate concerns.** AniList (`CatalogRepository`) provides the *metadata*
  catalog with no API key; the bound `AnimeSource` provides the *playable streams*. `CatalogRepository` is
  the whole catalog surface, not just search/detail: the five Home rows (`trending`, `popularThisSeason`,
  `topAiring`, `allTimePopular`, `upcoming`), the Explore grid `browse(genre, sort, page)`, and
  `anilistIdForMal(malId)`. `AniListCatalogRepository` builds **raw GraphQL strings inline** (no
  Apollo/codegen) through a shared `mediaPage()` helper and computes the current season locally — copy that
  pattern when adding a row or query. They are deliberately decoupled — the sample source matches any query
  so playback always resolves against AniList-browsed titles (that "always resolves" is a property of
  `SampleLocalSource` returning a fixed list for *any* query, **not** of the seam; a real source that
  returns no match throws in `SourceStreamRepository`). The join happens in
  `data/.../source/SourceStreamRepository.kt`, while `AppModule.bindAnimeSource` chooses *which* source it
  talks to.

## Conventions

- Navigation is a single Compose `NavHost` in `MainActivity.kt`; routes live in
  `app/.../ui/navigation/Destinations.kt` (`TopTab` enum = bottom nav, `Routes` = detail/player). The
  bottom `NavigationBar` shows only when the current route is a `TopTab` route, so detail/player have none;
  adding a tab = a new `TopTab` entry **plus** a `composable(tab.route)` in `MainActivity`'s `NavHost`.
- ViewModels are `@HiltViewModel`, obtained via `hiltViewModel()`, and inject only domain interfaces/models
  (plus framework types — `SavedStateHandle`, `@ApplicationContext Context`, the Hilt-provided Media3
  `CacheDataSource.Factory`) — never `:data` types. There are only a handful, and most are **co-located in
  their screen file** (`DetailsViewModel` in `DetailsScreen.kt`, `HomeViewModel` in `HomeScreen.kt`,
  `LibraryViewModel` in `LibraryScreen.kt`); only `MoreViewModel` and `PlayerViewModel` get their own file.
  Look inside the screen file before assuming a missing `*ViewModel.kt`.
- Error/empty states degrade **silently**: repo calls are wrapped in `runCatching { … }.getOrDefault/
  getOrNull`, and screens render blank or a short hint (Home drops rows whose loader fails or returns empty;
  Details just returns from the `Scaffold` when `detail` is null) — there are **no spinners or error
  dialogs**. Match that pattern rather than adding loading/error UI that clashes with it.
- Networking: Retrofit + Moshi + OkHttp, wired in `data/.../remote/NetworkModule.kt`.
- Settings persist via DataStore (`DataStoreSettingsRepository`) exposed as Kotlin `Flow`s.
- Source-dir quirk: `:domain` keeps sources under `src/main/kotlin/`, while `:data` and `:app` use
  `src/main/java/` (still Kotlin). Put new files in the directory the module already uses.
