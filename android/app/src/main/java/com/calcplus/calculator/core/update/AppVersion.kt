package com.calcplus.calculator.core.update

/**
 * Dotted-numeric version comparison (decisions §13, shared rule with iOS).
 *
 * Component by component, missing components treated as `0` — so `1.0` and
 * `1.0.0` are equal (iOS ships `CFBundleShortVersionString` `1.0`, Android
 * ships `versionName` `1.0.0` and the two must never nag each other).
 *
 * Anything unparseable (empty, non-numeric, a stray suffix) compares as
 * **not newer**, so a malformed or hostile `version.json` can never produce a
 * bogus "update available". Pure and side-effect free: no I/O, no logging.
 */
object AppVersion {
    /**
     * True only when [latest] is a strictly greater dotted-numeric version than
     * [current]. False when they are equal, when [latest] is older, and when
     * either string is not a plain dotted-numeric version.
     */
    fun isNewer(latest: String, current: String): Boolean {
        val latestParts = parse(latest) ?: return false
        val currentParts = parse(current) ?: return false
        val size = maxOf(latestParts.size, currentParts.size)
        for (index in 0 until size) {
            // Missing components are 0: "1.0" == "1.0.0", "1.0.1" > "1.0".
            val l = latestParts.getOrElse(index) { 0L }
            val c = currentParts.getOrElse(index) { 0L }
            if (l != c) return l > c
        }
        return false
    }

    /**
     * `"1.2.3"` → `[1, 2, 3]`; null for anything that is not one or more
     * non-negative integers separated by single dots. Digits only on purpose:
     * `1.0-beta`, `v1.0`, `1..0`, `" 1.0"` and `""` are all unparseable and so
     * compare as "not newer".
     */
    private fun parse(version: String): List<Long>? {
        if (version.isEmpty()) return null
        val parts = version.split('.')
        val numbers = ArrayList<Long>(parts.size)
        for (part in parts) {
            if (part.isEmpty() || part.any { it !in '0'..'9' }) return null
            numbers += part.toLongOrNull() ?: return null
        }
        return numbers
    }
}
