package com.anilocal.app.data.source

import com.anilocal.app.domain.model.AnimeDetail
import com.anilocal.app.domain.model.AnimeSummary
import java.text.Normalizer
import java.util.Locale

/**
 * Decides which of a source's search results IS a given AniList title — the step that used to be
 * `source.search(title).firstOrNull()`.
 *
 * Trusting a site's first hit is wrong for sequels: searching for a season-2 title ranks the
 * season-1 entry first (it matches the shared base name best), and because the adapter numbers each
 * entry's episodes 1..N, "S2 episode 3" then resolves cleanly to S1 episode 3 — no error anywhere,
 * just the wrong season. AniList even spells some sequels in ways no site search understands: the
 * English title of The Angel Next Door season 2 is literally "The Angel Next Door Spoils Me Rotten2".
 *
 * So every candidate is scored against EVERY name AniList has for the title (English, romaji,
 * native, synonyms), season- and part-aware, and checked against the names of its franchise
 * neighbours (prequel, sequel, side story, …): a candidate that looks more like a neighbour than
 * like the title itself IS that neighbour and is rejected. Pure and deterministic — the policy is
 * unit-testable without a source or a network.
 */
internal object TitleMatcher {

    /** A chosen source entry; [confident] = a strong title match, not just the site's top hit. */
    data class Match(val candidate: AnimeSummary, val confident: Boolean)

    /** One candidate's verdict: [score] in 0..1 against the target's own names, and [margin] — how
     *  much better it fits us than our closest franchise neighbour (the tie-breaker). */
    data class Verdict(val candidate: AnimeSummary, val score: Double, val rejected: Boolean, val margin: Double = score)

    /** A title as compared: content tokens with any season/part marker lifted out. */
    data class Parsed(
        val tokens: List<String>,
        /** Season number; null = unknown ("Final Season"). Defaults to 1 when the title has no marker. */
        val season: Int?,
        val explicitSeason: Boolean,
        /** Split-cour part number, or null when the title has no part marker. */
        val part: Int?,
    )

    internal class Neighbour(val parsed: Parsed, val earlier: Boolean)

    /** The AniList title being looked for, parsed once per resolve. */
    class Target(anime: AnimeDetail) {
        val title: String = anime.title
        internal val own: List<Parsed> = (listOf(anime.title) + anime.altTitles).mapNotNull(::parse).distinct()
        internal val neighbours: List<Neighbour> = anime.related.flatMap { rel ->
            val earlier = rel.relation in EARLIER_RELATIONS
            (listOf(rel.anime.title) + rel.anime.altTitles).mapNotNull(::parse).map { Neighbour(it, earlier) }
        }

        /** A later entry in a franchise (has a prequel/parent) — its bare franchise name means the
         *  EARLIER entry, so a site's unverified top hit is never trusted for it. */
        internal val isContinuation: Boolean = anime.related.any { it.relation in EARLIER_RELATIONS }

        /** At most [MAX_SEARCHES] distinct queries, best first: the display title, the first
         *  Latin-script alternative (usually the romaji), then the display title's base name with
         *  any season/part marker stripped — a broad query that lists every season of the show. */
        val searchQueries: List<String> = buildList {
            add(anime.title)
            anime.altTitles.firstOrNull { it.isLatinScript() }?.let(::add)
            parse(anime.title)?.takeIf { it.explicitSeason || it.part != null }
                ?.let { add(it.tokens.joinToString(" ")) }
        }.distinctBy { it.lowercase(Locale.ROOT).replace(NON_ALNUM, "") }.take(MAX_SEARCHES)
    }

    /**
     * Search with each of the target's queries until a confident match turns up, then pick. Falls
     * back to the site's own top hit only for a standalone title (no prequel/parent), and even then
     * never to a candidate that was positively identified as a different entry.
     */
    suspend fun find(target: Target, search: suspend (String) -> List<AnimeSummary>): Match? {
        val seen = HashSet<String>()
        var best: Verdict? = null
        var topHit: Verdict? = null
        for (query in target.searchQueries) {
            for (hit in search(query)) {
                if (!seen.add(hit.id)) continue
                val v = verdict(target, hit)
                if (v.rejected) continue
                if (topHit == null) topHit = v
                // Equal scores (a remake sharing the original's English title) go to the candidate
                // that is more distinct from the neighbours; full ties keep the site's order.
                if (best == null || v.score > best.score || (v.score == best.score && v.margin > best.margin)) best = v
            }
            if (best != null && best.score >= CONFIDENT) break
        }
        best?.takeIf { it.score >= ACCEPT }?.let { return Match(it.candidate, confident = it.score >= CONFIDENT) }
        if (!target.isContinuation) topHit?.let { return Match(it.candidate, confident = false) }
        return null
    }

    fun verdict(target: Target, candidate: AnimeSummary): Verdict {
        // A blank title is no evidence either way (some sources fill titles lazily).
        val c = parse(candidate.title) ?: return Verdict(candidate, 0.0, rejected = false)
        val own = target.own.maxOfOrNull { pairScore(it, c) } ?: 0.0
        // Every one of our names makes a contradicting season/part claim → a different entry.
        if (own < 0) return Verdict(candidate, 0.0, rejected = true)
        var closest = 0.0
        for (n in target.neighbours) {
            val ns = pairScore(n.parsed, c)
            // Looks more like the neighbour than like us → it IS the neighbour. On an exact tie the
            // unmarked name belongs to the EARLIER entry (season 1 is "X", season 2 is "X 2"), so a
            // tie with a prequel/parent rejects, while a tie with a sequel keeps it as ours.
            if (ns >= NEIGHBOUR_MIN && (ns > own + EPSILON || (n.earlier && ns >= own - EPSILON))) {
                return Verdict(candidate, own, rejected = true)
            }
            closest = maxOf(closest, ns)
        }
        return Verdict(candidate, own, rejected = false, margin = own - closest)
    }

    /**
     * Similarity of two parsed titles in 0..1, scaled down when a season/part is claimed on one
     * side only, or [CONFLICT] (negative) when both sides claim different ones.
     */
    fun pairScore(a: Parsed, b: Parsed): Double {
        val season = when {
            a.season == null || b.season == null -> 1.0   // "Final Season": no numeric claim to check
            a.season == b.season -> 1.0
            a.explicitSeason && b.explicitSeason -> return CONFLICT
            else -> IMPLICIT_SEASON_MISMATCH               // "X" vs "X Season 2"
        }
        val part = when {
            a.part == b.part -> 1.0
            a.part != null && b.part != null -> return CONFLICT
            (a.part ?: b.part) == 1 -> 1.0                 // "X Part 1" vs "X"
            else -> IMPLICIT_PART_MISMATCH                 // "X" vs "X Part 2"
        }
        return similarity(a.tokens, b.tokens) * season * part
    }

    /** Weighted Dice coefficient over tokens. Stopwords count a quarter (so "The X" ≈ "X" but
     *  "Tokyo Ghoul √A" still beats "Tokyo Ghoul"), and two alphabetic tokens of length ≥ 4 within
     *  one edit per five chars count as equal ("bungou"/"bungo") so romanization variants don't
     *  sink a match. */
    private fun similarity(a: List<String>, b: List<String>): Double {
        val unmatched = b.toMutableList()
        var shared = 0.0
        for (t in a) {
            val i = unmatched.indexOfFirst { it == t || fuzzyEqual(it, t) }
            if (i >= 0) shared += weight(t) + weight(unmatched.removeAt(i))
        }
        val total = a.sumOf(::weight) + b.sumOf(::weight)
        return if (total == 0.0) 0.0 else shared / total
    }

    private fun weight(token: String) = if (token in STOPWORDS) STOPWORD_WEIGHT else 1.0

    private fun fuzzyEqual(a: String, b: String): Boolean {
        if (a.length < 4 || b.length < 4 || !a.all(Char::isLetter) || !b.all(Char::isLetter)) return false
        return editDistance(a, b) * 5 <= maxOf(a.length, b.length)
    }

    private fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }

    /** Normalize and tokenize [title], lifting out one season marker and one part marker. */
    fun parse(title: String): Parsed? {
        var s = Normalizer.normalize(title, Normalizer.Form.NFKD).replace(MARKS, "").lowercase(Locale.ROOT)
        s = s.replace('×', ' ').replace("&", " and ")
        s = CJK_SEASON.replace(s) { " season ${it.groupValues[1]} " }
        s = ORDINAL_SUFFIX.replace(s) { it.groupValues[1] }   // "2nd season" → "2 season"
        s = LETTER_DIGIT.replace(s, " ")                      // "rotten2" → "rotten 2", "s2" → "s 2"
        val t = s.split(NON_ALNUM).filter { it.isNotEmpty() && it !in NOISE }.toMutableList()
        if (t.isEmpty()) return null

        val part = takePart(t)
        var season: Int? = 1
        var explicit = false
        when (val found = takeSeason(t)) {
            null -> {}
            FINAL -> { season = null; explicit = true }
            else -> { season = found; explicit = true }
        }
        if (t.isEmpty()) return null
        return Parsed(t, season, explicit, part)
    }

    /** Removes and returns a part/cour number ("part 2", "2nd cour", "part ii"), or null. */
    private fun takePart(t: MutableList<String>): Int? {
        for (i in t.indices) {
            if (t[i] != "part" && t[i] != "cour") continue
            t.getOrNull(i + 1)?.let { numberOf(it) }?.let { n -> t.removeAt(i + 1); t.removeAt(i); return n }
            t.getOrNull(i - 1)?.let { numberOf(it) }?.let { n -> t.removeAt(i); t.removeAt(i - 1); return n }
        }
        return null
    }

    /** Removes and returns the season marker: a number, [FINAL], or null when the title has none. */
    private fun takeSeason(t: MutableList<String>): Int? {
        for (i in t.indices) {
            if (t[i] != "season") continue
            t.getOrNull(i + 1)?.toIntOrNull()?.let { n -> t.removeAt(i + 1); t.removeAt(i); return n }
            val before = t.getOrNull(i - 1)
            if (before == "final") { t.removeAt(i); return FINAL }   // keep "final" as a content token
            before?.let { numberOf(it) }?.let { n -> t.removeAt(i); t.removeAt(i - 1); return n }
        }
        for (i in 0 until t.size - 1) {
            if (t[i] == "s") t[i + 1].toIntOrNull()?.let { n -> t.removeAt(i + 1); t.removeAt(i); return n }
        }
        t.indexOfFirst { it in ROMAN_ANYWHERE }.takeIf { it >= 0 }?.let { i -> return ROMAN.getValue(t.removeAt(i)) }
        if (t.size >= 2) {
            val last = t.last()
            ROMAN_TRAILING[last]?.let { t.removeAt(t.lastIndex); return it }
            // A trailing small number is a season ("Bungo Stray Dogs 2") unless it is part of a
            // name ("Kaiju No. 8"); larger ones are names too ("Mob Psycho 100", "(2011)").
            val n = last.toIntOrNull()
            if (n != null && n in 1..MAX_TRAILING_SEASON && t[t.lastIndex - 1] !in NUMBERED_NOUNS) {
                t.removeAt(t.lastIndex)
                return n
            }
        }
        return null
    }

    private fun numberOf(token: String): Int? = token.toIntOrNull() ?: ORDINAL_WORDS[token] ?: ROMAN[token]

    private fun String.isLatinScript() = any { it.isLetter() } &&
        all { !it.isLetter() || Character.UnicodeScript.of(it.code) == Character.UnicodeScript.LATIN }

    // Relation labels as AniListCatalogRepository.prettyRelation renders them. An unmarked franchise
    // name ("X") denotes the earliest entry, so these neighbours own it over us on a tie.
    private val EARLIER_RELATIONS = setOf("Prequel", "Parent story")

    private const val MAX_SEARCHES = 3
    const val ACCEPT = 0.6
    const val CONFIDENT = 0.85
    private const val NEIGHBOUR_MIN = 0.6
    private const val EPSILON = 1e-6
    private const val CONFLICT = -1.0
    private const val STOPWORD_WEIGHT = 0.25
    private const val IMPLICIT_SEASON_MISMATCH = 0.4
    private const val IMPLICIT_PART_MISMATCH = 0.5
    private const val MAX_TRAILING_SEASON = 20
    private const val FINAL = -1

    private val MARKS = Regex("\\p{M}+")
    private val NON_ALNUM = Regex("[^\\p{L}\\p{Nd}]+")
    private val LETTER_DIGIT = Regex("(?<=\\p{L})(?=\\p{Nd})|(?<=\\p{Nd})(?=\\p{L})")
    private val ORDINAL_SUFFIX = Regex("\\b(\\d+)(?:st|nd|rd|th)\\b")
    private val CJK_SEASON = Regex("第\\s*(\\d+)\\s*[期季]")

    /** Release-format noise sites append to titles. */
    private val NOISE = setOf("dub", "dubbed", "sub", "subbed", "uncensored")
    private val STOPWORDS = setOf("the", "a", "an", "of", "and", "to", "in", "on", "x", "wa", "no", "ga", "ni", "wo", "o", "de", "mo")
    private val NUMBERED_NOUNS = setOf("no", "number", "part", "cour", "episode", "ep", "vol", "volume", "movie", "film", "chapter")
    private val ORDINAL_WORDS = mapOf(
        "first" to 1, "second" to 2, "third" to 3, "fourth" to 4, "fifth" to 5,
        "sixth" to 6, "seventh" to 7, "eighth" to 8, "ninth" to 9, "tenth" to 10,
    )
    private val ROMAN = mapOf("ii" to 2, "iii" to 3, "iv" to 4, "vi" to 6, "vii" to 7, "viii" to 8, "ix" to 9)
    private val ROMAN_ANYWHERE = setOf("ii", "iii", "iv")   // never English words, so safe mid-title
    private val ROMAN_TRAILING = ROMAN.filterKeys { it !in ROMAN_ANYWHERE }
}
