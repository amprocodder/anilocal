# Onboard — all-in-one anime player (server-free)

> Branded **Onboard** in the launcher/UI (midnight-blue accent); the codebase, package
> (`com.anilocal.app`), repo, and CI artifact keep the original **AniLocal** name.

A single-APK anime app with a 9anime-style UI (spotlight hero, badge poster cards, numbered episode grid). **AniList** provides the entire browse
catalog; playable streams come from a **user-selected source** behind one abstract `AnimeSource`
plugin seam. The app ships **no** built-in stream sources; it can **install and load
Aniyomi/Anikku-style extension APKs** as the stream sources — so you can choose any source.
Sideload-only (not Google Play eligible); it bundles no extensions.

> Status: builds via CI (`gradle :app:assembleDebug` → the `anilocal-debug-apk` artifact for
> sideloading). Extension discovery/loading and in-app install are verified on-device by sideloading
> an extension; the rest builds clean.

## What works
- 9anime-style **bottom-nav shell**: Home · Explore · Library · **Downloads** · More.
- **Offline downloads (Netflix-style, fully in-app)**: hold an episode in the grid →
  Media3 `DownloadManager` saves the video to app storage; **subtitles** are pulled to local
  files and **skip-times are cached** too, so playback + auto-skip work with no network. A
  **Downloads tab** (offline-accessible) lists them with progress + **pause / resume / cancel**
  controls, and browser posters get a **downloaded badge**. Everything the UI shows reads from
  Room, so it works fully offline.
- **WiFi-only downloads** setting (More tab): sets the `DownloadManager` requirement to
  unmetered, so downloads auto-pause on mobile data and resume on WiFi.
- **Download quality selection**: a default-quality preference (More tab) plus a per-episode
  quality picker shown when the source offers multiple variants (pre-selected from the
  default). The chosen quality is stored and shown on the Downloads tab.
- **Auto-skip** toggle (More tab) persisted via **DataStore**; when on, the player seeks past
  each intro/outro window automatically (once per marker).
- **Real catalog** via **AniList** GraphQL (trending on Home, search in Explore, full detail
  pages). No API key needed. *(TMDB wired as optional artwork enrichment — see config.)*
- **Related titles** on every detail page: the franchise's prequels, sequels, seasons, movies
  and side stories in watch-order (AniList relations), each tagged and one tap away.
- **Player** (Media3/ExoPlayer) with the **Skip-Intro/Outro button** (`FreakIntroButton`
  equivalent), fed by **AniSkip** (real op/ed times by MAL id) — demo markers for titles with
  no MAL id so the control always demonstrates.
- **Room persistence**: "My List" (Library tab) + "Continue Watching" (Home), with watch
  progress saved during playback.
- **User-selectable stream sources**: a runtime **source registry** + picker (More tab). No
  built-in sources ship; **installed Aniyomi extensions** appear automatically and resolve
  streams for AniList-browsed titles. **Browse extensions** (More → Browse extensions) lists a
  pre-seeded repo's `index.min.json` and installs sources via the system installer.
- Hilt DI, Compose + Material3, Coil images, OkHttp 5/Retrofit/Moshi.

## Configure optional features (the app builds & runs without these)
- **TMDB artwork** (optional): get a free v3 key at themoviedb.org, add to
  `~/.gradle/gradle.properties` or the project `gradle.properties`: `TMDB_API_KEY=xxxx`.

## Extension sources & posture
- **No backend, no ads, no signature spoofing.** AniList stays the only browse layer; extensions
  only resolve streams.
- The app **bundles no extensions** and ships **no default piracy content** — you add a repo and
  install sources yourself (the community `yuzono/anime-repo` is pre-seeded as a starting point).
  What you install, and where it streams from, is your responsibility.
- **Sideload-only.** Loading third-party extension APKs needs `QUERY_ALL_PACKAGES` +
  `REQUEST_INSTALL_PACKAGES`, so this build is **not Google Play eligible** (it already ships via the
  CI APK). Trust/signature gating of extensions is a planned follow-up.

## Build
1. Open the project root in Android Studio (Koala+); it will generate the Gradle wrapper
   scripts (`gradlew`) and sync. (Wrapper pinned to Gradle 8.9 for AGP 8.7.)
2. JDK 17, Android SDK 35 installed. `minSdk 24`.
3. Run the `app` config on a device/emulator. Browse works immediately (AniList); to **play**,
   install an extension first (More → Browse extensions), then pick it in the source picker.

## Architecture — 4 Gradle modules
The dependency direction is compile-enforced (not just convention):

```
:app  ──►  :data  ──►  :extensions  ──►  :domain
  └──────────►─┴─────────────────────────►─┘
```

- **`:domain`** — pure Kotlin (`kotlin("jvm")`, no Android dependency). Models, repository
  interfaces, the `AnimeSource` seam. `import android.*` here won't compile — that's the boundary.
- **`:data`** — Android library. All repository implementations: AniList/TMDB, AniSkip, Room,
  DataStore, Media3 downloads, the source registry, extension-repo
  browse/install, and the Hilt wiring (`di/AppModule`). Depends on `:domain` + `:extensions`.
- **`:extensions`** — Android library hosting Aniyomi extensions: the vendored Aniyomi source-api
  (see `extensions/VENDORING.md`), the `AniyomiSourceAdapter`, the Injekt runtime, and the
  `AnimeExtensionLoader` (+ child-first classloader). Depends on `:domain`.
- **`:app`** — Android application. Compose UI, navigation, ViewModels, the Media3 player UI.
  References **only domain interfaces** (no `com.anilocal.app.data.*` /
  `eu.kanade.*` imports), so the UI can't reach into impls or vendored extension types.

Build files stay small via the version catalog (`gradle/libs.versions.toml`); a `build-logic`
convention plugin is a sensible later step if more modules are added.
