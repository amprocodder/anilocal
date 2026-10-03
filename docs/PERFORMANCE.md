# Phase 2 performance and playback recovery update

Version 0.2.1 (version code 2) builds on the installed Phase 2 extensions release,
`94eb83f`, including Onboard branding, extension loading/settings/private installation,
Auto sources, fullscreen controls, autoplay, offline browsing, season downloads,
download retry, and live progress/speed/ETA.

## Update compatibility

Both CI APK variants use Phase 2's committed `app/debug.keystore`, application ID
`com.anilocal.app`, and user database version 5. Migration 4→5 is retained. The user
DB no longer permits destructive fallback; the disposable catalog cache still does.
Minification stays disabled because dynamically loaded extensions reference named host APIs.
The performance APK disables debugging and uses the same signing identity as the development APK.

The earlier builds at `d4ff79b` and `37bea17` used the older Phase 1 baseline and a
different signing key. They cannot update the Phase 2 installation and are superseded.
Install this corrected APK over the existing app; no uninstall or data reset is required.

## Changes

- Fatal player errors trigger serialized player recreation with fresh source resolution and
  actual media-byte speed tests. Recovery bypasses title matches/pins/prefetch, requires the
  requested episode, preserves position and play intent, and prefers a healthy alternative URL.
  Three attempts use backoff; 30 seconds of uninterrupted playback resets the budget. Closing
  the player or switching episodes cancels obsolete work. Offline playback stays on cached media.
- Home's five shelves share one AniList GraphQL request and retain Phase 2's existing disk
  cache keys and stale-while-revalidate behavior. Search/navigation cancel obsolete requests.
- Library/download grouping and JSON work run away from the main thread. Large settings/list
  content renders lazily; settings writes are serialized and repeated values are suppressed.
- Native download writes are ordered, per-episode mutations serialized, and idle polling sleeps
  until a native event. Live progress/speed/ETA remain available. Subtitle transfers share a
  bounded cancellable pool; resumed downloads keep source headers and fresh retry resolution.
- Room progress/library changes and MAL mirror replacement use transactions; duplicate sync
  requests coalesce and an incomplete or obsolete-user sync cannot replace the saved mirror.

## Validation

All 106 local regression tests passed (57 app, 49 data), covering recovery/session state,
fresh media probes, catalog/cache behavior, UI request cancellation, settings, Room writes,
MAL synchronization, and subtitle transfer cancellation/concurrency. The test sources remain
under the ignored `.local-dev` directory, following the repository's convention.
CI assembles both APK variants and runs lint on all Android modules.
Update installation is checked on an Android 15 tablet-sized emulator against the original
Phase 2 APK with seeded library, progress, downloads/headers, MAL, settings, and extension
storage/preferences. The update installed successfully and every seeded user table, database
version/Room identity, settings file, and extension fixture matched exactly after startup.
The signing certificate SHA-256 for both Phase 2 and this update is
`1e6dce00e6c64f281c630eb444c0c637ce0104c4a16ec87de80056de05c8cf1f`.
Emulator verification does not establish frame performance or OS
compatibility on a physical Galaxy Tab S10 Ultra or Pixel 9a.

The earlier Phase 1 timing measurements and 119-test count do not validate this Phase 2 update.
