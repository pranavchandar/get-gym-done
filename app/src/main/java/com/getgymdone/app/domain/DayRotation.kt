package com.getgymdone.app.domain

/**
 * "What workout day should I show on Home?"
 *
 * Rule (from the handover):
 *  - If a session is in progress, show that day (handled by caller).
 *  - Otherwise the next day is (lastDayNumber % dayCount) + 1.
 *  - If no sessions exist yet, default to 1.
 */
fun nextDayNumber(lastDayNumber: Int?, dayCount: Int): Int {
    if (dayCount <= 0) return 1
    if (lastDayNumber == null) return 1
    return (lastDayNumber % dayCount) + 1
}
