package com.getgymdone.app.domain

import com.getgymdone.app.data.db.entities.WorkoutDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DayRotationTest {

    private fun day(number: Int, rest: Boolean = false) =
        WorkoutDay(id = "d$number", splitId = "s", dayNumber = number, name = "Day $number", muscleGroups = emptyList(), isRestDay = rest)

    // ── nextDayNumber ────────────────────────────────────────────────────────

    @Test fun `nextDayNumber defaults to 1 with no history`() {
        assertEquals(1, nextDayNumber(lastDayNumber = null, dayCount = 4))
    }

    @Test fun `nextDayNumber wraps at the end of the cycle`() {
        assertEquals(3, nextDayNumber(lastDayNumber = 2, dayCount = 4))
        assertEquals(1, nextDayNumber(lastDayNumber = 4, dayCount = 4))
    }

    @Test fun `nextDayNumber is safe for an empty split`() {
        assertEquals(1, nextDayNumber(lastDayNumber = 3, dayCount = 0))
    }

    // ── nextWorkoutDay ───────────────────────────────────────────────────────

    @Test fun `nextWorkoutDay skips a rest day in the rotation`() {
        // 1 train, 2 train, 3 REST, 4 train. Last completed day 2 -> 3 is rest -> land on 4.
        val days = listOf(day(1), day(2), day(3, rest = true), day(4))
        assertEquals(4, nextWorkoutDay(days, lastCompletedDayNumber = 2)?.dayNumber)
    }

    @Test fun `nextWorkoutDay wraps past trailing rest days back to day 1`() {
        val days = listOf(day(1), day(2), day(3, rest = true), day(4, rest = true))
        assertEquals(1, nextWorkoutDay(days, lastCompletedDayNumber = 2)?.dayNumber)
    }

    @Test fun `nextWorkoutDay starts at first training day with no history`() {
        val days = listOf(day(1, rest = true), day(2), day(3))
        // No completion yet -> first trainable day by number is day 2.
        assertEquals(2, nextWorkoutDay(days, lastCompletedDayNumber = null)?.dayNumber)
    }

    @Test fun `nextWorkoutDay returns null when the split is rest-only`() {
        val days = listOf(day(1, rest = true), day(2, rest = true))
        assertNull(nextWorkoutDay(days, lastCompletedDayNumber = 1))
    }

    // ── maxConsecutiveRestDays ───────────────────────────────────────────────

    @Test fun `maxConsecutiveRestDays is zero without rest days`() {
        assertEquals(0, maxConsecutiveRestDays(listOf(day(1), day(2), day(3))))
    }

    @Test fun `maxConsecutiveRestDays counts a mid-cycle rest run`() {
        val days = listOf(day(1), day(2, rest = true), day(3, rest = true), day(4))
        assertEquals(2, maxConsecutiveRestDays(days))
    }

    @Test fun `maxConsecutiveRestDays counts the wrap-around run`() {
        // Rest on days 4, 1, 3 (only day 2 trains): the cyclic run 3-4-1 is 3 long.
        val days = listOf(day(1, rest = true), day(2), day(3, rest = true), day(4, rest = true))
        assertEquals(3, maxConsecutiveRestDays(days))
    }

    @Test fun `maxConsecutiveRestDays caps at the cycle length when all rest`() {
        val days = listOf(day(1, rest = true), day(2, rest = true))
        assertEquals(2, maxConsecutiveRestDays(days))
    }
}
