package com.calcplus.calculator.core.domain.model

/**
 * Retention rules for "Recently deleted" (iteration-2-decisions §3, §11):
 * items stay for [RETENTION_DAYS] from their `deletedAt` stamp, then are
 * purged — rows AND files — at app start and on every transition to Unlocked.
 * Mirrors iOS `enum TrashPolicy`; the numbers are shared verbatim.
 */
object TrashPolicy {
    const val RETENTION_DAYS = 30

    private const val DAY_MS = 86_400_000L

    /** Retention expressed in epoch-millis distance. */
    const val RETENTION_MS: Long = RETENTION_DAYS * DAY_MS

    /** The instant at which an item stamped [deletedAt] becomes purgeable. */
    fun expiresAt(deletedAt: Long): Long = deletedAt + RETENTION_MS

    /** Inclusive at exactly [RETENTION_DAYS] (same rule as iOS `isExpired`). */
    fun isExpired(deletedAt: Long, now: Long): Boolean = expiresAt(deletedAt) <= now

    /**
     * The `deletedAt` threshold at or below which an item is expired at [now];
     * the DAO expiry queries take this directly (`deletedAt <= :cutoff`).
     */
    fun expiryCutoff(now: Long): Long = now - RETENTION_MS

    /**
     * Whole days until expiry, rounded UP, clamped at 0. A freshly trashed
     * item reads "30 days left"; anything at or past expiry reads 0.
     */
    fun daysLeft(deletedAt: Long, now: Long): Int {
        val remaining = expiresAt(deletedAt) - now
        if (remaining <= 0L) return 0
        return ((remaining + DAY_MS - 1) / DAY_MS).toInt()
    }
}
