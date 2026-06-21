# AniLocal — all-in-one anime player (server-free)

A single-APK anime app with the AniLab-style menu layout, fully local operation, and **no
bundled scraper**. Stream resolution is an abstract `AnimeSource` plugin seam; a legal
**SampleLocalSource** (Creative-Commons clip) ships so the app plays video out of the box.

> Status: **authored, not yet compiled** (built outside the dev sandbox). Open in Android
> Studio (Koala+), let Gradle sync, fix any version nits, run. See "Build" below.

## What works
- AniLab-style **bottom-nav shell**: Home · Explore · Library · **Downloads** · More.
- **Offline downloads (Netflix-style, fully in-app)**: tap the download icon on an episode →
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
- **Player** (Media3/ExoPlayer) with the **Skip-Intro/Outro button** (`FreakIntroButton`
  equivalent), fed by **AniSkip** (real op/ed times by MAL id) — demo markers for the
  keyless sample so the control always demonstrates.
- **Room persistence**: "My List" (Library tab) + "Continue Watching" (Home), with watch
  progress saved during playback.
- **Google Sign-In** via **Firebase** (More tab) — guarded so it no-ops until you add your
  own project; then it actually works.
- **Source seam** (`AnimeSource`) + `SampleLocalSource` (plays a CC Big Buck Bunny clip), so
  the app streams lawfully with **no scraper**.
- Hilt DI, Compose + Material3, Coil images, OkHttp/Retrofit/Moshi.

## Configure optional features (the app builds & runs without these)
- **TMDB artwork** (optional): get a free v3 key at themoviedb.org, add to
  `~/.gradle/gradle.properties` or the project `gradle.properties`: `TMDB_API_KEY=xxxx`.
- **Google Sign-In**: create a Firebase project, add an Android app with your package
  (`com.anilocal.app`) and your signing **SHA-1**, download **`app/google-services.json`**
  (the build auto-applies the plugin once it's present), and set the **Web client id**:
  `GOOGLE_WEB_CLIENT_ID=xxxx.apps.googleusercontent.com` in `gradle.properties`.

## Deliberately absent (the "scraper-shaped hole")
- No pirate scraper, no private backend, no Notix ads, no signature spoofing.
- `AnimeSource` is the single drop-in point. A lawful implementation (e.g. **your own
  Jellyfin/Plex/local files**) plugs in here. What you drop in is your responsibility.

## Build
1. Open the project root in Android Studio (Koala+); it will generate the Gradle wrapper
   scripts (`gradlew`) and sync. (Wrapper pinned to Gradle 8.9 for AGP 8.7.)
2. JDK 17, Android SDK 35 installed. `minSdk 24`.
3. Run the `app` config on a device/emulator. The Home tab → a sample title → Play
   demonstrates playback + the skip button with no network and no account.

## Architecture — 3 Gradle modules
The dependency direction is compile-enforced (not just convention):

```
:app  ──►  :data  ──►  :domain
  └────────────────────►─┘
```

- **`:domain`** — pure Kotlin (`kotlin("jvm")`, no Android dependency). Models, repository
  interfaces, the `AnimeSource` seam. `import android.*` here won't compile — that's the
  boundary. Fast JVM unit tests.
- **`:data`** — Android library (`com.anilocal.app.data`). All implementations: AniList/TMDB,
  AniSkip, Room, DataStore, Firebase auth, Media3 downloads, the sample source, and the Hilt
  wiring (`di/AppModule`). Depends only on `:domain`.
- **`:app`** — Android application. Compose UI, navigation, ViewModels, the Media3 player UI,
  Google Sign-In UI. Depends on `:domain` + `:data` but references **only domain interfaces**
  (verified: 0 imports of `com.anilocal.app.data.*`), so features can't reach into impls.

Build files stay small via the version catalog (`gradle/libs.versions.toml`); a `build-logic`
convention plugin is a sensible later step if more modules are added.
