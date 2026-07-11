package com.anilocal.app.data.metadata.extension

/**
 * A small, ranked allow-list of community-recommended anime sources used ONLY to bootstrap "Auto
 * (best source)" so the race has proven candidates to measure on day one. Once installed, the
 * empirical scoreboard (win-rate + latency) takes over ranking entirely — this list never re-ranks
 * or curates after the initial seed, so there's no ongoing upkeep.
 *
 * Verified against the pre-seeded yuzono repo in mid-2026, best first. Note the churn that reshaped
 * this list: HiAnime (shut down 03/2026) and AnimeKai (05/2026) are gone and were removed upstream;
 * the surviving/replacement sources are seeded instead. Package names must match the repo index
 * exactly — a typo just means that seed is silently skipped (not found in the index).
 */
object RecommendedSources {

    private const val PREFIX = "eu.kanade.tachiyomi.animeextension."

    /** Ranked best-first. Torrentio is intentionally excluded — it needs debrid/torrent config and
     *  wouldn't resolve out of the box, so it's a poor auto-install default. */
    val RANKED: List<String> = listOf(
        "en.animepahe",      // most reliable survivor of the 2026 crackdown; clean encodes
        "en.kickassanime",   // Wotaku's first-listed site; most-updated EN extension in the repo
        "en.anikoto",        // EverythingMoe #1 site as of mid-2026; the post-shutdown replacement
        "all.anizone",       // recommended by Wotaku + EverythingMoe; reliable
        "en.allanime",       // huge catalog, sub+dub, softsubs (canonical Aniyomi source)
        "en.kotokai",        // AnimeKai clone (animekaitv.to), same codebase as Anikoto
        "en.animeparadise",  // official-sub rips; good secondary
    ).map { PREFIX + it }

    /** How many seed sources Auto mode tries to have installed to race against. */
    const val TARGET = 4
}
