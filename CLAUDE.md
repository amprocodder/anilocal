# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**AniLocal** — an Android anime player (Kotlin, Compose, single APK), **branded "Onboard" to the
user** (launcher label/icon, Home wordmark) with a midnight-blue accent on near-black surfaces —
code, packages, and CI names remain AniLocal. AniList provides the entire
browse/metadata catalog (no API key); playable streams come from a **user-selected** `AnimeSource`
behind one plugin seam. The app ships **no** built-in stream sources; it can **discover, install, and
load Aniyomi/Anikku-style extension APKs** as the stream sources, so the user can "choose any source".
Read `README.md` for the product-level feature list and rationale.

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

**No test suite is committed** and CI only assembles: CI (`.github/workflows/android.yml`) runs
`gradle :app:assembleDebug` on every push and uploads the APK (artifact `anilocal-debug-apk`) for
sideloading — it provisions Gradle 8.9 via `gradle/actions/setup-gradle`, **not** a committed wrapper,
so don't "fix" CI by adding one. The pure-JVM `:domain` module is the fast, dependency-free place to add
unit tests.

**Debug signing is pinned to the committed `app/debug.keystore`** (a standard, non-secret debug key;
storepass/keypass `android`, alias `androiddebugkey`; `signingConfigs.debug` in `app/build.gradle.kts`,
with a `!app/debug.keystore` exception under `.gitignore`'s `*.keystore` rule). This keeps every debug
APK — CI artifact or local build — on ONE signing identity so they update-install over each other.
Don't delete or regenerate it: a new key makes every sideload collide with
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` and forces an uninstall + data wipe.

**The repo is app-only by design — the GitHub side ships the app and nothing else.** Local dev tooling
(an on-device build/test chain, its test sources, IDE scratch) is intentionally kept out of git via
`.gitignore` and is **not** part of the project: never commit it, nor the working-tree edits that wire
it in (e.g. uncommitted `sourceSets` srcDir pointers in the module `build.gradle.kts` files). So a fresh
checkout has no tests and no test wiring — that's expected. The code **builds clean** (CI proves it on
every push), so don't assume an uncompiled first build.

`gradle.properties` turns on `org.gradle.configuration-cache` and `nonTransitiveRClass`; if a first
build trips a config-cache violation, disabling it is a fair triage step.

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
  `SettingsRepository` (`repo/SettingsRepository.kt`), and `ExtensionRepository` (`repo/ExtensionRepository.kt`
  — lists extensions from each configured `index.min.json` repo and downloads an APK for the system
  installer). The `AnimeSource` seam (`source/`) also defines
  `SourcePreference` (sealed: Toggle/EditText/Select/MultiSelect) — a UI-agnostic mapping of an
  extension's settings so `:app` can render/persist them without touching `androidx.preference`/`eu.kanade.*`.
- **`:data`** — Android library. *All* repository implementations live here: AniList/TMDB/MAL APIs,
  AniSkip, Room, DataStore, the Media3 download stack, the source registry, the
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
  `AniyomiSourceAdapter.resolve()` tries the ext-lib-14 `getVideoList(episode)` path **first**, falling
  back to the lib-16 `getHosterList → getVideoList(hoster)` pipeline — classic-first avoids an
  `AbstractMethodError` on lib-14 sources, and every source call is `runCatching`-wrapped so a
  lib-mismatched extension degrades to empty. The adapter also implements `preferences()`/`setPreference()`
  by building a real androidx `PreferenceScreen` and reading/writing the source's own `source_<id>`
  SharedPreferences. The vendored `network/interceptor/CloudflareInterceptor` (+ `WebViewInterceptor`,
  `util/system/WebViewUtil`) is wired into NetworkHelper's single client, so the **Cloudflare bypass is
  always on** (solves the JS challenge in a headless `android.webkit` WebView, re-supplies `cf_clearance`;
  degrades silently if no WebView).
- **`:app`** — Compose UI, navigation, ViewModels, the Media3 player UI.
  **References only domain interfaces** — it must not import `com.anilocal.app.data.*` (an injected
  impl reaching into the UI would break the seam). Inject domain repository interfaces instead.

Dependencies are managed centrally in the version catalog `gradle/libs.versions.toml`; module
`build.gradle.kts` files stay thin and reference `libs.*`.

## Where everything is wired: `data/.../di/AppModule.kt`

A single Hilt `@Module` `@Binds` every domain interface to its `:data` impl. This is the swap point
for the whole app. The most important binding:

```kotlin
// Built-in sources are a multibound Set the registry merges with extensions — NOT bound singly.
// The app ships NONE, so the set is empty; @Multibinds keeps it injectable with zero contributions.
@Multibinds abstract fun animeSources(): Set<@JvmSuppressWildcards AnimeSource>
@Binds @Singleton abstract fun bindSourceRegistry(impl: SourceRegistryImpl): SourceRegistry
```

Streams are **no longer one compile-time binding** (the old `bindAnimeSource` is gone). Built-in
`AnimeSource`s would be contributed via `@Binds @IntoSet` (the app currently ships none, so the set is
empty — `@Multibinds` declares it); `SourceRegistryImpl` (in `:data`) merges that set with the
extensions `AnimeExtensionLoader` discovers and exposes a reactive `sources: StateFlow`.
`SourceStreamRepository` injects the `SourceRegistry` + `SettingsRepository` and routes resolution to
the **user-selected** source (`selectedSourceId` in DataStore, chosen in the More-screen picker),
falling back to the first available source. **To add a built-in source, add one `@Binds @IntoSet`
line** — selection, routing, and the picker are automatic. The `AnimeSource` contract is **five required suspend methods** —
`popular`/`search`/`detail` (catalog-shaped) *and* `servers`/`resolve` (stream-shaped) — **plus a
`val info: SourceInfo` property** (`info.id` keys the registry; `info.isExternal` tags extensions in
the picker; `info.configurable` flags sources with settings) **and two more suspend methods with default
impls** for per-source settings, `preferences()` and `setPreference()` (no-ops unless overridden). So a
minimal source implements six members and a configurable one eight, even though browsing goes through
AniList's `CatalogRepository`. `SourceStreamRepository` is the join: per request it runs `search` → `detail` →
`servers().firstOrNull()` → `resolve`, sorts variants by height descending, and throws when `search`
finds no match (an extension that finds nothing throws and the player degrades to a short hint —
there is no longer a built-in source that matched *any* query to keep playback always-resolving). It exposes
`resolveStream` (single, highest quality — but carrying the de-duped **union of every variant's
subtitles**, so captions on lower renditions aren't lost to the height sort → online playback) and
`resolveStreams` (all variants → the download quality picker).

The single `@Binds` module is **`AppModule` (in `:data`, package `com.anilocal.app.di`)**, which
`@Binds` every domain interface → `:data` impl. `:data` additionally has three `@Provides` `object`
modules under `com.anilocal.app.data.*`: `NetworkModule` (Retrofit/Moshi/OkHttp + the five APIs —
AniList/AniSkip/TMDB/MAL/extension-repo), `DatabaseModule` (Room), and `DownloadModule` (the Media3
cache/manager stack). Adding a new repo/source
impl → add a `@Binds` in `AppModule`. **`:app` has no DI module:** build-time keys (currently just
`TMDB_API_KEY`) live only in `:app`'s `BuildConfig`, and nothing bridges `:app`'s
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
  rewritten to `file://` and **deleted manually in `remove()`**. There are **two Room databases**
  (`DatabaseModule.kt`): `anilocal.db` (version 5, `exportSchema = false`) holds user data and **every
  schema bump must ship a `Migration`** (`MIGRATION_4_5` — the `headersJson` column — is the pattern;
  `fallbackToDestructiveMigration()` remains only as a last-resort for pathless jumps), while
  `anilocal-cache.db` is the **disposable** JSON cache (one `kv_cache` table) where destructive
  migration is always fine.

- **Stale-while-revalidate reads via `JsonCache`** (`data/.../cache/JsonCache.kt`, over the disposable
  cache DB): repositories call `cache.cached(key, type, ttl) { fetch() }` — **any** cached hit (fresh
  or stale) is served instantly, a past-TTL hit also kicks a deduped background refresh, and only a
  cold cache blocks on the network. That makes Home/Explore/Details/Search, AniSkip markers, and the
  extension-repo index render offline once seen (namespaced keys: `home:*`, `browse:*`, `search:*`,
  `popular:*`, `detail:*`, `skip:*`, `src:*`, `extrepo:*`, `malmap:*` — the last is permanent, the
  rest sweep after 30 days). `CatalogRepository` binds to `CachedCatalogRepository` (decorator over
  `AniListCatalogRepository`); when adding a catalog query, add it to **both**. Stream resolution still
  needs the network — it caches only the search→detail leg (episode ids, 4h) to cut round-trips, never
  server/stream URLs (tokenized/short-lived); a cached match that stops resolving falls back to one
  live re-run which overwrites the entry on success. Posters survive offline via the app-wide Coil
  loader in `AniLocalApp` (`respectCacheHeaders(false)` + 256 MB disk cache).

- **One shared Media3 `Cache`** (`data/.../download/DownloadModule.kt`) is written by the
  `DownloadManager` and read back by the playback `CacheDataSource.Factory`. The player is built on that
  factory, so it transparently serves downloaded bytes offline and falls through to network when online.
  There are **two** `DefaultHttpDataSource.Factory` singletons: the unqualified one is the player's
  (per-stream headers set by `PlayerViewModel`), `@Named("downloadHttp")` is the `DownloadManager`'s —
  they must stay separate or playback headers clobber a running download's Referer (403s). Download
  headers ride a `ResolvingDataSource` that consults `DownloadHeaderStore` **per request**: enqueue/
  resume point the store at the download's headers (persisted in `DownloadEntity.headersJson`), and
  on a cold process — including a headless scheduler/boot service restart, where no ViewModel ever
  constructs `DownloadRepositoryImpl` — the store lazily restores them from Room on the download
  thread itself.

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
  re-runs or overrides offline markers). When a title has **no MAL id** (anything AniSkip can't key),
  `AniSkipRepository` returns **hard-coded demo markers** instead of an empty list so
  the Skip control always demonstrates — that `idMal == null` branch is intentional, not a bug. Auto-skip
  fires at most once per marker. AniSkip is queried with the player's real `episodeLengthSec` first and
  **retried with `episodeLength=0`** if that finds nothing (a length mismatch >~±25s makes AniSkip answer
  `found:false`; `0` disables its length filter and still returns absolute op/ed times — keep the retry).
  On `STATE_ENDED` the player **auto-advances to the next episode in place** (reuses the same ExoPlayer,
  resets per-episode state, re-runs `load()` so a downloaded next ep plays from cache), gated on the
  `autoPlayNext` setting and `hasEpisode()`; an `advancing` guard fires it exactly once, and with no next
  episode it `progress.remove()`s the title from Continue Watching. The player's built-in next/prev
  buttons are disabled.

- **Optional features no-op when unconfigured.** TMDB artwork is gated on build-time
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
  - **TMDB** is a documented *scaffold* (`data/.../metadata/tmdb/TmdbApi.kt`) — it doesn't consume its key
    yet (AniList already supplies artwork). Code paths must stay functional with all of these absent.

- **Catalog vs. source are separate concerns.** AniList (`CatalogRepository`) provides the *metadata*
  catalog with no API key; the bound `AnimeSource` provides the *playable streams*. `CatalogRepository` is
  the whole catalog surface, not just search/detail: the five Home rows (`trending`, `popularThisSeason`,
  `topAiring`, `allTimePopular`, `upcoming`), the Explore grid `browse(genre, sort, page)`, and
  `anilistIdForMal(malId)`. `AniListCatalogRepository` builds **raw GraphQL strings inline** (no
  Apollo/codegen) through a shared `mediaPage()` helper and computes the current season locally — copy that
  pattern when adding a row or query. They are deliberately decoupled — a source must find its own match
  for an AniList-browsed title; resolution is **not** guaranteed (a source that returns no match throws in
  `SourceStreamRepository` and the player shows a short hint). The join happens in
  `data/.../source/SourceStreamRepository.kt`, which picks *which* source to talk to **at runtime** from
  the `SourceRegistry` by the persisted `selectedSourceId` (falling back to the first available source) —
  there is no compile-time `bindAnimeSource` binding (it was removed).

## Conventions

- Navigation is a single Compose `NavHost` in `MainActivity.kt`; routes live in
  `app/.../ui/navigation/Destinations.kt` (`TopTab` enum = bottom nav = Home/Explore/Library/Downloads/More;
  `Routes` = the non-tab routes: `detail`, `player`, `extensions` (Browse extensions → install via the
  system installer), and `source/{sourceId}/preferences` (per-source settings, shown when
  `SourceInfo.configurable`)). The bottom `NavigationBar` shows only when the current route is a `TopTab`
  route, so the non-tab routes have none; adding a tab = a new `TopTab` entry **plus** a
  `composable(tab.route)` in `MainActivity`'s `NavHost`.
- ViewModels are `@HiltViewModel`, obtained via `hiltViewModel()`, and inject only domain interfaces/models
  (plus framework types — `SavedStateHandle`, `@ApplicationContext Context`, the Hilt-provided Media3
  `CacheDataSource.Factory`; `PlayerViewModel` also takes a `DefaultHttpDataSource.Factory` for per-stream
  request headers and a `@Named("appScope")` `CoroutineScope` to persist final position after
  `viewModelScope` is cancelled) — never `:data` types. Most are **co-located in their screen file**
  (`DetailsViewModel`/`HomeViewModel`/`LibraryViewModel`/`ExploreViewModel`/`DownloadsViewModel`/
  `ExtensionsViewModel`/`SourcePreferencesViewModel`, each in its `*Screen.kt`); only `AppViewModel`
  (app-open MAL sync + the resume bar), `MoreViewModel`, and `PlayerViewModel` get their own file. Look
  inside the screen file before assuming a missing `*ViewModel.kt`.
- Error/empty states degrade **silently**: repo calls are wrapped in `runCatching { … }.getOrDefault/
  getOrNull`, and screens render blank or a short hint (Home drops rows whose loader fails or returns empty;
  Details renders nothing but its floating back button until `detail` is non-null) — there are **no
  spinners or error dialogs** for load/error states. The one deliberate exception is the player's
  transient center rebuffer spinner (playback stall feedback, not a load state). Match that pattern
  rather than adding loading/error UI that clashes with it.
- Networking: Retrofit + Moshi + OkHttp, wired in `data/.../remote/NetworkModule.kt` — one shared
  client with explicit timeouts, a 20 MB HTTP cache, and a bounded `RetryInterceptor` (ONE retry with
  backoff on IO errors, or one honored short `Retry-After` on 429 — AniList rate-limits at 90 req/min;
  Explore additionally debounces typed queries 300 ms).
- Settings persist via DataStore (`DataStoreSettingsRepository`) exposed as Kotlin `Flow`s — including
  `autoPlayNext`, `autoSkip`, `wifiOnlyDownloads`, default download quality, and **subtitle
  scale/background** (the player applies these live to the `SubtitleView`; More shows a to-scale preview
  that reproduces the caption's true on-screen px).
- Source-dir quirk: `:domain` keeps sources under `src/main/kotlin/`, while `:data` and `:app` use
  `src/main/java/` (still Kotlin). Put new files in the directory the module already uses.
