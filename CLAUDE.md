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

**No test suite is committed** and CI only lints and assembles: CI (`.github/workflows/android.yml`)
runs on every push — it checks the commit messages, runs `:domain:check` plus lint on the Android
modules, assembles `:app:assembleDebug` and `:app:assembleBenchmark`, and uploads both APKs (artifacts
`anilocal-debug-apk` and `anilocal-optimized-apk`) for sideloading — it provisions Gradle 8.9 via `gradle/actions/setup-gradle`, **not** a committed wrapper,
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

## Git & commit rules

Solo project; every commit and push is made through Claude Code. These rules are mandatory.

**Branching**
- `main` is the only long-lived branch and always holds the latest working build. Commit to it directly.
- A short-lived branch (`feat/<topic>`, `fix/<topic>`) is fine for risky work, but merge it back
  (fast-forward or squash) and delete it the same session — never leave work stranded off `main`.
- Never force-push `main`, never rewrite pushed history, never delete `main`.
- Old branch tips are preserved as `archive/<name>` tags; don't delete those.

**Commit messages** — Conventional Commits, checked by `.github/hooks/commit-msg` (locally via
`git config core.hooksPath .github/hooks`, and by CI on every push):

```
type(scope): imperative lowercase summary, ≤72 chars, no trailing period

Optional body: what changed and why (not how), wrapped at ~72 columns.
```

- **type** — `feat` (user-visible feature), `fix` (bug fix), `perf`, `refactor` (no behaviour
  change), `docs`, `build` (Gradle, deps, signing), `ci`, `test`, `chore` (housekeeping, releases),
  `revert`, `style`. Add `!` after the type/scope for a breaking change (e.g. a Room schema bump
  without a migration path).
- **scope** — optional, lowercase, the feature area: `player`, `downloads`, `sources`, `extensions`,
  `settings`, `library`, `home`, `explore`, `details`, `mal`, `skip`, `cache`, `net`, `ui`, `app`, or a
  module (`domain`, `data`). Nest with `/` (`extensions/ui`). Reuse an existing scope before inventing one
  (`git log --format=%s | grep -oE '^[a-z]+\([^)]+\)' | sort | uniq -c`).
- One logical change per commit. Don't bundle an unrelated fix into a feature commit; split it.

**Before pushing**
- Build locally — it's much faster than CI. The dev machine has Android Studio's SDK at
  `~/Android/Sdk` (`local.properties` → `sdk.dir`, gitignored) and Gradle 8.9 on PATH. Any code change
  must pass `gradle :domain:check :app:assembleDebug` locally before pushing; docs/CI-only changes are
  exempt. CI is the backstop, not the gate: don't block on it, but if a run goes red
  (`gh run list --limit 3`), fix it next with a `fix`/`build` commit — no reverting history.
- Never commit secrets, `local.properties`, a release keystore, or local dev tooling (see above);
  `app/debug.keystore` is the one intentional exception.

**Releases**
- Bump `versionCode` (+1) and `versionName` (semver) in `app/build.gradle.kts` in a dedicated
  `chore(release): vX.Y.Z` commit, tag it `vX.Y.Z`, build `gradle :app:assembleDebug
  :app:assembleBenchmark` locally from that commit, and publish both APKs as a GitHub release
  (`gh release create vX.Y.Z <apks> --generate-notes`). Both build types sign with the committed
  debug key, so local and CI APKs install over each other.

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
the **user-selected** source (`selectedSourceId` in DataStore, chosen in the Settings-tab picker),
falling back to the first available source. **To add a built-in source, add one `@Binds @IntoSet`
line** — selection, routing, and the picker are automatic. The `AnimeSource` contract is **five required suspend methods** —
`popular`/`search`/`detail` (catalog-shaped) *and* `servers`/`resolve` (stream-shaped) — **plus a
`val info: SourceInfo` property** (`info.id` keys the registry; `info.isExternal` tags extensions in
the picker; `info.configurable` flags sources with settings) **and two more suspend methods with default
impls** for per-source settings, `preferences()` and `setPreference()` (no-ops unless overridden). So a
minimal source implements six members and a configurable one eight, even though browsing goes through
AniList's `CatalogRepository`. `SourceStreamRepository` is the join: per request it runs `search` → `detail` →
`servers().firstOrNull()` → `resolve`, sorts variants by height descending, and throws when no search
result is the title (an extension that finds nothing throws and the player degrades to a short hint —
there is no longer a built-in source that matched *any* query to keep playback always-resolving).
**Which search result is the title is decided by `TitleMatcher`, never by the site's ranking** — a
sequel's search lists its prequel first, and since the adapter numbers every entry's episodes 1..N,
taking `firstOrNull()` played season 1's episodes under season 2 with no error anywhere. So
`StreamRepository` takes the whole `AnimeDetail` (not a title string): candidates are scored against
every AniList name (`altTitles`: romaji/English/native/synonyms), season- and part-aware ("Rotten2",
"2nd Season", "第2期", "II" all parse as season 2), and rejected when they look more like a franchise
neighbour (`related`, with the neighbours' own `altTitles`) than like the title; up to three searches
(display title, romaji, season-stripped base name) run until a confident match. A site's unverified top
hit is trusted only for a standalone title, never for one with a prequel/parent. The requested episode
must exist on the matched entry — there's no fallback to episode 1. `FreshStreamRecovery` (the
player's mid-episode `recoverStream`) matches through the same `TitleMatcher` and ranks a confident
match above a faster unverified one. It exposes
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
  pattern rather than reading Media3 state in the UI. **Failed downloads retry by RE-RESOLUTION**
  (`DownloadRepository.retry`): a queued episode's tokenized stream URL usually expires before it
  gets one of the 2 parallel download slots, so a retry re-resolves a fresh URL at the original
  quality, refetches subs, and swaps the Media3 download in place (same id; the Room row is
  rewritten, and the resulting `onDownloadRemoved` is suppressed via the `retrying` guard so it
  can't delete the row). `STATE_FAILED` auto-retries this way (2× per process, backoff); the
  Downloads-screen Retry button calls `retry()` — never `resume()`, which is a no-op for FAILED.
  **Gotchas:** download state persists as an Int code,
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
  `popular:*`, `detail:*`, `skip:*`, `srcmatch:*`, `extrepo:*`/`extmeta:*`, `bestsrc:*`/`srcstats:*`,
  `malmap:*` — the last is permanent, the rest sweep after 30 days untouched). `CatalogRepository`
  binds to `CachedCatalogRepository` (decorator over `AniListCatalogRepository`); when adding a catalog
  query, add it to **both**. Stream resolution still
  needs the network — it caches only the search→detail leg (`srcmatch:<sourceId>:<anilistId>`: episode
  ids + match confidence, 4h) to cut round-trips, never server/stream URLs (tokenized/short-lived); a
  cached match that stops resolving falls back to one live re-run which overwrites the entry on success.
  Key by **AniList id, never title** (a remake shares its original's display title). A `detail:` row
  cached before `altTitles` existed is refreshed on its next read (`JsonCache.cached`'s `outdated`). Posters survive offline via the app-wide Coil
  loader in `AniLocalApp` (`respectCacheHeaders(false)` + 256 MB disk cache).

- **One shared Media3 `Cache`** (`data/.../download/DownloadModule.kt`) is written by the
  `DownloadManager` and read back by the player's `CacheDataSource.Factory` (built per stream in
  `PlayerViewModel.playbackDataSource`), so it transparently serves downloaded bytes offline and falls
  through to network when online. Every playback factory is **read-only** (`setCacheWriteDataSinkFactory(null)`
  + `FLAG_IGNORE_CACHE_ON_ERROR`): the `SimpleCache` uses `NoOpCacheEvictor` and *is* the user's
  downloads, so streaming must never write into it (Media3 writes through by default, and with tokenized
  URLs every re-watch wrote another full copy that nothing ever deleted) — and **never add a
  `CacheEvictor`**: it can't tell downloaded spans from streamed ones and would silently delete offline
  episodes. The cost is that rewinding into already-watched video re-fetches it. There are **two** `DefaultHttpDataSource.Factory` singletons: the unqualified one is the player's
  (per-stream headers set by `PlayerViewModel`), `@Named("downloadHttp")` is the `DownloadManager`'s —
  they must stay separate or playback headers clobber a running download's Referer (403s). Download
  headers ride a `ResolvingDataSource` that consults `DownloadHeaderStore` **per request**: enqueue/
  resume point the store at the download's headers (persisted in `DownloadEntity.headersJson`), and
  on a cold process — including a headless scheduler/boot service restart, where no ViewModel ever
  constructs `DownloadRepositoryImpl` — the store lazily restores them from Room on the download
  thread itself.

- **The source's own request headers must reach the media host intact.** Neither download HTTP factory
  sets a User-Agent: Media3 1.4.1 applies a constructor UA *after* the request properties and overwrites
  the source's (the header scraper CDNs gate on, and the one `cf_clearance` is bound to).
  `FALLBACK_USER_AGENT` (a browser UA) is added only when the source supplied none — by the download
  resolver in `DownloadModule` and by `PlayerViewModel.playbackDataSource`, deliberately duplicated since
  `:app` can't import `:data`; never "AniLocal", which UA-gated CDNs reject. Playback and downloads run on
  `HttpURLConnection`, not OkHttp, so they share no cookie jar or `CloudflareInterceptor` with the
  resolve legs (and Android installs no default `CookieHandler`); instead `AniyomiSourceAdapter` copies
  the jar's cookies for the stream host (e.g. a solved `cf_clearance`) into `VideoStream.headers` — host-
  scoped, and a source's own `Cookie` header wins. Don't "fix" this by moving playback onto
  `OkHttpDataSource`: that needs a Hilt→Injekt bridge, and Injekt is only seeded lazily when the
  extension loader first runs.

- **The download service crosses the module/manifest boundary.** `AniLocalDownloadService` (code in
  `:data`) plus its permissions (`FOREGROUND_SERVICE*`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`)
  and the `PlatformScheduler` JobService are declared in **`:app`'s `AndroidManifest.xml`** — `:data`'s
  manifest is empty by design, so edit `:app`'s manifest for any download-service change. The framework
  instantiates the service, so it can't constructor-inject; it pulls its deps through a Hilt `@EntryPoint`
  (`DownloadEntryPoint`) — add new service deps there + `DownloadModule`, not to a constructor.

- **Player has a dual online/offline path** (`app/.../player/PlayerViewModel.kt`): on load it first
  checks `downloads.getOffline(...)`. Offline → skip markers and subtitles come from the cached Room
  record. Online → AniList resolves the title, the bound `AnimeSource` resolves the stream.
  **Skip markers: the source's own timestamps first, AniSkip only fills gaps.** Aniyomi extensions
  publish per-file intro/outro windows (`Video.timestamps`; the HiAnime-family megaplay/megacloud
  sources such as Anikoto fill them) — `AniyomiSourceAdapter` maps them onto `VideoStream.skips`. They
  are aligned to that exact file, whereas AniSkip's crowd-sourced times belong to whatever cut the
  submitter watched (a source's ~16s pre-roll shifts every timestamp; AoT ep 1's "same-length" AniSkip
  OP covered the cold open). The player holds them as `knownMarkers` (online: `stream.skips`; offline:
  the download's snapshot) and, once per episode at `STATE_READY` with a real duration, asks AniSkip
  **only for a type they lack**, merging per type via the domain `withFallback` (each type is decided
  independently — "has any marker" ≠ "has both"; that conflation is what lost outros). Downloads snapshot
  `stream.skips.withFallback(aniskip)` at enqueue, and a retry re-snapshots from the re-resolved file. A
  merge can only add windows, never clobber shown ones. `playableMarkers` clamps an OUTRO that runs past
  the file's end (instead of dropping it) and drops anything else outside the media. When a title has
  **no MAL id**, `AniSkipRepository` returns **no markers** — never fabricated ones (a Skip button
  jumping to made-up times is worse than none). Auto-skip fires at most once per marker.
  **How AniSkip answers** (`aniskip-api` source): per requested type independently, the top-voted
  submission whose `episodeLength` is within **±20s** of ours (any length for `0`), each tagged with its
  own `episodeLength`; HTTP 404 when no type matched. So a length-matched answer often holds only an OP
  or only an ED: `AniSkipRepository` takes each type from the same-cut query if plausible, else from
  the `episodeLength=0` query. An ED from a different-length cut is **shifted by the length
  difference** (sampled across 239 episodes: OPs keep absolute time, EDs keep their distance from the
  end); windows are rejected unless an OP starts in the first half and an ED in the second (no fixed
  OP cap — Steins;Gate ep 1 opens at 10:42) and spans 10–150s (OP) / 10–180s (ED). Cache keys are
  `skip:v2:<mal>:<ep>:<lenSec>` holding that query's raw submissions (24h).
  On `STATE_ENDED` the player **auto-advances to the next episode in place** (same screen and
  `PlayerView`; `playEpisode` resets per-episode state and normally re-runs `load()`, which builds a
  fresh ExoPlayer — a downloaded next ep plays from cache), gated on the `autoPlayNext` setting and
  `hasEpisode()`; an `advancing` guard fires it exactly once, and with no next episode it
  `progress.remove()`s the title from Continue Watching. **Pre-cache next episode**
  (`precacheNextEpisode`, default on, only with `autoPlayNext`) makes that switch instant: in the last
  2 min `maybePrecacheNext` re-resolves the next episode's link (the READY-time `prefetchNext` is a
  whole episode old by then, and tokenized links expire), builds its media into a hidden, paused
  **standby** ExoPlayer (its `PlayerOwner` holds an inactive generation, so its callbacks are ignored),
  and lets it buffer only once the current episode is fully buffered — or in the last 20 s if the
  current one isn't stalled — so the two never compete for bandwidth; `StandbyLoadControl` caps it at
  15 s. `playEpisode` then `takeStandby`s and `promote`s it (re-binds the owner, lifts the cap, swaps
  `_player`) instead of loading: ~20–40 ms from `STATE_ENDED` to the next episode's first frame. Any
  standby failure (expired link, no second decoder) just releases it and the normal `load()` runs; it
  is also released on seeking back >5 min from the end, recovery, the setting going off, an episode
  jump, and `onCleared`. The player's built-in next/prev
  buttons are disabled.
  **Two layouts, one tree** (`PlayerScreen`): playback opens **fullscreen** (sensor-landscape, bars
  hidden); exiting fullscreen (button or Back) shows the **watch page** (`WatchPanel.kt`: details +
  episode list) — portrait with the video on top on phones, landscape side-by-side on tablets
  (`smallestScreenWidthDp >= 600`). Back on the watch page leaves the player. The video `Box` stays
  the first child of one `BoxWithConstraints` and only its size changes, so the `PlayerView`/SurfaceView
  is never recreated on a mode switch — keep it that way (no Column/Row swap around it).

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
    `LaunchedEffect` on every app open) through the gated/throttled `syncIfDue()`; **"Sync now"** in Settings
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

- **"Auto (best source)" races installed sources and pins the winner.** `selectedSourceId` can hold a
  sentinel `Sources.AUTO`; when set, `SourceStreamRepository.resolveStreams` routes through
  `AutoSourceSelector` (`data/.../source/`) instead of one fixed source. The per-source pipeline is
  factored out as `resolveVia(source, anime, ep)` (the cached-match fast path + a live fallback,
  returning a `SourceResolution` that also reports whether the title match was `confident`) — the
  **one** unit both the manual path and the selector's race run, so there's a single pipeline. The
  selector: (1) reuses a per-title **pin** (`bestsrc:<anilistId>` in the disposable JsonCache, 7-day TTL) —
  a warm pinned resolve is the common case and matches manual latency (a pin won weakly, i.e. nothing
  matched confidently, is kept on a weak result rather than re-raced every episode); (2) on a pin miss/fail, **races**
  the top-K(≤4) sources ranked by a persisted scoreboard (`srcstats:<sourceId>`: latency EWMA that moves
  only on success ÷ Laplace-smoothed success rate), staggered 300 ms, gated by a shared `Semaphore(3)`,
  12 s per candidate / 15 s overall; the FIRST *strong* success (non-empty **and** a confident title
  match) wins immediately, unconfident "weak" ones (a site's unverified top hit) are a fallback only if
  nothing strong lands. Probes run in a
  scope **detached** from the caller (a child of `appScope`) so a slow blocking lib-14 loser can't hold
  up returning the winner; a `finally { raceScope.cancel() }` tears losers down on every exit.
  **Never persists a stream URL** (tokenized) — only the winning source id + stats, all re-derivable and
  swept with the cache. Manual-mode resolves also call `selector.record(...)`, so Auto starts warm. Only
  a **total** wipeout throws (the player surfaces the resolver's own message, e.g. "no source could
  resolve … (4 tried)"). Two hardening fixes ride with it because racing makes latent bugs routine: the
  vendored `CloudflareInterceptor` now serializes its cookie-delete + WebView solve under a process-wide
  lock (with a post-acquire `cf_clearance` re-check + `isCanceled` bail — see `extensions/VENDORING.md`),
  and `DownloadHeaderStore` is keyed **per stream-URL host** (concurrent downloads on different
  sources/hosts each keep their own Referer; cold-process restore seeds every active download's headers
  via the new `DownloadDao.activeNow()`).

- **Extensions install PRIVATELY (silent, verified) — no system-installer tap.** `AnimeExtensionLoader`
  now loads from two places, deduped by package (higher `versionCode` wins; a system install ties in its
  favour): installed (shared) packages **and** read-only APKs in `AnimeExtensionLoader.privateDir` =
  `filesDir/exts/<pkg>.apk` (parsed via `getPackageArchiveInfo`, `sourceDir`/`publicSourceDir` patched,
  then the same lib-12–16 gate + `ChildFirstPathClassLoader`). `ExtensionRepository.privateInstall`
  (`:data`) downloads → verifies the APK's signing-cert SHA-256 against the repo's `repo.json`
  `signingKeyFingerprint` (`ExtensionSignatures`) → enforces no-downgrade + no-signer-swap on updates →
  copies **read-only** into `filesDir/exts` (Android 14+ refuses to class-load writable dex) →
  `registry.refresh()`. `requireVerified=true` (auto-install) rejects any repo without a fingerprint;
  manual install from a fingerprint-less repo is allowed (user-initiated). `SourceRegistry.refresh()` is
  now on the **domain interface** so an install/uninstall rescans live (no app restart — the old gap).
  The Extensions screen installs silently, shows installed/uninstall state, and removes private
  extensions by deleting the file. **The app ships BLANK — no sources, no repos, nothing installs implicitly.**
  `extensionRepoBaseUrls` defaults to empty (an unset key falls back to the old yuzono default only for
  installs that already have private extensions, so upgrades aren't orphaned); users add/remove repos on
  the Extensions screen (which also lists installed extensions no configured repo carries, so they stay
  uninstallable). **Recommended sources (explicit opt-in only):** `RecommendedSources` (`:data`) is a small
  ranked community allow-list (verified against `RecommendedSources.REPO_URL`, the yuzono repo;
  HiAnime/AnimeKai are dead & removed — don't re-add); `installRecommended(target)` adds that repo to the
  user's list and tops up to `target` verified seeds (idempotent) — run ONLY from the "Install recommended
  sources" button in Settings. Selecting Auto must never trigger it. With zero sources, resolution throws the
  domain `NoSourceInstalledException`, which the player shows as-is (paused, no reconnect loop). The empirical scoreboard takes over ranking
  from there — no ongoing curation. **Auto-eviction** closes the loop: `ExtensionRepository.pruneLosers`
  (run alongside provisioning) uninstalls app-installed packages whose sources have a poor aggregate
  success rate after ≥`MIN_ATTEMPTS` recorded resolves — but ONLY ones that are **not** the pinned best
  for any title (`AutoSourceSelector.pinnedSourceIds`, enumerated via `CacheDao.entriesLike("bestsrc:%")`),
  never a user's manual install (only `settings.autoInstalledSources` is eligible), and never below
  `MIN_KEPT_PACKAGES`. Evicted packages go on `settings.evictedSources` so provisioning won't re-add them.
  The source→package link is `SourceInfo.pkg` (set by the loader/adapter; null for built-ins). Because a
  race loser that's merely *slower* is cancelled and records nothing, accumulated failures mean a source
  actually failed to resolve — so this targets dead/broken sources, not working-but-slow ones.

## Conventions

- Navigation is a single Compose `NavHost` in `MainActivity.kt`; routes live in
  `app/.../ui/navigation/Destinations.kt` (`TopTab` enum = bottom nav = Home/Explore/Library/Downloads/Settings — the Settings tab is `MoreScreen`/`MoreViewModel` in code;
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
  getOrNull`, and screens render blank or a short hint — an empty list uses the shared `EmptyState` (icon +
  title + hint, `ui/common/ScreenChrome.kt`) under the shared `ScreenHeader` (Home drops rows whose loader fails or returns empty;
  Details renders nothing but its floating back button until `detail` is non-null) — there are **no
  spinners or error dialogs** for load/error states. The one deliberate exception is the player's
  transient center rebuffer spinner (playback stall feedback, not a load state). Match that pattern
  rather than adding loading/error UI that clashes with it.
- Networking: Retrofit + Moshi + OkHttp, wired in `data/.../remote/NetworkModule.kt` — one shared
  client with explicit timeouts, a 20 MB HTTP cache, and a bounded `RetryInterceptor` (ONE retry with
  backoff on IO errors, or one honored short `Retry-After` on 429 — AniList rate-limits at 90 req/min;
  Explore additionally debounces typed queries 300 ms).
- Settings persist via DataStore (`DataStoreSettingsRepository`) exposed as Kotlin `Flow`s — including
  `autoPlayNext`, `precacheNextEpisode`, `autoSkip`, `wifiOnlyDownloads`, default download quality, and **subtitle
  scale/background** (the player applies these live to the `SubtitleView`; Settings shows a to-scale preview
  that reproduces the caption's true on-screen px).
- Every module keeps its Kotlin sources under `src/main/java/` (`:domain` was moved there from
  `src/main/kotlin/` to match). Put new files there, not in a `kotlin/` directory.
