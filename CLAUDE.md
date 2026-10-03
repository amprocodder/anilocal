# CLAUDE.md

Guidance for work in this repository. Read [README.md](README.md) for product behavior and
[docs/PERFORMANCE.md](docs/PERFORMANCE.md) for measurements and validation limits.

## Product and toolchain

AniLocal is a Kotlin/Compose Android app with public AniList metadata and a source registry.
There is no private backend or bundled scraper. The two built-in sources stream lawful
Big Buck Bunny and Sintel CC clips from public sample hosts and deliberately match any catalog title. The 720p Bunny test clip is silent; Sintel includes audio. First-time
catalog browsing, streams, and downloads require internet; completed episodes play offline
through Downloads. Do not describe the demo as bundled video or network-free initial playback.

Use **JDK 17**, **Gradle 8.9**, **AGP 8.7**, **compileSdk/targetSdk 35**, **minSdk 24**.
Only `gradle-wrapper.properties` is committed, not wrapper scripts/JAR. Use system Gradle
8.9 or the ignored `.local-dev/gradle` helper on this workstation. Do not add a wrapper just
to change CI: GitHub Actions provisions Gradle with `gradle/actions/setup-gradle`.

```bash
gradle :domain:test :data:testDebugUnitTest :app:testDebugUnitTest :data:lintDebug :app:lintDebug
gradle :app:assembleDebug :app:assembleBenchmark
```

CI runs these tests/lint and builds both APKs, uploading `anilocal-debug-apk` and
`anilocal-optimized-apk`. The `benchmark` build initializes from release, enables R8/resource
shrinking, uses release library variants, and has debug signing for local installation.
Production release signing is separate. Verify behavior in the optimized build as well as
JVM tests: Moshi uses reflection, so new DTOs/offline metadata may need consumer ProGuard rules.
Configuration caching and non-transitive R classes are enabled in `gradle.properties`.

## Module boundary and DI

```
:app  ──►  :data  ──►  :domain
  └────────────────────►─┘
```

`:domain` is pure Kotlin (`src/main/kotlin`), with no Android dependency. It owns repository
interfaces, models, `AnimeSource`, and `SourceRegistry`. `:data` and `:app` use
`src/main/java` for Kotlin sources. `:app` references domain interfaces/models and must not
import `com.anilocal.app.data.*`; implementations and Hilt bindings belong in `:data`.
Dependencies use `gradle/libs.versions.toml`.

`data/.../di/AppModule.kt` (package `com.anilocal.app.di`) binds repositories and
`SourceRegistryImpl`. Sources use Hilt set multibindings:

```kotlin
@Binds @IntoSet
abstract fun bindSampleSource(impl: SampleLocalSource): AnimeSource
```

`SampleSintelSource` contributes a second source. To add another implementation, contribute
it to the set rather than replacing a single `AnimeSource` binding. `SourceRegistry` exposes
a reactive ordered list; installed-extension loading is not implemented. More persists the
selected source id through More → Preferred streaming source. `SourceStreamRepository` falls back to the built-in sample or another
registered source if that id is unavailable. Keep metadata independent of stream sources.
The `AnimeSource` contract has `info` plus suspend `popular`, `search`, `detail`, `servers`,
and `resolve` methods.

`NetworkModule`, `DatabaseModule`, and `DownloadModule` provide APIs, persistence, and the
Media3 stack. `AppModule` also provides `@Named("appScope")`: process-lifetime IO work such
as final progress saves and download reconciliation. `:app` and `:data` generate their own
BuildConfig. The data module uses its `BuildConfig.DEBUG` for debug-only HTTP logging; optional
Google/TMDB keys remain app build fields and are not automatically forwarded between modules.

## Catalog and UI performance

- `CatalogRepository.home()` returns `HomeCatalog`. Its compatible default implementation
  fetches independent shelves concurrently, preserves cancellation, and tolerates an individual
  shelf failure. AniList overrides it with one aliased GraphQL request for five shelves.
  Complete Home results populate the shared browse-page cache; partial/error responses must
  not poison cache entries. Queries use JSON/GraphQL variables for user text and ids.
- `SuspendingLruCache` bounds process-memory results and coalesces identical active requests.
  AniList pages/Home live three minutes; details and MAL-id mappings live thirty minutes.
  Failed loads are not cached. Cache work runs off Main, and cancellation must release pending
  work rather than convert it into an empty successful cache entry. Catalog caches are not an
  offline disk metadata catalog.
- Explore debounces text for 300 ms and cancels the previous request immediately when normalized
  criteria change. Browse genre/sort changes are immediate. Genre/sort do not affect active
  text search, and normalized-equivalent queries do not repeat requests.
- `CatalogLoad`/`catalogResult` provide bounded twenty-second loading and Retry feedback for
  Home/Details/Explore, preserving existing content across a failed refresh. Quiet errors must
  not leave these screens permanently blank or indefinitely loading. Preserve coroutine
  cancellation; `loadOrNull` rethrows it and checks activity after optional work returns.
- Library computes the merged local/MAL list and every MAL status projection in one background
  pass per database update. Local items win by MAL id; MAL-only entries use `mal-<id>` and
  resolve through `catalog.anilistIdForMal` before detail navigation. Switching filters reuses
  projected lists without re-querying Room. New taps cancel obsolete mapping navigation.
- Lists/grids use lazy composition, stable keys, and content types. Grids remain adaptive for
  phone/tablet widths. More has keyed lazy sections and local slider/username state; only a
  completed slider gesture persists. Preference actors serialize writes and coalesce queued
  bursts without assuming they are the only writers. MAL manual sync awaits username
  persistence and ignores duplicate Sync taps. Firebase auth shares one listener among active
  collectors and releases it after the subscription timeout; missing Firebase config stays safe.

## Playback and source selection

`resolveStreams` resolves all quality variants from the selected source's first server for
Download quality dialogs. `resolveStream` selects its highest quality without speed testing.
Online Player uses `resolveFastestStream` on initial load and every fatal-error restart: fresh
source/server resolution overlaps fresh media probes, with three concurrent source slots,
three server slots, and three probe slots. A slow source cannot delay a healthy candidate's
probe. Duplicate URL/header candidates share a probe while retaining quality/tie order.
Resolution/session budgets are twelve/sixteen seconds; source/server/probe timeouts bound
individual work. Healthy alternative URLs are preferred after a failure. Equal measurements
prefer quality and stable source/server order, with the selected source first.

`HttpStreamSpeedProbe` reads bounded media bytes, follows bounded HLS playlists to a real
media segment, honors stream headers, and cancels underlying HTTP calls. Do not measure only
manifest size or reuse previous speed measurements during recovery. Per-player HTTP/cache
factories prevent source headers from mutating the download manager's shared configuration.

Player checks `downloads.getOffline` before any catalog/source network work. Offline subtitles,
markers, title/artwork come from Room, with an upstream that prevents HTTP access. Online markers
are fetched when duration becomes available; AniSkip caches by MAL id, episode, and duration.
The intentional null-MAL-id path returns demo markers. Auto-skip fires once per marker.

`PlaybackRecovery` serializes fatal restarts, preserves position and playback intent, uses
backoff, and allows three automatic attempts. Thirty seconds of continuous actual playback
resets its budget; readiness, pauses, and buffering do not. Manual Retry resets it. Release the
old player and attach replacements through its StateFlow. Skip UI updates only when entering
or leaving a marker, and progress polling slows while paused. Progress is saved about every
six seconds of actual playback, on pause, and through appScope on teardown; unchanged progress
must not rewrite Room.

## Persistence and downloads

Room is the source for library, watch progress, downloaded badges, and download rows. Database
version is **4**, `exportSchema = false`, with destructive migration fallback: a schema change
requires a version change and currently wipes saved local data. Performance changes should not
silently change that contract.

Library toggle is transactional. Progress save/remove operations are ordered by a mutex,
compare persisted values, and skip unchanged writes. MAL sync reads the public `load.json`
endpoint by username (no OAuth/API key), coalesces concurrent same-user syncs, and performs
network/JSON work off Main. It replaces the mirror in one transaction only after all pages
arrive and the username is still current; failure or reaching the page limit retains the
old list. Automatic sync is enabled by preference and throttled to thirty minutes. Manual
sync bypasses that throttle. No writes are sent to MAL.

Details prepares up to three different episode requests at once, fetching variants and
markers concurrently. Per-episode guards prevent duplicate work; prepared quality dialogs
queue and carry episode identity. Capture picker identity in callbacks so a repeated tap from
an old dialog cannot select or dismiss the next episode. Native enqueue/download concurrency
is independent of preparation slots.

One Media3 Cache serves downloads and playback. The DownloadManager listener, active-only
one-second progress sampling, and startup reconciliation feed a coalesced transactional Room
write queue. Do not read native download objects directly in the UI. DataStore WiFi changes
update native requirements without waking it for unrelated setting changes. Per-id operation
locks order enqueue/removal; subtitle downloads are cancellable and bounded in parallel.
After committing a queued row, native enqueue finishes even if navigation cancels the caller.
Removal waits for the native removal callback before deleting Room/sidecar state, preventing
late callbacks from deleting a new download of the same id.

Download state Int codes are shared with DAO queries (`1` means completed). Subtitle/marker
metadata uses Moshi JSON columns; offline subtitle URIs point to files under the download
subs directory. Preserve cleanup and reflective serialization across optimized builds.
`AniLocalDownloadService`, scheduler components, and foreground permissions live in the app
manifest. The data manifest also declares `RECEIVE_BOOT_COMPLETED` for standalone library
lint and manifest merging. Service dependencies come through `DownloadEntryPoint` in data.

## Optional configuration and conventions

- Google Sign-In requires `app/google-services.json`, registered signing SHA-1, and app
  `GOOGLE_WEB_CLIENT_ID`. The Services plugin self-applies only if that file exists. Missing
  configuration shows a hint and must not crash startup. `TMDB_API_KEY`/`TmdbApi` remain a
  scaffold; AniList supplies artwork.
- Navigation uses `MainActivity`'s NavHost and `ui/navigation/Destinations.kt`. Bottom navigation
  appears only on top-tab routes. Tabs restore state and use short fades. Resume-bar collection
  is isolated from screen content so progress updates do not recompose the whole NavHost.
- ViewModels use `@HiltViewModel`/`hiltViewModel`; most are colocated with their screen. Inject
  domain repositories/models and framework dependencies, never implementation classes.
- Tests cover cache/concurrency/cancellation, storage ordering, download preparation, UI retry,
  and recovery without live network. Broader device evidence belongs in the performance doc;
  never infer physical tablet/phone results from JVM tests or emulator frame timings alone.
