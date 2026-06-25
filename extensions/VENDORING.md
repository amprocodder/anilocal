# Vendored Aniyomi source-api

The files under `src/main/java/eu/kanade/` and `src/main/java/tachiyomi/` are vendored from
[aniyomiorg/aniyomi](https://github.com/aniyomiorg/aniyomi) (`master`, fetched 2026-06-23) — the
**real** anime source-api classes that installed extension APKs bind to at load time.

## Why vendored, not a dependency
Aniyomi's `source-api` / `core:common` are local Gradle modules with no Maven publication, and the
only published `extensions-lib` artifacts are compile-only **stubs** whose method bodies
`throw Exception("Stub!")`. A host that loads extension APKs must put the real classes on its runtime
classpath, so they are copied here. Extensions still compile against the v14 stub and resolve to
these classes via the child-first class loader (Phase 3).

## What was trimmed / adapted (not on the stream-resolve path)
- **`source/*` (manga API)** — not fetched; anime extensions implement `animesource.*` only.
- **`NetworkHelper`** — rewritten minimal: dropped DoH providers, Brotli and HTTP logging. Keeps
  `client` / `cloudflareClient` / `nonCloudflareClient` / `cookieJar` / `defaultUserAgentProvider`.
  The **Cloudflare/WebView bypass is re-added** (`network/interceptor/CloudflareInterceptor.kt` +
  `WebViewInterceptor.kt` + `util/system/WebViewUtil.kt`), ported to plain `android.webkit` (no
  androidx.webkit) and `android.util.Log` (no logcat); it solves the JS challenge in a headless
  WebView and re-supplies the fresh `cf_clearance` cookie via [AndroidCookieJar]. On failure/timeout
  or when WebView is unavailable it degrades silently (never throws past the host's runCatching).
- **`NetworkPreferences`** — rewritten to drop the `tachiyomi.core.common.preference` framework.
- **`PreferenceScreen`** — KMP `expect`/`actual` flattened to a single typealias.
- **`AnimeFilterList`** — dropped the `@androidx.compose.runtime.Stable` annotation (no Compose here).

## Compiler notes
- `OkHttpExtensions.parseAs` uses Kotlin context receivers → `-Xcontext-receivers` is set in
  `build.gradle.kts`.
- OkHttp is pinned app-wide to `5.0.0-alpha.14` (what this code targets); the Injekt fork is
  `com.github.mihonapp:injekt` from JitPack.

`AniyomiSourceAdapter` (in `com.anilocal.app.extensions`) maps a loaded source onto the domain
`AnimeSource` seam; `AniyomiInjektModule` + `AniyomiRuntime` seed the Injekt singletons it needs.
