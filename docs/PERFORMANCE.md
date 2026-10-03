# Performance changes and verification

The work covers startup, every navigation tab, metadata, library/MAL, downloads,
playback/recovery, subtitles/skip controls, settings, and authentication. The primary
target is the Galaxy Tab S10 Ultra (2960×1848); the secondary target is the Pixel 9a
(1080×2424). No physical Samsung or Pixel was connected during this work.

## Changes and checks

| Area | Change | Verification |
| --- | --- | --- |
| Startup/navigation | Create a player only when media is ready; isolate resume-bar updates; shorter transitions | Emulator launches/navigation; recovery regression tests |
| Home/catalog | Five shelves in one request; bounded TTL/LRU caches; share same-key work; cap distinct pending requests at 16 per cache | Live HTTP 200: 150 titles, one request, 35,745 bytes; partial-response, expiry, burst and cancellation tests |
| Explore | 300 ms search debounce; cancel obsolete requests; immediate filters; stale results cannot win | Latest-query/filter/retry/timeout/cancellation tests; emulator search |
| Details | Cached/shared metadata; parallel stream/skip preparation; three concurrent episode preparations; queued quality dialogs identify the episode | Nine rapid requests with maximum three preparations; duplicate/stale picker tests; two real episode downloads |
| Library/MAL | Merge/project large lists off Main once; keyed grids; coalesced sync and atomic publication | 5,000-title fixture across all statuses; worker-thread, pagination, failure-preservation and throttle tests |
| Downloads | Sample progress only while active; ordered changed-only Room batches; per-ID serialization; bounded subtitle work | Real optimized transfer 3%→7%, paused at 9%, resumed at 12%, completed; write/removal/startup race and sidecar deletion tests |
| Download compatibility | Persist isolated source headers; reconcile missing completed native downloads; pause cleanly at Android 15 service timeout | Header/startup tests; ordinary native service runtime; timeout callback source-reviewed and build-checked, not triggered at runtime |
| Player | Observe skip-window changes instead of position ticks; remember caption styling; poll slowly while paused; save during playback | Decoded video/offline playback; local subtitle/skip fixture; recovery and duration-guard tests |
| Sources/recovery | Overlap resolution and fresh media probes; three concurrent probes; retain healthy alternatives; no speed cache | Coroutine virtual-time overlap test (2 s instead of sequential 3 s), concurrency, repeat-resolution/probe, HTTP/HLS/header/cancellation tests |
| Settings/auth | Lazy sections/local subscriptions; coalesced ordered writes; save username before sync; shared auth listener | Settings burst/external-change and duplicate-action tests; emulator settings and unconfigured sign-in |
| APK | R8/resource shrinking; release HTTP logging disabled; preserve reflective JSON models | Optimized build; catalog and persisted metadata runtime checks |

Fatal recovery recreates ExoPlayer, preserves the resume position, runs fresh online
source/server resolution and speed tests, and prefers healthy alternatives to the
failed URL. Three attempts use backoff; 30 seconds of uninterrupted playing resets
the budget. Offline recovery uses cached media and local sidecars.

The original demo URLs returned HTTP 403. Built-in sources now use a public
1280×720 Big Buck Bunny test clip and the W3C-hosted Sintel trailer. The Bunny
clip is 10 seconds and video-only; Sintel includes audio. Skip timings outside the loaded
video's duration are ignored. The first subtitle is selected by default and the
player exposes subtitle selection. Decoder fallback handles hardware initialization
failures before abandoning playback.

## Build and tests

Verification uses JDK 17, Gradle 8.9, and Android SDK 35:

```sh
gradle :app:assembleDebug :app:assembleBenchmark \
  :app:testDebugUnitTest :data:testDebugUnitTest :domain:test \
  :app:lintDebug :data:lintDebug
```

119 JVM tests pass (52 app, 67 data). Lint reports zero errors. Existing dependency
upgrade/icon warnings remain. The older Navigation library's custom lint checks are
skipped because their lint API is incompatible; other lint checks run normally.

The signed `benchmark` APK uses release optimization, disables debugging, and is
profileable by the Android shell. It uses the development signing key and replaces
the debug APK. ARM64, ARMv7, x86 and x86_64 are included. Store signing is separate.

| APK | Bytes |
| --- | ---: |
| Preserved baseline debug | 25,802,347 |
| Final debug | 26,127,109 |
| Final optimized | 5,040,788 |

The optimized APK is about 80% smaller than the baseline debug APK. This compares
build variants and does not attribute the entire reduction to application code.

## Runtime evidence

Evidence is under `/home/adam/.local/share/anilocal-performance`. Build logs and the
independent Home HTTP check are in `.local-dev/performance`; tests/lint reports are
in each module's `build/reports`.

- Android 15 tablet screens render live posters and real Cowboy Bebop details. All
  five MAL categories render populated subsets of the 5,000-title fixture.
- Episode 1 and 2 downloads completed after separate quality choices; episode 3
  downloaded from the other sample source. The foreground download service started.
- Real cached video played with emulator WiFi and mobile data disabled. A controlled
  local VTT/skip fixture verified rendered subtitles and auto-skip against completed
  media. These sidecars are test fixtures, not bundled sample tracks.
- Subtitle size persisted at 160%; source/quality settings worked. Empty-username
  MAL sync returned its error and re-enabled controls; unconfigured sign-in stayed
  responsive.
- Actual decoder failures exercised bounded recreation and the Retry state.
  Earlier fallback-enabled checks decoded downloaded video successfully. The stable
  emulator rejects the older W3C Bunny trailer's 853-pixel width in both its hardware and
  software AVC decoders. The cache SHA-256 matches the original public file, so
  this failure is not a corrupted download. The final default Bunny URL was replaced
  with a standard 1280×720 test clip. The Sintel trailer is 854 pixels wide.
- Optimized A tablet checks rendered live catalog posters, search, genre and
  sort results with an empty search field, and real episode details. A fresh native
  transfer progressed from 3% to 7%, stayed paused at 9%, resumed at 12%, and completed.
  This check used a temporary emulator WiFi ingress rate limit, removed afterward;
  the earlier console-speed attempt did not reliably throttle WiFi.
- Optimized A decoded the completed Sintel video with both WiFi and
  mobile data disabled. Controlled persisted subtitle/skip JSON loaded correctly:
  the local VTT visibly rendered "Offline subtitle verification", and the 5–15 s
  intro marker triggered a seek during playback. Evidence is in `final-runtime`,
  including `optimized-caption-track-controls.png` and native codec logs.
- All five optimized A tabs also rendered and remained navigable at the
  Pixel 9a-sized 1080×2424/density-420 layout on the same Android 15 emulator.
  No application fatal errors were recorded during these checks.
- Removing the real episode 2 download removed its Downloads row and restored its
  episode Download control, while episode 1 retained its Downloaded badge. Cleanup
  confirmed an empty temporary ingress-filter list and enabled WiFi/mobile data.
- After the default Bunny URL replacement, final optimized B repeated the tablet
  catalog/tab checks, completed a fresh default-source download, and decoded its
  1280×720 video with WiFi and mobile data both disabled. The screenshot is
  `final-runtime/final-default-bunny-offline-decoded.png`; codec logs contain no
  playback error. The final rebuild again passed all 119 tests and lint checks.

Public-list MAL pagination uses deterministic fake API tests. Firebase listener and
cancellation behavior were reviewed; configured sign-in was not exercised because no
Firebase credentials were supplied. TMDB remains an optional scaffold. Only the two
demo stream sources are bundled; AniList supplies real metadata.

## Measurements

The preserved baseline includes the earlier automatic playback recovery change.
`tools/performance/emulator_check.py` measures five cold launches and repeated scrolls
of all five tabs on an explicitly selected emulator. Evidence includes APK hashes,
Android version/API, density, screenshots/XML, and raw `gfxinfo` frame statistics.
Matched runs reset emulator app data and seed the same 5,000 MAL rows, 201 local
library rows, and 30 queued downloads. Actual transfers are checked separately.

Matched tablet measurements use emulator 35.4.9 (build 13025442), Android 15/API 35,
native 2960×1848 at density 320, 4 GB guest RAM, four CPU cores, SwiftShader and Vulkan
disabled. Emulator 37.2.12 repeatedly crashed in native host code, including with the
preserved baseline APK, so those failed runs are excluded. Android 17/API 37 install
and launch checks failed in the emulator's package service before the app ran;
Android 17 compatibility remains unverified.

Live network catalog/image timing varies. The emulator uses software rendering and
60 Hz. Frame times cannot establish smoothness on a physical tablet's GPU or at
higher refresh rates. The exploratory tablet baseline's startup median was 1,100 ms;
matched comparisons are recorded below.

| Metric | Baseline debug | Earlier debug | Optimized A | Final optimized B |
| --- | ---: | ---: | ---: | ---: |
| Cold process launch median, ms | 1,347 | 1,351 | 668 | 655 |
| Home frame p50/p95, ms | 42/61 | 46/65 | 25/34 | 26/48 |
| Explore frame p50/p95, ms | 46/61 | 44/61 | 44/61 | 44/61 |
| Library frame p50/p95, ms | 22/31 | 20/32 | 38/53 | 21/46 |
| Downloads frame p50/p95, ms | 23/30 | 18/32 | 26/44 | 16/32 |
| More frame p50/p95, ms | 44/61 | 44/57 | 42/61 | 42/61 |

Each run has five process-launch samples and one scroll batch per tab. Earlier
debug and optimized A precede the final Bunny URL replacement; B measures the
final optimized APK. No performance code changed between A and B. Launch uses
`am start -W` TotalTime; it does not measure when network catalog content is ready.
Both optimized launch medians are about 50% lower than baseline debug, and Home
frame times are lower in both. Explore and More changed little. Library and
Downloads varied across optimized batches, and Library p95 remained above
baseline. Do not attribute these repeat differences to the video URL change.
Debug startup was effectively unchanged, with mixed frame results. These
observations combine application changes and build optimization; they do not
establish a universal speedup or isolate individual changes. Seeded Library and
Downloads fixture content matches; one live Home title changed between optimized
runs. All completed runs have empty fatal-error logs.

Raw comparisons are `matched-baseline-tablet.json`, `matched-final-tablet.json`,
`matched-optimized-tablet.json`, and `matched-final-optimized-tablet.json` in the
evidence directory. Optimized A is preserved as `measured-optimized.apk` with
SHA-256 `fde91ef7713bbae4ee6ca5175c4d618ed8d8720f93929dc6f9696302639c7b16`.
The final optimized APK SHA-256 is
`789634dba7e78233e8a3d1f16a50ee0712add48e0ac266aa6977d3f4a83df6b1`.
