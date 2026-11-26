# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

**AniLocal** — an Android anime player (Kotlin, Compose, single APK), **branded "Onboard" to the
user** (launcher label and tugboat icon, Home wordmark) with a midnight-blue accent on near-black surfaces —
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
wrapper, or use a system `gradle`. The wrapper/CI are pinned to **Gradle 9.9.2** (AGP 9.5.2 needs 9.1+).
Toolchain: **JDK 17**, **Kotlin 2.4** (AGP 9's built-in Kotlin — there is no `kotlin-android` plugin),
**compileSdk 37.2** (`release(37) { minorApiLevel = 2 }` — Compose 1.13 requires 37.1+), **targetSdk 35**,
**minSdk 24**. targetSdk stays 35 **on purpose**: from 36, Android ignores an app's forced orientation on
screens ≥600dp, which would break the player's sensor-landscape fullscreen on tablets.

```bash
gradle :app:assembleDebug          # build the installable debug APK (what CI does)
gradle build                       # build + verify all modules
gradle :domain:test                # JVM unit tests for the pure-Kotlin domain module
gradle :data:testDebugUnitTest     # Android-library unit tests
```

**No test suite is committed** and CI only lints and assembles: CI (`.github/workflows/android.yml`)
runs on every push — it checks the commit messages, runs `:domain:check` plus lint on the Android
modules, assembles `:app:assembleDebug` and `:app:assembleBenchmark` (R8-optimized), and uploads both APKs (artifacts
`anilocal-debug-apk` and `anilocal-optimized-apk`) for sideloading — it provisions Gradle 9.9.2 via `gradle/actions/setup-gradle`, **not** a committed wrapper,
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

**`app/src/main/baseline-prof.txt` is a committed startup/journey profile** (AGP compiles it into the
APK; ProfileInstaller applies it on sideloaded installs; R8 rewrites it to the optimized code). It was
recorded on a device, not by a benchmark module: install a benchmark build with `isMinifyEnabled = false`
(a throwaway copy — never commit that), `adb shell pm art clear-app-profiles com.anilocal.app`, drive
cold start / Home and Explore scrolling / Details / Settings / Downloads / playback a few times, then
`adb shell cmd package dump-profiles --dump-classes-and-methods com.anilocal.app` and pull
`/data/misc/profman/com.anilocal.app-primary.prof.txt`. Drop the `Landroidx/compose` lines (the Compose
libraries ship their own profiles) and replace the file. Refresh it after large UI changes; a stale
profile only loses its benefit (rules for code that no longer exists are dropped).

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
  `revert`, `style`. Add `!` after the type/scope for a breaking change (e.g. a database schema bump
  without a migration path).
- **scope** — optional, lowercase, the feature area: `player`, `downloads`, `sources`, `extensions`,
  `settings`, `library`, `home`, `explore`, `details`, `mal`, `skip`, `cache`, `net`, `ui`, `app`, or a
  module (`domain`, `data`). Nest with `/` (`extensions/ui`). Reuse an existing scope before inventing one
  (`git log --format=%s | grep -oE '^[a-z]+\([^)]+\)' | sort | uniq -c`).
- One logical change per commit. Don't bundle an unrelated fix into a feature commit; split it.

**Before pushing**
- Build locally — it's much faster than CI. The dev machine has Android Studio's SDK at
  `~/Android/Sdk` (`local.properties` → `sdk.dir`, gitignored) and needs Gradle 9.9.2 on PATH. Any code change
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
  enforced boundary, not a convention. Keep it that way. The repository interfaces (each built in
  `:data`'s `DataGraph`): `CatalogRepository`, `StreamRepository`, `SkipRepository`, `LibraryRepository`,
  `ProgressRepository` (all in `repo/Repositories.kt`), then **one file each** —
  `DownloadRepository` (`repo/DownloadRepository.kt`), `MalRepository` (`repo/MalRepository.kt`),
  `SettingsRepository` (`repo/SettingsRepository.kt`), and `ExtensionRepository` (`repo/ExtensionRepository.kt`
  — lists extensions from each configured `index.min.json` repo and downloads an APK for the system
  installer). The `AnimeSource` seam (`source/`) also defines
  `SourcePreference` (sealed: Toggle/EditText/Select/MultiSelect) — a UI-agnostic mapping of an
  extension's settings so `:app` can render/persist them without touching `androidx.preference`/`eu.kanade.*`.
- **`:data`** — Android library. *All* repository implementations live here: AniList/MAL APIs,
  AniSkip, the SQLite databases, the settings file, the Media3 download stack, the source registry, the
  extension-repo browse/install impls, and the object graph (`DataGraph`). Depends on `:domain` and
  `:extensions`.
- **`:extensions`** — Android library that makes AniLocal a host for Aniyomi extensions. Vendors the
  **real** Aniyomi anime source-api (`eu.kanade.tachiyomi.animesource.*` + a trimmed `network` package,
  copied from aniyomiorg/aniyomi — see `extensions/VENDORING.md`), plus host code under
  `com.anilocal.app.extensions`: `AniyomiSourceAdapter` (maps a loaded source onto the domain
  `AnimeSource` seam), `AniyomiInjektModule`/`AniyomiRuntime` (seed the Injekt singletons loaded
  sources resolve at construction), and `loader/AnimeExtensionLoader` + `ChildFirstPathClassLoader`
  (discover installed extension APKs by the `tachiyomi.animeextension` feature, lib-version 12–16, and
  reflect their source classes in). Depends on `:domain`; `:data` consumes it via `implementation` so
  the vendored `eu.kanade.*` types never reach `:app`. Loaded extensions link against the **host's** copies
  of OkHttp, okio, jsoup, kotlinx.serialization, coroutines, RxJava 1, Injekt, androidx.preference and
  QuickJS (`app.cash.quickjs`, the JS engine shared extractors like StreamWish deobfuscate with), so each
  must stay **at or above** what extensions are built against — a missing/older class surfaces only as
  a silent "nothing found". **Release and benchmark builds run R8** (`-dontobfuscate`, no resource
  shrinking), so the same set is the host's ABI under shrinking: `extensions/consumer-rules.pro` keeps
  the vendored `eu.kanade.*`/`tachiyomi.*` API strictly and those libraries' public API (Aniyomi's own
  list), plus the generic signature of Injekt's `FullTypeReference` subclasses (host registrations key
  on it). A library extensions call that isn't listed there is stripped from optimized builds only —
  debug still works — so add any new one to that file. The vendored `parseAs` uses Kotlin context *parameters*
  (`context(json: Json)`, as upstream does; same compiled signature as the old context receivers).
  `AniyomiSourceAdapter.resolve()` picks the pipeline the extension implements: the ext-lib-14
  `getVideoList(episode)` path by default (classic-first avoids an `AbstractMethodError` on lib-14
  sources), but **hosters-first** (`getHosterList → getVideoList(hoster)`) when the extension's own
  classes declare the lib-16 hoster API (`implementsHosterApi`, a reflective walk that stops at the
  vendored `eu.kanade.tachiyomi.animesource.*` base classes) — for those the classic call only reaches
  `AnimeHttpSource`'s default, a wasted `GET baseUrl + episode.url`. Each path falls back to the other,
  and every source call is `runCatching`-wrapped so a lib-mismatched extension degrades to empty. The adapter also implements `preferences()`/`setPreference()`
  by building a real androidx `PreferenceScreen` and reading/writing the source's own `source_<id>`
  SharedPreferences. The vendored `network/interceptor/CloudflareInterceptor` (+ `WebViewInterceptor`,
  `util/system/WebViewUtil`) is wired into NetworkHelper's single client, so the **Cloudflare bypass is
  always on** (solves the JS challenge in a headless `android.webkit` WebView, re-supplies `cf_clearance`;
  degrades silently if no WebView).
- **`:app`** — Compose UI, navigation, ViewModels, the Media3 player UI.
  **References only domain interfaces** — it must not import `com.anilocal.app.data.*` (an
  implementation reaching into the UI would break the seam). The one exception is the composition root:
  `AniLocalApp.kt` builds the `DataGraph` and `AppGraph.kt` re-exposes it as domain interfaces; every
  screen and ViewModel gets its dependencies from `AppGraph`.

Dependencies are managed centrally in the version catalog `gradle/libs.versions.toml`; module
`build.gradle.kts` files stay thin and reference `libs.*`.

## Where everything is wired: `data/.../DataGraph.kt`

There is **no DI framework**. `DataGraph` (`:data`, package `com.anilocal.app.data`) builds every
repository, client and database itself — each a `by lazy` property, so it's created once, on first use,
in dependency order — and exposes them as domain interfaces (plus the few framework objects the app
needs: the Media3 `mediaCache`, `okHttp`, `appScope`). This is the swap point for the whole app: to
change which impl backs an interface, change its line here. The most important wiring:

```kotlin
// The app ships NO built-in sources: the registry merges an empty set with installed extensions.
val sources: SourceRegistry by lazy { SourceRegistryImpl(app, appScope, builtIns = emptySet()) }
```

Streams are **not one compile-time binding**. Built-in `AnimeSource`s would be passed in that
`builtIns` set (the app currently ships none); `SourceRegistryImpl` (in `:data`) merges that set with the
extensions `AnimeExtensionLoader` discovers and exposes a reactive `sources: StateFlow`.
`SourceStreamRepository` injects the `SourceRegistry` + `SettingsRepository` and routes resolution to
the **user-selected** source (`selectedSourceId` in the settings file, chosen in the Settings-tab picker),
falling back to the first available source. **To add a built-in source, add it to `builtIns` in
`DataGraph`** — selection, routing, and the picker are automatic. The `AnimeSource` contract is **five required suspend methods** —
`popular`/`search`/`detail` (catalog-shaped) *and* `servers`/`resolve` (stream-shaped) — **plus a
`val info: SourceInfo` property** (`info.id` keys the registry; `info.isExternal` tags extensions in
the picker; `info.configurable` flags sources with settings) **and two more suspend methods with default
impls** for per-source settings, `preferences()` and `setPreference()` (no-ops unless overridden). So a
minimal source implements six members and a configurable one eight, even though browsing goes through
AniList's `CatalogRepository`. `SourceStreamRepository` is the join: per request it runs `search` → `detail` →
`servers().firstOrNull()` → `resolve`, sorts variants by the audio setting's fit, then height
descending (see "Track languages" below), and throws when no search result is the title (an
extension that finds nothing throws and the player degrades to a short hint — there is no longer a
built-in source that matched *any* query to keep playback always-resolving).
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
match above a faster unverified one, and a variant in the preferred audio above a faster one. It exposes
`resolveStream` (single, highest quality — but carrying the de-duped **union of every variant's
subtitles**, so captions on lower renditions aren't lost to the height sort → online playback),
`resolveStreams` (all variants → the download quality picker), and `prefetch(anime, ep)`: a background
resolve of the episode the user is likely to play next — on Details, the episode of a title the user is
partway through (the resume bar's episode gets more: a whole warmed-up player, see "Warm player" below). `resolveStream`
serves a fresh prefetch (≤ 4 min old — links are tokenized; at most 2 kept) instead of resolving again,
and falls back to a live resolve if it failed. Prefetches are keyed by the selected source and audio
setting too, so one made before the user changed either is never played. Downloaded episodes are never prefetched.

`DataGraph` builds the infrastructure through three small factory objects under
`com.anilocal.app.data.*`: `NetworkModule` (the one shared `OkHttpClient`) and
`DownloadModule` (the Media3 cache/manager stack). The four APIs (AniList/AniSkip/MAL/extension-repo)
are plain classes over `JsonHttp`. Adding a new repo/source impl → a constructor call + property in
`DataGraph` (and, if the UI needs it, a getter in `AppGraph`). In `:app`, **`AniLocalApp`** is the
composition root (it implements `DataGraphHolder`) and **`AppGraph`** is what the UI sees; ViewModels
are built with `appViewModel { graph -> XViewModel(graph.catalog, …) }`, which scopes them like
`viewModel()`. **No module generates a `BuildConfig`** and there are no build-time keys.

## Key cross-cutting patterns

- **The database is the offline source of truth for the UI.** Everything the UI shows (downloads list,
  badges, progress) reads from it — never directly from Media3 or the network. `DownloadRepositoryImpl`'s
  `init` block **bridges** Media3 → the database three ways: a `DownloadManager.Listener` writes state changes
  (`dao.updateState(...)`) and removals (`dao.deleteById`), and the `wifiOnlyDownloads` settings `Flow`
  drives `downloadManager.setRequirements(...)`. When adding download/offline features, follow this bridge
  pattern rather than reading Media3 state in the UI. **Failed downloads retry by RE-RESOLUTION**
  (`DownloadRepository.retry`): a queued episode's tokenized stream URL usually expires before it
  gets one of the 2 parallel download slots, so a retry re-resolves a fresh URL at the original
  quality, refetches subs, and swaps the Media3 download in place (same id; the row is
  rewritten, and the resulting `onDownloadRemoved` is suppressed via the `retrying` guard so it
  can't delete the row). `STATE_FAILED` auto-retries this way (2× per process, backoff); the
  Downloads-screen Retry button calls `retry()` — never `resume()`, which is a no-op for FAILED.
  **Gotchas:** download state persists as an Int code,
  and the DAO hard-codes `state = 1` to mean COMPLETED — a magic number duplicated from private companion
  consts, easy to break; subtitles, skip markers and headers are stored as **JSON columns** on `DownloadEntity`
  (not separate tables); offline subtitle files are pulled into `<downloadDir>/subs/` with their URLs
  rewritten to `file://` and **deleted manually in `remove()`**.
- **Storage is hand-written SQLite — no Room** (`data/.../local/`). `Db` wraps the platform
  `SQLiteOpenHelper`: versioned create/migrate, WAL, coroutine `read`/`write` on the IO pool plus blocking
  `readNow`/`transaction` for Media3's download threads, and `observe(tables) { query }` Flows that re-run
  when a `write` to one of those tables **reports a change** (a write block returns whether it changed a
  row — return `false` for a no-op so the frequent download-progress writes don't wake every observer).
  The DAOs (`LibraryDao`/`DownloadDao`/`MalDao`/`ProgressDao`/`CacheDao`) are plain SQL. There are two
  files: **`anilocal.db`** (`AppDatabase`, version 5) holds user data and **every schema bump must add a
  migration** to its `Schema` (the 4→5 `headersJson` column is the pattern); a version with no migration
  fails loudly rather than wiping data. **`anilocal-cache.db`** (`CacheDatabase`) is the **disposable**
  JSON cache (one `kv_cache` table) and drops/recreates on any version mismatch. The CREATE statements are
  byte-for-byte what Room generated and **Room's identity hash is kept in `room_master_table`**, so the
  files stay interchangeable with older Room-based builds (a downgrade still opens them) — when the schema
  changes, change both the statements and the hash's meaning deliberately.

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
  cached before `altTitles` existed is refreshed on its next read (`JsonCache.cached`'s `outdated`). Posters survive offline via the app's own
  image loader (`app/.../ui/image/`, no Coil): `RemoteImage` is the composable every screen uses — it
  fills the size its modifier gives it and loads + draws in one modifier node (an arriving image redraws
  without recomposing; leaving the screen cancels the load). `ImageLoader` (built lazily by `AniLocalApp`,
  which implements `ImageLoader.Provider`) keeps a memory LRU checked synchronously on the main thread, a
  256 MB disk cache of the original bytes served regardless of cache headers, one shared download per
  URL over the shared OkHttp client, and decodes off-main, downsampled to the drawn size, as hardware
  bitmaps. `ImageDiskCache` keeps **Coil 2's file layout** (`cache/image_cache/<sha256(url) hex>.1`) so
  images cached by older builds still render offline — keep those names. In a lazy list the node is
  reused across items: `onReset` must keep the measured size (a reused item isn't remeasured).

- **One shared Media3 `Cache`** (`data/.../download/DownloadModule.kt`) is written by the
  `DownloadManager` and read back by the player's `CacheDataSource.Factory` (built per stream in
  `PlayerViewModel.playbackDataSource`), so it transparently serves downloaded bytes offline and falls
  through to network when online. Every playback factory is **read-only** (`setCacheWriteDataSinkFactory(null)`
  + `FLAG_IGNORE_CACHE_ON_ERROR`): the `SimpleCache` uses `NoOpCacheEvictor` and *is* the user's
  downloads, so streaming must never write into it (Media3 writes through by default, and with tokenized
  URLs every re-watch wrote another full copy that nothing ever deleted) — and **never add a
  `CacheEvictor`**: it can't tell downloaded spans from streamed ones and would silently delete offline
  episodes. The cost is that rewinding into already-watched video re-fetches it. The player builds its own `DefaultHttpDataSource.Factory` **per stream** (`PlayerViewModel.playbackDataSource`)
  and the `DownloadManager` has its own (`DownloadModule.downloadDataSourceFactory`) — they must stay
  separate or playback headers clobber a running download's Referer (403s). Download
  headers ride a `ResolvingDataSource` that consults `DownloadHeaderStore` **per request**: enqueue/
  resume point the store at the download's headers (persisted in `DownloadEntity.headersJson`), and
  on a cold process — including a headless scheduler/boot service restart, where no ViewModel ever
  constructs `DownloadRepositoryImpl` — the store lazily restores them from the database on the download
  thread itself.

- **The source's own request headers must reach the media host intact.** Neither download HTTP factory
  sets a User-Agent: Media3 (1.4.1 through at least 1.11.1) applies a constructor UA *after* the request properties and overwrites
  the source's (the header scraper CDNs gate on, and the one `cf_clearance` is bound to).
  `FALLBACK_USER_AGENT` (a browser UA) is added only when the source supplied none — by the download
  resolver in `DownloadModule` and by `PlayerViewModel.playbackDataSource`, deliberately duplicated since
  `:app` can't import `:data`; never "AniLocal", which UA-gated CDNs reject. Playback and downloads run on
  `HttpURLConnection`, not OkHttp, so they share no cookie jar or `CloudflareInterceptor` with the
  resolve legs (and Android installs no default `CookieHandler`); instead `AniyomiSourceAdapter` copies
  the jar's cookies for the stream host (e.g. a solved `cf_clearance`) into `VideoStream.headers` — host-
  scoped, and a source's own `Cookie` header wins. Don't "fix" this by moving playback onto
  `OkHttpDataSource`: that needs the graph→Injekt bridge, and Injekt is only seeded lazily when the
  extension loader first runs.

- **The download service crosses the module/manifest boundary.** `AniLocalDownloadService` (code in
  `:data`) plus its permissions (`FOREGROUND_SERVICE*`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`)
  and the `PlatformScheduler` JobService are declared in **`:app`'s `AndroidManifest.xml`** — `:data`'s
  manifest is empty by design, so edit `:app`'s manifest for any download-service change. The framework
  instantiates the service, so it can't take constructor arguments; it reads them from the graph via
  `(applicationContext as DataGraphHolder).dataGraph` — add new service deps to `DataGraph` (as
  `internal` properties), not to a constructor.

- **Player has a dual online/offline path** (`app/.../player/PlayerViewModel.kt`): on load it first
  checks `downloads.getOffline(...)`. Offline → skip markers and subtitles come from the cached database
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
  jumping to made-up times is worse than none). Auto-skip fires at most once per marker. An OUTRO
  that runs to (within `END_THRESHOLD_MS` of) the file's end **is** the episode's end: skipping it
  (button or auto-skip, `skipPast`) seeks to the end and calls `endEpisode()` at once — the advance
  `STATE_ENDED` runs — instead of waiting for Media3 to fetch and decode the last segment just to reach
  end-of-stream, which nothing watches for a stall (that wait was the outro-skip hang).
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
  video surface; `playEpisode` resets per-episode state and normally re-runs `load()`, which builds a
  fresh ExoPlayer — a downloaded next ep plays from cache), gated on the `autoPlayNext` setting and
  `hasEpisode()`; an `advancing` guard fires it exactly once, and with no next episode it
  `progress.remove()`s the title from Continue Watching. **Pre-cache next episode**
  (`precacheNextEpisode`, default on, only with `autoPlayNext`) makes that switch instant. Its timing is
  measured to where the content ends (`contentEndMs`: the start of such an end-reaching outro, else the
  file's end), so an outro skip finds the standby already buffering. In the last
  2 min `maybePrecacheNext` re-resolves the next episode's link (the READY-time `prefetchNext` is a
  whole episode old by then, and tokenized links expire), builds its media into a hidden, paused
  **standby** ExoPlayer (its `PlayerOwner` holds an inactive generation, so its callbacks are ignored),
  and lets it buffer only once the current episode is buffered to that point — or in the last 20 s if the
  current one isn't stalled — so the two never compete for bandwidth; `StandbyLoadControl` caps it at
  15 s. `playEpisode` then `takeStandby`s and `promote`s it (re-binds the owner, lifts the cap, swaps
  `_player`) instead of loading: ~20–40 ms from `STATE_ENDED` to the next episode's first frame. Any
  standby failure (expired link, no second decoder) just releases it and the normal `load()` runs; it
  is also released on seeking back >5 min from the end, recovery, the setting going off, an episode
  jump, and `onCleared`. The player's built-in next/prev
  buttons are disabled.
  **Warm player — resuming never reloads.** Closing the player doesn't release it: the screen calls
  `PlayerViewModel.hide()` (paused, progress saved, media session inactive, standby dropped) and
  `AppNavigation.prune` keeps that entry's `ViewModelStore` as the *warm* player instead of clearing
  it. At app open (`AppRoot`, 1.5 s after STARTED) the resume bar's episode is built the same way off
  screen — `nav.warmUp(route, …)` constructs `playerViewModel(graph, route, startHidden = true)` into a
  fresh store under `viewModel()`'s default key — which resolves it, prepares it paused at the saved
  position and buffers `STANDBY_BUFFER_US` (a capped `StandbyLoadControl`, lifted on show). Opening the
  same anime + episode (resume bar, Continue Watching, Details) **adopts** that store in
  `AppNavigation.ownerOf`, so `PlayerScreen` finds the very same view model, `show(startMs)` resumes it
  (startMs 0 = started from the beginning, so it seeks to 0), and the video is on screen during the
  slide-up — no resolve, no buffering. One is kept at a time: opening any other episode releases it, a
  warm-up older than 10 min is rebuilt (tokenized links), and `ON_STOP` releases it (no decoder held in
  the background; the next start warms up again). Hidden, a view model saves no progress, prefetches
  nothing, builds no standby and starts no stream recovery (`PlaybackRecovery.pauseRetries`: an attempt
  already running finishes; failures queue and run when it's shown).
  **Two layouts, one tree** (`PlayerScreen`): playback opens **fullscreen** (sensor-landscape, bars
  hidden); exiting fullscreen (button or Back) shows the **watch page** (`WatchPanel.kt`: details +
  episode list) — portrait with the video on top on phones, landscape side-by-side on tablets
  (`smallestScreenWidthDp >= 600`). Back on the watch page leaves the player. The video `Box` stays
  the first child of one `BoxWithConstraints` and only its size changes, so its SurfaceView is never
  recreated on a mode switch — keep it that way (no Column/Row swap around it). On tablets a toggle
  **glides** (`VideoGlide`, `Motion.FULLSCREEN_MS`): the *old* layout is transformed (a uniform
  scale + move in a `graphicsLayer`, which the SurfaceView follows) until the video sits where the new
  mode puts it, the watch page fades in or out around it, and only then does the layout switch
  (`layoutFullscreen` lags `fullscreen`). On the watch page the video is laid out at its fullscreen
  16:9 size and scaled into the box (`laidOutAt`), so a toggle never resizes the SurfaceView: one
  resized while its on-screen rect stays the same (exactly where a glide ends) shows a frame at its old
  scale. Phones snap — their toggle rotates the screen, and the system's rotation animation is the
  transition.
  **The player draws its own video and captions — no media3-ui.** `VideoSurface` is a `SurfaceView` sized
  like Media3's AspectRatioFrameLayout (`VideoScale` Fit/Zoom/Stretch, same 1% tolerance), black until
  the current player's first frame (PlayerView's shutter), and on a player swap it moves the surface from
  the outgoing player to the new one (what keeps the standby promotion instant). `Captions` is a Compose
  port of the parts of Media3's SubtitlePainter the app used — text size `CAPTION_TEXT_SIZE_FRACTION`
  (0.0533) × scale of the **video frame's** height, white text with a 2dp black outline, the optional
  translucent box as a background span on the edge layout, cue line/position anchors, bitmap cues, embedded
  styles ignored, and Media3's per-line bidi wrap for RTL text — drawn over the video frame, exactly as
  SubtitleView inside PlayerView's content frame did. Settings' preview uses the same `Captions`.
  **Track languages** (Settings → Language: `subtitleLanguage`, default Auto = the device's language,
  or Off; `audioLanguage`, default Japanese, or Auto = the source's own pick). The loose matching lives
  in the domain `TrackLanguages`: sources name tracks in free text ("English [CC]", "Spanish - Latino",
  "jpn"), so `identify` reads names, native names and 639-2 codes as whole words, and a bare 2-letter
  code only when nothing longer names a language ("de" in "Español de España"). Captions start in the
  first of `subtitleOrder` (the setting, the device's, English) — `buildMedia` flags that sidecar
  DEFAULT and rewrites every sidecar's language to its ISO code, and `carriedTrackParameters` sets the
  same order as Media3's preferred text languages for a stream's own (HLS) caption renditions. With none
  of those, the first sidecar whose language can't be told is the default, never one known to be in
  another language (the CC menu still lists it). A caption picked in the menu carries to later episodes
  only by label/language — never by list position, which put the next episode in another language. The
  audio setting is Media3's preferred audio language (a stream's own audio renditions) and, because
  sources that offer an episode subbed and dubbed tell them apart only in the variant's label ("Sub"/
  "Dub", "jpn"/"eng", "Latino"), `TrackLanguages.audioFit` ranks variants ahead of height in
  `SourceStreamRepository.resolveFrom` and ahead of speed in `FreshStreamRecovery` — a height-only sort
  played whichever version came in the higher resolution. Downloads pick the same way: the domain
  `pickDownloadVariant` (season downloads, the quality picker's pre-selection, retries) takes the best
  audio fit, then the quality setting's height rule, so a cap never swaps the sub for the dub.
  **Headset/Bluetooth play-pause** rides the platform's `android.media.session.MediaSession`
  (`PlayerMediaSession` in `MediaButtons.kt`, owned by `PlayerViewModel`; no media3-session, no service,
  no notification). It isn't tied to an ExoPlayer, so player swaps don't touch it; the view model feeds it
  playing/buffering/paused and the title. It advertises only PLAY/PAUSE and routes both to `setPlaying`,
  the same path as the overlay button — nothing else (seek, next, stop, speed) is offered, so nothing can
  drive the ExoPlayer behind the view model's back. A toggle key (`PLAY_PAUSE`/`HEADSETHOOK`) is
  intercepted in `onMediaButtonEvent` and calls `togglePlay()` on the first ACTION_DOWN: it flips the play
  *intent*, because a player that's still connecting isn't playing, so a state-based toggle could never
  pause a loading episode.
  A hardware keyboard's (e.g. a tablet keyboard cover's) Space and arrows aren't media keys, so the
  session never sees them (its media keys, if any, already reach the session). `PlayerScreen`
  installs `MainActivity.keyInterceptor` (consulted in `dispatchKeyEvent`, *before* focus routing):
  an unmodified **Space** calls `togglePlay()`, **Left/Right** seek 10s with the double-tap's
  flash (holding one repeats), **F** toggles fullscreen, **C** toggles captions (`toggleCaptions()`:
  back on with the user's last menu pick, else the media's default track; a chip names the result),
  and **Enter** presses the Skip Intro/Outro pill while it shows (`activeSkip`, the pill's own test;
  Enter is consumed only then, so it still presses a Tab-focused button otherwise). A Compose key
  modifier would miss them whenever nothing is focused, focus routing would spend the arrows moving
  between overlay buttons, and Space would then press the focused button (Back included). On Home,
  `AppRoot` installs the same hook while the resume bar shows: **F** resumes that episode (the player
  opens fullscreen). It's Home-only because other tabs have text fields F must type into. An
  interceptor clears itself on leaving only if it's still the installed one.
  Every player (`createPlayer`) also **handles audio focus** (`USAGE_MEDIA`/`CONTENT_TYPE_MOVIE`: playing
  pauses another app's music; another app taking focus pauses this one) and **pauses when headphones
  disconnect** (`setHandleAudioBecomingNoisy`). Both turn `playWhenReady` off, which the player listener
  mirrors into the play intent. A call is a *transient* loss: playback is held while still READY (so the
  recovery watchdog ignores it) and resumes on its own.

- **Optional features no-op when unconfigured.** **MAL sync needs no config at all** (username only),
  and nothing needs a build-time key:
  - **MAL sync** uses **no API key and no OAuth** — it mirrors the user's PUBLIC list from MAL's own
    `load.json` page endpoint (`myanimelist.net/animelist/{username}/load.json?status=7`), by **username
    only** (entered in Settings → the settings file). Per-entry status is MAL's numeric code, mapped in
    `MalRepositoryImpl`. Sync is one-way/read-only: it pages the list (~300/page, ≤50 pages), then
    `dao.clear()` + `upsertAll()` (full replace, not merge), throttled to once per 30 min. Requires the
    user's MAL list privacy to be Public. It fires from `AppViewModel.onAppOpen()` (a `MainActivity`
    `LaunchedEffect` on every app open) through the gated/throttled `syncIfDue()`; **"Sync now"** in Settings
    calls the un-throttled `sync()`. Library's **"My List"** then merges the local library with the *entire*
    MAL mirror (deduped by MAL id, local wins); MAL-only rows get a synthetic `mal-<malId>` id, so opening
    one must first resolve `catalog.anilistIdForMal(malId)` before navigating — the detail route is keyed by
    **AniList id** (`DetailsScreen` does `animeId.toInt()`), and a miss shows a "not found" Toast.
  Code paths must stay functional with MAL unconfigured.

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
  only on success ÷ Laplace-smoothed success rate), staggered 300 ms, gated by a shared `Semaphore(3)`
  whose permit follows the detached extension call (released when the call ends, not at its deadline —
  so the cap bounds the calls really in flight; a candidate's clock starts once it holds a permit),
  12 s per candidate / 15 s overall (each probe runs detached and is *awaited* under its deadline —
  a blocking lib-14 call or a 30 s Cloudflare latch ignores cancellation, so a `withTimeout` around the
  call itself never fired on time); the FIRST *strong* success (non-empty **and** a confident title
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

- Navigation is **hand-written** (`app/.../ui/navigation/`, no navigation library):
  - `Route` (`Routes.kt`) — typed `@Serializable` destinations: the five tab roots (`TopTab` enum = bottom
    nav = Home/Explore/Library/Downloads/Settings — the Settings tab is `MoreScreen`/`MoreViewModel` in
    code), `Detail(animeId)`, `Player(animeId, episode, startMs)`, `Extensions`, `SourcePreferences(sourceId)`.
    Screens and ViewModels take these arguments directly (no `SavedStateHandle` args).
  - `Navigator` — **one back stack per tab**; switching tabs keeps each tab's screens, Back pops (then from
    another tab's root returns Home, then leaves the app), re-selecting the current tab pops to its root.
    `update(id, route)` changes an entry's route without rebuilding it (the player mirrors auto-advance's
    episode into its route so a restore reopens the right episode).
  - `NavDisplay` + `AppNavigation` (activity-scoped ViewModel) — each entry gets its own `ViewModelStore` and
    `rememberSaveable` state, disposed only once it's off every stack and off screen; the stacks survive
    config changes and process death (saved as JSON). Predictive back seeks the pop animation
    (`enableOnBackInvokedCallback` is on). The player slides up from the bottom edge over the screen it
    opens from and back down on close (`Motion`; a slide only — its SurfaceView moves with the Compose
    layers but can't fade with them), while `AppRoot`'s bottom bar sinks under it and rises back; the
    bar slides over the screen's edge and claims its space only once fully up, so the screen above is
    laid out once. A screen's own `BackHandler` (the player's) wins over the navigator's; the closing
    player's `active` is false so its handler stands down, and `openPlayer` ignores a second open. A
    closed player's store is kept as the warm player rather than cleared (see "Warm player").
  - The bottom bar shows only at a tab's root. Adding a tab = a `TopTab` entry + a `Route` + a branch in
    `MainActivity`'s `when (route)`; adding a screen = a `Route` + a branch.
- ViewModels are plain classes built with `appViewModel { graph -> … }` and take only domain
  interfaces/models and route arguments (plus framework types — the application `Context`, the Media3
  `mediaCache` the player reads downloads from, and `appScope` to persist final position after
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
- Networking: OkHttp + kotlinx.serialization — the two libraries the extension host already ships, so the
  data layer adds no HTTP/JSON library of its own (no Retrofit, no Moshi, no kotlin-reflect). `JsonHttp`
  (`data/.../remote/`) is the hand-written JSON-over-HTTP client: `Call.await()` (suspends; cancelling
  the coroutine cancels the call), `HttpException(code)` for non-2xx, and streaming decode via okio.
  Every JSON model is `@Serializable` (domain models included — `:domain` applies the serialization
  plugin and stays pure Kotlin) and is (de)serialized with compile-time serializers through the one
  **`AppJson`** config. `AppJson` deliberately reads/writes **exactly what the old Moshi setup did**
  (every field written incl. defaults, nulls omitted, unknown keys ignored, lenient for MAL's
  numbers-as-strings) — the JSON cache and the downloads table's JSON columns hold rows written by
  older builds, so don't change those flags without a migration. `NetworkModule` holds one shared
  client with explicit timeouts, a 20 MB HTTP cache, and a bounded `RetryInterceptor` (ONE retry with
  backoff on IO errors, or one honored short `Retry-After` on 429 — AniList rate-limits at 90 req/min;
  Explore additionally debounces typed queries 300 ms).
- Settings persist in the app's own settings file — `files/settings.json`, a flat JSON object
  (`SettingsFile`: loaded once off the main thread, edits serialized and written atomically via
  `AtomicFile` before they're published; no DataStore) — exposed by `SettingsRepositoryImpl` as Kotlin
  `Flow`s. On the first run after the switch it migrated DataStore's `settings.preferences_pb` with a small
  hand-written protobuf reader (`LegacyDataStorePreferences`) and left that file in place. Keys include
  `autoPlayNext`, `precacheNextEpisode`, `autoSkip`, `wifiOnlyDownloads`, default download quality,
  `subtitleLanguage`/`audioLanguage` (ISO 639-1 codes), and **subtitle scale/background** (the player applies these live to `Captions`; Settings shows a to-scale preview
  that reproduces the caption's true on-screen px).
- Every module keeps its Kotlin sources under `src/main/java/` (`:domain` was moved there from
  `src/main/kotlin/` to match). Put new files there, not in a `kotlin/` directory.
- Icons are the app's own vectors in `app/.../ui/icons/AppIcons.kt` — there is **no material-icons
  dependency** (the extended set was 11 MB of code for 50 glyphs). Call sites read
  `AppIcons.Filled.X` / `AppIcons.Outlined.X` / `AppIcons.AutoMirrored.Filled.X`; to add a glyph, copy
  its path data into `AppIcons` (from the Apache-2.0 Material sources, or draw your own).
- **The design system is the app's own — no Material.** `ui/theme/Theme.kt` is `AniLocalTheme` +
  `AppTheme.colors` / `AppTheme.type`: the Onboard palette and type scale under the *Material role
  names* the screens were written against (`primary`, `onSurfaceVariant`, `surfaceContainerHigh`,
  `titleMedium`, `bodySmall`…), so a role means what it always did. `ui/kit/` holds the components, named
  and shaped like the Material ones they replaced so call sites read the same: `Text`/`Icon`/`Surface`
  (+ `LocalContentColor`/`LocalTextStyle`/`ProvideTextStyle`), `Button` (filled pill) / `TonalButton` /
  `TextButton` / `IconButton` / `TonalIconButton`, `FilterChip` (filled when selected, **no checkmark**),
  `Switch`, `RadioButton`, `Checkbox`, `Slider` (continuous track, snaps to `steps` with a haptic tick,
  no tick marks), `TextInput` (label *above* the box), `AlertDialog`, `DropdownMenu`/`DropdownMenuItem`
  (a `Popup` — UI dumps don't see it), `Linear`/`CircularProgressIndicator`, `TopBar`, and
  `NavigationBar`/`NavigationBarItem`. There are **no ripples or shadows**: `PressIndication` (the
  theme's `LocalIndication`) tints a pressed element; `PressIndication.Button`/`.Card` also dip it 3%
  (filled buttons, posters). Flat surfaces separated by hairlines (`outlineVariant`). Restyle by
  changing tokens or a kit component, not call sites; the player keeps its own button styling and layout.
- **The logo** is a tugboat traced from the user's draft into vectors: `res/drawable/ic_launcher_foreground.xml`
  (adaptive foreground, also the themed-icon monochrome layer; 50/108 tall to stay in the safe zone) on
  `ic_launcher_background` `#000000` (pure black), `mipmap-anydpi/ic_launcher_legacy.xml` (pre-26 tile), and
  `drawable/ic_logo.xml` (the bare glyph, tinted by its user — the Home wordmark draws it in `tertiary`).
