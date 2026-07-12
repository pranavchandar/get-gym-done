package com.getgymdone.app.domain

import com.getgymdone.app.data.db.entities.SetLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProgressionAdviceTest {

    /** [at] orders sessions; sets sharing a [session] id are grouped as one session. */
    private fun set(session: String, setNumber: Int, weightKg: Double, reps: Int, at: Long) =
        SetLog(
            id = "$session-$setNumber",
            sessionId = session,
            exerciseId = "ex",
            setNumber = setNumber,
            weightKg = weightKg,
            reps = reps,
            completedAt = at,
        )

    @Test fun `suggests next load when a set position stalls at top reps`() {
        val history = listOf(
            set("a", 1, 100.0, 8, at = 1000), set("a", 2, 100.0, 8, at = 1000),
            set("b", 1, 100.0, 8, at = 2000), set("b", 2, 100.0, 8, at = 2000),
        )
        val s = weightIncreaseSuggestion(history, repsHigh = 8, incrementKg = 2.5)
        assertNotNull(s)
        assertEquals(100.0, s!!.currentWeightKg, 1e-9)
        assertEquals(102.5, s.suggestedWeightKg, 1e-9)
        assertEquals(8, s.reps)
    }

    @Test fun `reports the lowest reps hit across the window`() {
        val history = listOf(
            set("a", 1, 100.0, 10, at = 1000),
            set("b", 1, 100.0, 8, at = 2000),
        )
        assertEquals(8, weightIncreaseSuggestion(history, repsHigh = 8, incrementKg = 2.5)!!.reps)
    }

    @Test fun `picks the heaviest qualifying set position`() {
        val history = listOf(
            set("a", 1, 60.0, 10, at = 1000), set("a", 2, 100.0, 8, at = 1000),
            set("b", 1, 60.0, 10, at = 2000), set("b", 2, 100.0, 8, at = 2000),
        )
        val s = weightIncreaseSuggestion(history, repsHigh = 8, incrementKg = 2.5)!!
        assertEquals(100.0, s.currentWeightKg, 1e-9)
        assertEquals(102.5, s.suggestedWeightKg, 1e-9)
    }

    @Test fun `returns null with too little history`() {
        val history = listOf(set("a", 1, 100.0, 8, at = 1000))
        assertNull(weightIncreaseSuggestion(history, repsHigh = 8, incrementKg = 2.5))
    }

    @Test fun `returns null when the weight is still climbing`() {
        val history = listOf(
            set("a", 1, 100.0, 8, at = 1000),
            set("b", 1, 102.5, 8, at = 2000),
        )
        assertNull(weightIncreaseSuggestion(history, repsHigh = 8, incrementKg = 2.5))
    }

    @Test fun `returns null when top reps are not yet hit`() {
        val history = listOf(
            set("a", 1, 100.0, 6, at = 1000),
            set("b", 1, 100.0, 6, at = 2000),
        )
        assertNull(weightIncreaseSuggestion(history, repsHigh = 8, incrementKg = 2.5))
    }

    @Test fun `ignores a set position not performed in every session`() {
        val history = listOf(
            set("a", 1, 100.0, 8, at = 1000),
            set("b", 1, 100.0, 8, at = 2000), set("b", 2, 100.0, 8, at = 2000),
        )
        // set 2 appears once -> ignored; set 1 stalled across both -> drives the suggestion.
        val s = weightIncreaseSuggestion(history, repsHigh = 8, incrementKg = 2.5)!!
        assertEquals(102.5, s.suggestedWeightKg, 1e-9)
    }
}
