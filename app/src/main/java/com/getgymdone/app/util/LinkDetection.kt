package com.getgymdone.app.util

import android.util.Patterns

/** A URL found inside a block of user-written text. [start]/[end] index into that text. */
data class LinkSpan(val start: Int, val end: Int, val url: String)

/** Punctuation that commonly trails a URL in prose and is not part of it. */
private const val TRAILING_PUNCTUATION = ".,;:!?)]}'\"…"

/**
 * Find the web links in [text] using [Patterns.WEB_URL].
 *
 * Bare hosts ("youtube.com/watch?v=x") are returned with an `https://` scheme prepended so they
 * actually open; anything carrying a non-web scheme is dropped. Notes are user-entered text and
 * must never become an arbitrary-intent launcher, so this is the only place a URL is minted and
 * [isSafeWebUrl] is the gate everything goes through before it is opened.
 */
fun detectLinks(text: String): List<LinkSpan> {
    if (text.isEmpty()) return emptyList()
    val out = mutableListOf<LinkSpan>()
    val matcher = Patterns.WEB_URL.matcher(text)
    while (matcher.find()) {
        val start = matcher.start()
        var end = matcher.end()
        var raw = text.substring(start, end)
        // Trailing sentence punctuation belongs to the prose, not the link.
        while (raw.isNotEmpty() && TRAILING_PUNCTUATION.contains(raw.last())) {
            raw = raw.dropLast(1)
            end--
        }
        if (raw.isEmpty()) continue
        val url = normalizeWebUrl(raw) ?: continue
        out += LinkSpan(start = start, end = end, url = url)
    }
    return out
}

/**
 * Turn a matched chunk of text into an openable http(s) URL, or null when it can't safely be one.
 * A chunk that already declares a scheme keeps it only if that scheme is http/https; everything
 * else (intent:, file:, javascript:, custom app schemes) is refused outright.
 */
fun normalizeWebUrl(raw: String): String? {
    val candidate = raw.trim()
    if (candidate.isEmpty()) return null
    val schemeSeparator = candidate.indexOf("://")
    if (schemeSeparator >= 0) {
        return if (isSafeWebUrl(candidate)) candidate else null
    }
    // No scheme at all: a bare host like "youtube.com/watch?v=x". A lone ":" before any "/" means
    // some other scheme is being smuggled in (e.g. "javascript:alert(1)") — refuse it.
    val firstSlash = candidate.indexOf('/')
    val firstColon = candidate.indexOf(':')
    if (firstColon >= 0 && (firstSlash < 0 || firstColon < firstSlash)) {
        // Allow "host:port/..." but nothing that looks like an alien scheme.
        val afterColon = candidate.substring(firstColon + 1)
        val port = afterColon.takeWhile { it.isDigit() }
        if (port.isEmpty()) return null
    }
    return "https://$candidate"
}

/** True only for http/https URLs — the only two schemes a note is ever allowed to open. */
fun isSafeWebUrl(url: String): Boolean {
    val lower = url.trim().lowercase()
    return lower.startsWith("http://") || lower.startsWith("https://")
}
