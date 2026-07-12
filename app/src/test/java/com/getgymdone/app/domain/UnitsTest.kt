package com.getgymdone.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {

    @Test fun `kg displays unchanged in kg`() {
        assertEquals(100.0, 100.0.kgToDisplay(WeightUnit.Kg), 1e-9)
    }

    @Test fun `kg converts to lbs for display`() {
        assertEquals(220.462, 100.0.kgToDisplay(WeightUnit.Lbs), 1e-3)
    }

    @Test fun `display to kg round-trips in both units`() {
        for (unit in WeightUnit.entries) {
            val display = 137.5.kgToDisplay(unit)
            assertEquals(137.5, display.displayToKg(unit), 1e-9)
        }
    }

    @Test fun `displayStep is the natural plate jump per unit`() {
        assertEquals(2.5, WeightUnit.Kg.displayStep, 1e-9)
        assertEquals(5.0, WeightUnit.Lbs.displayStep, 1e-9)
    }

    @Test fun `fromStored parses case-insensitively and defaults to kg`() {
        assertEquals(WeightUnit.Lbs, WeightUnit.fromStored("LBS"))
        assertEquals(WeightUnit.Kg, WeightUnit.fromStored("kg"))
        assertEquals(WeightUnit.Kg, WeightUnit.fromStored("anything"))
    }
}
