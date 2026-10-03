# AniLocal — Android anime player

A Kotlin/Compose anime app with Home · Explore · Library · Downloads · More and no private
backend or bundled scraper. AniList supplies catalog metadata; registered `AnimeSource`
implementations supply playable streams.

Two lawful demo sources ship: **Big Buck Bunny** and **Sintel** (Blender Foundation,
Creative Commons), streamed from public sample hosts. Both deliberately match any catalog title
and play their own demo clip. The small 720p Bunny test clip is silent; Sintel includes audio.
Browsing, the first stream, and downloading require internet access. Completed downloads,
the local library, and saved watch progress are available offline; no account is required
for the built-in sources.

See [performance behavior, measurements, and validation](docs/PERFORMANCE.md) for the
optimization work and its verification limits.

## Features

- **Catalog and search:** Home loads five shelves in one AniList GraphQL request. Bounded
  process-memory caches reuse Home/browse/search results for three minutes and details/MAL
  mappings for thirty minutes; concurrent identical requests share work. Explore waits
  300 ms after typing, cancels obsolete requests, and applies browse filters immediately.
  Home, Explore, and Details show loading/retry feedback, retain useful content during a
  refresh failure, and stop waiting after a twenty-second UI deadline.
- **Library and Continue Watching:** Room stores local titles and playback progress. Library
  merges local titles with a public MyAnimeList mirror, with local entries winning by MAL id.
  Large list mapping and all category projections run off the UI thread and are reused when
  changing filters. Lists and grids compose visible items lazily and keep stable item keys.
- **In-app downloads:** episode download buttons resolve streams and skip markers in parallel.
  Up to three different episodes prepare at once; per-episode duplicate guards and queued
  quality dialogs keep choices associated with the right episode. Media3 saves videos to app
  storage, while subtitles and skip markers are stored for offline playback. Downloads show
  progress, quality, pause/resume/retry, and removal controls; completed titles get badges.
  WiFi-only mode pauses downloads on metered networks and resumes on unmetered networks.
- **Playback:** Media3/ExoPlayer supports subtitles, adjustable subtitle size/background, and
  manual or automatic intro/outro skipping through AniSkip. Initial online playback and each
  fatal-error restart freshly resolve registered sources and servers and measure bounded
  media samples. HLS tests use a media segment. Healthy alternatives to a failed URL are
  preferred, and the replacement player resumes from the saved position. Recovery makes up
  to three attempts with backoff; thirty seconds of continuous playback resets the limit.
  A manual Retry button remains available. Offline playback/recovery uses downloaded bytes
  and local metadata without catalog calls or source speed tests.
- **Settings and accounts:** DataStore persists preferences; settings sections compose lazily,
  slider movement stays local until release, and queued preference bursts coalesce. Optional
  Google Sign-In uses your Firebase project. MyAnimeList sync needs only a username and a
  public list, saves that username before manual sync, and replaces the local mirror atomically
  only after fetching the complete list.
  More → Preferred streaming source chooses the download source; playback tests available
  connections and prefers your selection when speed and quality tie.

## Optional configuration

The app builds and its demo sources work without these settings.

- **Google Sign-In:** create a Firebase project, register `com.anilocal.app` and your signing
  SHA-1, then add `app/google-services.json`. The Google Services plugin applies only when
  that file exists. Set `GOOGLE_WEB_CLIENT_ID=xxxx.apps.googleusercontent.com` in your project
  or user `gradle.properties`. An unconfigured build shows a configuration hint when tapped.
- **MyAnimeList:** enter your username in More → MyAnimeList Sync. Set your MAL list to Public.
  Sync is read-only; automatic sync on app open is optional and throttled to thirty minutes.
- **TMDB:** its API declaration and optional `TMDB_API_KEY` are scaffolding. Artwork currently
  comes from AniList; entering a TMDB key does not yet enable enrichment.

## Build and checks

Use **JDK 17**, **Gradle 8.9**, and **Android SDK 35** (AGP 8.7, minimum Android API 24).
The Gradle wrapper scripts/JAR are not committed; use an installed Gradle 8.9 or this
workstation's ignored `.local-dev/gradle` helper. Open the project in an Android Studio
version that supports AGP 8.7 for editing and device runs.

```bash
gradle :domain:test :data:testDebugUnitTest :app:testDebugUnitTest :data:lintDebug :app:lintDebug
gradle :app:assembleDebug :app:assembleBenchmark
```

- `app/build/outputs/apk/debug/` contains the development APK.
- `app/build/outputs/apk/benchmark/` contains the optimized APK: release R8/resource shrinking,
  release library dependencies, and debug signing for sideloading and comparison. Use this
  variant for performance measurements; configure your own signing for a production release.
- GitHub Actions runs the JVM tests and lint, builds both variants, and uploads
  `anilocal-debug-apk` and `anilocal-optimized-apk`.

For a playback demonstration, open a catalog title → Play while online. The selected source
supplies download variants; online playback compares available sources and plays the fastest
healthy demo trailer. Download an episode before testing offline playback from Downloads.
Automated tests exercise concurrency, caching, cancellation, recovery, and storage behavior
without live services; runtime/device results are recorded in the performance document.

## Architecture and source extensions

Dependencies flow `:app → :data → :domain`, with `:app` also depending on `:domain` directly.
`:domain` is pure Kotlin and defines models/repository interfaces. `:data` implements AniList,
AniSkip, Room, DataStore, Firebase auth, Media3 downloads, and source registration. `:app`
contains Compose screens, navigation, ViewModels, and the player UI and references domain
interfaces rather than data implementations.

Add a lawful source by implementing `AnimeSource` and contributing it with Hilt `@IntoSet`
in `data/.../di/AppModule.kt`. `SourceRegistry` exposes available sources; More stores the
selection used by stream resolution. The catalog stays separate. The registry currently
contains the two built-ins; dynamic extension installation is future work. There is no
private backend, advertising service, scraper, or signature-spoofing code.
